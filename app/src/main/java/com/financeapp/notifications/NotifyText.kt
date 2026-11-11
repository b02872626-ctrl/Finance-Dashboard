package com.financeapp.notifications

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Helpers for the casual notification voice (owner, Nov 2026): amounts as
 * a friend would text them, and the person's first name.
 */
object NotifyText {

    /** 250 → "250", 12.5 → "12.50", 1,250 → "1.3k", 32,000 → "32k", 1,200,000 → "1.2m". */
    fun k(value: Double): String {
        val v = abs(value)
        if (v < 1000) {
            return if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
        }
        val thousands = (v / 100).roundToLong() / 10.0          // one decimal, half up
        if (thousands < 1000) return oneDecimal(thousands) + "k"
        val millions = (v / 100_000).roundToLong() / 10.0
        return oneDecimal(millions) + "m"
    }

    private fun oneDecimal(x: Double): String =
        if (x == Math.floor(x)) x.toLong().toString() else String.format(Locale.US, "%.1f", x)

    /** "Dawit Haile" → "Dawit"; blank → null (the copy then starts with "hey"). */
    fun firstName(fullName: String?): String? =
        fullName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
}
