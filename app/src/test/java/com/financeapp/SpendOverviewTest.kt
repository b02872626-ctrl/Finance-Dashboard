package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.viewmodel.SpendPreset
import com.financeapp.viewmodel.buildSpendOverview
import com.financeapp.viewmodel.changePct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/** NAH-27: the Analytics tab's today / this week / range numbers. Today is Thu 12 Nov 2026, 12:00. */
class SpendOverviewTest {
    private val g = "GREGORIAN"
    private fun at(m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(2026, m - 1, d, h, 0) }.timeInMillis
    private val now = at(11, 12)
    private var id = 0L

    private fun spend(ms: Long, amount: Double, who: String = "Shop", bank: String = "Commercial Bank of Ethiopia", type: String = "DEBIT") =
        TransactionEntity(
            id = ++id, sender = "CBE", bankName = bank, type = type, amount = amount, balance = null,
            counterparty = who, accountNumber = null, refNumber = null, dateTime = ms,
            serviceCharge = null, vat = null, disasterFund = null, totalCharged = null, rawBody = "",
        )

    @Test fun todayWeekAndMonth() {
        val rows = listOf(
            spend(at(11, 12, 9), 100.0), spend(at(11, 12, 10), 50.0),   // today
            spend(at(11, 10), 200.0),                                    // Tue, this week
            spend(at(11, 3), 100.0),                                     // Tue last week (same point)
            spend(at(11, 6), 999.0),                                     // Fri last week: after the same point
            spend(at(10, 5), 300.0),                                     // last month, before the same point
            spend(at(11, 1, 9), 400.0),                                  // this month, last week
            spend(at(11, 14), 7.0),                                      // future: ignored
            spend(at(11, 11), 80.0, type = "CREDIT"),                    // income: not spending
        )
        val o = buildSpendOverview(rows, g, now, SpendPreset.THIS_MONTH)
        assertEquals(150.0, o.todaySpent, 0.001)
        assertEquals(2, o.todayCount)
        assertEquals(350.0, o.weekSpent, 0.001)                 // 150 + 200
        assertEquals(250, o.weekChangePct)                      // 350 vs 100
        assertEquals(150.0 + 200 + 100 + 999 + 400, o.monthSpent, 0.001)
        assertEquals(80.0, o.moneyIn, 0.001)
    }

    @Test fun monthRangeHasADayPerDateAndNothingForTheFuture() {
        val o = buildSpendOverview(listOf(spend(at(11, 1), 40.0), spend(at(11, 12), 60.0)), g, now, SpendPreset.THIS_MONTH)
        assertEquals(30, o.days.size)                           // November
        assertEquals(40.0, o.days[0]!!, 0.001)
        assertEquals(60.0, o.days[11]!!, 0.001)
        assertNull(o.days[12])                                  // 13 Nov hasn't happened
        assertEquals(100.0, o.total, 0.001)
        assertEquals(100.0 / 12, o.avgPerDay, 0.001)            // 12 days so far
    }

    @Test fun weekAndLast30() {
        val rows = listOf(spend(at(11, 9), 10.0), spend(at(10, 14), 5.0), spend(at(10, 13), 1000.0))
        val week = buildSpendOverview(rows, g, now, SpendPreset.THIS_WEEK)
        assertEquals(7, week.days.size)
        assertEquals(10.0, week.total, 0.001)
        val d30 = buildSpendOverview(rows, g, now, SpendPreset.LAST_30)
        assertEquals(30, d30.days.size)
        assertEquals(15.0, d30.total, 0.001)                    // 14 Oct is day 1; 13 Oct is before
    }

    @Test fun customRangeComparesWithTheSameLengthBefore() {
        val from = at(11, 1, 0); val to = at(11, 8, 0)          // 1–7 Nov
        val rows = listOf(spend(at(11, 3), 300.0), spend(at(10, 28), 100.0), spend(at(10, 20), 999.0))
        val o = buildSpendOverview(rows, g, now, SpendPreset.CUSTOM, from to to)
        assertEquals(7, o.days.size)
        assertEquals(300.0, o.total, 0.001)
        assertEquals(200, o.changePct)                          // vs 25–31 Oct: 100
    }

    @Test fun payeesAndAccountsFollowTheRange() {
        val rows = listOf(
            spend(at(11, 2), 50.0, "Abebe"), spend(at(11, 3), 70.0, "Abebe"),
            spend(at(11, 4), 90.0, "Taxi", bank = "Telebirr"),
            spend(at(10, 2), 900.0, "Old"),
        )
        val o = buildSpendOverview(rows, g, now, SpendPreset.THIS_MONTH)
        assertEquals(listOf("Abebe", "Taxi"), o.topPayees.map { it.name })
        assertEquals(120.0, o.topPayees[0].total, 0.001)
        assertEquals(listOf("Commercial Bank of Ethiopia", "Telebirr"), o.perAccount.map { it.first })
        assertEquals(emptyList<Any>(), o.categories)            // nobody labelled anything
    }

    @Test fun changeNeedsSomethingBefore() {
        assertNull(changePct(100.0, 0.0))
        assertEquals(-50, changePct(50.0, 100.0))
    }
}
