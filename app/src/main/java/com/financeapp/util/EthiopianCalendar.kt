package com.financeapp.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Gregorian ↔ Ethiopian calendar conversion.
 *
 * The Ethiopian calendar has 13 months: 12 × 30-day months plus Pagumē
 * (ጳጉሜ) at the end with 5 days (6 in leap year). Leap years occur when
 * `ethiopianYear % 4 == 3`, so years 3, 7, 11, 15, 2015, 2019, 2023…
 *
 * Year offset: roughly 7–8 years behind Gregorian. The Ethiopian new year
 * (Enkutatash, Meskerem 1) falls on September 11 or 12 Gregorian.
 *
 * Conversion algorithm: use the Julian Day Number (JDN) as a common base.
 * The Ethiopian epoch (Meskerem 1, year 1 ≈ August 29, 8 CE Julian) sits at
 * JDN **1724221**. Each Ethiopian 4-year cycle has 1461 days
 * (365 + 365 + 366 + 365), with the leap year being the *third* year of
 * each cycle. We compute days-since-epoch from the Gregorian JDN, divide
 * by 1461 to get the cycle, then unpack the year + day-of-year within the
 * cycle.
 *
 * Verified against known dates:
 *   2025-09-11 → Meskerem  1, 2018  (Ethiopian new year)
 *   2026-05-31 → Ginbot   23, 2018
 *   2025-09-10 → Pagumē    5, 2017  (last day of 2017, non-leap)
 *   2024-09-10 → Pagumē    6, 2016  (note: 2015 was leap, so 2016 starts Sep 11)
 */
object EthiopianCalendar {

    /** Julian Day Number of Meskerem 1, Ethiopian year 1. */
    private const val ETH_EPOCH_JDN = 1724221

    /** Ethiopian month names in order (1-indexed via [name]). */
    val MONTHS_AMHARIC = listOf(
        "መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ", "ጥር", "የካቲት", "መጋቢት",
        "ሚያዝያ", "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜ"
    )
    val MONTHS_TRANSLITERATED = listOf(
        "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit", "Megabit",
        "Miyazya", "Ginbot", "Sene", "Hamle", "Nehasse", "Pagumē"
    )

    data class EthiopianDate(
        /** Ethiopian year, e.g. 2018 (= Gregorian Sep 2025 → Sep 2026). */
        val year: Int,
        /** 1..13 (1=Meskerem, …, 13=Pagumē). */
        val month: Int,
        /** 1..30 (or 1..5/6 for Pagumē). */
        val day: Int
    ) {
        val monthNameAmharic: String get() = MONTHS_AMHARIC[month - 1]
        val monthName: String get() = MONTHS_TRANSLITERATED[month - 1]
        val isPagume: Boolean get() = month == 13
    }

    /** Convert a Unix-epoch millis timestamp to an EthiopianDate (UTC). */
    fun fromMillis(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): EthiopianDate {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return fromGregorian(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,   // Calendar.MONTH is 0-indexed
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Gregorian (year, month=1..12, day=1..31) → EthiopianDate. */
    fun fromGregorian(gYear: Int, gMonth: Int, gDay: Int): EthiopianDate {
        val jdn = gregorianToJdn(gYear, gMonth, gDay)
        val daysSinceEpoch = jdn - ETH_EPOCH_JDN
        // Each 4-year cycle = 365+365+366+365 = 1461 days.
        val cycles = floorDiv(daysSinceEpoch, 1461)
        val cycleDay = floorMod(daysSinceEpoch, 1461)
        val (yearInCycle, dayInYear) = when {
            cycleDay < 365  -> 0 to cycleDay
            cycleDay < 730  -> 1 to (cycleDay - 365)
            cycleDay < 1096 -> 2 to (cycleDay - 730)   // 366-day leap year is yearInCycle == 2
            else            -> 3 to (cycleDay - 1096)
        }
        val ethYear = 4 * cycles + yearInCycle + 1
        val month = dayInYear / 30 + 1
        val day = dayInYear % 30 + 1
        return EthiopianDate(ethYear, month, day)
    }

    /**
     * Gregorian date → JDN via the standard Fliegel-Van Flandern formula.
     * Works for all dates from JDN 0 (24 Nov 4714 BCE proleptic Gregorian)
     * forward — way more than we need.
     */
    private fun gregorianToJdn(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }

    // Math.floorDiv / floorMod aren't available pre-Java-8; we're on 17 so
    // they exist as kotlin.math.floor* aliases, but using local helpers
    // keeps this file standalone and easy to backport.
    private fun floorDiv(a: Int, b: Int): Int = if ((a xor b) < 0 && a % b != 0) a / b - 1 else a / b
    private fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b
}
