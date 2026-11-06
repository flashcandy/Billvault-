package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiExtractionService
import com.example.data.preferences.SettingsManager
import com.example.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BillValidUnitTest {

    private lateinit var aiExtractionService: AiExtractionService
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        settingsManager = SettingsManager(context)
        aiExtractionService = AiExtractionService(context, settingsManager)
    }

    @Test
    fun `test warranty end date calculation`() {
        // Oct 24, 2024
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.OCTOBER, 24, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDateMillis = cal.timeInMillis

        // Add 12 months
        val endDateMillis = DateUtils.calculateWarrantyEndDate(startDateMillis, 12)

        val endCal = Calendar.getInstance().apply {
            timeInMillis = endDateMillis
        }

        assertEquals(2025, endCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, endCal.get(Calendar.MONTH))
        assertEquals(24, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `test reminder dates calculation`() {
        val cal = Calendar.getInstance().apply {
            set(2025, Calendar.OCTOBER, 24, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endDateMillis = cal.timeInMillis

        val reminders = DateUtils.calculateReminderDates(endDateMillis)

        assertEquals(3, reminders.size)
        val (days30, date30) = reminders[0]
        val (days7, date7) = reminders[1]
        val (days1, date1) = reminders[2]

        assertEquals(30, days30)
        assertEquals(7, days7)
        assertEquals(1, days1)

        assertTrue(date30 < date7)
        assertTrue(date7 < date1)
        assertTrue(date1 < endDateMillis)
    }

    @Test
    fun `test json validator with markdown code fence`() {
        val rawAiResponse = """
            ```json
            {
              "sellerName": "Flipkart Internet Pvt Ltd",
              "invoiceNumber": "FK-IN-2024-88492",
              "orderId": "OD309485720194820",
              "invoiceDate": "2024-10-24",
              "totalAmount": 18999.0,
              "taxAmount": 2898.15,
              "items": [
                {
                  "productName": "Nothing Phone (2a) 5G (8GB/128GB)",
                  "serialNumber": "NP2A884920194",
                  "quantity": 1,
                  "amount": 18999.0,
                  "warrantyMonths": 12,
                  "notes": "Brand warranty included"
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = aiExtractionService.validateAndParseJson(rawAiResponse)

        assertNotNull(parsed)
        assertEquals("Flipkart Internet Pvt Ltd", parsed?.sellerName)
        assertEquals("FK-IN-2024-88492", parsed?.invoiceNumber)
        assertEquals("OD309485720194820", parsed?.orderId)
        assertEquals("2024-10-24", parsed?.invoiceDate)
        assertEquals(18999.0, parsed?.totalAmount ?: 0.0, 0.01)
        assertEquals(2898.15, parsed?.taxAmount ?: 0.0, 0.01)

        assertEquals(1, parsed?.items?.size)
        val item = parsed?.items?.get(0)
        assertEquals("Nothing Phone (2a) 5G (8GB/128GB)", item?.productName)
        assertEquals("NP2A884920194", item?.serialNumber)
        assertEquals(12, item?.warrantyMonths)
    }

    @Test
    fun `test sample Flipkart invoice text rule parser`() {
        val flipkartInvoiceText = """
            Tax Invoice / Bill of Supply
            Flipkart Internet Pvt Ltd
            Buildings Alyssa, Begonia & Clover, Embassy Tech Village
            Outer Ring Road, Devarabeesanahalli Village, Bengaluru, 560103, Karnataka, India
            
            Invoice Number: FK-IN-2024-88492
            Order ID: OD309485720194820
            Invoice Date: 24/10/2024
            
            Description: Nothing Phone (2a) 5G
            Qty: 1
            Gross Amount: ₹16,100.85
            IGST (18%): ₹2,898.15
            Total Amount Paid: ₹18,999.00
            
            1 Year Warranty on handset and 6 Months Warranty on accessories.
        """.trimIndent()

        val parsed = aiExtractionService.parseInvoiceWithRules(flipkartInvoiceText)

        assertEquals("Flipkart Internet Pvt Ltd", parsed.sellerName)
        assertEquals("FK-IN-2024-88492", parsed.invoiceNumber)
        assertEquals("OD309485720194820", parsed.orderId)
        assertEquals(18999.00, parsed.totalAmount, 0.01)
        assertEquals(2898.15, parsed.taxAmount, 0.01)
        assertTrue(parsed.items.isNotEmpty())
        assertEquals(12, parsed.items[0].warrantyMonths)
    }
}
