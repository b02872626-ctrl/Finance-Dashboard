package com.financeapp.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Every date label in the redesigned UI. Each one follows the calendar
 * setting (`"GREGORIAN"` / `"ETHIOPIAN"`, see [LocalCalendarSystem]); times
 * are always 24-hour. Labels are short enough to fit both calendars in the
 * same space: "Today, 12:36", "Sunday, 25 Oct" / "Ehud, Tikimt 15".
 */
object KashDates {
    private const val DAY = 86_400_000L
    private fun ethiopian(cal: String) = cal == "ETHIOPIAN"

    /** Ethiopian weekday names, Monday first (matches Calendar order below). */
    private val EC_WEEKDAYS = mapOf(
        Calendar.MONDAY to "Segno", Calendar.TUESDAY to "Maksegno", Calendar.WEDNESDAY to "Erob",
        Calendar.THURSDAY to "Hamus", Calendar.FRIDAY to "Arb", Calendar.SATURDAY to "Kidame",
        Calendar.SUNDAY to "Ehud",
    )

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysAgo(ms: Long, now: Long = System.currentTimeMillis()): Long =
        (startOfDay(now) - startOfDay(ms)) / DAY

    /** "12:36". */
    fun time(ms: Long): String = SimpleDateFormat("H:mm", Locale.US).format(Date(ms))

    /** "25 Oct" / "Tikimt 15". */
    fun dayMonth(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).let { "${it.monthName} ${it.day}" }
        else SimpleDateFormat("d MMM", Locale.US).format(Date(ms))

    /** "Sunday" / "Ehud". */
    fun weekday(ms: Long, cal: String): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return if (ethiopian(cal)) EC_WEEKDAYS.getValue(c.get(Calendar.DAY_OF_WEEK))
        else SimpleDateFormat("EEEE", Locale.US).format(Date(ms))
    }

    /**
     * Day header above a group of rows. "Today", "Yesterday", "Sunday, 25 Oct".
     * In Ethiopian mode Today/Yesterday also carry the date ("Today · Tikimt 17").
     */
    fun dayHeader(ms: Long, cal: String): String = when (daysAgo(ms)) {
        0L -> if (ethiopian(cal)) "Today · ${dayMonth(ms, cal)}" else "Today"
        1L -> if (ethiopian(cal)) "Yesterday · ${dayMonth(ms, cal)}" else "Yesterday"
        else -> "${weekday(ms, cal)}, ${dayMonth(ms, cal)}"
    }

    /** "Today, 12:36", "Yesterday, 18:40", "Oct 19, 8:05" / "Tikimt 9, 8:05". */
    fun whenShort(ms: Long, cal: String): String = when (daysAgo(ms)) {
        0L -> "Today, ${time(ms)}"
        1L -> "Yesterday, ${time(ms)}"
        else -> "${monthDay(ms, cal)}, ${time(ms)}"
    }

    /** "Oct 19" / "Tikimt 9" (month first, no year). */
    fun monthDay(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).let { "${it.monthName} ${it.day}" }
        else SimpleDateFormat("MMM d", Locale.US).format(Date(ms))

    /**
     * The full "When" on a transaction. Gregorian: "Today, 12:36" for recent
     * days, else "Oct 26, 2026 · 18:40". Ethiopian: "Tikimt 16, 2019 · 18:40".
     */
    fun full(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).let { "${it.monthName} ${it.day}, ${it.year} · ${time(ms)}" }
        else if (daysAgo(ms) <= 1) whenShort(ms, cal)
        else SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms)) + " · " + time(ms)

    /** Chart label: "Oct" / "Tik". */
    fun monthShort(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).monthName.take(3)
        else SimpleDateFormat("MMM", Locale.US).format(Date(ms))

    /** "October" / "Tikimt". */
    fun monthName(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).monthName
        else SimpleDateFormat("MMMM", Locale.US).format(Date(ms))

    /** "October 2026" / "Tikimt 2019" (calendar month title). */
    fun monthYear(ms: Long, cal: String): String =
        if (ethiopian(cal)) EthiopianCalendar.fromMillis(ms).let { "${it.monthName} ${it.year}" }
        else SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(ms))

    /**
     * Start (inclusive) and end (exclusive) of the month containing [ms] in
     * the chosen calendar. Ethiopian months are 30 days (Pagume 5 or 6).
     */
    fun monthBounds(ms: Long, cal: String): Pair<Long, Long> {
        if (ethiopian(cal)) {
            val e = EthiopianCalendar.fromMillis(ms)
            val start = startOfDay(ms) - (e.day - 1) * DAY
            // Walk forward to the next month's first day (handles Pagume).
            var end = start + 28 * DAY
            while (EthiopianCalendar.fromMillis(end + DAY / 2).month == e.month) end += DAY
            return start to startOfDay(end + DAY / 2)
        }
        val c = Calendar.getInstance().apply {
            timeInMillis = startOfDay(ms); set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start to c.timeInMillis
    }

    /** Start of the year containing [ms] (Meskerem 1 in Ethiopian mode) to now-exclusive end. */
    fun yearBounds(ms: Long, cal: String): Pair<Long, Long> {
        if (ethiopian(cal)) {
            var start = monthBounds(ms, cal).first
            while (EthiopianCalendar.fromMillis(start + DAY / 2).month != 1) start = monthBounds(start - DAY, cal).first
            var end = start + 360 * DAY
            while (EthiopianCalendar.fromMillis(end + DAY / 2).month != 1 || EthiopianCalendar.fromMillis(end + DAY / 2).day != 1) end += DAY
            return start to startOfDay(end + DAY / 2)
        }
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(ms); set(Calendar.DAY_OF_YEAR, 1) }
        val start = c.timeInMillis
        c.add(Calendar.YEAR, 1)
        return start to c.timeInMillis
    }

    /** Monday 00:00 of the week containing [ms]. */
    fun weekStart(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(ms) }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val back = (dow - Calendar.MONDAY + 7) % 7
        return c.timeInMillis - back * DAY
    }
}
