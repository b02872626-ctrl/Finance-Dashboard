package com.financeapp.viewmodel

import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.util.KashDates

/** The ranges the Analytics chart can show (NAH-27). */
enum class SpendPreset { THIS_WEEK, THIS_MONTH, LAST_30, CUSTOM }

/**
 * The Analytics tab (NAH-27): today and this week up top, then one chosen
 * range as a daily line, and what that range went on. Categories are not
 * the focus: most people never label their transactions.
 */
data class SpendOverview(
    val todaySpent: Double = 0.0,
    val todayCount: Int = 0,
    val weekSpent: Double = 0.0,
    /** % against the same days of last week; null when last week had nothing. */
    val weekChangePct: Int? = null,
    val monthSpent: Double = 0.0,
    /** % against the same point of last month; null when it had nothing. */
    val monthChangePct: Int? = null,

    val preset: SpendPreset = SpendPreset.THIS_MONTH,
    val fromMs: Long = 0L,
    /** Exclusive. */
    val toMs: Long = 0L,
    /** Spending per day of the range; null for days still to come. */
    val days: List<Double?> = emptyList(),
    val total: Double = 0.0,
    /** Per elapsed day. */
    val avgPerDay: Double = 0.0,
    /** % against the same stretch of the period before; null when it had nothing. */
    val changePct: Int? = null,
    val moneyIn: Double = 0.0,
    val topPayees: List<PayeeTotal> = emptyList(),
    val perAccount: List<Pair<String, Double>> = emptyList(),
    /** Only what people labelled; empty for most users, so it sits last. */
    val categories: List<CategoryTotal> = emptyList(),
    val hasAnyTransactions: Boolean = false,
    val isLoading: Boolean = true,
)

private const val DAY = 86_400_000L

/** Whole days between two day starts, safe across clock changes. */
private fun dayIndex(fromDayStart: Long, ms: Long): Int =
    ((KashDates.startOfDay(ms) - fromDayStart + DAY / 2) / DAY).toInt()

/** [from, to) for a preset; CUSTOM uses [custom]. */
fun spendRange(preset: SpendPreset, now: Long, cal: String, custom: Pair<Long, Long>?): Pair<Long, Long> {
    val today = KashDates.startOfDay(now)
    return when (preset) {
        SpendPreset.THIS_WEEK -> KashDates.weekStart(now).let { it to it + 7 * DAY }
        SpendPreset.THIS_MONTH -> KashDates.monthBounds(now, cal)
        SpendPreset.LAST_30 -> (today - 29 * DAY) to (today + DAY)
        SpendPreset.CUSTOM -> custom ?: KashDates.monthBounds(now, cal)
    }
}

/** Where the period before starts: last week, last month, or the same length earlier. */
private fun previousStart(preset: SpendPreset, from: Long, to: Long, cal: String): Long = when (preset) {
    SpendPreset.THIS_WEEK -> from - 7 * DAY
    SpendPreset.THIS_MONTH -> KashDates.monthBounds(from - 1, cal).first
    SpendPreset.LAST_30 -> from - 30 * DAY
    SpendPreset.CUSTOM -> from - (to - from)
}

/** Rounded % change, or null when there is nothing to compare against. */
fun changePct(now: Double, before: Double): Int? =
    if (before <= 0.0) null else Math.round((now - before) / before * 100).toInt()

fun buildSpendOverview(
    all: List<TransactionEntity>,
    cal: String,
    now: Long,
    preset: SpendPreset,
    custom: Pair<Long, Long>? = null,
): SpendOverview {
    val spending = all.filter { it.isSpending() }
    fun spentIn(from: Long, to: Long) = spending.filter { it.dateTime in from until to }.sumOf { it.amount }
    val today = KashDates.startOfDay(now)
    val endOfToday = today + DAY

    // ── Up top: today, this week and this month, each against the same point before.
    val todayRows = spending.filter { it.dateTime in today until endOfToday }
    val weekStart = KashDates.weekStart(now)
    val weekSpent = spentIn(weekStart, endOfToday)
    val lastWeek = spentIn(weekStart - 7 * DAY, endOfToday - 7 * DAY)
    val monthStart = KashDates.monthBounds(now, cal).first
    val monthSpent = spentIn(monthStart, endOfToday)
    val prevMonthStart = KashDates.monthBounds(monthStart - 1, cal).first
    val elapsed = endOfToday - monthStart
    val lastMonth = spentIn(prevMonthStart, minOf(prevMonthStart + elapsed, monthStart))

    // ── The chosen range, one value per day, nothing for days to come.
    val (from, to) = spendRange(preset, now, cal, custom)
    val dayCount = dayIndex(from, to - 1) + 1
    val days = MutableList<Double?>(dayCount) { i -> if (from + i * DAY >= endOfToday) null else 0.0 }
    val rangeRows = spending.filter { it.dateTime in from until minOf(to, endOfToday) }
    rangeRows.forEach { tx ->
        val i = dayIndex(from, tx.dateTime)
        if (i in 0 until dayCount) days[i] = (days[i] ?: 0.0) + tx.amount
    }
    val total = rangeRows.sumOf { it.amount }
    val elapsedDays = days.count { it != null }.coerceAtLeast(1)
    val prevFrom = previousStart(preset, from, to, cal)
    val shownEnd = minOf(to, endOfToday)
    val prevTotal = spentIn(prevFrom, minOf(prevFrom + (shownEnd - from), from))

    val inRange = all.filter { it.dateTime in from until to }
    return SpendOverview(
        todaySpent = todayRows.sumOf { it.amount },
        todayCount = todayRows.size,
        weekSpent = weekSpent,
        weekChangePct = changePct(weekSpent, lastWeek),
        monthSpent = monthSpent,
        monthChangePct = changePct(monthSpent, lastMonth),
        preset = preset,
        fromMs = from,
        toMs = to,
        days = days,
        total = total,
        avgPerDay = total / elapsedDays,
        changePct = changePct(total, prevTotal),
        moneyIn = inRange.filter { it.isIncome() }.sumOf { it.amount },
        topPayees = rangeRows.filter { !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!!.trim() }
            .map { (name, rows) -> PayeeTotal(name, rows.size, rows.sumOf { it.amount }) }
            .sortedByDescending { it.total }
            .take(5),
        perAccount = rangeRows.groupBy { it.bankName }
            .map { (name, rows) -> name to rows.sumOf { it.amount } }
            .sortedByDescending { it.second },
        categories = rangeRows.filter { !it.category.isNullOrBlank() }
            .groupBy { TransactionCategoryCatalog.normalize(it.category!!) }
            .map { (name, rows) -> CategoryTotal(name, rows.sumOf { it.amount }, rows.size) }
            .sortedByDescending { it.total },
        hasAnyTransactions = all.isNotEmpty(),
        isLoading = false,
    )
}
