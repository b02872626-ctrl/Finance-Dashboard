package com.financeapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class KashDatesTest {

    private fun ms(y: Int, m: Int, d: Int, h: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(y, m - 1, d, h, 0)
    }.timeInMillis

    @Test
    fun gregorianMonthBounds() {
        val (from, to) = KashDates.monthBounds(ms(2026, 10, 27), "GREGORIAN")
        assertEquals(ms(2026, 10, 1, 0), from)
        assertEquals(ms(2026, 11, 1, 0), to)
    }

    @Test
    fun ethiopianMonthIsThirtyDays() {
        // 27 Oct 2026 = Tikimt 17, 2019.
        val at = ms(2026, 10, 27)
        assertEquals("Tikimt", EthiopianCalendar.fromMillis(at).monthName)
        val (from, to) = KashDates.monthBounds(at, "ETHIOPIAN")
        assertEquals(1, EthiopianCalendar.fromMillis(from + 3_600_000L).day)
        assertEquals(30L, (to - from + 43_200_000L) / 86_400_000L)
        assertEquals("Hidar", EthiopianCalendar.fromMillis(to + 3_600_000L).monthName)
    }

    @Test
    fun ethiopianYearStartsOnMeskerem1() {
        val (from, to) = KashDates.yearBounds(ms(2026, 10, 27), "ETHIOPIAN")
        val start = EthiopianCalendar.fromMillis(from + 3_600_000L)
        assertEquals(1, start.month); assertEquals(1, start.day)
        val days = (to - from + 43_200_000L) / 86_400_000L
        assertTrue(days == 365L || days == 366L)
    }

    @Test
    fun labelsFollowCalendar() {
        val at = ms(2026, 10, 19, 8)
        assertEquals("Oct 19", KashDates.monthDay(at, "GREGORIAN"))
        assertEquals("Tikimt 9", KashDates.monthDay(at, "ETHIOPIAN"))
        assertEquals("Tikimt 9, 2019 · 8:00", KashDates.full(at, "ETHIOPIAN"))
        assertEquals("19 Oct", KashDates.dayMonth(at, "GREGORIAN"))
    }

    @Test
    fun weekStartsMonday() {
        val start = KashDates.weekStart(ms(2026, 10, 29)) // a Thursday
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
    }

    init { TimeZone.setDefault(TimeZone.getTimeZone("Africa/Addis_Ababa")) }
}
