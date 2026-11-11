package com.financeapp.viewmodel

import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.notifications.NotificationRules
import com.financeapp.util.KashDates
import java.util.Calendar

/**
 * Kash Wrapped (NAH-34): one month's money story, built only from what every
 * bank SMS has (amounts, who, fees, when). No categories: most people never
 * label anything.
 */
data class Wrapped(
    val monthStartMs: Long,
    val payments: Int,
    val accounts: Int,
    val spent: Double,
    val moneyIn: Double,
    /** % against the month before; null when it had no spending. */
    val vsLastMonthPct: Int?,
    val biggest: Moment?,
    /** Who you paid most often (2+ times), or null. */
    val regular: PayeeTotal?,
    val fees: Double,
    /** The bank that charged the most fees, and how much. */
    val feeBank: Pair<String, Double>?,
    /** A day in the month on the weekday you spent most, and that weekday's share of spending. */
    val busiestWeekdayMs: Long?,
    val busiestWeekdayPct: Int,
    val partOfDay: PartOfDay?,
    val tip: Tip,
) {
    val kept: Double get() = moneyIn - spent
    /** Share of money in that was kept; null with no money in. */
    val keptPct: Int? get() = if (moneyIn > 0) Math.round(kept / moneyIn * 100).toInt() else null
}

data class Moment(val amount: Double, val who: String, val atMs: Long)

enum class PartOfDay(val label: String) { MORNING("mornings"), AFTERNOON("afternoons"), EVENING("evenings"), NIGHT("late nights") }

/** One suggestion for next month; the UI words it. */
sealed interface Tip {
    data class Fees(val fees: Double) : Tip
    data class SpentMore(val pct: Int) : Tip
    data class Overspent(val over: Double) : Tip
    data class KeptWell(val pct: Int) : Tip
    data class BusyDay(val weekdayMs: Long) : Tip
    data object KeepGoing : Tip
}

/** Fewer than this many payments in a month: nothing worth wrapping. */
const val WRAPPED_MIN_PAYMENTS = 3

private fun partOf(ms: Long): PartOfDay = when (Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> PartOfDay.MORNING
    in 12..16 -> PartOfDay.AFTERNOON
    in 17..21 -> PartOfDay.EVENING
    else -> PartOfDay.NIGHT
}

private fun weekdayOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)

/** The month's story, or null when it has fewer than [WRAPPED_MIN_PAYMENTS] payments. */
fun buildWrapped(all: List<TransactionEntity>, cal: String, anyMsInMonth: Long): Wrapped? {
    val (from, to) = KashDates.monthBounds(anyMsInMonth, cal)
    val month = all.filter { it.dateTime in from until to }
    val spending = month.filter { it.isSpending() }
    if (spending.size < WRAPPED_MIN_PAYMENTS) return null
    val spent = spending.sumOf { it.amount }
    val moneyIn = month.filter { it.isIncome() }.sumOf { it.amount }

    val prevFrom = KashDates.monthBounds(from - 1, cal).first
    val prevSpent = all.filter { it.dateTime in prevFrom until from && it.isSpending() }.sumOf { it.amount }
    val vs = if (prevSpent > 0) Math.round((spent - prevSpent) / prevSpent * 100).toInt() else null

    val biggest = spending.maxByOrNull { it.amount }?.let {
        Moment(it.amount, it.counterparty?.trim()?.takeIf(String::isNotBlank) ?: it.bankName, it.dateTime)
    }
    val regular = spending.filter { !it.counterparty.isNullOrBlank() }
        .groupBy { it.counterparty!!.trim() }
        .map { (name, rows) -> PayeeTotal(name, rows.size, rows.sumOf { it.amount }) }
        .filter { it.count >= 2 }
        .maxWithOrNull(compareBy<PayeeTotal> { it.count }.thenBy { it.total })

    val feeRows = month.filter { NotificationRules.feesOf(it) > 0 }
    val fees = feeRows.sumOf { NotificationRules.feesOf(it) }
    val feeBank = feeRows.groupBy { it.bankName }
        .map { (bank, rows) -> bank to rows.sumOf { NotificationRules.feesOf(it) } }
        .maxByOrNull { it.second }

    val byWeekday = spending.groupBy { weekdayOf(it.dateTime) }.mapValues { (_, r) -> r.sumOf { it.amount } }
    val topDay = byWeekday.maxByOrNull { it.value }
    val busiestMs = topDay?.let { (wd, _) -> spending.first { weekdayOf(it.dateTime) == wd }.dateTime }
    val busiestPct = topDay?.let { Math.round(it.value / spent * 100).toInt() } ?: 0
    val part = spending.groupBy { partOf(it.dateTime) }.maxByOrNull { (_, r) -> r.sumOf { it.amount } }?.key

    val tip: Tip = when {
        fees >= 100 || (spent > 0 && fees / spent >= 0.02) -> Tip.Fees(fees)
        vs != null && vs >= 20 -> Tip.SpentMore(vs)
        moneyIn > 0 && spent > moneyIn -> Tip.Overspent(spent - moneyIn)
        moneyIn > 0 && (moneyIn - spent) / moneyIn >= 0.2 -> Tip.KeptWell(Math.round((moneyIn - spent) / moneyIn * 100).toInt())
        busiestMs != null && busiestPct >= 30 -> Tip.BusyDay(busiestMs)
        else -> Tip.KeepGoing
    }

    return Wrapped(
        monthStartMs = from,
        payments = spending.size,
        accounts = month.map { it.bankName }.distinct().size,
        spent = spent,
        moneyIn = moneyIn,
        vsLastMonthPct = vs,
        biggest = biggest,
        regular = regular,
        fees = fees,
        feeBank = feeBank,
        busiestWeekdayMs = busiestMs,
        busiestWeekdayPct = busiestPct,
        partOfDay = part,
        tip = tip,
    )
}

/**
 * The month the Home card offers: last month, during the first week of a
 * new month, when it has enough to wrap. Null otherwise.
 */
fun wrappedCardMonth(all: List<TransactionEntity>, cal: String, now: Long): Long? {
    val (thisMonth, _) = KashDates.monthBounds(now, cal)
    if (now - thisMonth >= 7 * 86_400_000L) return null
    val last = KashDates.monthBounds(thisMonth - 1, cal).first
    return last.takeIf { buildWrapped(all, cal, last) != null }
}
