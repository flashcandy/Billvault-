package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.InvoiceWithItems
import com.example.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Transaction
    @Query("SELECT * FROM invoices ORDER BY invoiceDate DESC")
    fun getAllInvoicesWithItems(): Flow<List<InvoiceWithItems>>

    @Transaction
    @Query("SELECT * FROM invoices WHERE id = :id")
    fun getInvoiceWithItemsById(id: Long): Flow<InvoiceWithItems?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoiceById(id: Long): Invoice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: Long)
}

@Dao
interface InvoiceItemDao {
    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun getItemsForInvoice(invoiceId: Long): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoice_items WHERE id = :id")
    suspend fun getItemById(id: Long): InvoiceItem?

    @Query("SELECT * FROM invoice_items ORDER BY warrantyEndDate ASC")
    fun getAllItems(): Flow<List<InvoiceItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InvoiceItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InvoiceItem>): List<Long>

    @Update
    suspend fun updateItem(item: InvoiceItem)

    @Delete
    suspend fun deleteItem(item: InvoiceItem)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isSent = 0 ORDER BY reminderDate ASC")
    fun getPendingReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isSent = 0 AND reminderDate <= :currentTimestamp")
    suspend fun getDueReminders(currentTimestamp: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE itemId = :itemId")
    fun getRemindersForItem(itemId: Long): Flow<List<Reminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<Reminder>): List<Long>

    @Query("UPDATE reminders SET isSent = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Long)

    @Query("DELETE FROM reminders WHERE itemId = :itemId")
    suspend fun deleteRemindersForItem(itemId: Long)
}

@Database(
    entities = [Invoice::class, InvoiceItem::class, Reminder::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    abstract fun invoiceItemDao(): InvoiceItemDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bill_valid_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
