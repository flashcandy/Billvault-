package com.example.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object DateUtils {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "N/A"
        return dateFormat.format(Date(timestamp))
    }

    fun formatIsoDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return isoFormat.format(Date(timestamp))
    }

    fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        )

        for (format in formats) {
            try {
                val date = format.parse(dateStr.trim())
                if (date != null) return date.time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return System.currentTimeMillis()
    }

    /**
     * Calculates warranty end date from start date and months duration.
     */
    fun calculateWarrantyEndDate(startDateMillis: Long, months: Int): Long {
        if (months <= 0) return startDateMillis
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = startDateMillis
            add(Calendar.MONTH, months)
        }
        return calendar.timeInMillis
    }

    /**
     * Calculates reminder dates for 30, 7, and 1 day before warranty end date.
     */
    fun calculateReminderDates(endDateMillis: Long): List<Pair<Int, Long>> {
        val oneDayMillis = TimeUnit.DAYS.toMillis(1)
        val reminder30Days = endDateMillis - (30 * oneDayMillis)
        val reminder7Days = endDateMillis - (7 * oneDayMillis)
        val reminder1Day = endDateMillis - (1 * oneDayMillis)

        return listOf(
            30 to reminder30Days,
            7 to reminder7Days,
            1 to reminder1Day
        )
    }

    /**
     * Returns remaining days until expiry. Negative if expired.
     */
    fun getDaysRemaining(endDateMillis: Long): Long {
        val now = System.currentTimeMillis()
        val diffMillis = endDateMillis - now
        return TimeUnit.MILLISECONDS.toDays(diffMillis)
    }

    enum class ExpiryStatus {
        EXPIRED,
        EXPIRING_SOON,
        ACTIVE
    }

    fun getExpiryStatus(endDateMillis: Long): ExpiryStatus {
        val now = System.currentTimeMillis()
        val thirtyDaysFromNow = now + TimeUnit.DAYS.toMillis(30)

        return when {
            endDateMillis <= now -> ExpiryStatus.EXPIRED
            endDateMillis <= thirtyDaysFromNow -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.ACTIVE
        }
    }

    fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        return try {
            format.format(amount)
        } catch (e: Exception) {
                String.format(Locale.getDefault(), "₹%.2f", amount)
        }
    }
}
