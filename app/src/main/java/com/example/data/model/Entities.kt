package com.example.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sellerName: String,
    val invoiceNumber: String,
    val orderId: String,
    val invoiceDate: Long, // Epoch timestamp in millis
    val totalAmount: Double,
    val taxAmount: Double,
    val sourceFilePath: String? = null,
    val sourceType: String = "PDF", // "PDF" or "IMAGE"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoiceId"])]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long = 0,
    val productName: String,
    val serialNumber: String = "",
    val quantity: Int = 1,
    val amount: Double = 0.0,
    val warrantyMonths: Int = 0,
    val warrantyStartDate: Long = System.currentTimeMillis(),
    val warrantyEndDate: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"])]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val reminderDate: Long, // Epoch timestamp in millis
    val daysBefore: Int, // 30, 7, 1
    val isSent: Boolean = false
)

data class InvoiceWithItems(
    @Embedded val invoice: Invoice,
    @Relation(
        parentColumn = "id",
        entityColumn = "invoiceId"
    )
    val items: List<InvoiceItem>
)

data class ItemWithReminders(
    @Embedded val item: InvoiceItem,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val reminders: List<Reminder>
)

data class ReminderWithDetails(
    @Embedded val reminder: Reminder,
    @Relation(
        parentColumn = "itemId",
        entityColumn = "id"
    )
    val item: InvoiceItem
)
