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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvoiceWithItems
import com.example.ui.components.NothingChip
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingStatusDot
import com.example.ui.components.NothingTextField
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAddInvoiceClick: () -> Unit,
    onInvoiceClick: (Long) -> Unit
) {
    val invoices by viewModel.invoicesWithItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    val expiringSoonCount = invoices.count { inv ->
        inv.items.any { DateUtils.getExpiryStatus(it.warrantyEndDate) == DateUtils.ExpiryStatus.EXPIRING_SOON }
    }
    val activeCount = invoices.count { inv ->
        inv.items.any { DateUtils.getExpiryStatus(it.warrantyEndDate) == DateUtils.ExpiryStatus.ACTIVE }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddInvoiceClick,
                containerColor = NothingRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .testTag("home_add_button")
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Invoice")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADD INVOICE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header with stats
            NothingHeader(
                title = "BILL VALID",
                subtitle = "$activeCount Active • $expiringSoonCount Expiring Soon"
            )

            // Search input
            NothingTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = "SEARCH INVOICES",
                placeholder = "Seller, product name, or invoice #...",
                testTag = "home_search_input",
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All",
                    "EXPIRING_SOON" to "Expiring Soon ($expiringSoonCount)",
                    "ACTIVE" to "Active ($activeCount)",
                    "EXPIRED" to "Expired"
                )
                items(filters) { (key, label) ->
                    NothingChip(
                        text = label,
                        isSelected = selectedFilter == key,
                        onClick = { viewModel.updateFilter(key) },
                        testTag = "filter_chip_$key"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Invoice list
            if (invoices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NO INVOICES FOUND",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + ADD INVOICE to scan or upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(invoices, key = { it.invoice.id }) { invoiceWithItems ->
                        InvoiceRowItem(
                            invoiceWithItems = invoiceWithItems,
                            onClick = { onInvoiceClick(invoiceWithItems.invoice.id) }
                        )
                        NothingDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceRowItem(
    invoiceWithItems: InvoiceWithItems,
    onClick: () -> Unit
) {
    val invoice = invoiceWithItems.invoice
    val items = invoiceWithItems.items

    val primaryItem = items.firstOrNull()
    val status = primaryItem?.let { DateUtils.getExpiryStatus(it.warrantyEndDate) }
        ?: DateUtils.ExpiryStatus.ACTIVE

    val daysRemaining = primaryItem?.let { DateUtils.getDaysRemaining(it.warrantyEndDate) } ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator dot
        NothingStatusDot(status = status, modifier = Modifier.padding(end = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = invoice.sellerName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = DateUtils.formatCurrency(invoice.totalAmount),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = primaryItem?.productName ?: "No products listed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SpaceGroteskFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                // Days remaining badge
                val (statusText, statusBg, statusColor) = when (status) {
                    DateUtils.ExpiryStatus.EXPIRED -> Triple("EXPIRED", NothingRed.copy(alpha = 0.2f), NothingRed)
                    DateUtils.ExpiryStatus.EXPIRING_SOON -> Triple("${daysRemaining}d LEFT", MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background)
                    DateUtils.ExpiryStatus.ACTIVE -> Triple("${daysRemaining}d REMAINING", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "DATE: ${DateUtils.formatDate(invoice.invoiceDate)} • INV: #${invoice.invoiceNumber.ifBlank { "N/A" }}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "View Invoice",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
