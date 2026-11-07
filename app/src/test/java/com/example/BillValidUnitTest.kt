package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.WarrantyUnit
import com.example.utils.DateUtils
import com.example.utils.ValidationResult
import com.example.utils.ValidationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.Category
import com.example.data.model.Item
import com.example.data.model.ItemWithDetails
import com.example.ui.screens.ItemRowCard
import org.junit.Rule
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillValidUnitTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testZone = ZoneId.of("UTC")

    @Test
    fun `expiry calculation supports days months and years`() {
        val startMillis = DateUtils.localDateToMillis(2025, 3, 15, testZone)

        // +30 DAYS -> 2025-04-14
        val plus30Days = DateUtils.calculateExpiryDate(startMillis, 30, WarrantyUnit.DAYS, testZone)
        assertNotNull(plus30Days)
        assertEquals(
            DateUtils.localDateToMillis(2025, 4, 14, testZone),
            plus30Days
        )

        // +6 MONTHS -> 2025-09-15
        val plus6Months = DateUtils.calculateExpiryDate(startMillis, 6, WarrantyUnit.MONTHS, testZone)
        assertNotNull(plus6Months)
        assertEquals(
            DateUtils.localDateToMillis(2025, 9, 15, testZone),
            plus6Months
        )

        // +2 YEARS -> 2027-03-15
        val plus2Years = DateUtils.calculateExpiryDate(startMillis, 2, WarrantyUnit.YEARS, testZone)
        assertNotNull(plus2Years)
        assertEquals(
            DateUtils.localDateToMillis(2027, 3, 15, testZone),
            plus2Years
        )
    }

    @Test
    fun `expiry calculation handles January 31 plus one month edge cases`() {
        // Non-leap year: Jan 31, 2025 + 1 month -> Feb 28, 2025
        val jan31NonLeap = DateUtils.localDateToMillis(2025, 1, 31, testZone)
        val feb28NonLeap = DateUtils.calculateExpiryDate(jan31NonLeap, 1, WarrantyUnit.MONTHS, testZone)
        assertEquals(
            DateUtils.localDateToMillis(2025, 2, 28, testZone),
            feb28NonLeap
        )

        // Leap year: Jan 31, 2024 + 1 month -> Feb 29, 2024
        val jan31Leap = DateUtils.localDateToMillis(2024, 1, 31, testZone)
        val feb29Leap = DateUtils.calculateExpiryDate(jan31Leap, 1, WarrantyUnit.MONTHS, testZone)
        assertEquals(
            DateUtils.localDateToMillis(2024, 2, 29, testZone),
            feb29Leap
        )

        // No warranty entered -> null expiry and empty reminders
        assertNull(DateUtils.calculateExpiryDate(jan31NonLeap, null, WarrantyUnit.MONTHS, testZone))
        assertNull(DateUtils.calculateExpiryDate(jan31NonLeap, 0, WarrantyUnit.MONTHS, testZone))
        assertTrue(DateUtils.calculateReminderDates(null).isEmpty())
    }

    @Test
    fun `filename sanitization strips unsafe characters and generates unique names`() {
        val raw = "../../Washing Machine: Bill*2026?.pdf"
        val sanitized = ValidationUtils.sanitizeFileName(raw)
        assertEquals("Washing_Machine_Bill_2026.pdf", sanitized)

        val unique = ValidationUtils.generateUniqueFileName(raw, "1001")
        assertEquals("Washing_Machine_Bill_2026_1001.pdf", unique)

        val emptyFallback = ValidationUtils.sanitizeFileName("///")
        assertEquals("attachment", emptyFallback)
    }

    @Test
    fun `category name validation checks required length limit and uniqueness`() {
        val existing = listOf("VEHICLE", "ELECTRONICS", "SHOPPING", "ELECTRIC BILLS")

        // Blank fails
        val blankRes = ValidationUtils.validateCategoryName("   ", existing)
        assertTrue(blankRes is ValidationResult.Invalid)

        // > 20 chars fails
        val tooLongRes = ValidationUtils.validateCategoryName("ABCDEFGHIJKLMNOPQRSTU", existing)
        assertTrue(tooLongRes is ValidationResult.Invalid)

        // Duplicate (case-insensitive) fails
        val dupRes = ValidationUtils.validateCategoryName("electronics", existing)
        assertTrue(dupRes is ValidationResult.Invalid)

        // Valid new category succeeds
        val validRes = ValidationUtils.validateCategoryName("APPLIANCES", existing)
        assertTrue(validRes is ValidationResult.Valid)
        assertEquals("APPLIANCES", (validRes as ValidationResult.Valid).cleanedValue)

        // Renaming existing category to its own name succeeds
        val selfRename = ValidationUtils.validateCategoryName("Electronics", existing, currentName = "ELECTRONICS")
        assertTrue(selfRename is ValidationResult.Valid)
    }

    @Test
    fun `room migration 1 to 2 preserves existing invoices items and reminders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v1_to_v2.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `invoices` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `sellerName` TEXT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `orderId` TEXT NOT NULL,
                            `invoiceDate` INTEGER NOT NULL,
                            `totalAmount` REAL NOT NULL,
                            `taxAmount` REAL NOT NULL,
                            `sourceFilePath` TEXT,
                            `sourceType` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `invoice_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `invoiceId` INTEGER NOT NULL,
                            `productName` TEXT NOT NULL,
                            `serialNumber` TEXT NOT NULL,
                            `quantity` INTEGER NOT NULL,
                            `amount` REAL NOT NULL,
                            `warrantyMonths` INTEGER NOT NULL,
                            `warrantyStartDate` INTEGER NOT NULL,
                            `warrantyEndDate` INTEGER NOT NULL,
                            `notes` TEXT NOT NULL,
                            FOREIGN KEY(`invoiceId`) REFERENCES `invoices`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `reminders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `itemId` INTEGER NOT NULL,
                            `reminderDate` INTEGER NOT NULL,
                            `daysBefore` INTEGER NOT NULL,
                            `isSent` INTEGER NOT NULL,
                            FOREIGN KEY(`itemId`) REFERENCES `invoice_items`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Insert v1 test data
        db.execSQL(
            """
            INSERT INTO `invoices` (`id`, `sellerName`, `invoiceNumber`, `orderId`, `invoiceDate`, `totalAmount`, `taxAmount`, `sourceFilePath`, `sourceType`, `createdAt`)
            VALUES (10, 'Flipkart', 'FK-999', 'OD-123', 1700000000000, 24999.0, 3800.0, NULL, 'PDF', 1700000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `invoice_items` (`id`, `invoiceId`, `productName`, `serialNumber`, `quantity`, `amount`, `warrantyMonths`, `warrantyStartDate`, `warrantyEndDate`, `notes`)
            VALUES (20, 10, 'Washing Machine', 'SN-WM-777', 1, 24999.0, 12, 1700000000000, 1731536000000, 'Migrated note')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `reminders` (`id`, `itemId`, `reminderDate`, `daysBefore`, `isSent`)
            VALUES (30, 20, 1728944000000, 30, 0)
            """.trimIndent()
        )

        // Run MIGRATION_1_2
        AppDatabase.MIGRATION_1_2.migrate(db)

        // 1. Verify default categories were created
        db.query("SELECT COUNT(*) FROM `categories` WHERE `isDefault` = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(4, cursor.getInt(0))
        }

        // 2. Verify migrated item in `items`
        db.query("SELECT `title`, `sellerName`, `invoiceNumber`, `warrantyValue`, `warrantyUnit` FROM `items` WHERE `id` = 20").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Washing Machine", cursor.getString(0))
            assertEquals("Flipkart", cursor.getString(1))
            assertEquals("FK-999", cursor.getString(2))
            assertEquals(12, cursor.getInt(3))
            assertEquals("MONTHS", cursor.getString(4))
        }

        // 3. Verify serialNumber migrated into `custom_fields`
        db.query("SELECT `name`, `value` FROM `custom_fields` WHERE `itemId` = 20").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Serial Number", cursor.getString(0))
            assertEquals("SN-WM-777", cursor.getString(1))
        }

        // 4. Verify reminder was preserved
        db.query("SELECT `daysBefore` FROM `reminders` WHERE `itemId` = 20").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(30, cursor.getInt(0))
        }

        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `title normalization replaces line breaks and collapses whitespace`() {
        val pasted = "Amount ₹ Discounts /Coupons ₹\r\nTaxable Value ₹\nSGST /UTGST ₹\rCGST ₹   Total ₹"
        val normalized = ValidationUtils.normalizeTitle(pasted)
        assertEquals("Amount ₹ Discounts /Coupons ₹ Taxable Value ₹ SGST /UTGST ₹ CGST ₹ Total ₹", normalized)
        assertEquals(
            "Washing Machine",
            ValidationUtils.normalizeTitle("   Washing   \n  Machine   ")
        )
    }

    @Test
    fun `title validation enforces 60 char limit and non-blank`() {
        // Blank
        val blank = ValidationUtils.validateTitle("   \n  ")
        assertTrue(blank is ValidationResult.Invalid)

        // 60 chars - valid
        val exact60 = "A".repeat(60)
        val valid60 = ValidationUtils.validateTitle(exact60)
        assertTrue(valid60 is ValidationResult.Valid)
        assertEquals(exact60, (valid60 as ValidationResult.Valid).cleanedValue)

        // 61 chars - invalid with descriptive error
        val tooLong = "A".repeat(61)
        val invalidTooLong = ValidationUtils.validateTitle(tooLong)
        assertTrue(invalidTooLong is ValidationResult.Invalid)
        assertTrue((invalidTooLong as ValidationResult.Invalid).errorMessage.contains("60"))
    }

    @Test
    fun `recalculating expiry when warranty start date changes`() {
        // Purchase date: Jan 1, 2026
        val purchaseDate = DateUtils.localDateToMillis(2026, 1, 1, testZone)
        val initialExpiry = DateUtils.calculateExpiryDate(purchaseDate, 1, WarrantyUnit.YEARS, testZone)
        assertEquals(DateUtils.localDateToMillis(2027, 1, 1, testZone), initialExpiry)

        // Delivery date (warranty start date edited): Jan 15, 2026
        val deliveryDate = DateUtils.localDateToMillis(2026, 1, 15, testZone)
        val recalculatedExpiry = DateUtils.calculateExpiryDate(deliveryDate, 1, WarrantyUnit.YEARS, testZone)
        assertEquals(DateUtils.localDateToMillis(2027, 1, 15, testZone), recalculatedExpiry)
    }

    @Test
    fun `item row card handles 200 character title without breaking layout`() {
        val longTitle = "Amount ₹ Discounts /Coupons ₹ Taxable Value ₹ SGST /UTGST ₹ CGST ₹ Total ₹ " + "A".repeat(150)
        assertTrue(longTitle.length >= 200)

        val item = Item(
            id = 1,
            title = longTitle,
            categoryId = 1,
            sellerName = "Flipkart",
            invoiceNumber = "FK-12345",
            orderId = "OD-98765",
            purchaseDate = 1700000000000,
            amount = 25000.0,
            warrantyValue = 1,
            warrantyUnit = "YEARS",
            warrantyStartDate = 1700000000000,
            expiryDate = 1731536000000,
            notes = "Invoice note",
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )
        val category = Category(id = 1, name = "ELECTRONICS", isDefault = true)
        val details = ItemWithDetails(
            item = item,
            category = category,
            customFields = emptyList(),
            attachments = emptyList(),
            reminders = emptyList()
        )

        composeTestRule.setContent {
            ItemRowCard(details = details, onClick = {})
        }

        // Verify the component renders and displays the category/seller and amount
        composeTestRule.onNodeWithText("ELECTRONICS • Flipkart").assertIsDisplayed()
        composeTestRule.onNodeWithText(DateUtils.formatCurrency(25000.0)).assertIsDisplayed()
    }
}
