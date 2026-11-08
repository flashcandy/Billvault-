package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.data.model.CategoryMarker
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.utils.DateUtils

@Composable
fun NothingHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(NothingRed)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = DotoFontFamily,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = SpaceGroteskFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun NothingMarkerDot(
    colorMarker: String,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isSquare = colorMarker.startsWith("SQUARE")
    val color = when (colorMarker) {
        CategoryMarker.DOT_RED, CategoryMarker.SQUARE_RED -> if (isSelected) Color.White else NothingRed
        CategoryMarker.DOT_WHITE, CategoryMarker.SQUARE_WHITE -> if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
        else -> if (isSelected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(7.dp)
            .clip(if (isSquare) RectangleShape else CircleShape)
            .background(color)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NothingChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorMarker: String? = null,
    onLongClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    val backgroundColor = if (isSelected) NothingRed else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
    val borderColor = if (isSelected) NothingRed else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (colorMarker != null) {
                NothingMarkerDot(colorMarker = colorMarker, isSelected = isSelected)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = contentColor
            )
        }
    }
}

@Composable
fun NothingStatusDot(status: DateUtils.ExpiryStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DateUtils.ExpiryStatus.EXPIRED -> NothingRed
        DateUtils.ExpiryStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.onBackground
        DateUtils.ExpiryStatus.ACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun NothingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    testTag: String = ""
) {
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isPrimary -> NothingRed
        else -> MaterialTheme.colorScheme.surface
    }

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isPrimary -> Color.White
        else -> MaterialTheme.colorScheme.onBackground
    }

    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        isPrimary -> NothingRed
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .testTag(testTag)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = contentColor
            )
        }
    }
}

@Composable
fun NothingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    errorMessage: String? = null,
    helperText: String? = null,
    counterText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    testTag: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp
            ),
            color = if (errorMessage != null) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            isError = errorMessage != null,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (errorMessage != null) NothingRed else NothingRed,
                unfocusedBorderColor = if (errorMessage != null) NothingRed else MaterialTheme.colorScheme.outline,
                errorBorderColor = NothingRed,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                errorTextColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .then(textFieldModifier)
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = NothingRed,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("${testTag}_error")
            )
        }
        if (!helperText.isNullOrBlank() || !counterText.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!helperText.isNullOrBlank()) {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = SpaceGroteskFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("${testTag}_helper")
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (!counterText.isNullOrBlank()) {
                    Text(
                        text = counterText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = DotoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (errorMessage != null) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("${testTag}_counter")
                    )
                }
            }
        }
    }
}

@Composable
fun NothingDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
fun CategoryManageDialog(
    initialCategory: Category? = null, // null means creating a new custom category
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, colorMarker: String) -> Unit,
    onDeleteRequest: ((Category) -> Unit)? = null
) {
    var name by remember(initialCategory) { mutableStateOf(initialCategory?.name ?: "") }
    var selectedMarker by remember(initialCategory) {
        mutableStateOf(initialCategory?.colorMarker ?: CategoryMarker.DOT_RED)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && initialCategory != null && !initialCategory.isDefault && onDeleteRequest != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    text = "DELETE CATEGORY?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Text(
                    text = "Deleting '${initialCategory.name}' will move all its items to UNCATEGORIZED.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            confirmButton = {
                NothingButton(
                    text = "CONFIRM DELETE",
                    onClick = {
                        confirmDelete = false
                        onDeleteRequest(initialCategory)
                    },
                    modifier = Modifier.width(150.dp),
                    testTag = "confirm_delete_category_button"
                )
            },
            dismissButton = {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = { confirmDelete = false },
                    modifier = Modifier.width(110.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCategory == null) "NEW CATEGORY" else "EDIT CATEGORY",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = DotoFontFamily,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NothingTextField(
                    value = name,
                    onValueChange = { if (it.length <= 25) name = it },
                    label = "CATEGORY NAME (MAX 20 CHARS)",
                    placeholder = "e.g. APPLIANCES",
                    errorMessage = errorMessage,
                    testTag = "category_dialog_name_input"
                )

                Text(
                    text = "NOTHING OS MARKER",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val markers = listOf(
                        CategoryMarker.DOT_RED to "DOT RED",
                        CategoryMarker.DOT_WHITE to "DOT WHT",
                        CategoryMarker.DOT_GRAY to "DOT GRY",
                        CategoryMarker.SQUARE_RED to "SQ RED",
                        CategoryMarker.SQUARE_WHITE to "SQ WHT"
                    )
                    markers.forEach { (marker, label) ->
                        val selected = selectedMarker == marker
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (selected) NothingRed.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (selected) NothingRed else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedMarker = marker }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NothingMarkerDot(colorMarker = marker, isSelected = false)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }

                if (initialCategory != null && !initialCategory.isDefault && onDeleteRequest != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    NothingButton(
                        text = "DELETE CATEGORY",
                        isPrimary = false,
                        onClick = { confirmDelete = true },
                        testTag = "delete_category_button"
                    )
                }
            }
        },
        confirmButton = {
            NothingButton(
                text = "SAVE",
                onClick = { onSave(name, selectedMarker) },
                modifier = Modifier.width(120.dp),
                testTag = "save_category_button"
            )
        },
        dismissButton = {
            NothingButton(
                text = "CANCEL",
                isPrimary = false,
                onClick = onDismiss,
                modifier = Modifier.width(110.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    )
}
