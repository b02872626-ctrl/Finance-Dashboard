package com.financeapp

import com.financeapp.util.EthiopianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EthiopianCalendarTest {

    @Test fun `New year — Sep 11 2025 maps to Meskerem 1, 2018`() {
        val e = EthiopianCalendar.fromGregorian(2025, 9, 11)
        assertEquals(2018, e.year)
        assertEquals(1, e.month)
        assertEquals(1, e.day)
        assertEquals("Meskerem", e.monthName)
    }

    @Test fun `Last day of 2017 — Sep 10 2025 maps to Pagume 5, 2017 (non-leap)`() {
        // Ethiopian 2017 has Pagumē with 5 days (2017 % 4 = 1, not leap).
        val e = EthiopianCalendar.fromGregorian(2025, 9, 10)
        assertEquals(2017, e.year)
        assertEquals(13, e.month)
        assertEquals(5, e.day)
        assertTrue(e.isPagume)
    }

    @Test fun `Mid-year — May 31 2026 maps to Ginbot 23, 2018`() {
        val e = EthiopianCalendar.fromGregorian(2026, 5, 31)
        assertEquals(2018, e.year)
        assertEquals(9, e.month)        // Ginbot is the 9th month
        assertEquals(23, e.day)
        assertEquals("Ginbot", e.monthName)
    }

    @Test fun `Ethiopian millennium — Sep 11 2007 maps to Meskerem 1, 2000`() {
        val e = EthiopianCalendar.fromGregorian(2007, 9, 12)  // 2007 is Gregorian leap, so new year shifts
        // Ethiopian millennium fell on Sep 12, 2007 Gregorian (the year before a Gregorian leap)
        assertEquals(2000, e.year)
        assertEquals(1, e.month)
        assertEquals(1, e.day)
    }

    @Test fun `Year following Eth leap — Sep 11 2023 maps to Meskerem 1, 2016`() {
        // Eth 2015 was leap (% 4 == 3), so 2016 starts on Sep 11 (not Sep 12).
        val e = EthiopianCalendar.fromGregorian(2023, 9, 12)
        assertEquals(2016, e.year)
        assertEquals(1, e.month)
        assertEquals(1, e.day)
    }

    @Test fun `Pagume 6 exists in leap year`() {
        // Eth 2015 is leap (2015 % 4 == 3). Last day = Pagumē 6 = Sep 10, 2023 Gregorian.
        val e = EthiopianCalendar.fromGregorian(2023, 9, 11)
        assertEquals(2015, e.year)
        assertEquals(13, e.month)
        assertEquals(6, e.day)
        assertTrue(e.isPagume)
    }

    @Test fun `Amharic month names match transliterated`() {
        assertEquals("መስከረም", EthiopianCalendar.MONTHS_AMHARIC[0])
        assertEquals("ጳጉሜ", EthiopianCalendar.MONTHS_AMHARIC[12])
        assertEquals(13, EthiopianCalendar.MONTHS_AMHARIC.size)
        assertEquals(13, EthiopianCalendar.MONTHS_TRANSLITERATED.size)
    }
}
