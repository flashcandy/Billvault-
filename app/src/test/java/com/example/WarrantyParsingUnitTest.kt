package com.example

import com.example.data.model.WarrantyUnit
import com.example.utils.DateUtils
import com.example.utils.HttpResponse
import com.example.utils.OpenRouterHttpClient
import com.example.utils.OpenRouterParseOutcome
import com.example.utils.OpenRouterService
import com.example.utils.ParsedPart
import com.example.utils.WarrantyParserUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WarrantyParsingUnitTest {

    private val utcZone = ZoneId.of("UTC")

    // =========================================================================
    // 1. JSON Validator Tests
    // =========================================================================

    @Test
    fun `json validator accepts valid output with DAYS MONTHS and YEARS`() {
        val rawJson = """
            {"parts":[
                {"label":"Body","durationValue":1,"durationUnit":"YEARS"},
                {"label":"Motor","durationValue":24,"durationUnit":"MONTHS"},
                {"label":"Battery","durationValue":90,"durationUnit":"DAYS"}
            ]}
        """.trimIndent()

        val result = OpenRouterService.validateAiJson(rawJson)
        assertTrue(result.isValidJson)
        assertTrue(result.discardedMessages.isEmpty())
        assertEquals(3, result.validParts.size)
        assertEquals(ParsedPart("Body", 1, "YEARS"), result.validParts[0])
        assertEquals(ParsedPart("Motor", 24, "MONTHS"), result.validParts[1])
        assertEquals(ParsedPart("Battery", 90, "DAYS"), result.validParts[2])
    }

    @Test
    fun `json validator handles missing fields by discarding invalid parts or rejecting missing root parts`() {
        // Missing root "parts" field
        val missingRootParts = """{"items":[{"label":"Body","durationValue":1,"durationUnit":"YEARS"}]}"""
        val rootResult = OpenRouterService.validateAiJson(missingRootParts)
        assertFalse(rootResult.isValidJson)

        // Missing fields inside individual part objects
        val missingPartFields = """
            {"parts":[
                {"durationValue":1,"durationUnit":"YEARS"},
                {"label":"Motor","durationUnit":"YEARS"},
                {"label":"Compressor","durationValue":5},
                {"label":"ValidPart","durationValue":2,"durationUnit":"YEARS"}
            ]}
        """.trimIndent()

        val partResult = OpenRouterService.validateAiJson(missingPartFields)
        assertTrue(partResult.isValidJson)
        assertEquals(1, partResult.validParts.size)
        assertEquals(ParsedPart("ValidPart", 2, "YEARS"), partResult.validParts[0])
        assertEquals(3, partResult.discardedMessages.size)
    }

    @Test
    fun `json validator discards parts with wrong durationUnit`() {
        val wrongUnitsJson = """
            {"parts":[
                {"label":"Adapter","durationValue":2,"durationUnit":"WEEKS"},
                {"label":"Cable","durationValue":1,"durationUnit":"YEAR"},
                {"label":"Screen","durationValue":48,"durationUnit":"HOURS"},
                {"label":"Body","durationValue":1,"durationUnit":"YEARS"}
            ]}
        """.trimIndent()

        val result = OpenRouterService.validateAiJson(wrongUnitsJson)
        assertTrue(result.isValidJson)
        assertEquals(1, result.validParts.size)
        assertEquals("Body", result.validParts[0].label)
        assertEquals(3, result.discardedMessages.size)
        assertTrue(result.discardedMessages.all { it.contains("durationUnit") })
    }

    @Test
    fun `json validator discards parts with negative zero or non-integer durationValue`() {
        val badValuesJson = """
            {"parts":[
                {"label":"NegativePart","durationValue":-2,"durationUnit":"YEARS"},
                {"label":"ZeroPart","durationValue":0,"durationUnit":"MONTHS"},
                {"label":"FloatPart","durationValue":1.5,"durationUnit":"YEARS"},
                {"label":"ValidMotor","durationValue":3,"durationUnit":"YEARS"}
            ]}
        """.trimIndent()

        val result = OpenRouterService.validateAiJson(badValuesJson)
        assertTrue(result.isValidJson)
        assertEquals(1, result.validParts.size)
        assertEquals(ParsedPart("ValidMotor", 3, "YEARS"), result.validParts[0])
        assertEquals(3, result.discardedMessages.size)
        assertTrue(result.discardedMessages.all { it.contains("positive integer") })
    }

    @Test
    fun `json validator discards labels over 30 characters and empty labels`() {
        val labelExactly30 = "A".repeat(30)
        val label31 = "B".repeat(31)
        val json = """
            {"parts":[
                {"label":"$labelExactly30","durationValue":1,"durationUnit":"YEARS"},
                {"label":"$label31","durationValue":2,"durationUnit":"YEARS"},
                {"label":"   ","durationValue":1,"durationUnit":"MONTHS"}
            ]}
        """.trimIndent()

        val result = OpenRouterService.validateAiJson(json)
        assertTrue(result.isValidJson)
        assertEquals(1, result.validParts.size)
        assertEquals(labelExactly30, result.validParts[0].label)
        assertEquals(2, result.discardedMessages.size)
        assertTrue(result.discardedMessages[0].contains("exceeds 30 characters"))
        assertTrue(result.discardedMessages[1].contains("empty"))
    }

    @Test
    fun `json validator rejects extra text outside the JSON`() {
        val validCore = """{"parts":[{"label":"Body","durationValue":1,"durationUnit":"YEARS"}]}"""

        // Leading text
        val withPrefix = "Here is the parsed warranty JSON:\n$validCore"
        assertFalse(OpenRouterService.validateAiJson(withPrefix).isValidJson)

        // Trailing text
        val withSuffix = "$validCore\nLet me know if you need anything else!"
        assertFalse(OpenRouterService.validateAiJson(withSuffix).isValidJson)

        // Markdown code fence
        val withMarkdownFence = "```json\n$validCore\n```"
        assertFalse(OpenRouterService.validateAiJson(withMarkdownFence).isValidJson)

        // Two JSON objects side by side (trailing JSON object after root closes)
        val twoObjects = "$validCore {\"extra\":true}"
        assertFalse(OpenRouterService.validateAiJson(twoObjects).isValidJson)
    }

    // =========================================================================
    // 2. Rule-Based Fallback Parser Tests
    // =========================================================================

    @Test
    fun `rule-based parser parses standard comma and and separated examples`() {
        // Example 1: "1 year body, 2 year motor"
        val res1 = WarrantyParserUtils.parseLocal("1 year body, 2 year motor")
        assertTrue(res1.unparsedSegments.isEmpty())
        assertEquals(
            listOf(
                ParsedPart("Body", 1, WarrantyUnit.YEARS),
                ParsedPart("Motor", 2, WarrantyUnit.YEARS)
            ),
            res1.parts
        )

        // Example 2: "2 years motor and 1 year body"
        val res2 = WarrantyParserUtils.parseLocal("2 years motor and 1 year body")
        assertTrue(res2.unparsedSegments.isEmpty())
        assertEquals(
            listOf(
                ParsedPart("Motor", 2, WarrantyUnit.YEARS),
                ParsedPart("Body", 1, WarrantyUnit.YEARS)
            ),
            res2.parts
        )
    }

    @Test
    fun `rule-based parser supports label-first pattern and case-insensitive units`() {
        val input = "Body 1 YEAR, motor 24 MONTHS and Battery 90 Days"
        val res = WarrantyParserUtils.parseLocal(input)
        assertTrue(res.unparsedSegments.isEmpty())
        assertEquals(
            listOf(
                ParsedPart("Body", 1, WarrantyUnit.YEARS),
                ParsedPart("Motor", 24, WarrantyUnit.MONTHS),
                ParsedPart("Battery", 90, WarrantyUnit.DAYS)
            ),
            res.parts
        )
    }

    @Test
    fun `rule-based parser does not guess on unclear inputs and returns them as unparsed`() {
        val unclearInput = "1 year body, lifetime warranty on frame, 2 weeks charger, 1 year, 1 year 6 months drum and 2 years motor"
        val result = WarrantyParserUtils.parseLocal(unclearInput)

        // Only "1 year body" and "2 years motor" are unambiguous
        assertEquals(
            listOf(
                ParsedPart("Body", 1, WarrantyUnit.YEARS),
                ParsedPart("Motor", 2, WarrantyUnit.YEARS)
            ),
            result.parts
        )

        // All 4 unclear segments must be preserved as unparsed lines without guessing
        assertEquals(
            listOf(
                "lifetime warranty on frame",
                "2 weeks charger",
                "1 year",
                "1 year 6 months drum"
            ),
            result.unparsedSegments
        )
    }

    // =========================================================================
    // 3. Expiry Calculation Tests (Each Unit, Month-End, Leap Years)
    // =========================================================================

    @Test
    fun `expiry calculation handles DAYS MONTHS YEARS month-end and leap years`() {
        // 1. DAYS across normal and leap February
        val feb28Leap = DateUtils.localDateToMillis(2024, 2, 28, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2024, 2, 29, utcZone),
            DateUtils.calculateExpiryDate(feb28Leap, 1, WarrantyUnit.DAYS, utcZone)
        )
        val feb28NonLeap = DateUtils.localDateToMillis(2025, 2, 28, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2025, 3, 1, utcZone),
            DateUtils.calculateExpiryDate(feb28NonLeap, 1, WarrantyUnit.DAYS, utcZone)
        )

        // 2. MONTHS: January 31 + 1 month in non-leap year (2025) -> Feb 28, 2025
        val jan31NonLeap = DateUtils.localDateToMillis(2025, 1, 31, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2025, 2, 28, utcZone),
            DateUtils.calculateExpiryDate(jan31NonLeap, 1, WarrantyUnit.MONTHS, utcZone)
        )

        // 3. MONTHS: January 31 + 1 month in leap year (2024) -> Feb 29, 2024
        val jan31Leap = DateUtils.localDateToMillis(2024, 1, 31, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2024, 2, 29, utcZone),
            DateUtils.calculateExpiryDate(jan31Leap, 1, WarrantyUnit.MONTHS, utcZone)
        )

        // 4. MONTHS: March 31 + 1 month -> April 30
        val mar31 = DateUtils.localDateToMillis(2025, 3, 31, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2025, 4, 30, utcZone),
            DateUtils.calculateExpiryDate(mar31, 1, WarrantyUnit.MONTHS, utcZone)
        )

        // 5. YEARS: Leap day Feb 29, 2024 + 1 year -> Feb 28, 2025; + 4 years -> Feb 29, 2028
        val feb29Leap = DateUtils.localDateToMillis(2024, 2, 29, utcZone)
        assertEquals(
            DateUtils.localDateToMillis(2025, 2, 28, utcZone),
            DateUtils.calculateExpiryDate(feb29Leap, 1, WarrantyUnit.YEARS, utcZone)
        )
        assertEquals(
            DateUtils.localDateToMillis(2028, 2, 29, utcZone),
            DateUtils.calculateExpiryDate(feb29Leap, 4, WarrantyUnit.YEARS, utcZone)
        )
    }

    // =========================================================================
    // 4. Outgoing AI Request Privacy & Mocked Network Client Tests
    // =========================================================================

    @Test
    fun `outgoing AI request contains only product name and warranty and never leaks seller address PAN phone or invoice`() = runTest {
        val capturedBodies = mutableListOf<String>()
        val capturedHeaders = mutableListOf<String>()

        val mockClient = OpenRouterHttpClient { _, apiKey, requestBodyJson, _ ->
            capturedHeaders.add(apiKey)
            capturedBodies.add(requestBodyJson)
            HttpResponse(
                statusCode = 200,
                body = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": "{\"parts\":[{\"label\":\"Body\",\"durationValue\":1,\"durationUnit\":\"YEARS\"},{\"label\":\"Motor\",\"durationValue\":2,\"durationUnit\":\"YEARS\"}]}"
                          }
                        }
                      ]
                    }
                """.trimIndent()
            )
        }

        // Sensitive invoice fields that must NEVER appear in the outgoing AI request
        val secretSeller = "Croma Infiniti Retail Ltd"
        val secretAddress = "Plot 42, MG Road, Indiranagar, Bengaluru 560038"
        val secretPan = "ABCDE1234F"
        val secretPhone = "9876543210"
        val secretInvoiceNo = "INV-2026-889900"
        val secretSerialNo = "SN-LG-99887766"
        val secretAmount = "42999.00"

        // Even if someone accidentally pastes labeled sensitive lines or PAN/phone into text,
        // sanitizeForAiPrompt strips them while keeping product name and warranty description.
        val rawWarrantyInput = """
            Seller: $secretSeller
            Address: $secretAddress
            PAN: $secretPan
            Phone: +91-$secretPhone
            Invoice Number: $secretInvoiceNo
            Serial Number: $secretSerialNo
            Amount: $secretAmount
            1 year body, 2 year motor
        """.trimIndent()

        val outcome = OpenRouterService.parseWarrantyWithAi(
            productName = "LG Front Load Washing Machine",
            warrantyText = rawWarrantyInput,
            apiKey = "sk-or-v1-mock-test-key",
            preferredModel = "google/gemma-3-27b-it:free",
            httpClient = mockClient
        )

        assertTrue(outcome is OpenRouterParseOutcome.Success)
        val success = outcome as OpenRouterParseOutcome.Success
        assertEquals(2, success.validParts.size)
        assertEquals(ParsedPart("Body", 1, "YEARS"), success.validParts[0])
        assertEquals(ParsedPart("Motor", 2, "YEARS"), success.validParts[1])

        assertEquals(1, capturedBodies.size)
        val outgoingJson = capturedBodies.first()

        // Verify product name and warranty text are present
        assertTrue(outgoingJson.contains("LG Front Load Washing Machine"))
        assertTrue(outgoingJson.contains("1 year body, 2 year motor"))

        // Verify NO sensitive data appears in the outgoing request
        assertFalse("Seller name must not appear in AI request", outgoingJson.contains(secretSeller))
        assertFalse("Address must not appear in AI request", outgoingJson.contains(secretAddress))
        assertFalse("PAN must not appear in AI request", outgoingJson.contains(secretPan))
        assertFalse("Phone must not appear in AI request", outgoingJson.contains(secretPhone))
        assertFalse("Invoice number must not appear in AI request", outgoingJson.contains(secretInvoiceNo))
        assertFalse("Serial number must not appear in AI request", outgoingJson.contains(secretSerialNo))
        assertFalse("Amount must not appear in AI request", outgoingJson.contains(secretAmount))
    }

    @Test
    fun `mocked network client verifies model fallback on rate limit and safe error logging`() = runTest {
        val triedModels = mutableListOf<String>()
        val loggedErrors = mutableListOf<String>()
        val secretApiKey = "sk-or-v1-super-secret-user-key-999"
        val warrantyInput = "1 year body, 5 years compressor"

        val mockClient = OpenRouterHttpClient { _, _, requestBodyJson, _ ->
            val modelRegex = Regex(""""model"\s*:\s*"([^"]+)"""")
            val modelId = modelRegex.find(requestBodyJson)?.groupValues?.get(1) ?: ""
            triedModels.add(modelId)

            if (triedModels.size == 1) {
                // First model fails with HTTP 429 Rate Limit
                HttpResponse(statusCode = 429, body = """{"error":"Rate limited"}""")
            } else {
                // Second (fallback) model succeeds
                HttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "{\"parts\":[{\"label\":\"Body\",\"durationValue\":1,\"durationUnit\":\"YEARS\"},{\"label\":\"Compressor\",\"durationValue\":5,\"durationUnit\":\"YEARS\"}]}"
                              }
                            }
                          ]
                        }
                    """.trimIndent()
                )
            }
        }

        val outcome = OpenRouterService.parseWarrantyWithAi(
            productName = "Refrigerator",
            warrantyText = warrantyInput,
            apiKey = secretApiKey,
            preferredModel = OpenRouterService.FALLBACK_MODELS[0],
            httpClient = mockClient,
            errorLogger = { loggedErrors.add(it) }
        )

        assertTrue(outcome is OpenRouterParseOutcome.Success)
        val success = outcome as OpenRouterParseOutcome.Success
        assertNotNull(success)
        assertTrue(success.usedFallbackModel)
        assertEquals(OpenRouterService.FALLBACK_MODELS[1], success.modelUsed)
        assertEquals(2, triedModels.size)

        // Verify error logger recorded the 429 failure WITHOUT logging the API key or warranty text
        assertEquals(1, loggedErrors.size)
        assertFalse(loggedErrors[0].contains(secretApiKey))
        assertFalse(loggedErrors[0].contains(warrantyInput))
    }
}
