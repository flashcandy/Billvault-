package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.example.data.preferences.SettingsManager
import com.example.utils.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ExtractedInvoiceResult(
    val sellerName: String = "",
    val invoiceNumber: String = "",
    val orderId: String = "",
    val invoiceDate: String = "", // YYYY-MM-DD
    val totalAmount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val items: List<ExtractedItemResult> = emptyList(),
    val rawText: String = "",
    val error: String? = null
)

data class ExtractedItemResult(
    val productName: String = "",
    val serialNumber: String = "",
    val quantity: Int = 1,
    val amount: Double = 0.0,
    val warrantyMonths: Int = 0,
    val notes: String = ""
)

class AiExtractionService(
    private val context: Context,
    private val settingsManager: SettingsManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Copy input Uri to app private storage and process text extraction + AI structured parse.
     */
    suspend fun processDocumentUri(uri: Uri): ExtractedInvoiceResult = withContext(Dispatchers.IO) {
        val fileType = getFileType(uri)
        val copiedFile = copyUriToPrivateStorage(uri, fileType) ?: return@withContext ExtractedInvoiceResult(
            error = "Failed to access document file"
        )

        val extractedText = if (fileType == "PDF") {
            extractTextFromPdf(copiedFile)
        } else {
            extractTextFromImageFile(copiedFile)
        }

        if (extractedText.isBlank()) {
            return@withContext ExtractedInvoiceResult(
                error = "Could not extract text from document. Ensure file is clear."
            )
        }

        // Run AI Extractor or Rule-based fallback
        val aiResult = parseInvoiceWithAiOrFallback(extractedText)
        aiResult.copy(rawText = extractedText)
    }

    private fun getFileType(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return if (mimeType.contains("pdf", ignoreCase = true) || uri.toString().endsWith(".pdf", ignoreCase = true)) {
            "PDF"
        } else {
            "IMAGE"
        }
    }

    private fun copyUriToPrivateStorage(uri: Uri, type: String): File? {
        return try {
            val extension = if (type == "PDF") ".pdf" else ".jpg"
            val file = File(context.filesDir, "invoice_${System.currentTimeMillis()}$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Phase 2: PDF Text extraction via PdfBox-Android, with fallback to PdfRenderer + ML Kit OCR.
     */
    private suspend fun extractTextFromPdf(pdfFile: File): String = withContext(Dispatchers.IO) {
        var text = ""
        try {
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper()
                text = stripper.getText(document).trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (text.length >= 20) {
            return@withContext text
        }

        // Embedded text failed or scanned PDF -> Render pages to bitmap and run ML Kit OCR
        return@withContext renderPdfAndRunOcr(pdfFile)
    }

    private suspend fun renderPdfAndRunOcr(pdfFile: File): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, 5) // max 5 pages

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageText = runMlKitOcr(bitmap)
                sb.append("--- PAGE ${i + 1} ---\n").append(pageText).append("\n\n")
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sb.toString().trim()
    }

    private suspend fun extractTextFromImageFile(imageFile: File): String = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    private suspend fun runMlKitOcr(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    /**
     * Send raw text to Gemini or OpenRouter AI, with validation and regex fallback.
     */
    private suspend fun parseInvoiceWithAiOrFallback(rawText: String): ExtractedInvoiceResult = withContext(Dispatchers.IO) {
        val provider = settingsManager.aiProvider
        val apiKey = if (provider == SettingsManager.PROVIDER_OPENROUTER) {
            settingsManager.openRouterApiKey
        } else {
            settingsManager.geminiApiKey
        }

        if (apiKey.isBlank()) {
            // Fallback to rule-based parser for offline / missing key
            return@withContext parseInvoiceWithRules(rawText)
        }

        val prompt = buildPrompt(rawText)
        val jsonResponse = if (provider == SettingsManager.PROVIDER_OPENROUTER) {
            callOpenRouterApi(prompt, apiKey, settingsManager.openRouterModelName)
        } else {
            callGeminiApi(prompt, apiKey, settingsManager.geminiModelName)
        }

        if (jsonResponse != null) {
            val parsed = validateAndParseJson(jsonResponse)
            if (parsed != null && (parsed.sellerName.isNotBlank() || parsed.items.isNotEmpty())) {
                return@withContext parsed
            }
        }

        // If AI call fails or response parsing fails, fallback to rules
        parseInvoiceWithRules(rawText)
    }

    private fun buildPrompt(rawText: String): String {
        return """
            You are a precise invoice and purchase receipt parser.
            Extract key purchase details from the text below and respond with ONLY valid JSON strictly matching this schema.
            Do NOT include markdown backticks or extra text outside JSON.
            If a field is missing, uncertain, or not mentioned, return empty string "" or 0, do NOT invent values.
            
            JSON schema:
            {
              "sellerName": "string",
              "invoiceNumber": "string",
              "orderId": "string",
              "invoiceDate": "YYYY-MM-DD",
              "totalAmount": 0.0,
              "taxAmount": 0.0,
              "items": [
                {
                  "productName": "string",
                  "serialNumber": "string",
                  "quantity": 1,
                  "amount": 0.0,
                  "warrantyMonths": 12,
                  "notes": "string"
                }
              ]
            }

            Invoice Text:
            $rawText
        """.trimIndent()
    }

    private fun callGeminiApi(prompt: String, apiKey: String, model: String): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val jsonObj = JSONObject(body)
            val candidates = jsonObj.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text")
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun callOpenRouterApi(prompt: String, apiKey: String, model: String): String? {
        return try {
            val url = "https://openrouter.ai/api/v1/chat/completions"
            val jsonPayload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.1)
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://billvault.app")
                .header("X-Title", "Bill Vault")
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null

            val jsonObj = JSONObject(body)
            val choices = jsonObj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                return message?.optString("content")
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Phase 3 & Unit Testing: JSON validation logic. Clean backticks and parse JSON safely.
     */
    fun validateAndParseJson(rawJson: String): ExtractedInvoiceResult? {
        return try {
            val cleaned = rawJson
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleaned)
            val sellerName = json.optString("sellerName", "").trim()
            val invoiceNumber = json.optString("invoiceNumber", "").trim()
            val orderId = json.optString("orderId", "").trim()
            val invoiceDate = json.optString("invoiceDate", "").trim()
            val totalAmount = json.optDouble("totalAmount", 0.0)
            val taxAmount = json.optDouble("taxAmount", 0.0)

            val itemsList = mutableListOf<ExtractedItemResult>()
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    itemsList.add(
                        ExtractedItemResult(
                            productName = itemObj.optString("productName", "").trim(),
                            serialNumber = itemObj.optString("serialNumber", "").trim(),
                            quantity = itemObj.optInt("quantity", 1),
                            amount = itemObj.optDouble("amount", 0.0),
                            warrantyMonths = itemObj.optInt("warrantyMonths", 0),
                            notes = itemObj.optString("notes", "").trim()
                        )
                    )
                }
            }

            ExtractedInvoiceResult(
                sellerName = sellerName,
                invoiceNumber = invoiceNumber,
                orderId = orderId,
                invoiceDate = invoiceDate,
                totalAmount = totalAmount,
                taxAmount = taxAmount,
                items = itemsList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Rule-based fallback parser for Flipkart / Amazon / standard tax invoices.
     */
    fun parseInvoiceWithRules(rawText: String): ExtractedInvoiceResult {
        var seller = ""
        var invoiceNo = ""
        var orderId = ""
        var date = ""
        var total = 0.0
        var tax = 0.0
        val items = mutableListOf<ExtractedItemResult>()

        val lines = rawText.lines()

        // Seller detection
        if (rawText.contains("Flipkart", ignoreCase = true)) {
            seller = "Flipkart Internet Pvt Ltd"
        } else if (rawText.contains("Amazon", ignoreCase = true)) {
            seller = "Amazon Retail India"
        } else if (rawText.contains("Croma", ignoreCase = true)) {
            seller = "Croma Electronics"
        } else {
            // First non-empty line as seller
            seller = lines.firstOrNull { it.trim().length > 3 }?.trim() ?: "Unknown Seller"
        }

        // Invoice Number Regex
        val invoiceNoRegex = Regex("""(?:Invoice|Inv)\s*(?:Number|No|#|\.)?[\s:]+([A-Z0-9\-_]{4,})""", RegexOption.IGNORE_CASE)
        val invoiceMatch = Regex("""(?:Invoice|Inv)\s*(?:Number|No|#|\.)?[\s:]+([A-Z0-9\-_]{4,})""", RegexOption.IGNORE_CASE).find(rawText)
            ?: Regex("""(?:Invoice|Inv)[\s#:]*([A-Z0-9\-_]{5,})""", RegexOption.IGNORE_CASE).find(rawText)
        if (invoiceMatch != null) {
            val candidate = invoiceMatch.groupValues[1]
            if (!candidate.equals("SUPPLY", ignoreCase = true) && !candidate.equals("BILL", ignoreCase = true)) {
                invoiceNo = candidate
            }
        }
        if (invoiceNo.isBlank()) {
            val fallbackMatch = Regex("""FK-IN-[0-9\-]+|AMZN-[0-9\-]+|CR-[0-9\-]+""").find(rawText)
            if (fallbackMatch != null) {
                invoiceNo = fallbackMatch.value
            }
        }

        // Order ID Regex
        val orderIdRegex = Regex("""(?:Order\s*(?:ID|No|#)|OD\d{15,})[\s:]*([A-Z0-9\-_]+)""", RegexOption.IGNORE_CASE)
        val orderMatch = orderIdRegex.find(rawText)
        if (orderMatch != null) {
            orderId = orderMatch.groupValues[1]
        }

        // Date Regex (YYYY-MM-DD, DD-MM-YYYY, or DD/MM/YYYY)
        val dateRegex = Regex("""\b(\d{1,2}[/\.-]\d{1,2}[/\.-]\d{2,4}|\d{4}[/\.-]\d{1,2}[/\.-]\d{1,2})\b""")
        val dateMatch = dateRegex.find(rawText)
        if (dateMatch != null) {
            val rawDateStr = dateMatch.value
            val millis = DateUtils.parseDateToMillis(rawDateStr)
            date = DateUtils.formatIsoDate(millis)
        }

        // Total Amount Regex
        val totalRegex = Regex("""(?:Total|Amount\s*Paid|Grand\s*Total|Net\s*Amount)[\s:₹Rs\.]*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
        val totalMatch = totalRegex.find(rawText)
        if (totalMatch != null) {
            total = totalMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        }

        // Tax Amount Regex
        val taxRegex = Regex("""(?:GST|Tax\s*Amount|IGST|CGST)(?:\s*\(\d+%\))?[\s:₹Rs\.]*([\d,]+\.?\d+)""", RegexOption.IGNORE_CASE)
        val taxMatch = taxRegex.find(rawText)
        if (taxMatch != null) {
            tax = taxMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
        }

        // Warranty Detection
        var warrantyMonths = 12 // Default 1 year warranty
        val warrantyRegex = Regex("""(\d+)\s*(?:Month|Year|Yr)s?\s*(?:Warranty|Protection|Guarantee)""", RegexOption.IGNORE_CASE)
        val warrantyMatch = warrantyRegex.find(rawText)
        if (warrantyMatch != null) {
            val num = warrantyMatch.groupValues[1].toIntOrNull() ?: 1
            if (rawText.contains("Year", ignoreCase = true) || rawText.contains("Yr", ignoreCase = true)) {
                warrantyMonths = num * 12
            } else {
                warrantyMonths = num
            }
        }

        // Item detection
        var productName = "Purchased Product"
        for (line in lines) {
            if (line.contains("Phone", true) || line.contains("Laptop", true) ||
                line.contains("Earbuds", true) || line.contains("Headphone", true) ||
                line.contains("Watch", true) || line.contains("TV", true) ||
                line.contains("Device", true)) {
                productName = line.trim()
                break
            }
        }

        items.add(
            ExtractedItemResult(
                productName = productName,
                serialNumber = "",
                quantity = 1,
                amount = if (total > 0) total else 0.0,
                warrantyMonths = warrantyMonths,
                notes = "Extracted from invoice text"
            )
        )

        return ExtractedInvoiceResult(
            sellerName = seller,
            invoiceNumber = invoiceNo,
            orderId = orderId,
            invoiceDate = date,
            totalAmount = total,
            taxAmount = tax,
            items = items
        )
    }
}
