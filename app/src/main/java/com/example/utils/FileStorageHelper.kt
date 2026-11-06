package com.example.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.Attachment
import com.example.data.preferences.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val existingAttachmentId: Long? = null,
    val uri: Uri? = null,
    val existingLocalPath: String? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

object FileStorageHelper {

    fun getAppPrivateBaseDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(baseDir, "BillValid").apply { mkdirs() }
    }

    fun getDisplayStoragePath(context: Context, settingsManager: SettingsManager): String {
        val safUriStr = settingsManager.safFolderUri
        val privatePath = "Android/data/${context.packageName}/files/BillValid/<category>/<item-id>/"
        return if (!safUriStr.isNullOrBlank()) {
            val readableSaf = Uri.decode(safUriStr).substringAfterLast(':')
            "$privatePath\n+ Backup Folder: $readableSaf/BillValid/"
        } else {
            privatePath
        }
    }

    fun getItemStorageDir(context: Context, categoryName: String, itemId: Long): File {
        val safeCategory = ValidationUtils.sanitizeFolderName(categoryName)
        val dir = File(getAppPrivateBaseDir(context), "$safeCategory/$itemId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun inspectUriMetadata(context: Context, uri: Uri): PendingAttachment {
        var displayName = "attachment"
        var size = 0L
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeType(uri.toString())

        if (uri.scheme == "content") {
            val cursor: Cursor? = try {
                context.contentResolver.query(uri, null, null, null, null)
            } catch (_: Exception) {
                null
            }
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        displayName = it.getString(nameIndex) ?: displayName
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !it.isNull(sizeIndex)) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        } else if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            displayName = file.name.ifBlank { "photo.jpg" }
            size = file.length()
        }

        val sanitizedName = ValidationUtils.sanitizeFileName(displayName)
        return PendingAttachment(
            uri = uri,
            fileName = sanitizedName,
            mimeType = mimeType,
            sizeBytes = size
        )
    }

    suspend fun saveAttachmentToStorage(
        context: Context,
        settingsManager: SettingsManager,
        pending: PendingAttachment,
        categoryName: String,
        itemId: Long
    ): Attachment? = withContext(Dispatchers.IO) {
        val targetDir = getItemStorageDir(context, categoryName, itemId)

        // If this is an already-saved attachment and the category didn't move its file, keep or move it
        if (pending.existingLocalPath != null && pending.uri == null) {
            val existingFile = File(pending.existingLocalPath)
            if (existingFile.exists()) {
                val finalFile = if (existingFile.parentFile?.absolutePath == targetDir.absolutePath) {
                    existingFile
                } else {
                    val destFile = File(targetDir, existingFile.name)
                    existingFile.copyTo(destFile, overwrite = true)
                    existingFile.delete()
                    destFile
                }
                return@withContext Attachment(
                    id = pending.existingAttachmentId ?: 0L,
                    itemId = itemId,
                    fileName = pending.fileName,
                    mimeType = pending.mimeType,
                    localPath = finalFile.absolutePath,
                    sizeBytes = finalFile.length(),
                    addedAt = System.currentTimeMillis()
                )
            }
        }

        val sourceUri = pending.uri ?: return@withContext null
        val uniqueToken = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val uniqueFileName = ValidationUtils.generateUniqueFileName(pending.fileName, uniqueToken)
        val destFile = File(targetDir, uniqueFileName)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                if (sourceUri.scheme == "file") {
                    val src = File(sourceUri.path ?: return@withContext null)
                    src.copyTo(destFile, overwrite = true)
                } else {
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }

        // Also mirror to SAF folder if configured by user in Settings
        val safTreeUriStr = settingsManager.safFolderUri
        if (!safTreeUriStr.isNullOrBlank()) {
            mirrorFileToSafTree(
                context = context,
                treeUri = Uri.parse(safTreeUriStr),
                categoryName = ValidationUtils.sanitizeFolderName(categoryName),
                itemId = itemId.toString(),
                fileName = uniqueFileName,
                mimeType = pending.mimeType,
                sourceFile = destFile
            )
        }

        Attachment(
            id = pending.existingAttachmentId ?: 0L,
            itemId = itemId,
            fileName = uniqueFileName,
            mimeType = pending.mimeType,
            localPath = destFile.absolutePath,
            sizeBytes = destFile.length(),
            addedAt = System.currentTimeMillis()
        )
    }

    private fun mirrorFileToSafTree(
        context: Context,
        treeUri: Uri,
        categoryName: String,
        itemId: String,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ) {
        try {
            val resolver = context.contentResolver
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            val billValidDirUri = findOrCreateSafDirectory(resolver, treeUri, rootDocUri, "BillValid") ?: return
            val categoryDirUri = findOrCreateSafDirectory(resolver, treeUri, billValidDirUri, categoryName) ?: return
            val itemDirUri = findOrCreateSafDirectory(resolver, treeUri, categoryDirUri, itemId) ?: return

            val newDocUri = DocumentsContract.createDocument(resolver, itemDirUri, mimeType, fileName) ?: return
            resolver.openOutputStream(newDocUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findOrCreateSafDirectory(
        resolver: android.content.ContentResolver,
        treeUri: Uri,
        parentDocUri: Uri,
        dirName: String
    ): Uri? {
        val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        try {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    if (name == dirName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to createDocument
        }

        return try {
            DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                dirName
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteAttachmentFile(localPath: String) {
        try {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            }
            val parent = file.parentFile
            if (parent != null && parent.exists() && parent.listFiles().isNullOrEmpty()) {
                parent.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteItemAttachmentsFolder(context: Context, categoryName: String, itemId: Long, attachments: List<Attachment>) {
        attachments.forEach { deleteAttachmentFile(it.localPath) }
        try {
            val dir = getItemStorageDir(context, categoryName, itemId)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openAttachmentWithIntent(context: Context, attachment: Attachment): Boolean {
        return try {
            val file = File(attachment.localPath)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun guessMimeType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
