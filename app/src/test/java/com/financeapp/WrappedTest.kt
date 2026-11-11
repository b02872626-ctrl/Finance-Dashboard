package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.viewmodel.PartOfDay
import com.financeapp.viewmodel.Tip
import com.financeapp.viewmodel.buildWrapped
import com.financeapp.viewmodel.wrappedCardMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** NAH-34: October 2026's story, from SMS data only. */
class WrappedTest {
    private val g = "GREGORIAN"
    private fun at(m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(2026, m - 1, d, h, 0) }.timeInMillis
    private var id = 0L
    private fun tx(
        ms: Long, amount: Double, who: String? = "Shop", type: String = "DEBIT",
        bank: String = "Commercial Bank of Ethiopia", fee: Double? = null,
    ) = TransactionEntity(
        id = ++id, sender = "CBE", bankName = bank, type = type, amount = amount, balance = null,
        counterparty = who, accountNumber = null, refNumber = null, dateTime = ms,
        serviceCharge = fee, vat = null, disasterFund = null, totalCharged = null, rawBody = "",
    )

    // Oct 2026: 1st is a Thursday. Fridays: 2, 9, 16, 23, 30.
    private val october = listOf(
        tx(at(10, 2, 19), 300.0, "Abebe G."),           // Fri evening
        tx(at(10, 9, 20), 200.0, "Abebe G."),           // Fri evening
        tx(at(10, 16, 21), 100.0, "Abebe G."),          // Fri evening
        tx(at(10, 5, 9), 1500.0, "Rent PLC", fee = 15.0),  // Mon morning, biggest
        tx(at(10, 7, 13), 400.0, "Taxi", bank = "Telebirr"),
        tx(at(10, 1, 10), 5000.0, "Employer", type = "CREDIT"),
        tx(at(9, 10), 2000.0, "Old"),                    // September spending
    )

    @Test fun theMonthsNumbers() {
        val w = buildWrapped(october, g, at(10, 15))!!
        assertEquals(5, w.payments)
        assertEquals(2, w.accounts)
        assertEquals(2500.0, w.spent, 0.001)
        assertEquals(5000.0, w.moneyIn, 0.001)
        assertEquals(2500.0, w.kept, 0.001)
        assertEquals(50, w.keptPct)
        assertEquals(25, w.vsLastMonthPct)               // 2500 vs 2000
    }

    @Test fun biggestRegularAndFees() {
        val w = buildWrapped(october, g, at(10, 15))!!
        assertEquals(1500.0, w.biggest!!.amount, 0.001)
        assertEquals("Rent PLC", w.biggest!!.who)
        assertEquals("Abebe G.", w.regular!!.name)
        assertEquals(3, w.regular!!.count)
        assertEquals(15.0, w.fees, 0.001)
        assertEquals("Commercial Bank of Ethiopia", w.feeBank!!.first)
    }

    @Test fun habits() {
        val w = buildWrapped(october, g, at(10, 15))!!
        // Monday 1500 beats Friday 600.
        assertEquals(Calendar.MONDAY, Calendar.getInstance().apply { timeInMillis = w.busiestWeekdayMs!! }.get(Calendar.DAY_OF_WEEK))
        assertEquals(60, w.busiestWeekdayPct)
        assertEquals(PartOfDay.MORNING, w.partOfDay)
    }

    @Test fun tipPicksTheMostUsefulThing() {
        // Spent 25% more than September: that comes before "kept well".
        assertEquals(Tip.SpentMore(25), buildWrapped(october, g, at(10, 15))!!.tip)
        // Big fees win over everything.
        val fees = october + tx(at(10, 20), 50.0, fee = 120.0)
        assertTrue(buildWrapped(fees, g, at(10, 15))!!.tip is Tip.Fees)
        // No September: kept half of what came in.
        val noPrev = october.filter { it.dateTime >= at(10, 1, 0) }
        assertEquals(Tip.KeptWell(50), buildWrapped(noPrev, g, at(10, 15))!!.tip)
    }

    @Test fun tooLittleToWrap() {
        assertNull(buildWrapped(listOf(tx(at(10, 2), 10.0), tx(at(10, 3), 10.0)), g, at(10, 15)))
    }

    @Test fun homeCardOnlyInTheFirstWeek() {
        assertEquals(at(10, 1, 0), wrappedCardMonth(october, g, at(11, 3)))
        assertNull(wrappedCardMonth(october, g, at(11, 9)))
        assertNull(wrappedCardMonth(emptyList(), g, at(11, 3)))
        assertNotNull(buildWrapped(october, g, at(10, 31)))
    }
}
