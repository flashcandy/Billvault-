package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.SettingsManager
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingChip
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingTextField
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onThemeChanged: (String) -> Unit
) {
    val settings = viewModel.settingsManager

    var provider by remember { mutableStateOf(settings.aiProvider) }
    var geminiKey by remember { mutableStateOf(settings.geminiApiKey) }
    var openRouterKey by remember { mutableStateOf(settings.openRouterApiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModelName) }
    var openRouterModel by remember { mutableStateOf(settings.openRouterModelName) }
    var currentTheme by remember { mutableStateOf(settings.themeMode) }

    var showKeyVisible by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

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
                    subtitle = "Configure AI model provider, API keys, privacy, and theme"
                )
            }

            // Privacy Warning Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NothingRed.copy(alpha = 0.1f))
                        .border(1.dp, NothingRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NothingRed,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PRIVACY & SECURITY NOTICE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = DotoFontFamily,
                                    letterSpacing = 1.2.sp
                                ),
                                color = NothingRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Invoice text is processed by your chosen AI provider (Gemini or OpenRouter). API keys are encrypted via EncryptedSharedPreferences. Document files are stored strictly in app-private storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // AI Provider Selection
            item {
                Text(
                    text = "AI EXTRACTION PROVIDER",
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingChip(
                        text = "Google Gemini API",
                        isSelected = provider == SettingsManager.PROVIDER_GEMINI,
                        onClick = {
                            provider = SettingsManager.PROVIDER_GEMINI
                            settings.aiProvider = SettingsManager.PROVIDER_GEMINI
                        },
                        testTag = "provider_gemini",
                        modifier = Modifier.weight(1f)
                    )

                    NothingChip(
                        text = "OpenRouter API",
                        isSelected = provider == SettingsManager.PROVIDER_OPENROUTER,
                        onClick = {
                            provider = SettingsManager.PROVIDER_OPENROUTER
                            settings.aiProvider = SettingsManager.PROVIDER_OPENROUTER
                        },
                        testTag = "provider_openrouter",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (provider == SettingsManager.PROVIDER_GEMINI) {
                item {
                    NothingTextField(
                        value = geminiModel,
                        onValueChange = {
                            geminiModel = it
                            settings.geminiModelName = it
                        },
                        label = "GEMINI MODEL NAME",
                        placeholder = "gemini-2.0-flash",
                        testTag = "input_gemini_model"
                    )
                }

                item {
                    ApiKeyInputField(
                        value = geminiKey,
                        onValueChange = {
                            geminiKey = it
                            settings.geminiApiKey = it
                        },
                        label = "GEMINI API KEY",
                        placeholder = "AIzaSy...",
                        isVisible = showKeyVisible,
                        onToggleVisibility = { showKeyVisible = !showKeyVisible },
                        testTag = "input_gemini_key"
                    )
                }
            } else {
                item {
                    NothingTextField(
                        value = openRouterModel,
                        onValueChange = {
                            openRouterModel = it
                            settings.openRouterModelName = it
                        },
                        label = "OPENROUTER MODEL NAME",
                        placeholder = "google/gemini-2.0-flash-001",
                        testTag = "input_openrouter_model"
                    )
                }

                item {
                    ApiKeyInputField(
                        value = openRouterKey,
                        onValueChange = {
                            openRouterKey = it
                            settings.openRouterApiKey = it
                        },
                        label = "OPENROUTER API KEY",
                        placeholder = "sk-or-v1-...",
                        isVisible = showKeyVisible,
                        onToggleVisibility = { showKeyVisible = !showKeyVisible },
                        testTag = "input_openrouter_key"
                    )
                }
            }

            item { NothingDivider() }

            // Theme Toggle
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

            // Clear Data
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
                    text = "CLEAR ALL INVOICES & KEYS",
                    isPrimary = false,
                    onClick = { showClearDialog = true },
                    testTag = "button_clear_data"
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
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
                    text = "This will permanently wipe all stored invoices, items, scheduled reminders, and API keys.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NothingButton(
                    text = "WIPE DATA",
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData()
                        geminiKey = ""
                        openRouterKey = ""
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

@Composable
fun ApiKeyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle Visibility"
                    )
                }
            },
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NothingRed,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
