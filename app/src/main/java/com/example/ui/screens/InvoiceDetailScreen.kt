package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvoiceItem
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingStatusDot
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils
import java.io.File

@Composable
fun InvoiceDetailScreen(
    invoiceId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val invoiceWithItemsState by viewModel.repository.getInvoiceWithItemsById(invoiceId).collectAsStateWithLifecycle(initialValue = null)

    var showDeleteDialog by remember { mutableStateOf(false) }

    val invoiceWithItems = invoiceWithItemsState

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
                    text = "INVOICE DETAILS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.testTag("detail_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Invoice",
                        tint = NothingRed
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (invoiceWithItems == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "INVOICE NOT FOUND",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = DotoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val invoice = invoiceWithItems.invoice
        val items = invoiceWithItems.items

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seller & Header Info
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = invoice.sellerName.uppercase(),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = DateUtils.formatCurrency(invoice.totalAmount),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.sp
                            ),
                            color = NothingRed
                        )

                        NothingDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DetailMetaRow(label = "INVOICE NUMBER", value = "#${invoice.invoiceNumber.ifBlank { "N/A" }}")
                        DetailMetaRow(label = "ORDER ID", value = invoice.orderId.ifBlank { "N/A" })
                        DetailMetaRow(label = "INVOICE DATE", value = DateUtils.formatDate(invoice.invoiceDate))
                        if (invoice.taxAmount > 0) {
                            DetailMetaRow(label = "TAX / GST", value = DateUtils.formatCurrency(invoice.taxAmount))
                        }
                    }
                }
            }

            // Document file action
            if (!invoice.sourceFilePath.isNullOrBlank()) {
                item {
                    val file = File(invoice.sourceFilePath)
                    if (file.exists()) {
                        NothingButton(
                            text = "OPEN SOURCE FILE",
                            icon = Icons.Default.OpenInNew,
                            isPrimary = false,
                            onClick = {
                                try {
                                    val uri = Uri.fromFile(file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, if (invoice.sourceType == "PDF") "application/pdf" else "image/*")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }

            // Products section
            item {
                Text(
                    text = "PURCHASED PRODUCTS (${items.size})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            items(items) { item ->
                DetailProductCard(item = item)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "DELETE INVOICE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this invoice and all associated reminders? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                NothingButton(
                    text = "CONFIRM DELETE",
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteInvoice(invoiceId) {
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

@Composable
fun DetailProductCard(item: InvoiceItem) {
    val status = DateUtils.getExpiryStatus(item.warrantyEndDate)
    val daysRemaining = DateUtils.getDaysRemaining(item.warrantyEndDate)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NothingStatusDot(status = status, modifier = Modifier.padding(end = 8.dp))

                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                if (item.amount > 0) {
                    Text(
                        text = DateUtils.formatCurrency(item.amount),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            DetailMetaRow(label = "SERIAL / IMEI", value = item.serialNumber.ifBlank { "N/A" })
            DetailMetaRow(label = "WARRANTY DURATION", value = "${item.warrantyMonths} Months")
            DetailMetaRow(label = "START DATE", value = DateUtils.formatDate(item.warrantyStartDate))
            DetailMetaRow(label = "EXPIRY DATE", value = DateUtils.formatDate(item.warrantyEndDate))

            NothingDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Warranty Countdown Banner
            val (statusText, statusBg, statusColor) = when (status) {
                DateUtils.ExpiryStatus.EXPIRED -> Triple("WARRANTY EXPIRED (${Math.abs(daysRemaining)} DAYS AGO)", NothingRed.copy(alpha = 0.2f), NothingRed)
                DateUtils.ExpiryStatus.EXPIRING_SOON -> Triple("EXPIRING SOON: $daysRemaining DAYS REMAINING", MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background)
                DateUtils.ExpiryStatus.ACTIVE -> Triple("ACTIVE: $daysRemaining DAYS REMAINING", NothingRed.copy(alpha = 0.1f), NothingRed)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusBg)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = statusColor
                )
            }
        }
    }
}
