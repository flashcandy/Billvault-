package com.example.utils

import java.util.Locale

sealed class ValidationResult {
    data class Valid(val cleanedValue: String) : ValidationResult()
    data class Invalid(val errorMessage: String) : ValidationResult()
}

object ValidationUtils {

    const val MAX_CATEGORY_NAME_LENGTH = 20
    const val MAX_TITLE_LENGTH = 60

    /**
     * Replaces line breaks (\r\n, \n, \r) with spaces.
     */
    fun replaceLineBreaksWithSpaces(raw: String): String {
        return raw.replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
    }

    /**
     * Normalizes a title:
     * - Replaces line breaks with spaces
     * - Collapses multiple spaces into a single space
     * - Trims leading and trailing whitespace
     */
    fun normalizeTitle(raw: String): String {
        return replaceLineBreaksWithSpaces(raw)
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Validates a title:
     * - Must not be blank
     * - Normalized length must not exceed MAX_TITLE_LENGTH (60)
     */
    fun validateTitle(raw: String): ValidationResult {
        val normalized = normalizeTitle(raw)
        if (normalized.isBlank()) {
            return ValidationResult.Invalid("Title is required")
        }
        if (normalized.length > MAX_TITLE_LENGTH) {
            return ValidationResult.Invalid("Title cannot exceed $MAX_TITLE_LENGTH characters (currently ${normalized.length})")
        }
        return ValidationResult.Valid(normalized)
    }

    /**
      * Validates a category name:
      * - Required (non-blank)
      * - Max 20 characters
      * - Unique (case-insensitive against existing category names, excluding currentName if renaming)
      */
    fun validateCategoryName(
        rawName: String,
        existingNames: List<String>,
        currentName: String? = null
    ): ValidationResult {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Category name is required")
        }
        if (trimmed.length > MAX_CATEGORY_NAME_LENGTH) {
            return ValidationResult.Invalid("Max $MAX_CATEGORY_NAME_LENGTH characters allowed")
        }
        val normalized = trimmed.uppercase(Locale.ROOT)
        val currentNormalized = currentName?.trim()?.uppercase(Locale.ROOT)

        val isDuplicate = existingNames.any { existing ->
            val existingNorm = existing.trim().uppercase(Locale.ROOT)
            existingNorm == normalized && existingNorm != currentNormalized
        }
        if (isDuplicate) {
            return ValidationResult.Invalid("Category name must be unique")
        }

        return ValidationResult.Valid(normalized)
    }

    /**
     * Sanitizes a user-provided file name so it is safe for filesystem and SAF storage.
     */
    fun sanitizeFileName(rawFileName: String): String {
        val withoutPath = rawFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()

        if (withoutPath.isEmpty()) return "attachment"

        val dotIndex = withoutPath.lastIndexOf('.')
        val rawBase = if (dotIndex > 0) withoutPath.substring(0, dotIndex) else withoutPath
        val rawExt = if (dotIndex > 0 && dotIndex < withoutPath.length - 1) {
            withoutPath.substring(dotIndex + 1)
        } else {
            ""
        }

        val cleanedBase = rawBase
            .replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_', '.', ' ')
            .take(50)
            .ifEmpty { "attachment" }

        val cleanedExt = rawExt
            .replace(Regex("""[^a-zA-Z0-9]"""), "")
            .lowercase(Locale.ROOT)
            .take(10)

        return if (cleanedExt.isNotEmpty()) {
            "$cleanedBase.$cleanedExt"
        } else {
            cleanedBase
        }
    }

    /**
     * Generates a unique file name by inserting a unique token before the extension.
     */
    fun generateUniqueFileName(
        rawFileName: String,
        uniqueToken: String = System.currentTimeMillis().toString()
    ): String {
        val sanitized = sanitizeFileName(rawFileName)
        val dotIndex = sanitized.lastIndexOf('.')
        val cleanToken = uniqueToken.replace(Regex("""[^a-zA-Z0-9_-]"""), "")
        return if (dotIndex > 0) {
            val base = sanitized.substring(0, dotIndex)
            val ext = sanitized.substring(dotIndex + 1)
            "${base}_${cleanToken}.$ext"
        } else {
            "${sanitized}_${cleanToken}"
        }
    }

    /**
     * Sanitizes a category name for safe directory creation.
     */
    fun sanitizeFolderName(name: String): String {
        return name.trim()
            .replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
            .replace(Regex("""_+"""), "_")
            .trim('_')
            .ifEmpty { "UNCATEGORIZED" }
    }
}
