package com.financeapp.notifications

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import java.util.Calendar
import kotlin.math.abs

/** Trigger ids, the first half of every dedup key. */
object Trigger {
    const val LOW_BALANCE = "low_balance"
    const val INCOME = "income"
    const val FEE_SPIKE = "fee_spike"
    const val FEE_DAY = "fee_day"
    const val SECURITY_LOCKOUT = "security_lockout"
    const val REVIEW_READY = "review_ready"
    const val CAPTURE_ONLY = "capture_only"
    const val DIGEST = "digest"
    const val WEEKLY = "weekly"
    const val MONTH_END = "month_end"
    const val BACKLOG = "backlog"
    const val REENGAGE = "reengage"
    const val SMS_LOST = "sms_lost"
}

/** 1 immediate · 2 evening digest · 3 scheduled · 4 account health. */
object Tier {
    const val IMMEDIATE = 1
    const val DIGEST = 2
    const val SCHEDULED = 3
    const val HEALTH = 4
}

enum class Delivery { SYSTEM, IN_APP, QUEUE }

/** Quiet hours as minutes after midnight; [end] may be earlier than [start] (overnight). */
data class QuietHours(val on: Boolean, val start: Int, val end: Int) {
    operator fun contains(minuteOfDay: Int): Boolean = on && start != end && if (start < end) {
        minuteOfDay in start until end
    } else {
        minuteOfDay >= start || minuteOfDay < end
    }
}

/**
 * The rules layer from the notification spec, as pure functions so they can
 * be unit-tested: quiet hours, frequency caps, historical suppression, the
 * confidence gate and the trigger thresholds.
 */
object NotificationRules {
    private const val DAY = 24 * 60 * 60 * 1000L
    const val MAX_TIER1_PER_DAY = 3
    const val MAX_TIER3_PER_WEEK = 2
    /** Older SMS than this are history (first run, upgrade rescan, a late scan): no alerts. */
    const val FRESH_WINDOW_MS = 24 * 60 * 60 * 1000L
    const val CONFIDENCE_GATE = 0.8
    const val BACKLOG_MIN_PENDING = 10
    const val BACKLOG_IDLE_MS = 3 * DAY
    const val REENGAGE_IDLE_MS = 7 * DAY
    const val SMS_LOST_ESCALATE_MS = DAY
    /** Ignore fee spikes below this many ETB, however large the share. */
    const val MIN_FEE_ETB = 5.0


    fun minuteOfDay(ms: Long): Int = Calendar.getInstance().run {
        timeInMillis = ms
        get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
    }

    /**
     * Where a notification goes. Security always interrupts. Tier 1 waits
     * out quiet hours and the daily cap in the digest, and shows in-app when
     * Kash is open. Tier 2 always waits for the digest. Tier 3 overflow past
     * the weekly cap collapses into the digest.
     */
    fun deliver(
        tier: Int,
        trigger: String,
        foreground: Boolean,
        quiet: Boolean,
        tier1ShownToday: Int,
        tier3ShownThisWeek: Int,
    ): Delivery = when {
        trigger == Trigger.SECURITY_LOCKOUT -> Delivery.SYSTEM
        tier == Tier.DIGEST -> Delivery.QUEUE
        quiet -> Delivery.QUEUE
        tier == Tier.IMMEDIATE && tier1ShownToday >= MAX_TIER1_PER_DAY -> Delivery.QUEUE
        tier == Tier.IMMEDIATE && foreground -> Delivery.IN_APP
        tier == Tier.SCHEDULED && tier3ShownThisWeek >= MAX_TIER3_PER_WEEK -> Delivery.QUEUE
        else -> Delivery.SYSTEM
    }

    /** True when [txDateTime] is too old for a transaction-level alert, or the whole batch is a rescan. */
    fun isHistorical(txDateTime: Long, nowMs: Long, batchIsHistorical: Boolean): Boolean =
        batchIsHistorical || nowMs - txDateTime > FRESH_WINDOW_MS

    /** The next time the clock reads [minuteOfDay], strictly after [nowMs]. */
    fun nextAt(nowMs: Long, minuteOfDay: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** A digest time inside quiet hours moves to the end of them. */
    fun digestMinute(chosen: Int, quiet: QuietHours): Int = if (chosen in quiet) quiet.end else chosen

    /** Low-balance floor: the user's, or 2× average daily spend (0 = not enough history). */
    fun balanceFloor(userFloor: Double?, spendLast30Days: Double): Double =
        userFloor ?: (2 * spendLast30Days / 30.0)

    /** Charges on one transaction: service charge + VAT + disaster fund, else total − amount. */
    fun feesOf(tx: TransactionEntity): Double {
        val parts = listOfNotNull(tx.serviceCharge, tx.vat, tx.disasterFund).sum()
        if (parts > 0) return parts
        val total = tx.totalCharged ?: return 0.0
        return (total - tx.amount).coerceAtLeast(0.0)
    }

    fun isFeeSpike(tx: TransactionEntity, percent: Int): Boolean {
        val fees = feesOf(tx)
        return tx.amount > 0 && fees >= MIN_FEE_ETB && fees / tx.amount * 100 > percent
    }

    /**
     * Category-dependent triggers only trust confirmed rows or strong
     * predictions, so a wrong guess can't raise a false alert.
     */
    fun categoryIs(tx: TransactionEntity, category: String): Boolean = when (tx.reviewStatus) {
        ReviewStatus.CONFIRMED, ReviewStatus.CHANGED -> tx.category == category
        else -> tx.predictedCategory == category && (tx.categoryConfidence ?: 0.0) >= CONFIDENCE_GATE
    }

    /**
     * A learned salary: the same sender paid a similar amount (±20%) in at
     * least two earlier calendar months.
     */
    fun looksLikeSalary(tx: TransactionEntity, earlierCredits: List<TransactionEntity>): Boolean {
        val who = tx.counterpartyId ?: tx.counterparty ?: return false
        val months = earlierCredits.asSequence()
            .filter { it.id != tx.id && it.dateTime < tx.dateTime && it.isIncome() }
            .filter { (it.counterpartyId ?: it.counterparty) == who }
            .filter { abs(it.amount - tx.amount) <= tx.amount * 0.2 }
            .map { Calendar.getInstance().apply { timeInMillis = it.dateTime }.let { c -> c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) } }
            .toSet()
        val own = Calendar.getInstance().apply { timeInMillis = tx.dateTime }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
        return (months - own).size >= 2
    }

    fun isIncomeLanded(tx: TransactionEntity, earlierCredits: List<TransactionEntity>): Boolean =
        tx.isIncome() && (categoryIs(tx, "Income") || looksLikeSalary(tx, earlierCredits))

    fun backlogDue(pending: Int, lastReviewMs: Long, nowMs: Long): Boolean =
        pending >= BACKLOG_MIN_PENDING && nowMs - lastReviewMs >= BACKLOG_IDLE_MS

    fun reengageDue(lastOpenMs: Long, newSinceOpen: Int, nowMs: Long): Boolean =
        lastOpenMs > 0 && nowMs - lastOpenMs >= REENGAGE_IDLE_MS && newSinceOpen > 0

    fun smsLostDue(lostSinceMs: Long, nowMs: Long): Boolean =
        lostSinceMs > 0 && nowMs - lostSinceMs >= SMS_LOST_ESCALATE_MS
}
