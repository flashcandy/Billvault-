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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Category
import com.example.data.model.ItemWithDetails
import com.example.ui.components.CategoryManageDialog
import com.example.ui.components.NothingChip
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingMarkerDot
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
    onAddItemClick: () -> Unit,
    onItemClick: (Long) -> Unit
) {
    val items by viewModel.itemsWithDetails.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val migrationWarning by viewModel.migrationWarning.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryDialogError by remember { mutableStateOf<String?>(null) }

    val expiringSoonItems = items.filter {
        DateUtils.getExpiryStatus(it.item.expiryDate) == DateUtils.ExpiryStatus.EXPIRING_SOON
    }
    val activeItems = items.filter {
        DateUtils.getExpiryStatus(it.item.expiryDate) == DateUtils.ExpiryStatus.ACTIVE
    }
    val expiredItems = items.filter {
        DateUtils.getExpiryStatus(it.item.expiryDate) == DateUtils.ExpiryStatus.EXPIRED
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItemClick,
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Bill")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADD BILL",
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
            NothingHeader(
                title = "BILL VAULT",
                subtitle = "${activeItems.size} Active • ${expiringSoonItems.size} Expiring Soon • ${expiredItems.size} Expired"
            )

            // Migration warning banner if a migration reset occurred
            if (!migrationWarning.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingRed.copy(alpha = 0.15f))
                        .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = migrationWarning!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissMigrationWarning() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = NothingRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Search input
            NothingTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = "SEARCH BILLS & WARRANTIES",
                placeholder = "Title, seller, invoice #, or custom field...",
                testTag = "home_search_input",
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Horizontal Category Filter Chips (long-press any chip to rename/delete)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    NothingChip(
                        text = "ALL",
                        isSelected = selectedCategoryId == null,
                        onClick = { viewModel.selectCategoryFilter(null) },
                        testTag = "category_filter_ALL"
                    )
                }

                items(categories, key = { it.id }) { category ->
                    NothingChip(
                        text = category.name,
                        isSelected = selectedCategoryId == category.id,
                        colorMarker = category.colorMarker,
                        onClick = { viewModel.selectCategoryFilter(category.id) },
                        onLongClick = {
                            editingCategory = category
                            categoryDialogError = null
                            showCategoryDialog = true
                        },
                        testTag = "category_filter_${category.name}"
                    )
                }

                item {
                    NothingChip(
                        text = "+ NEW",
                        isSelected = false,
                        onClick = {
                            editingCategory = null
                            categoryDialogError = null
                            showCategoryDialog = true
                        },
                        testTag = "home_new_category_chip"
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (items.isEmpty()) {
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
                            text = "NO ITEMS FOUND",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + ADD BILL to create an entry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (expiringSoonItems.isNotEmpty()) {
                        item(key = "header_expiring_soon") {
                            SectionGroupHeader(
                                title = "EXPIRING SOON (${expiringSoonItems.size})",
                                accentColor = MaterialTheme.colorScheme.onBackground,
                                showDot = true
                            )
                        }
                        items(expiringSoonItems, key = { "exp_soon_${it.item.id}" }) { details ->
                            ItemRowCard(
                                details = details,
                                onClick = { onItemClick(details.item.id) }
                            )
                            NothingDivider()
                        }
                    }

                    if (activeItems.isNotEmpty()) {
                        item(key = "header_active") {
                            SectionGroupHeader(
                                title = "ACTIVE (${activeItems.size})",
                                accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                showDot = false
                            )
                        }
                        items(activeItems, key = { "active_${it.item.id}" }) { details ->
                            ItemRowCard(
                                details = details,
                                onClick = { onItemClick(details.item.id) }
                            )
                            NothingDivider()
                        }
                    }

                    if (expiredItems.isNotEmpty()) {
                        item(key = "header_expired") {
                            SectionGroupHeader(
                                title = "EXPIRED (${expiredItems.size})",
                                accentColor = NothingRed,
                                showDot = true
                            )
                        }
                        items(expiredItems, key = { "expired_${it.item.id}" }) { details ->
                            ItemRowCard(
                                details = details,
                                onClick = { onItemClick(details.item.id) }
                            )
                            NothingDivider()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
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
                            viewModel.selectCategoryFilter(it.id)
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
}

@Composable
private fun SectionGroupHeader(
    title: String,
    accentColor: Color,
    showDot: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = DotoFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = accentColor
        )
    }
}

@Composable
fun ItemRowCard(
    details: ItemWithDetails,
    onClick: () -> Unit
) {
    val item = details.item
    val category = details.category
    val status = DateUtils.getExpiryStatus(item.expiryDate)
    val daysRemaining = DateUtils.getDaysRemaining(item.expiryDate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NothingStatusDot(status = status, modifier = Modifier.padding(end = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                if (item.amount != null && item.amount > 0) {
                    Text(
                        text = DateUtils.formatCurrency(item.amount),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (category != null) {
                        NothingMarkerDot(colorMarker = category.colorMarker)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = category.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.sellerName.isNotBlank()) {
                        Text(
                            text = " • ${item.sellerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (details.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attachments",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${details.attachments.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expiry Status Badge
                val (statusText, statusBg, statusColor) = when {
                    daysRemaining == null -> Triple(
                        "NO EXPIRY",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    status == DateUtils.ExpiryStatus.EXPIRED -> Triple(
                        "EXPIRED",
                        NothingRed.copy(alpha = 0.2f),
                        NothingRed
                    )
                    status == DateUtils.ExpiryStatus.EXPIRING_SOON -> Triple(
                        "${daysRemaining}d LEFT",
                        MaterialTheme.colorScheme.onBackground,
                        MaterialTheme.colorScheme.background
                    )
                    else -> Triple(
                        "${daysRemaining}d REMAINING",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

            val expDisplay = if (item.expiryDate != null) {
                "EXP: ${DateUtils.formatDate(item.expiryDate)}"
            } else {
                "NO WARRANTY"
            }

            Text(
                text = "PURCHASED: ${DateUtils.formatDate(item.purchaseDate)} • $expDisplay",
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
            contentDescription = "View Details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
