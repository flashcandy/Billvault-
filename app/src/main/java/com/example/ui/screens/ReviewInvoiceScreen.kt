package com.example.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.ExtractedInvoiceResult
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingTextField
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils

@Composable
fun ReviewInvoiceScreen(
    extractedResult: ExtractedInvoiceResult,
    viewModel: MainViewModel,
    onSaved: (Long) -> Unit
) {
    var sellerName by remember { mutableStateOf(extractedResult.sellerName.ifBlank { "Flipkart / Amazon" }) }
    var invoiceNumber by remember { mutableStateOf(extractedResult.invoiceNumber) }
    var orderId by remember { mutableStateOf(extractedResult.orderId) }
    var invoiceDateStr by remember {
        mutableStateOf(
            if (extractedResult.invoiceDate.isNotBlank()) extractedResult.invoiceDate
            else DateUtils.formatIsoDate(System.currentTimeMillis())
        )
    }
    var totalAmountStr by remember { mutableStateOf(if (extractedResult.totalAmount > 0) extractedResult.totalAmount.toString() else "0.00") }
    var taxAmountStr by remember { mutableStateOf(if (extractedResult.taxAmount > 0) extractedResult.taxAmount.toString() else "0.00") }

    val items = remember {
        mutableStateListOf<InvoiceItem>().apply {
            if (extractedResult.items.isNotEmpty()) {
                val parsedDateMillis = DateUtils.parseDateToMillis(invoiceDateStr)
                extractedResult.items.forEach { extItem ->
                    add(
                        InvoiceItem(
                            productName = extItem.productName.ifBlank { "Purchased Item" },
                            serialNumber = extItem.serialNumber,
                            quantity = if (extItem.quantity > 0) extItem.quantity else 1,
                            amount = extItem.amount,
                            warrantyMonths = if (extItem.warrantyMonths > 0) extItem.warrantyMonths else 12,
                            warrantyStartDate = parsedDateMillis,
                            warrantyEndDate = DateUtils.calculateWarrantyEndDate(
                                parsedDateMillis,
                                if (extItem.warrantyMonths > 0) extItem.warrantyMonths else 12
                            ),
                            notes = extItem.notes
                        )
                    )
                }
            } else {
                val now = System.currentTimeMillis()
                add(
                    InvoiceItem(
                        productName = "Product Item 1",
                        serialNumber = "",
                        quantity = 1,
                        amount = 0.0,
                        warrantyMonths = 12,
                        warrantyStartDate = now,
                        warrantyEndDate = DateUtils.calculateWarrantyEndDate(now, 12),
                        notes = ""
                    )
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                NothingButton(
                    text = "SAVE INVOICE & REMINDERS",
                    onClick = {
                        val parsedDate = DateUtils.parseDateToMillis(invoiceDateStr)
                        val total = totalAmountStr.toDoubleOrNull() ?: 0.0
                        val tax = taxAmountStr.toDoubleOrNull() ?: 0.0

                        val invoice = Invoice(
                            sellerName = sellerName.ifBlank { "Unknown Seller" },
                            invoiceNumber = invoiceNumber,
                            orderId = orderId,
                            invoiceDate = parsedDate,
                            totalAmount = total,
                            taxAmount = tax,
                            sourceType = "PDF"
                        )

                        // Update items warranty start date to match parsed date if applicable
                        val updatedItems = items.map { item ->
                            val start = if (item.warrantyStartDate <= 0) parsedDate else item.warrantyStartDate
                            val end = DateUtils.calculateWarrantyEndDate(start, item.warrantyMonths)
                            item.copy(
                                warrantyStartDate = start,
                                warrantyEndDate = end
                            )
                        }

                        viewModel.saveExtractedInvoice(invoice, updatedItems) { id ->
                            onSaved(id)
                        }
                    },
                    testTag = "review_save_button"
                )
            }
        },
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
                    title = "REVIEW DETAILS",
                    subtitle = "Verify and edit extracted fields before saving"
                )
            }

            item {
                Text(
                    text = "INVOICE HEADER",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = DotoFontFamily,
                        letterSpacing = 1.5.sp
                    ),
                    color = NothingRed
                )
            }

            item {
                NothingTextField(
                    value = sellerName,
                    onValueChange = { sellerName = it },
                    label = "SELLER / STORE NAME",
                    placeholder = "e.g. Flipkart, Amazon, Apple",
                    testTag = "input_seller_name"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = "INVOICE NUMBER",
                        placeholder = "FK-10293",
                        modifier = Modifier.weight(1f),
                        testTag = "input_invoice_number"
                    )

                    NothingTextField(
                        value = orderId,
                        onValueChange = { orderId = it },
                        label = "ORDER ID",
                        placeholder = "OD889102",
                        modifier = Modifier.weight(1f),
                        testTag = "input_order_id"
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NothingTextField(
                        value = invoiceDateStr,
                        onValueChange = { invoiceDateStr = it },
                        label = "DATE (YYYY-MM-DD)",
                        placeholder = "2024-10-24",
                        modifier = Modifier.weight(1f),
                        testTag = "input_invoice_date"
                    )

                    NothingTextField(
                        value = totalAmountStr,
                        onValueChange = { totalAmountStr = it },
                        label = "TOTAL AMOUNT (₹)",
                        placeholder = "18999.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        testTag = "input_total_amount"
                    )
                }
            }

            item {
                NothingDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PURCHASED ITEMS & WARRANTY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = DotoFontFamily,
                            letterSpacing = 1.5.sp
                        ),
                        color = NothingRed
                    )

                    NothingButton(
                        text = "+ ADD ITEM",
                        isPrimary = false,
                        onClick = {
                            val startDate = DateUtils.parseDateToMillis(invoiceDateStr)
                            items.add(
                                InvoiceItem(
                                    productName = "New Item ${items.size + 1}",
                                    quantity = 1,
                                    amount = 0.0,
                                    warrantyMonths = 12,
                                    warrantyStartDate = startDate,
                                    warrantyEndDate = DateUtils.calculateWarrantyEndDate(startDate, 12)
                                )
                            )
                        },
                        modifier = Modifier.width(110.dp)
                    )
                }
            }

            itemsIndexed(items) { index, item ->
                ItemEditorCard(
                    index = index + 1,
                    item = item,
                    onItemChange = { updated -> items[index] = updated },
                    onDelete = { if (items.size > 1) items.removeAt(index) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ItemEditorCard(
    index: Int,
    item: InvoiceItem,
    onItemChange: (InvoiceItem) -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ITEM #$index",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete item",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            NothingTextField(
                value = item.productName,
                onValueChange = { onItemChange(item.copy(productName = it)) },
                label = "PRODUCT NAME",
                placeholder = "e.g. Nothing Phone (2a)",
                testTag = "item_name_$index"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NothingTextField(
                    value = item.serialNumber,
                    onValueChange = { onItemChange(item.copy(serialNumber = it)) },
                    label = "SERIAL NUMBER / IMEI",
                    placeholder = "S/N or IMEI...",
                    modifier = Modifier.weight(1f),
                    testTag = "item_serial_$index"
                )

                NothingTextField(
                    value = item.warrantyMonths.toString(),
                    onValueChange = {
                        val months = it.toIntOrNull() ?: 0
                        onItemChange(item.copy(warrantyMonths = months))
                    },
                    label = "WARRANTY (MONTHS)",
                    placeholder = "12",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    testTag = "item_warranty_$index"
                )
            }

            val computedEnd = DateUtils.calculateWarrantyEndDate(item.warrantyStartDate, item.warrantyMonths)
            Text(
                text = "EXPIRY DATE: ${DateUtils.formatDate(computedEnd)} (${DateUtils.getDaysRemaining(computedEnd)} days remaining)",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = NothingRed
            )
        }
    }
}
