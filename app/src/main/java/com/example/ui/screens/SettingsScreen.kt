package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Category
import com.example.data.preferences.SettingsManager
import com.example.ui.components.CategoryManageDialog
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingChip
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingMarkerDot
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.FileStorageHelper

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onThemeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val settings = viewModel.settingsManager
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var currentTheme by remember { mutableStateOf(settings.themeMode) }
    var safFolderUri by remember { mutableStateOf(settings.safFolderUri) }
    var showClearDialog by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryDialogError by remember { mutableStateOf<String?>(null) }

    // Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE) launcher
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            settings.safFolderUri = uri.toString()
            safFolderUri = uri.toString()
        }
    }

    val storagePathText = remember(safFolderUri) {
        FileStorageHelper.getDisplayStoragePath(context, settings)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NothingHeader(
                    title = "SETTINGS",
                    subtitle = "Storage folders, categories, appearance, and vault data"
                )
            }

            // 1. Attachment Storage & SAF Backup Folder
            item {
                Text(
                    text = "ATTACHMENT STORAGE & BACKUP",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ACTIVE STORAGE PATH",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = storagePathText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = SpaceGroteskFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.testTag("settings_storage_path_text")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        NothingButton(
                            text = if (safFolderUri.isNullOrBlank()) {
                                "CHOOSE VISIBLE BACKUP FOLDER (SAF)"
                            } else {
                                "CHANGE VISIBLE BACKUP FOLDER"
                            },
                            icon = Icons.Default.FolderOpen,
                            isPrimary = false,
                            onClick = { safTreeLauncher.launch(null) },
                            testTag = "button_choose_saf_folder"
                        )

                        if (!safFolderUri.isNullOrBlank()) {
                            NothingButton(
                                text = "USE APP-PRIVATE STORAGE ONLY",
                                isPrimary = false,
                                onClick = {
                                    settings.safFolderUri = null
                                    safFolderUri = null
                                },
                                testTag = "button_reset_saf_folder"
                            )
                        }
                    }
                }
            }

            item { NothingDivider() }

            // 2. Categories Management
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CATEGORIES (${categories.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = DotoFontFamily,
                            letterSpacing = 1.5.sp
                        ),
                        color = NothingRed
                    )

                    NothingChip(
                        text = "+ NEW",
                        isSelected = false,
                        onClick = {
                            editingCategory = null
                            categoryDialogError = null
                            showCategoryDialog = true
                        },
                        testTag = "settings_new_category_button"
                    )
                }
            }

            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .clickable {
                            editingCategory = category
                            categoryDialogError = null
                            showCategoryDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NothingMarkerDot(colorMarker = category.colorMarker)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (category.isDefault) "DEFAULT (RENAMEABLE)" else "CUSTOM (RENAME / DELETE)",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            editingCategory = category
                            categoryDialogError = null
                            showCategoryDialog = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Category",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item { NothingDivider() }

            // 3. Appearance & Theme
            item {
                Text(
                    text = "APPEARANCE & THEME",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SettingsManager.THEME_DARK to "Dark (Nothing)",
                        SettingsManager.THEME_LIGHT to "Light",
                        SettingsManager.THEME_SYSTEM to "System"
                    ).forEach { (mode, label) ->
                        NothingChip(
                            text = label,
                            isSelected = currentTheme == mode,
                            onClick = {
                                currentTheme = mode
                                settings.themeMode = mode
                                onThemeChanged(mode)
                            },
                            testTag = "theme_$mode"
                        )
                    }
                }
            }

            item { NothingDivider() }

            // 4. Data Management
            item {
                Text(
                    text = "DATA MANAGEMENT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            item {
                NothingButton(
                    text = "CLEAR ALL VAULT DATA & FILES",
                    isPrimary = false,
                    onClick = { showClearDialog = true },
                    testTag = "button_clear_data"
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showCategoryDialog) {
        CategoryManageDialog(
            initialCategory = editingCategory,
            errorMessage = categoryDialogError,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, marker ->
                val target = editingCategory
                if (target == null) {
                    viewModel.createCategory(name, marker) { res ->
                        res.onSuccess {
                            showCategoryDialog = false
                        }.onFailure { err ->
                            categoryDialogError = err.message
                        }
                    }
                } else {
                    viewModel.renameCategory(target, name, marker) { res ->
                        res.onSuccess {
                            showCategoryDialog = false
                        }.onFailure { err ->
                            categoryDialogError = err.message
                        }
                    }
                }
            },
            onDeleteRequest = { catToDelete ->
                viewModel.deleteCustomCategory(catToDelete) {
                    showCategoryDialog = false
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "CLEAR ALL APP DATA?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Text(
                    text = "This will permanently wipe all stored bills, custom fields, attachment files, and scheduled reminders.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NothingButton(
                    text = "WIPE DATA",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData()
                        safFolderUri = null
                    },
                    modifier = Modifier.width(140.dp)
                )
            },
            dismissButton = {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = { showClearDialog = false },
                    modifier = Modifier.width(100.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
