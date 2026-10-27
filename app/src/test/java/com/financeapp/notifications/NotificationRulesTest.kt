package com.financeapp.notifications

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationRulesTest {
    private val day = 24 * 60 * 60 * 1000L

    private fun tx(
        id: Long = 1,
        type: String = "CREDIT",
        amount: Double = 1000.0,
        dateTime: Long = at(2026, 10, 27, 12),
        counterparty: String? = "ACME PLC",
        predicted: String? = null,
        confidence: Double? = null,
        status: String = ReviewStatus.PENDING,
        category: String? = null,
        serviceCharge: Double? = null,
        vat: Double? = null,
        totalCharged: Double? = null,
    ) = TransactionEntity(
        id = id, sender = "CBE", bankName = "Commercial Bank of Ethiopia", type = type, amount = amount,
        balance = null, counterparty = counterparty, accountNumber = null, refNumber = null, dateTime = dateTime,
        serviceCharge = serviceCharge, vat = vat, disasterFund = null, totalCharged = totalCharged,
        category = category, predictedCategory = predicted, categoryConfidence = confidence, reviewStatus = status,
        rawBody = "",
    )

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun quietHoursWrapPastMidnight() {
        val q = QuietHours(true, 22 * 60, 7 * 60)
        assertTrue(23 * 60 in q)
        assertTrue(3 * 60 in q)
        assertFalse(7 * 60 in q)
        assertFalse(12 * 60 in q)
        assertTrue(22 * 60 in q)
        assertFalse(23 * 60 in q.copy(on = false))
    }

    @Test fun quietHoursSameDay() {
        val q = QuietHours(true, 13 * 60, 15 * 60)
        assertTrue(14 * 60 in q)
        assertFalse(16 * 60 in q)
    }

    @Test fun securityAlwaysInterrupts() {
        assertEquals(Delivery.SYSTEM, NotificationRules.deliver(Tier.IMMEDIATE, Trigger.SECURITY_LOCKOUT, foreground = true, quiet = true, 99, 99))
    }

    @Test fun tier1WaitsOutQuietHoursAndCap() {
        assertEquals(Delivery.QUEUE, NotificationRules.deliver(Tier.IMMEDIATE, Trigger.INCOME, false, quiet = true, 0, 0))
        assertEquals(Delivery.QUEUE, NotificationRules.deliver(Tier.IMMEDIATE, Trigger.INCOME, false, false, tier1ShownToday = 3, 0))
        assertEquals(Delivery.SYSTEM, NotificationRules.deliver(Tier.IMMEDIATE, Trigger.INCOME, false, false, 2, 0))
    }

    @Test fun tier1ShowsInAppWhenOpen() {
        assertEquals(Delivery.IN_APP, NotificationRules.deliver(Tier.IMMEDIATE, Trigger.LOW_BALANCE, foreground = true, quiet = false, 0, 0))
    }

    @Test fun tier2AlwaysWaitsForDigest() {
        assertEquals(Delivery.QUEUE, NotificationRules.deliver(Tier.DIGEST, Trigger.REVIEW_READY, false, false, 0, 0))
    }

    @Test fun tier3OverflowCollapsesIntoDigest() {
        assertEquals(Delivery.SYSTEM, NotificationRules.deliver(Tier.SCHEDULED, Trigger.WEEKLY, false, false, 0, 1))
        assertEquals(Delivery.QUEUE, NotificationRules.deliver(Tier.SCHEDULED, Trigger.WEEKLY, false, false, 0, 2))
    }

    @Test fun historyIsSuppressed() {
        val now = at(2026, 10, 27, 20)
        assertFalse(NotificationRules.isHistorical(now - 60_000, now, batchIsHistorical = false))
        assertTrue(NotificationRules.isHistorical(now - 2 * day, now, batchIsHistorical = false))
        assertTrue(NotificationRules.isHistorical(now - 60_000, now, batchIsHistorical = true))
    }

    @Test fun nextAtIsStrictlyLater() {
        val now = at(2026, 10, 27, 19, 30)
        assertEquals(at(2026, 10, 27, 20), NotificationRules.nextAt(now, 20 * 60))
        assertEquals(at(2026, 10, 28, 20), NotificationRules.nextAt(at(2026, 10, 27, 20), 20 * 60))
    }

    @Test fun digestInsideQuietHoursMovesToTheirEnd() {
        val q = QuietHours(true, 22 * 60, 7 * 60)
        assertEquals(7 * 60, NotificationRules.digestMinute(23 * 60, q))
        assertEquals(20 * 60, NotificationRules.digestMinute(20 * 60, q))
    }

    @Test fun floorDefaultsToTwiceDailySpend() {
        assertEquals(200.0, NotificationRules.balanceFloor(null, 3000.0), 0.001)
        assertEquals(50.0, NotificationRules.balanceFloor(50.0, 3000.0), 0.001)
    }

    @Test fun feesFromPartsOrTotal() {
        assertEquals(11.5, NotificationRules.feesOf(tx(serviceCharge = 10.0, vat = 1.5)), 0.001)
        assertEquals(12.0, NotificationRules.feesOf(tx(amount = 100.0, totalCharged = 112.0)), 0.001)
        assertTrue(NotificationRules.isFeeSpike(tx(amount = 200.0, serviceCharge = 15.0), 5))
        // Big share, tiny amount: not worth an interrupt.
        assertFalse(NotificationRules.isFeeSpike(tx(amount = 20.0, serviceCharge = 3.0), 5))
        assertFalse(NotificationRules.isFeeSpike(tx(amount = 10_000.0, serviceCharge = 15.0), 5))
    }

    @Test fun confidenceGate() {
        assertTrue(NotificationRules.categoryIs(tx(predicted = "Income", confidence = 0.8), "Income"))
        // CREDIT → Income type fallback is a guess (0.55): not enough.
        assertFalse(NotificationRules.categoryIs(tx(predicted = "Income", confidence = 0.55), "Income"))
        assertTrue(NotificationRules.categoryIs(tx(status = ReviewStatus.CONFIRMED, category = "Income"), "Income"))
        assertFalse(NotificationRules.categoryIs(tx(status = ReviewStatus.CHANGED, category = "Loan", predicted = "Income", confidence = 0.9), "Income"))
    }

    @Test fun salaryPatternNeedsTwoEarlierMonths() {
        val salary = tx(id = 10, amount = 20_000.0, dateTime = at(2026, 10, 27, 9))
        val aug = tx(id = 1, amount = 19_500.0, dateTime = at(2026, 8, 27, 9))
        val sep = tx(id = 2, amount = 20_400.0, dateTime = at(2026, 9, 27, 9))
        val other = tx(id = 3, amount = 20_000.0, dateTime = at(2026, 9, 2, 9), counterparty = "SOMEONE")
        assertTrue(NotificationRules.looksLikeSalary(salary, listOf(aug, sep, salary)))
        assertFalse(NotificationRules.looksLikeSalary(salary, listOf(sep, other, salary)))
        assertFalse(NotificationRules.looksLikeSalary(salary, listOf(aug.copy(amount = 5_000.0), sep)))
        assertTrue(NotificationRules.isIncomeLanded(salary, listOf(aug, sep)))
        assertFalse(NotificationRules.isIncomeLanded(salary.copy(type = "DEBIT"), listOf(aug, sep)))
    }

    @Test fun nudgeThresholds() {
        val now = at(2026, 10, 27, 20)
        assertTrue(NotificationRules.backlogDue(10, now - 3 * day, now))
        assertFalse(NotificationRules.backlogDue(9, now - 10 * day, now))
        assertFalse(NotificationRules.backlogDue(50, now - day, now))
        assertTrue(NotificationRules.reengageDue(now - 8 * day, 3, now))
        assertFalse(NotificationRules.reengageDue(now - 8 * day, 0, now))
        assertFalse(NotificationRules.reengageDue(0, 3, now))
        assertTrue(NotificationRules.smsLostDue(now - day, now))
        assertFalse(NotificationRules.smsLostDue(0, now))
    }

    @Test fun lastDayOfMonth() {
        assertTrue(ScheduledTriggers.isLastDayOfMonth(at(2026, 10, 31, 20), "GREGORIAN"))
        assertFalse(ScheduledTriggers.isLastDayOfMonth(at(2026, 10, 30, 20), "GREGORIAN"))
        // Ethiopian: Tikimt 30, 2019 is 9 Nov 2026; Pagume 5, 2018 is 10 Sep 2026.
        assertTrue(ScheduledTriggers.isLastDayOfMonth(at(2026, 11, 9, 20), "ETHIOPIAN"))
        assertTrue(ScheduledTriggers.isLastDayOfMonth(at(2026, 9, 10, 20), "ETHIOPIAN"))
        assertFalse(ScheduledTriggers.isLastDayOfMonth(at(2026, 9, 9, 20), "ETHIOPIAN"))
    }

    @Test fun shortNames() {
        assertEquals("Abebe G.", TransactionTriggers.shortName("ABEBE GIRMA TADESSE"))
        assertEquals("Kaldis Coffee Bole Branc", TransactionTriggers.shortName("Kaldis Coffee Bole Branch Addis"))
    }
}
