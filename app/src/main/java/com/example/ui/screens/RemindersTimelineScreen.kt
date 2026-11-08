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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Item
import com.example.data.model.ItemWithDetails
import com.example.ui.components.NothingDivider
import com.example.ui.components.NothingHeader
import com.example.ui.components.NothingStatusDot
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.DateUtils

@Composable
fun RemindersTimelineScreen(
    viewModel: MainViewModel,
    onItemClick: (Long) -> Unit
) {
    val itemsWithDetails by viewModel.allItemsWithDetails.collectAsStateWithLifecycle()

    val sortedItems = itemsWithDetails
        .filter { it.effectiveExpiryDate != null && (it.effectiveExpiryDate ?: 0L) > 0L }
        .sortedBy { it.effectiveExpiryDate }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            NothingHeader(
                title = "REMINDERS TIMELINE",
                subtitle = "Scheduled protection plan alerts (30d, 7d, 1d before expiry)"
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (sortedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NO UPCOMING REMINDERS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedItems, key = { it.item.id }) { details ->
                        ReminderTimelineCard(
                            details = details,
                            onClick = { onItemClick(details.item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderTimelineCard(
    details: ItemWithDetails,
    onClick: () -> Unit
) {
    val item = details.item
    val effectiveExpiry = details.effectiveExpiryDate
    val status = DateUtils.getExpiryStatus(effectiveExpiry)
    val daysRemaining = DateUtils.getDaysRemaining(effectiveExpiry) ?: 0L

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NothingStatusDot(status = status, modifier = Modifier.padding(end = 8.dp))

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                val badgeText = when {
                    daysRemaining < 0L -> "EXPIRED"
                    daysRemaining == 0L -> "EXPIRES TODAY"
                    else -> "${daysRemaining}d LEFT"
                }

                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = DotoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = if (daysRemaining <= 30L) NothingRed else MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "EXPIRY DATE: ${if (effectiveExpiry != null) DateUtils.formatDate(effectiveExpiry) else "NONE"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NothingDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MilestoneChip(label = "30 DAYS ALERT", isActive = daysRemaining <= 30L)
                MilestoneChip(label = "7 DAYS ALERT", isActive = daysRemaining <= 7L)
                MilestoneChip(label = "1 DAY ALERT", isActive = daysRemaining <= 1L)
            }
        }
    }
}

@Composable
fun MilestoneChip(label: String, isActive: Boolean) {
    val bg = if (isActive) NothingRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val textCol = if (isActive) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textCol)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = textCol
            )
        }
    }
}
