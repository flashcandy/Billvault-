package com.example.utils

import android.util.Log
import com.example.data.model.WarrantyUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class OpenRouterModel(
    val id: String,
    val displayName: String
)

data class AiValidationResult(
    val isValidJson: Boolean,
    val validParts: List<ParsedPart>,
    val discardedMessages: List<String>,
    val errorMessage: String? = null
)

sealed class OpenRouterParseOutcome {
    data class Success(
        val modelUsed: String,
        val usedFallbackModel: Boolean,
        val validParts: List<ParsedPart>,
        val discardedMessages: List<String>
    ) : OpenRouterParseOutcome()

    data class Failure(
        val reason: String
    ) : OpenRouterParseOutcome()
}

data class HttpResponse(
    val statusCode: Int,
    val body: String
)

fun interface OpenRouterHttpClient {
    suspend fun postJson(
        url: String,
        apiKey: String,
        requestBodyJson: String,
        timeoutMs: Int
    ): HttpResponse
}

object DefaultOpenRouterHttpClient : OpenRouterHttpClient {
    override suspend fun postJson(
        url: String,
        apiKey: String,
        requestBodyJson: String,
        timeoutMs: Int
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBodyJson)
                writer.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            HttpResponse(statusCode = code, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

object OpenRouterService {

    const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val REQUEST_TIMEOUT_MS = 15_000
    const val MAX_LABEL_LENGTH = 30

    val FREE_MODELS: List<OpenRouterModel> = listOf(
        OpenRouterModel(
            id = "google/gemma-3-27b-it:free",
            displayName = "Gemma 3 27B (Free)"
        ),
        OpenRouterModel(
            id = "meta-llama/llama-3.3-70b-instruct:free",
            displayName = "Llama 3.3 70B Instruct (Free)"
        ),
        OpenRouterModel(
            id = "qwen/qwen-2.5-72b-instruct:free",
            displayName = "Qwen 2.5 72B Instruct (Free)"
        ),
        OpenRouterModel(
            id = "mistralai/mistral-small-3.1-24b-instruct:free",
            displayName = "Mistral Small 3.1 24B (Free)"
        )
    )

    val DEFAULT_MODEL_ID: String = FREE_MODELS.first().id

    // Built-in fallback list of 3 free models
    val FALLBACK_MODELS: List<String> = listOf(
        "google/gemma-3-27b-it:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free"
    )

    const val SYSTEM_PROMPT = """You are a strict warranty parser. Turn the user's product name and warranty description into structured warranty parts.
Output JSON ONLY, with no markdown code fences and no text outside the JSON.
The expected JSON shape is:
{"parts":[{"label":"Body","durationValue":1,"durationUnit":"YEARS"}]}
Rules:
1. "label" must be non-empty, at most 30 characters, short, and taken from the user's wording where possible.
2. "durationValue" must be a positive integer (> 0).
3. "durationUnit" must be strictly one of "DAYS", "MONTHS", or "YEARS"."""

    fun getModelFallbackChain(chosenModel: String): List<String> {
        val primary = chosenModel.trim().ifBlank { DEFAULT_MODEL_ID }
        val chain = mutableListOf(primary)
        for (fallback in FALLBACK_MODELS) {
            if (fallback !in chain) {
                chain.add(fallback)
            }
        }
        return chain
    }

    private val panRegex = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("""(?:\+?\d{1,3}[\s\-]?)?\b\d{10}\b""")
    private val sensitiveFieldLineRegex = Regex(
        """(?m)^\s*(?:seller|store|address|pan|gstin|phone|mobile|tel|invoice(?:\s*(?:no|number|#))?|serial(?:\s*(?:no|number|#))?|amount|total|price)\s*[:\-].*$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strips any accidentally pasted sensitive identifiers (PAN, 10-digit phone numbers,
     * or labeled Seller/Address/PAN/Phone/Invoice/Serial/Amount lines).
     */
    fun sanitizeForAiPrompt(raw: String): String {
        return raw
            .replace(sensitiveFieldLineRegex, "")
            .replace(panRegex, "")
            .replace(phoneRegex, "")
            .trim()
    }

    /**
     * Builds the OpenRouter chat completion payload.
     * PRIVACY GUARANTEE: Only accepts and sends [productName] and [warrantyText].
     * Never sends seller name, address, PAN, phone, invoice number, serial numbers, or amounts.
     */
    fun buildRequestPayload(
        model: String,
        productName: String,
        warrantyText: String
    ): String {
        val safeProduct = sanitizeForAiPrompt(productName)
        val safeWarranty = sanitizeForAiPrompt(warrantyText)
        val userContent = "Product: $safeProduct\nWarranty: $safeWarranty"

        val root = JSONObject()
        root.put("model", model.trim())
        root.put("temperature", 0.0)

        val messages = JSONArray()
        val systemMsg = JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        }
        val userMsg = JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        }
        messages.put(systemMsg)
        messages.put(userMsg)
        root.put("messages", messages)

        return root.toString()
    }

    /**
     * Validates the raw content returned by the model.
     * - Rejects any extra text outside the root JSON object.
     * - Validates each part in "parts":
     *   - label: non-empty string, <= 30 characters
     *   - durationValue: positive integer (> 0)
     *   - durationUnit: DAYS, MONTHS, or YEARS
     * - Discards invalid parts and records a clear message for each discarded part.
     */
    fun validateAiJson(rawResponseText: String): AiValidationResult {
        val trimmed = rawResponseText.trim()
        if (trimmed.isEmpty()) {
            return AiValidationResult(
                isValidJson = false,
                validParts = emptyList(),
                discardedMessages = emptyList(),
                errorMessage = "Empty response from AI model"
            )
        }

        if (!isStrictlySingleJsonObject(trimmed)) {
            return AiValidationResult(
                isValidJson = false,
                validParts = emptyList(),
                discardedMessages = emptyList(),
                errorMessage = "Extra text outside JSON is not permitted"
            )
        }

        val jsonObject = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            return AiValidationResult(
                isValidJson = false,
                validParts = emptyList(),
                discardedMessages = emptyList(),
                errorMessage = "Invalid JSON syntax"
            )
        }

        if (!jsonObject.has("parts") || jsonObject.isNull("parts")) {
            return AiValidationResult(
                isValidJson = false,
                validParts = emptyList(),
                discardedMessages = emptyList(),
                errorMessage = "Missing required 'parts' field in JSON"
            )
        }

        val partsArray = jsonObject.optJSONArray("parts")
            ?: return AiValidationResult(
                isValidJson = false,
                validParts = emptyList(),
                discardedMessages = emptyList(),
                errorMessage = "'parts' field must be a JSON array"
            )

        val validParts = mutableListOf<ParsedPart>()
        val discardedMessages = mutableListOf<String>()

        for (i in 0 until partsArray.length()) {
            val partNumber = i + 1
            val itemObj = partsArray.optJSONObject(i)
            if (itemObj == null) {
                discardedMessages.add("Discarded part #$partNumber: item is not a JSON object.")
                continue
            }

            if (!itemObj.has("label") || itemObj.isNull("label") ||
                !itemObj.has("durationValue") || itemObj.isNull("durationValue") ||
                !itemObj.has("durationUnit") || itemObj.isNull("durationUnit")
            ) {
                discardedMessages.add("Discarded part #$partNumber: missing required field (label, durationValue, or durationUnit).")
                continue
            }

            val rawLabelAny = itemObj.opt("label")
            if (rawLabelAny !is String) {
                discardedMessages.add("Discarded part #$partNumber: label must be a text string.")
                continue
            }

            val label = rawLabelAny.trim()
            if (label.isEmpty()) {
                discardedMessages.add("Discarded part #$partNumber: label cannot be empty.")
                continue
            }

            if (label.length > MAX_LABEL_LENGTH) {
                val preview = label.take(15) + "..."
                discardedMessages.add("Discarded part '$preview': label exceeds $MAX_LABEL_LENGTH characters (${label.length}/$MAX_LABEL_LENGTH).")
                continue
            }

            val rawDurationAny = itemObj.opt("durationValue")
            val durationValue: Int? = when (rawDurationAny) {
                is Int -> rawDurationAny
                is Long -> if (rawDurationAny in Int.MIN_VALUE..Int.MAX_VALUE) rawDurationAny.toInt() else null
                is Number -> {
                    val d = rawDurationAny.toDouble()
                    if (d % 1.0 == 0.0 && d in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                        d.toInt()
                    } else {
                        null
                    }
                }
                else -> null
            }

            if (durationValue == null || durationValue <= 0) {
                discardedMessages.add("Discarded part '$label': durationValue must be a positive integer.")
                continue
            }

            val rawUnitAny = itemObj.opt("durationUnit")
            val durationUnit = (rawUnitAny as? String)?.trim() ?: ""
            if (durationUnit !in WarrantyUnit.ALL) {
                discardedMessages.add("Discarded part '$label': invalid durationUnit '$durationUnit' (must be DAYS, MONTHS, or YEARS).")
                continue
            }

            validParts.add(
                ParsedPart(
                    label = label,
                    durationValue = durationValue,
                    durationUnit = durationUnit
                )
            )
        }

        return AiValidationResult(
            isValidJson = true,
            validParts = validParts,
            discardedMessages = discardedMessages,
            errorMessage = null
        )
    }

    /**
     * Checks that [text] starts with `{` and ends with `}` and that the root JSON object
     * spans the entire string with no prefix or suffix text outside the JSON.
     */
    internal fun isStrictlySingleJsonObject(text: String): Boolean {
        if (!text.startsWith("{") || !text.endsWith("}")) return false

        var depth = 0
        var inString = false
        var escaped = false

        for (i in text.indices) {
            val ch = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') {
                    depth++
                } else if (ch == '}') {
                    depth--
                    if (depth < 0) return false
                    if (depth == 0 && i < text.lastIndex) {
                        // Root object closed before the end of the string -> trailing text exists!
                        return false
                    }
                }
            }
        }

        return depth == 0 && !inString
    }

    /**
     * Extracts `choices[0].message.content` from an OpenRouter API response JSON.
     */
    internal fun extractAssistantContent(openRouterResponseBody: String): String? {
        return try {
            val root = JSONObject(openRouterResponseBody)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val firstChoice = choices.optJSONObject(0) ?: return null
            val message = firstChoice.optJSONObject("message") ?: return null
            if (!message.has("content") || message.isNull("content")) return null
            message.optString("content")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calls OpenRouter with the user's chosen model and falls back through [FALLBACK_MODELS]
     * if the chosen model fails, times out, or is rate-limited.
     * Logs errors safely WITHOUT ever logging the warranty text or API key.
     */
    suspend fun parseWarrantyWithAi(
        productName: String,
        warrantyText: String,
        apiKey: String,
        preferredModel: String,
        httpClient: OpenRouterHttpClient = DefaultOpenRouterHttpClient,
        errorLogger: (String) -> Unit = ::defaultSafeErrorLogger
    ): OpenRouterParseOutcome {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return OpenRouterParseOutcome.Failure("No OpenRouter API key configured.")
        }
        if (warrantyText.isBlank()) {
            return OpenRouterParseOutcome.Failure("Warranty description is empty.")
        }

        val modelsToTry = getModelFallbackChain(preferredModel)
        var lastFailureReason = "AI request failed."

        for ((attemptIndex, modelId) in modelsToTry.withIndex()) {
            val payload = buildRequestPayload(
                model = modelId,
                productName = productName,
                warrantyText = warrantyText
            )

            try {
                val response = withTimeout(REQUEST_TIMEOUT_MS.toLong()) {
                    httpClient.postJson(
                        url = OPENROUTER_API_URL,
                        apiKey = cleanKey,
                        requestBodyJson = payload,
                        timeoutMs = REQUEST_TIMEOUT_MS
                    )
                }

                if (response.statusCode == 429) {
                    lastFailureReason = "Model rate-limited (HTTP 429)."
                    errorLogger("OpenRouter model '$modelId' rate-limited (HTTP 429) on attempt #${attemptIndex + 1}.")
                    continue
                }

                if (response.statusCode !in 200..299) {
                    lastFailureReason = "HTTP error ${response.statusCode}."
                    errorLogger("OpenRouter model '$modelId' failed with HTTP status ${response.statusCode} on attempt #${attemptIndex + 1}.")
                    continue
                }

                val assistantContent = extractAssistantContent(response.body)
                if (assistantContent == null) {
                    lastFailureReason = "Malformed completion envelope from model."
                    errorLogger("OpenRouter model '$modelId' returned an unreadable response structure on attempt #${attemptIndex + 1}.")
                    continue
                }

                val validation = validateAiJson(assistantContent)
                if (!validation.isValidJson) {
                    lastFailureReason = validation.errorMessage ?: "Invalid JSON output from model."
                    errorLogger("OpenRouter model '$modelId' failed JSON validation (${validation.errorMessage}) on attempt #${attemptIndex + 1}.")
                    continue
                }

                return OpenRouterParseOutcome.Success(
                    modelUsed = modelId,
                    usedFallbackModel = attemptIndex > 0,
                    validParts = validation.validParts,
                    discardedMessages = validation.discardedMessages
                )
            } catch (e: Exception) {
                val exceptionClass = e::class.java.simpleName
                lastFailureReason = "Request failed ($exceptionClass)."
                errorLogger("OpenRouter request for model '$modelId' threw $exceptionClass on attempt #${attemptIndex + 1}.")
            }
        }

        return OpenRouterParseOutcome.Failure(lastFailureReason)
    }

    private fun defaultSafeErrorLogger(safeMessage: String) {
        try {
            Log.w("OpenRouterService", safeMessage)
        } catch (_: Exception) {
            // Ignored in local JVM tests where android.util.Log may not be mocked
        }
    }
}
