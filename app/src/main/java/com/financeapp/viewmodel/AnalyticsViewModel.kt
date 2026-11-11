package com.financeapp.viewmodel

import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import androidx.lifecycle.*
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.Calendar

/** Aggregated data for the Insights tab (matches design/insights/Insights_light.png). */
data class CategoryTotal(
    val category: String,
    val total: Double,
    val transactionCount: Int
)

/** Per-category drill-down state (Food Spending screen). */
data class CategoryDetailState(
    val category: String = "",
    val monthSpending: Double = 0.0,
    val todaySpending: Double = 0.0,
    val avgDailySpend: Double = 0.0,
    val percentOfTotal: Double = 0.0,
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true
)

/** One month's numbers for the Analytics tab (AppAnalytics, AppAnalyticsMore). */
data class MonthReport(
    val fromMs: Long = 0L,
    val toMs: Long = 0L,
    val isCurrent: Boolean = true,
    val spent: Double = 0.0,
    val moneyIn: Double = 0.0,
    /** Six months ending with this one, oldest first. */
    val sixMonths: List<MonthInOut> = emptyList(),
    /** Categories with spending this month, biggest first. */
    val categories: List<CategoryTotal> = emptyList(),
    /** Spending per day of the month, up to today for the current month. */
    val days: List<Double> = emptyList(),
    val topPayees: List<PayeeTotal> = emptyList(),
    /** Spending per account (full bank name), biggest first. */
    val perAccount: List<Pair<String, Double>> = emptyList(),
    /** Spending rows this month with no category. */
    val uncategorized: Int = 0,
    /** Spending rows with no category, all time (Start reviewing badge). */
    val uncategorizedAllTime: Int = 0,
    val hasAnyTransactions: Boolean = false,
    val isLoading: Boolean = true,
)

data class MonthInOut(val startMs: Long, val moneyIn: Double, val spent: Double)
data class PayeeTotal(val name: String, val count: Int, val total: Double)

class AnalyticsViewModel(private val repo: TransactionRepository) : ViewModel() {

    /**
     * One shared full-table query for every derived flow below. Room re-runs
     * a query on every table change (e.g. each sync marking rows synced), so
     * four separate getAllTransactions() collectors meant four full reloads.
     */
    private val allTransactions: SharedFlow<List<TransactionEntity>> =
        repo.getAllTransactions()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val _calendar = MutableStateFlow("GREGORIAN")
    /** Any instant inside the month being shown; defaults to now. */
    private val _reportAt = MutableStateFlow(System.currentTimeMillis())
    val reportAt: StateFlow<Long> = _reportAt.asStateFlow()

    fun setCalendar(calendarSystem: String) { _calendar.value = calendarSystem }
    fun setReportMonth(anyMsInMonth: Long) { _reportAt.value = anyMsInMonth }

    val report: StateFlow<MonthReport> = combine(allTransactions, _calendar, _reportAt) { all, cal, at ->
        buildReport(all, cal, at)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthReport())

    // ── Analytics tab (NAH-27): today, this week, and one chosen range.
    private val _preset = MutableStateFlow(SpendPreset.THIS_MONTH)
    private val _custom = MutableStateFlow<Pair<Long, Long>?>(null)

    fun setPreset(preset: SpendPreset) { _preset.value = preset }
    /** Pick dates: [from, toExclusive). */
    fun setCustomRange(from: Long, toExclusive: Long) {
        _custom.value = from to toExclusive
        _preset.value = SpendPreset.CUSTOM
    }

    val overview: StateFlow<SpendOverview> = combine(allTransactions, _calendar, _preset, _custom) { all, cal, preset, custom ->
        buildSpendOverview(all, cal, System.currentTimeMillis(), preset, custom)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendOverview())

    // ── Kash Wrapped (NAH-34).
    /** One month's story; null when it has too little to wrap. */
    fun wrapped(anyMsInMonth: Long): Flow<Wrapped?> = combine(allTransactions, _calendar) { all, cal ->
        buildWrapped(all, cal, anyMsInMonth)
    }.flowOn(Dispatchers.Default)

    /** Last month, while the Home card should offer it (first week of a month). */
    val wrappedCard: StateFlow<Long?> = combine(allTransactions, _calendar) { all, cal ->
        wrappedCardMonth(all, cal, System.currentTimeMillis())
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Last month, when it can be wrapped (the Analytics row). */
    val lastMonthWrapped: StateFlow<Long?> = combine(allTransactions, _calendar) { all, cal ->
        val last = com.financeapp.util.KashDates.monthBounds(
            com.financeapp.util.KashDates.monthBounds(System.currentTimeMillis(), cal).first - 1, cal,
        ).first
        last.takeIf { buildWrapped(all, cal, it) != null }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Months that have transactions, newest first (the month picker). */
    val availableMonths: StateFlow<List<Long>> = combine(allTransactions, _calendar) { all, cal ->
        all.map { com.financeapp.util.KashDates.monthBounds(it.dateTime, cal).first }
            .plus(com.financeapp.util.KashDates.monthBounds(System.currentTimeMillis(), cal).first)
            .distinct().sortedDescending().take(24)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildReport(all: List<TransactionEntity>, cal: String, at: Long): MonthReport {
        val dates = com.financeapp.util.KashDates
        val now = System.currentTimeMillis()
        val (from, to) = dates.monthBounds(at, cal)
        val isCurrent = now in from until to
        val inMonth = all.filter { it.dateTime in from until to }
        val spending = inMonth.filter { it.isSpending() }

        val six = (0 until 6).fold(listOf(from)) { acc, _ -> acc + dates.monthBounds(acc.last() - 1, cal).first }
            .take(6).reversed()
            .map { start ->
                val end = dates.monthBounds(start, cal).second
                val rows = all.filter { it.dateTime in start until end }
                MonthInOut(start, rows.filter { it.isIncome() }.sumOf { it.amount }, rows.filter { it.isSpending() }.sumOf { it.amount })
            }

        val categories = spending.filter { !it.category.isNullOrBlank() }
            .groupBy { TransactionCategoryCatalog.normalize(it.category!!) }
            .map { (name, rows) -> CategoryTotal(name, rows.sumOf { it.amount }, rows.size) }
            .sortedByDescending { it.total }

        val dayCount = (((if (isCurrent) dates.startOfDay(now) + 86_400_000L else to) - from + 43_200_000L) / 86_400_000L).toInt().coerceAtLeast(1)
        val days = DoubleArray(dayCount)
        spending.forEach { tx ->
            val i = ((dates.startOfDay(tx.dateTime) - from + 43_200_000L) / 86_400_000L).toInt()
            if (i in 0 until dayCount) days[i] += tx.amount
        }

        val payees = spending.filter { !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!!.trim() }
            .map { (name, rows) -> PayeeTotal(name, rows.size, rows.sumOf { it.amount }) }
            .sortedByDescending { it.total }
            .take(5)

        val perAccount = spending.groupBy { it.bankName }
            .map { (name, rows) -> name to rows.sumOf { it.amount } }
            .sortedByDescending { it.second }

        return MonthReport(
            fromMs = from,
            toMs = to,
            isCurrent = isCurrent,
            spent = spending.sumOf { it.amount },
            moneyIn = inMonth.filter { it.isIncome() }.sumOf { it.amount },
            sixMonths = six,
            categories = categories,
            days = days.toList(),
            topPayees = payees,
            perAccount = perAccount,
            uncategorized = spending.count { it.category.isNullOrBlank() },
            uncategorizedAllTime = all.count { it.isSpending() && it.category.isNullOrBlank() },
            hasAnyTransactions = all.isNotEmpty(),
            isLoading = false,
        )
    }

    /**
     * Per-category drill-down state, memoized by category name so repeated
     * `categoryDetail(name)` calls return the SAME StateFlow. Without the
     * cache, every recomposition would spin up a fresh StateFlow that
     * briefly emits its empty default value before the real data lands —
     * visible to the user as a flicker between empty + loaded states.
     */
    private val categoryDetailFlows =
        java.util.concurrent.ConcurrentHashMap<String, StateFlow<CategoryDetailState>>()

    fun categoryDetail(category: String): StateFlow<CategoryDetailState> =
        categoryDetailFlows.getOrPut(category) {
            combine(allTransactions, _calendar, _reportAt) { all, cal, at -> Triple(all, cal, at) }
                .map { (all, cal, at) ->
                    runCatching { buildCategoryDetail(all, category, com.financeapp.util.KashDates.monthBounds(at, cal)) }
                        .getOrElse {
                            // Don't let a bad row crash the screen — fall
                            // back to an empty state with the category name
                            // so the user at least sees the header.
                            android.util.Log.e("AnalyticsVM", "buildCategoryDetail failed", it)
                            CategoryDetailState(category = category, isLoading = false)
                        }
                }
                .flowOn(Dispatchers.Default)
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    CategoryDetailState(category = category)
                )
        }

    private fun buildCategoryDetail(all: List<TransactionEntity>, category: String, month: Pair<Long, Long>): CategoryDetailState {
        val normalized = TransactionCategoryCatalog.normalize(category)
        val (todayStart, todayEnd) = todayBounds()
        val (monthStart, monthEnd) = month

        val monthExpenses = all.filter { it.isSpending() && it.dateTime >= monthStart && it.dateTime < monthEnd }
        val matchedMonth = monthExpenses.filter {
            it.category?.let(TransactionCategoryCatalog::normalize).equals(normalized, ignoreCase = true)
        }

        val totalMonth = monthExpenses.sumOf { it.amount }
        val catMonth = matchedMonth.sumOf { it.amount }
        val catToday = matchedMonth.filter { it.dateTime in todayStart..todayEnd }.sumOf { it.amount }
        val now = System.currentTimeMillis()
        val daysSoFar = (((if (now < monthEnd) now else monthEnd - 1) - monthStart) / 86_400_000L + 1).coerceAtLeast(1)
        val percent = if (totalMonth > 0) (catMonth / totalMonth) * 100.0 else 0.0

        return CategoryDetailState(
            category = category,
            monthSpending = catMonth,
            todaySpending = catToday,
            avgDailySpend = catMonth / daysSoFar,
            percentOfTotal = percent,
            transactions = matchedMonth.sortedByDescending { it.dateTime },
            isLoading = false
        )
    }

    private fun todayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 86_400_000L - 1)
    }

}

class AnalyticsViewModelFactory(private val repo: TransactionRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AnalyticsViewModel(repo) as T
    }
}
