package com.example.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BillValidApplication
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.utils.DateUtils

class ExpiryReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(context)
        val reminderDao = database.reminderDao()
        val itemDao = database.invoiceItemDao()

        val now = System.currentTimeMillis()
        val dueReminders = reminderDao.getDueReminders(now)

        for (reminder in dueReminders) {
            val item = itemDao.getItemById(reminder.itemId) ?: continue

            val daysRemaining = DateUtils.getDaysRemaining(item.warrantyEndDate)
            val title = when {
                daysRemaining <= 0 -> "Warranty Expired: ${item.productName}"
                daysRemaining == 1L -> "Warranty Expires Tomorrow: ${item.productName}"
                else -> "Warranty Expiring in $daysRemaining Days: ${item.productName}"
            }

            val body = "Protection plan for ${item.productName} (S/N: ${item.serialNumber.ifBlank { "N/A" }}) ends on ${DateUtils.formatDate(item.warrantyEndDate)}."

            sendNotification(reminder.id.toInt(), item.id, title, body)
            reminderDao.markReminderSent(reminder.id)
        }

        return Result.success()
    }

    private fun sendNotification(notificationId: Int, itemId: Long, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_ITEM_ID", itemId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, BillValidApplication.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
