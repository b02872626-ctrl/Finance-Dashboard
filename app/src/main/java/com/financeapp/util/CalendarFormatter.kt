package com.financeapp.util

import androidx.compose.runtime.compositionLocalOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Composition-scoped calendar preference. Read in any composable via
 * `LocalCalendarSystem.current`. AppNavigation provides the current value
 * from SettingsViewModel at the root, and every date-rendering site
 * reactively re-renders when the user flips the Settings toggle.
 *
 * Default "GREGORIAN" — matches behavior before the toggle existed.
 */
val LocalCalendarSystem = compositionLocalOf { "GREGORIAN" }

/**
 * Date formatting that respects the user's [calendarSystem] preference
 * (`"GREGORIAN"` or `"ETHIOPIAN"`). Time is always 24-hour Gregorian
 * regardless — see [SettingsRepository.getCalendarSystem] doc.
 *
 * Three formatters cover most rendering sites:
 *  - [formatDayLabel]  "Today" / "Yesterday" / "Apr 12, 2026" or "Miyazya 4, 2018"
 *  - [formatShort]     "Apr 12" or "Miyazya 4"     (no year — for inline rows)
 *  - [formatLong]      "Apr 12, 2026 · 14:32" or "Miyazya 4, 2018 · 14:32"
 *
 * Pass `calendarSystem` from SettingsRepository or the matching Flow.
 */
object CalendarFormatter {

    fun formatDayLabel(timestampMs: Long, calendarSystem: String, locale: Locale = Locale.getDefault()): String {
        val today = startOfDay(System.currentTimeMillis())
        val rowDay = startOfDay(timestampMs)
        return when {
            rowDay == today                       -> "Today"
            rowDay == today - 86_400_000L         -> "Yesterday"
            else                                  -> formatDate(timestampMs, calendarSystem, locale)
        }
    }

    fun formatShort(timestampMs: Long, calendarSystem: String, locale: Locale = Locale.getDefault()): String =
        if (calendarSystem == "ETHIOPIAN") {
            val e = EthiopianCalendar.fromMillis(timestampMs)
            "${e.monthName} ${e.day}"
        } else {
            SimpleDateFormat("MMM d", locale).format(Date(timestampMs))
        }

    fun formatDate(timestampMs: Long, calendarSystem: String, locale: Locale = Locale.getDefault()): String =
        if (calendarSystem == "ETHIOPIAN") {
            val e = EthiopianCalendar.fromMillis(timestampMs)
            "${e.monthName} ${e.day}, ${e.year}"
        } else {
            SimpleDateFormat("MMM d, yyyy", locale).format(Date(timestampMs))
        }

    fun formatLong(timestampMs: Long, calendarSystem: String, locale: Locale = Locale.getDefault()): String {
        val timeStr = SimpleDateFormat("HH:mm", locale).format(Date(timestampMs))
        return "${formatDate(timestampMs, calendarSystem, locale)} · $timeStr"
    }

    private fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
