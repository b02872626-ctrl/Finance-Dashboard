package com.financeapp.parsing

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shared utilities for SMS parsing. All methods are safe and never throw.
 */
object ParserUtils {

    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val ignoredReceiptUrlHints = listOf(
        "forms.gle",
        "google.com/forms",
        "forms.google.com"
    )

    /** Parse an amount string like "6,000.00" or "1005.75" → Double */
    fun parseAmount(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().replace(",", "").toDoubleOrNull()
    }

    /** Parse amount, defaulting to 0.0 on failure */
    fun parseAmountOrZero(raw: String?): Double = parseAmount(raw) ?: 0.0

    /** Parse "dd/MM/yyyy" + "HH:mm:ss" → epoch millis */
    fun parseDateTime(date: String?, time: String?): Long? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            sdf.parse("${date.trim()} ${time.trim()}")?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Parse "dd/MM/yyyy HH:mm:ss" in one string → epoch millis */
    fun parseDateTimeString(dateTime: String?): Long? {
        if (dateTime.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            sdf.parse(dateTime.trim())?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Compute MD5 hash of the SMS body for deduplication */
    fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    /** Extract all URLs from the SMS body in a stable, display-ready format. */
    fun extractUrls(body: String): List<String> =
        urlRegex.findAll(body)
            .map { normalizeUrl(it.value) }
            .filterNot(::isIgnoredReceiptUrl)
            .distinct()
            .toList()

    /** Extract the first URL from the SMS body, if any. */
    fun extractFirstUrl(body: String): String? = extractUrls(body).firstOrNull()

    private fun normalizeUrl(url: String): String =
        url.trimEnd('.', ',', ';', ':', ')')

    private fun isIgnoredReceiptUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return ignoredReceiptUrlHints.any(lower::contains)
    }
}
