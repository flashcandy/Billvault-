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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Attachment
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.CustomFieldTemplate
import com.example.data.model.Item
import com.example.data.model.ItemWithDetails
import com.example.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE UPPER(name) = UPPER(:name) LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnore(categories: List<Category>): List<Long>

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)
}

@Dao
interface ItemDao {
    @Transaction
    @Query("SELECT * FROM items ORDER BY COALESCE(expiryDate, 9223372036854775807) ASC, purchaseDate DESC")
    fun getAllItemsWithDetails(): Flow<List<ItemWithDetails>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithDetailsById(id: Long): Flow<ItemWithDetails?>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemWithDetailsByIdOnce(id: Long): ItemWithDetails?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items ORDER BY COALESCE(expiryDate, 9223372036854775807) ASC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemCount(): Int

    @Query("UPDATE items SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun moveItemsToCategory(oldCategoryId: Long, newCategoryId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields WHERE itemId = :itemId ORDER BY sortOrder ASC, id ASC")
    fun getCustomFieldsForItem(itemId: Long): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields WHERE itemId = :itemId ORDER BY sortOrder ASC, id ASC")
    suspend fun getCustomFieldsForItemOnce(itemId: Long): List<CustomField>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFields(fields: List<CustomField>)

    @Query("DELETE FROM custom_fields WHERE itemId = :itemId")
    suspend fun deleteCustomFieldsForItem(itemId: Long)

    @Query("SELECT * FROM custom_field_templates WHERE categoryId = :categoryId ORDER BY id ASC")
    fun getTemplatesForCategory(categoryId: Long): Flow<List<CustomFieldTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: CustomFieldTemplate): Long

    @Query("DELETE FROM custom_field_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY addedAt ASC")
    fun getAttachmentsForItem(itemId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY addedAt ASC")
    suspend fun getAttachmentsForItemOnce(itemId: Long): List<Attachment>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachmentById(id: Long): Attachment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>): List<Long>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachmentById(id: Long)

    @Query("DELETE FROM attachments WHERE itemId = :itemId")
    suspend fun deleteAttachmentsForItem(itemId: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isSent = 0 ORDER BY reminderDate ASC")
    fun getPendingReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isSent = 0 AND reminderDate <= :currentTimestamp")
    suspend fun getDueReminders(currentTimestamp: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE itemId = :itemId ORDER BY daysBefore DESC")
    fun getRemindersForItem(itemId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE itemId = :itemId ORDER BY daysBefore DESC")
    suspend fun getRemindersForItemOnce(itemId: Long): List<Reminder>

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
    entities = [
        Category::class,
        Item::class,
        CustomField::class,
        CustomFieldTemplate::class,
        Attachment::class,
        Reminder::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        var migrationFailedMessage: String? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create categories table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `colorMarker` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                // Insert default categories
                db.execSQL("INSERT OR IGNORE INTO `categories` (`id`, `name`, `isDefault`, `colorMarker`, `sortOrder`) VALUES (1, 'VEHICLE', 1, 'DOT_RED', 0)")
                db.execSQL("INSERT OR IGNORE INTO `categories` (`id`, `name`, `isDefault`, `colorMarker`, `sortOrder`) VALUES (2, 'ELECTRONICS', 1, 'DOT_WHITE', 1)")
                db.execSQL("INSERT OR IGNORE INTO `categories` (`id`, `name`, `isDefault`, `colorMarker`, `sortOrder`) VALUES (3, 'SHOPPING', 1, 'DOT_GRAY', 2)")
                db.execSQL("INSERT OR IGNORE INTO `categories` (`id`, `name`, `isDefault`, `colorMarker`, `sortOrder`) VALUES (4, 'ELECTRIC BILLS', 1, 'SQUARE_RED', 3)")

                // 2. Create items table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `sellerName` TEXT NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `purchaseDate` INTEGER NOT NULL,
                        `amount` REAL,
                        `warrantyValue` INTEGER,
                        `warrantyUnit` TEXT NOT NULL,
                        `warrantyStartDate` INTEGER NOT NULL,
                        `expiryDate` INTEGER,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_categoryId` ON `items` (`categoryId`)")

                // 3. Create custom_fields table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_fields` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_fields_itemId` ON `custom_fields` (`itemId`)")

                // 4. Create custom_field_templates table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_field_templates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_field_templates_categoryId_name` ON `custom_field_templates` (`categoryId`, `name`)")

                // 5. Create attachments table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_itemId` ON `attachments` (`itemId`)")

                // 6. Migrate existing rows from invoices & invoice_items into items if tables exist
                db.execSQL(
                    """
                    INSERT INTO `items` (
                        `id`, `title`, `categoryId`, `sellerName`, `invoiceNumber`, `orderId`,
                        `purchaseDate`, `amount`, `warrantyValue`, `warrantyUnit`,
                        `warrantyStartDate`, `expiryDate`, `notes`, `createdAt`, `updatedAt`
                    )
                    SELECT
                        ii.`id`,
                        ii.`productName`,
                        2,
                        COALESCE(inv.`sellerName`, ''),
                        COALESCE(inv.`invoiceNumber`, ''),
                        COALESCE(inv.`orderId`, ''),
                        COALESCE(inv.`invoiceDate`, ii.`warrantyStartDate`),
                        CASE WHEN ii.`amount` > 0 THEN ii.`amount` ELSE inv.`totalAmount` END,
                        CASE WHEN ii.`warrantyMonths` > 0 THEN ii.`warrantyMonths` ELSE NULL END,
                        'MONTHS',
                        ii.`warrantyStartDate`,
                        CASE WHEN ii.`warrantyMonths` > 0 THEN ii.`warrantyEndDate` ELSE NULL END,
                        ii.`notes`,
                        COALESCE(inv.`createdAt`, ii.`warrantyStartDate`),
                        COALESCE(inv.`createdAt`, ii.`warrantyStartDate`)
                    FROM `invoice_items` ii
                    LEFT JOIN `invoices` inv ON ii.`invoiceId` = inv.`id`
                    """.trimIndent()
                )

                // Migrate non-empty serialNumbers into custom_fields
                db.execSQL(
                    """
                    INSERT INTO `custom_fields` (`itemId`, `name`, `type`, `value`, `sortOrder`)
                    SELECT `id`, 'Serial Number', 'TEXT', `serialNumber`, 0
                    FROM `invoice_items`
                    WHERE `serialNumber` IS NOT NULL AND TRIM(`serialNumber`) != ''
                    """.trimIndent()
                )

                // 7. Recreate reminders table referencing items(id)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `reminderDate` INTEGER NOT NULL,
                        `daysBefore` INTEGER NOT NULL,
                        `isSent` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `reminders_new` (`id`, `itemId`, `reminderDate`, `daysBefore`, `isSent`)
                    SELECT r.`id`, r.`itemId`, r.`reminderDate`, r.`daysBefore`, r.`isSent`
                    FROM `reminders` r
                    INNER JOIN `items` i ON r.`itemId` = i.`id`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `reminders`")
                db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_itemId` ON `reminders` (`itemId`)")

                // 8. Drop old tables
                db.execSQL("DROP TABLE IF EXISTS `invoice_items`")
                db.execSQL("DROP TABLE IF EXISTS `invoices`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val instance = try {
                    val db = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "bill_valid_database"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                    // Verify schema integrity by opening writable database
                    db.openHelper.writableDatabase
                    db
                } catch (e: Exception) {
                    e.printStackTrace()
                    migrationFailedMessage =
                        "Database migration encountered an error and had to be reset: ${e.localizedMessage ?: "Schema mismatch"}"
                    appContext.deleteDatabase("bill_valid_database")
                    Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "bill_valid_database"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
