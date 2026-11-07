package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import java.time.Instant
import java.time.ZoneId
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.example.data.model.Attachment
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingMarkerDot
import com.example.ui.components.NothingStatusDot
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils
import com.example.utils.FileStorageHelper
import kotlin.math.abs

@Composable
fun InvoiceDetailScreen(
    itemId: Long,
    viewModel: MainViewModel,
    onEditItem: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val detailsState by viewModel.repository.getItemWithDetailsById(itemId)
        .collectAsStateWithLifecycle(initialValue = null)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var attachmentToDelete by remember { mutableStateOf<Attachment?>(null) }

    val details = detailsState

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "ITEM DETAILS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onEditItem(itemId) },
                    modifier = Modifier.testTag("detail_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Item",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.testTag("detail_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = NothingRed
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (details == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ITEM NOT FOUND",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val item = details.item
        val category = details.category
        val customFields = details.customFields.sortedBy { it.sortOrder }
        val attachments = details.attachments.sortedBy { it.addedAt }
        val status = DateUtils.getExpiryStatus(item.expiryDate)
        val daysRemaining = DateUtils.getDaysRemaining(item.expiryDate)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header & Warranty Countdown Box
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (category != null) {
                                    NothingMarkerDot(colorMarker = category.colorMarker)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = category.name.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            NothingStatusDot(status = status)
                        }

                        Text(
                            text = item.title.uppercase(),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (item.amount != null && item.amount > 0) {
                            Text(
                                text = DateUtils.formatCurrency(item.amount),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = DotoFontFamily,
                                    letterSpacing = 1.sp
                                ),
                                color = NothingRed
                            )
                        }

                        NothingDivider(modifier = Modifier.padding(vertical = 4.dp))

                        if (item.sellerName.isNotBlank()) {
                            DetailMetaRow(label = "SELLER / STORE", value = item.sellerName)
                        }
                        if (item.invoiceNumber.isNotBlank()) {
                            DetailMetaRow(label = "INVOICE / BILL #", value = "#${item.invoiceNumber}")
                        }
                        if (item.orderId.isNotBlank()) {
                            DetailMetaRow(label = "ORDER ID", value = item.orderId)
                        }
                        DetailMetaRow(label = "PURCHASE DATE", value = DateUtils.formatDate(item.purchaseDate))

                        if (item.warrantyValue != null && item.warrantyValue > 0) {
                            DetailMetaRow(
                                label = "WARRANTY DURATION",
                                value = "${item.warrantyValue} ${item.warrantyUnit}"
                            )
                        }

                        // WARRANTY START (Editable)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val startMillis = item.warrantyStartDate ?: item.purchaseDate
                                    val localDate = Instant.ofEpochMilli(startMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    DatePickerDialog(
                                        context,
                                        { _, year, monthZeroBased, dayOfMonth ->
                                            val pickedMillis = DateUtils.localDateToMillis(year, monthZeroBased + 1, dayOfMonth)
                                            viewModel.updateWarrantyStartDate(item.id, pickedMillis)
                                        },
                                        localDate.year,
                                        localDate.monthValue - 1,
                                        localDate.dayOfMonth
                                    ).show()
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "WARRANTY START",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit warranty start date",
                                    modifier = Modifier.size(14.dp),
                                    tint = NothingRed
                                )
                            }
                            Text(
                                text = DateUtils.formatDate(item.warrantyStartDate ?: item.purchaseDate),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "Use the delivery date if it differs from purchase.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        DetailMetaRow(
                            label = "EXPIRY",
                            value = if (item.expiryDate != null) DateUtils.formatDate(item.expiryDate) else "NONE"
                        )

                        NothingDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Countdown Banner
                        val (bannerText, bannerBg, bannerColor) = when {
                            daysRemaining == null -> Triple(
                                "NO WARRANTY / NO EXPIRY SCHEDULED",
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            status == DateUtils.ExpiryStatus.EXPIRED -> Triple(
                                "EXPIRED (${abs(daysRemaining)} DAYS AGO)",
                                NothingRed.copy(alpha = 0.2f),
                                NothingRed
                            )
                            status == DateUtils.ExpiryStatus.EXPIRING_SOON -> Triple(
                                "EXPIRING SOON: $daysRemaining DAYS REMAINING",
                                MaterialTheme.colorScheme.onBackground,
                                MaterialTheme.colorScheme.background
                            )
                            else -> Triple(
                                "ACTIVE: $daysRemaining DAYS REMAINING",
                                NothingRed.copy(alpha = 0.1f),
                                NothingRed
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(bannerBg)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bannerText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = bannerColor
                            )
                        }
                    }
                }
            }

            // Custom Fields Section (in sortOrder)
            if (customFields.isNotEmpty()) {
                item {
                    Text(
                        text = "CUSTOM FIELDS (${customFields.size})",
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
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            customFields.forEachIndexed { idx, field ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = field.name.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = field.type,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    Text(
                                        text = field.value.ifBlank { "—" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                if (idx < customFields.lastIndex) {
                                    NothingDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Attachments Section
            if (attachments.isNotEmpty()) {
                item {
                    Text(
                        text = "ATTACHMENTS (${attachments.size})",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = DotoFontFamily,
                            letterSpacing = 1.5.sp
                        ),
                        color = NothingRed
                    )
                }

                items(attachments, key = { it.id }) { attachment ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${attachment.mimeType.uppercase()} • ${DateUtils.formatFileSize(attachment.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    FileStorageHelper.openAttachmentWithIntent(context, attachment)
                                },
                                modifier = Modifier.testTag("preview_attachment_${attachment.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Preview Attachment",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            IconButton(
                                onClick = { attachmentToDelete = attachment },
                                modifier = Modifier.testTag("delete_attachment_${attachment.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Attachment",
                                    tint = NothingRed
                                )
                            }
                        }
                    }
                }
            }

            // Notes Section
            if (item.notes.isNotBlank()) {
                item {
                    Text(
                        text = "NOTES",
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
                            .padding(16.dp)
                    ) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (attachmentToDelete != null) {
        val targetAtt = attachmentToDelete!!
        AlertDialog(
            onDismissRequest = { attachmentToDelete = null },
            title = {
                Text(
                    text = "DELETE ATTACHMENT?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Text(
                    text = "Remove '${targetAtt.fileName}' from this item and delete its stored file?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NothingButton(
                    text = "DELETE FILE",
                    onClick = {
                        viewModel.deleteAttachment(targetAtt)
                        attachmentToDelete = null
                    },
                    modifier = Modifier.width(140.dp)
                )
            },
            dismissButton = {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = { attachmentToDelete = null },
                    modifier = Modifier.width(100.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "DELETE ITEM?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this item, its reminders, and all stored attachment files? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NothingButton(
                    text = "CONFIRM DELETE",
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteItem(itemId) {
                            onBack()
                        }
                    },
                    modifier = Modifier.width(160.dp)
                )
            },
            dismissButton = {
                NothingButton(
                    text = "CANCEL",
                    isPrimary = false,
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.width(100.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun DetailMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
