package com.example.utils

import com.example.data.model.WarrantyUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {

    private val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    fun formatDate(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "NONE"
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(displayFormatter)
    }

    fun formatIsoDate(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return ""
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(isoFormatter)
    }

    fun parseDateToMillis(dateStr: String, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val trimmed = dateStr.trim()
        if (trimmed.isBlank()) return null

        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        )

        for (formatter in formatters) {
            try {
                val localDate = LocalDate.parse(trimmed, formatter)
                return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // Try next
            }
        }
        return null
    }

    fun localDateToMillis(
        year: Int,
        month: Int, // 1..12
        dayOfMonth: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        return LocalDate.of(year, month, dayOfMonth)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Calculates warranty/protection expiry date from start date, warranty value, and unit.
     * Returns null if warrantyValue is null or <= 0.
     * Handles month-end edge cases (e.g., Jan 31 + 1 month -> Feb 28/29).
     */
    fun calculateExpiryDate(
        startDateMillis: Long,
        warrantyValue: Int?,
        warrantyUnit: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (warrantyValue == null || warrantyValue <= 0) return null

        val startLocalDate = Instant.ofEpochMilli(startDateMillis)
            .atZone(zoneId)
            .toLocalDate()

        val endLocalDate = when (warrantyUnit.uppercase(Locale.ROOT)) {
            WarrantyUnit.DAYS -> startLocalDate.plusDays(warrantyValue.toLong())
            WarrantyUnit.MONTHS -> startLocalDate.plusMonths(warrantyValue.toLong())
            WarrantyUnit.YEARS -> startLocalDate.plusYears(warrantyValue.toLong())
            else -> startLocalDate.plusMonths(warrantyValue.toLong())
        }

        return endLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Calculates reminder dates for 30, 7, and 1 day before warranty end date.
     */
    fun calculateReminderDates(endDateMillis: Long?): List<Pair<Int, Long>> {
        if (endDateMillis == null || endDateMillis <= 0L) return emptyList()
        val oneDayMillis = TimeUnit.DAYS.toMillis(1)
        return listOf(
            30 to (endDateMillis - 30 * oneDayMillis),
            7 to (endDateMillis - 7 * oneDayMillis),
            1 to (endDateMillis - 1 * oneDayMillis)
        )
    }

    /**
     * Returns remaining days until expiry. Negative if expired, null if no expiry.
     */
    fun getDaysRemaining(
        endDateMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (endDateMillis == null || endDateMillis <= 0L) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(endDateMillis).atZone(zoneId).toLocalDate()
        return ChronoUnit.DAYS.between(today, endDate)
    }

    enum class ExpiryStatus {
        EXPIRED,
        EXPIRING_SOON,
        ACTIVE
    }

    fun getExpiryStatus(
        endDateMillis: Long?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ExpiryStatus {
        if (endDateMillis == null || endDateMillis <= 0L) return ExpiryStatus.ACTIVE
        val daysRemaining = getDaysRemaining(endDateMillis, nowMillis, zoneId) ?: return ExpiryStatus.ACTIVE
        return when {
            daysRemaining < 0L -> ExpiryStatus.EXPIRED
            daysRemaining <= 30L -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.ACTIVE
        }
    }

    fun formatCurrency(amount: Double?): String {
        if (amount == null) return ""
        val format = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        return try {
            format.format(amount)
        } catch (_: Exception) {
            String.format(Locale.US, "₹%.2f", amount)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
