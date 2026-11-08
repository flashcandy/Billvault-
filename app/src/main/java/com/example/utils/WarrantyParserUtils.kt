package com.example.utils

import com.example.data.model.WarrantyUnit
import java.util.Locale

data class ParsedPart(
    val label: String,
    val durationValue: Int,
    val durationUnit: String
)

data class ParseResult(
    val parts: List<ParsedPart>,
    val unparsedSegments: List<String>
)

object WarrantyParserUtils {

    private const val MAX_LABEL_LENGTH = 30

    // Pattern 1: "<number> year(s)|month(s)|day(s) <label>" (case-insensitive)
    // Examples: "1 year body", "2 years motor", "6 months battery", "90 days screen"
    private val patternNumberFirst = Regex(
        """^\s*(\d+)\s*(days?|months?|years?)\s+(?:on\s+|for\s+)?(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Pattern 2: "<label> <number> year(s)|month(s)|day(s)" (case-insensitive)
    // Examples: "body 1 year", "motor 2 years", "compressor: 5 years", "battery - 6 months"
    private val patternLabelFirst = Regex(
        """^\s*(.+?)\s*(?:[:\-]\s*|\s+)(\d+)\s*(days?|months?|years?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Detects if a candidate label still contains another "<number> <unit>" expression
    // (e.g., "1 year 6 months motor"), which is ambiguous and should not be guessed.
    private val embeddedDurationRegex = Regex(
        """\b\d+\s*(?:days?|months?|years?)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Local rule-based fallback parser.
     * Supports case-insensitive patterns:
     * - "<number> year(s)|month(s)|day(s) <label>"
     * - "<label> <number> year(s)|month(s)|day(s)"
     * - Comma, semicolon, newline, or "and" separated lists.
     *
     * Does not guess: any segment that cannot be unambiguously parsed into a positive
     * integer duration, valid unit (DAYS, MONTHS, YEARS), and non-empty label (<= 30 chars)
     * is returned in [ParseResult.unparsedSegments] so the user can add it manually.
     */
    fun parseLocal(rawText: String): ParseResult {
        if (rawText.isBlank()) {
            return ParseResult(emptyList(), emptyList())
        }

        // Split by commas (including ", and "), semicolons, newlines, or standalone "and"
        val segments = rawText.split(
            Regex("""\s*(?:,\s*and\b|,|;|\n|\band\b)\s*""", RegexOption.IGNORE_CASE)
        )
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val parts = mutableListOf<ParsedPart>()
        val unparsed = mutableListOf<String>()

        for (segment in segments) {
            val match1 = patternNumberFirst.matchEntire(segment)
            val match2 = if (match1 == null) patternLabelFirst.matchEntire(segment) else null

            when {
                match1 != null -> {
                    val num = match1.groupValues[1].toIntOrNull()
                    val unitStr = normalizeUnit(match1.groupValues[2])
                    val rawLabel = match1.groupValues[3]
                    val cleanedLabel = cleanLabel(rawLabel)

                    if (num != null && num > 0 && unitStr != null && isValidLabel(cleanedLabel)) {
                        parts.add(
                            ParsedPart(
                                label = cleanedLabel,
                                durationValue = num,
                                durationUnit = unitStr
                            )
                        )
                    } else {
                        unparsed.add(segment)
                    }
                }
                match2 != null -> {
                    val rawLabel = match2.groupValues[1]
                    val num = match2.groupValues[2].toIntOrNull()
                    val unitStr = normalizeUnit(match2.groupValues[3])
                    val cleanedLabel = cleanLabel(rawLabel)

                    if (num != null && num > 0 && unitStr != null && isValidLabel(cleanedLabel)) {
                        parts.add(
                            ParsedPart(
                                label = cleanedLabel,
                                durationValue = num,
                                durationUnit = unitStr
                            )
                        )
                    } else {
                        unparsed.add(segment)
                    }
                }
                else -> {
                    unparsed.add(segment)
                }
            }
        }

        return ParseResult(parts = parts, unparsedSegments = unparsed)
    }

    private fun normalizeUnit(unitRaw: String): String? {
        val lower = unitRaw.trim().lowercase(Locale.ROOT)
        return when {
            lower == "day" || lower == "days" -> WarrantyUnit.DAYS
            lower == "month" || lower == "months" -> WarrantyUnit.MONTHS
            lower == "year" || lower == "years" -> WarrantyUnit.YEARS
            else -> null
        }
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.isBlank() || label.length > MAX_LABEL_LENGTH) return false
        if (embeddedDurationRegex.containsMatchIn(label)) return false
        // Label must contain at least one letter
        if (label.none { it.isLetter() }) return false
        return true
    }

    fun cleanLabel(raw: String): String {
        val trimmed = raw.trim()
            .replace(Regex("""^[:\-]+|[:\-]+$"""), "")
            .trim()
        if (trimmed.isBlank()) return ""

        return trimmed.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
