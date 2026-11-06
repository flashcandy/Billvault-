package com.example.data.repository

import com.example.data.local.InvoiceDao
import com.example.data.local.InvoiceItemDao
import com.example.data.local.ReminderDao
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.InvoiceWithItems
import com.example.data.model.Reminder
import com.example.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class InvoiceRepository(
    private val invoiceDao: InvoiceDao,
    private val invoiceItemDao: InvoiceItemDao,
    private val reminderDao: ReminderDao
) {

    val allInvoicesWithItems: Flow<List<InvoiceWithItems>> = invoiceDao.getAllInvoicesWithItems()
    val pendingReminders: Flow<List<Reminder>> = reminderDao.getPendingReminders()
    val allItems: Flow<List<InvoiceItem>> = invoiceItemDao.getAllItems()

    fun getInvoiceWithItemsById(id: Long): Flow<InvoiceWithItems?> =
        invoiceDao.getInvoiceWithItemsById(id)

    suspend fun getInvoiceById(id: Long): Invoice? = invoiceDao.getInvoiceById(id)

    suspend fun getItemById(id: Long): InvoiceItem? = invoiceItemDao.getItemById(id)

    suspend fun saveInvoiceWithItems(
        invoice: Invoice,
        items: List<InvoiceItem>
    ): Long {
        val invoiceId = invoiceDao.insertInvoice(invoice)

        for (rawItem in items) {
            val startDate = if (rawItem.warrantyStartDate > 0) rawItem.warrantyStartDate else invoice.invoiceDate
            val endDate = if (rawItem.warrantyMonths > 0) {
                DateUtils.calculateWarrantyEndDate(startDate, rawItem.warrantyMonths)
            } else {
                startDate
            }

            val itemToSave = rawItem.copy(
                invoiceId = invoiceId,
                warrantyStartDate = startDate,
                warrantyEndDate = endDate
            )

            val itemId = invoiceItemDao.insertItem(itemToSave)

            // Schedule reminders if warranty is present
            if (rawItem.warrantyMonths > 0) {
                reminderDao.deleteRemindersForItem(itemId)
                val reminderPairs = DateUtils.calculateReminderDates(endDate)
                val now = System.currentTimeMillis()

                for ((daysBefore, reminderDate) in reminderPairs) {
                    val isAlreadySent = reminderDate < now
                    val reminder = Reminder(
                        itemId = itemId,
                        reminderDate = reminderDate,
                        daysBefore = daysBefore,
                        isSent = isAlreadySent
                    )
                    reminderDao.insertReminder(reminder)
                }
            }
        }

        return invoiceId
    }

    suspend fun updateInvoiceItem(item: InvoiceItem) {
        val endDate = if (item.warrantyMonths > 0) {
            DateUtils.calculateWarrantyEndDate(item.warrantyStartDate, item.warrantyMonths)
        } else {
            item.warrantyStartDate
        }

        val updatedItem = item.copy(warrantyEndDate = endDate)
        invoiceItemDao.updateItem(updatedItem)

        // Re-schedule reminders
        reminderDao.deleteRemindersForItem(updatedItem.id)
        if (updatedItem.warrantyMonths > 0) {
            val reminderPairs = DateUtils.calculateReminderDates(endDate)
            val now = System.currentTimeMillis()

            for ((daysBefore, reminderDate) in reminderPairs) {
                val isAlreadySent = reminderDate < now
                val reminder = Reminder(
                    itemId = updatedItem.id,
                    reminderDate = reminderDate,
                    daysBefore = daysBefore,
                    isSent = isAlreadySent
                )
                reminderDao.insertReminder(reminder)
            }
        }
    }

    suspend fun deleteInvoice(invoiceId: Long) {
        invoiceDao.deleteInvoiceById(invoiceId)
    }

    suspend fun seedSampleDataIfEmpty() {
        // We check if database already has data
        // If empty, seed sample FlipKart / Amazon invoices
        val now = System.currentTimeMillis()
        val oneMonthAgo = now - TimeUnit.DAYS.toMillis(35)
        val fiveMonthsAgo = now - TimeUnit.DAYS.toMillis(150)
        val thirteenMonthsAgo = now - TimeUnit.DAYS.toMillis(400)

        // Invoice 1: Expiring Soon (Purchased 5 months ago with 6 months warranty -> ~30 days left)
        val invoice1 = Invoice(
            sellerName = "Flipkart Internet Pvt Ltd",
            invoiceNumber = "FK-IN-2024-88492",
            orderId = "OD309485720194820",
            invoiceDate = fiveMonthsAgo,
            totalAmount = 18999.00,
            taxAmount = 2898.15,
            sourceType = "PDF"
        )
        val item1 = InvoiceItem(
            productName = "Nothing Phone (2a) 5G (8GB/128GB)",
            serialNumber = "NP2A884920194",
            quantity = 1,
            amount = 18999.00,
            warrantyMonths = 6,
            warrantyStartDate = fiveMonthsAgo,
            notes = "Brand warranty, registered on Nothing website"
        )

        // Invoice 2: Active (Purchased 1 month ago with 12 months warranty)
        val invoice2 = Invoice(
            sellerName = "Amazon Retail India",
            invoiceNumber = "AMZN-2024-99381",
            orderId = "403-9182743-1092837",
            invoiceDate = oneMonthAgo,
            totalAmount = 8990.00,
            taxAmount = 1371.35,
            sourceType = "PDF"
        )
        val item2 = InvoiceItem(
            productName = "Sony WH-CH720N Wireless Headphones",
            serialNumber = "S2024CH720N99",
            quantity = 1,
            amount = 8990.00,
            warrantyMonths = 12,
            warrantyStartDate = oneMonthAgo,
            notes = "Sony India 1-Year warranty card included"
        )

        // Invoice 3: Expired (Purchased 13 months ago with 12 months warranty)
        val invoice3 = Invoice(
            sellerName = "Croma Electronics",
            invoiceNumber = "CR-2023-11204",
            orderId = "CR-ORD-00918",
            invoiceDate = thirteenMonthsAgo,
            totalAmount = 2499.00,
            taxAmount = 381.20,
            sourceType = "IMAGE"
        )
        val item3 = InvoiceItem(
            productName = "Logitech K380 Multi-Device Keyboard",
            serialNumber = "LOGI-K380-7718",
            quantity = 1,
            amount = 2499.00,
            warrantyMonths = 12,
            warrantyStartDate = thirteenMonthsAgo,
            notes = "Expired 1-year hardware warranty"
        )

        saveInvoiceWithItems(invoice1, listOf(item1))
        saveInvoiceWithItems(invoice2, listOf(item2))
        saveInvoiceWithItems(invoice3, listOf(item3))
    }
}
