package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.ui.screens.ReviewList
import com.financeapp.ui.screens.ReviewTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Review list: today / this month / older, the headline, the badge and the snackbar text. */
class ReviewListTest {
    private val day = 86_400_000L
    private val monthStart = 1_700_000_000_000L
    private val todayStart = monthStart + 10 * day

    private fun tx(id: Long, at: Long) = TransactionEntity(
        id = id, sender = "CBE", bankName = "CBE", type = "DEBIT", amount = 10.0, balance = null,
        counterparty = "Someone", accountNumber = null, refNumber = null, dateTime = at,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null, rawBody = "",
    )

    @Test
    fun `rows split into today, this month and older, newest first`() {
        val rows = listOf(tx(1, monthStart - day), tx(2, todayStart + 1), tx(3, monthStart + day), tx(4, todayStart + 5))
        val split = ReviewList.split(rows, todayStart, monthStart)
        assertEquals(listOf(4L, 2L), split.today.map { it.id })
        assertEquals(listOf(4L, 2L, 3L), split.month.map { it.id })
        assertEquals(listOf(1L), split.older.map { it.id })
    }

    @Test
    fun `the top of the screen counts, then says it is done`() {
        assertEquals("5" to " to sort today", ReviewList.headline(ReviewTab.TODAY, 5))
        assertEquals("7" to " to sort this month", ReviewList.headline(ReviewTab.MONTH, 7))
        assertEquals("All" to " sorted", ReviewList.headline(ReviewTab.MONTH, 0))
        assertEquals("This month is done. Nice.", ReviewList.subtitle(ReviewTab.MONTH, 0))
        assertEquals("Newest first. Tap Sort and say what it was.", ReviewList.subtitle(ReviewTab.TODAY, 2))
    }

    @Test
    fun `the tab badge is this month's count, none at zero, capped`() {
        assertNull(ReviewList.badge(0))
        assertEquals("12", ReviewList.badge(12))
        assertEquals("99+", ReviewList.badge(140))
    }

    @Test
    fun `the snackbar names the sender and the rule`() {
        assertEquals("Yonas Girma is Rent. New ones will be too.", ReviewList.sortedText("Yonas Girma", "Rent", rule = true))
        assertEquals("Ride is Taxi.", ReviewList.sortedText("Ride", "Taxi", rule = false))
    }
}
