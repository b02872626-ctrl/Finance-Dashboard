package com.financeapp.viewmodel

import androidx.lifecycle.*
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class AnalyticsState(
    val monthlyData: List<MonthlyAggregate>          = emptyList(),
    val perBankExpense: Map<String, Double>           = emptyMap(),
    val allCounterparties: List<Pair<String, Double>> = emptyList(),
    val totalIncome: Double                           = 0.0,
    val totalExpense: Double                          = 0.0,
    val isLoading: Boolean                            = true
)

/** Aggregated data for the Insights tab (matches design/insights/Insights_light.png). */
data class InsightsState(
    val todaySpending: Double = 0.0,
    val todayCount: Int = 0,
    val monthSpending: Double = 0.0,
    val monthAvgDaily: Double = 0.0,
    val topCategory: String = "",
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val uncategorizedCount: Int = 0,
    /** 35 cells (5 weeks × 7 days). Value is spending for that day (0 if none / outside month). */
    val monthDayValues: List<Double> = List(35) { 0.0 },
    val monthMaxDay: Double = 0.0,
    /** Spending per weekday (Sun..Sat) over the last week. 7 values. */
    val weekBars: List<Double> = List(7) { 0.0 },
    val isLoading: Boolean = true
)

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

data class PeriodInsights(
    val fromMs: Long   = 0L,
    val toMs: Long     = Long.MAX_VALUE,
    val totalIncome: Double  = 0.0,
    val totalExpense: Double = 0.0,
    val netFlow: Double      = 0.0,
    val avgDailySpend: Double = 0.0,
    val txCount: Int         = 0,
    val topSpendCategory: String = "",  // bank that had the most expense
    val biggestTx: Double    = 0.0,
    val biggestTxLabel: String = "",
    val savingsRate: Double  = 0.0,     // (income - expense) / income * 100
    val days: Int            = 1
)

class AnalyticsViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Date range filter for Period Insights — defaults to current month
    private val _periodFrom = MutableStateFlow(currentMonthStart())
    private val _periodTo   = MutableStateFlow(System.currentTimeMillis())
    val periodFrom: StateFlow<Long> = _periodFrom.asStateFlow()
    val periodTo:   StateFlow<Long> = _periodTo.asStateFlow()

    val state: StateFlow<AnalyticsState> = combine(
        repo.getMonthlyAggregates(System.currentTimeMillis() - 365L * 24 * 3600 * 1000),
        repo.getAllTransactions()
    ) { monthly, all ->
        val expenses = all.filter {
            it.type in listOf(
                TransactionType.DEBIT.name,
                TransactionType.TRANSFER_OUT.name,
                TransactionType.PAYMENT.name
            )
        }
        val perBank = expenses.groupBy { it.bankName }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        val counterparties = expenses
            .filter { !it.counterparty.isNullOrBlank() }
            .groupBy { it.counterparty!! }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val dfIn  = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val dfOut = java.text.SimpleDateFormat("MMM", java.util.Locale.US)
        val formattedMonthly = monthly.map { agg ->
            try {
                val date = dfIn.parse(agg.month)
                agg.copy(month = date?.let { dfOut.format(it) } ?: agg.month)
            } catch (e: Exception) { agg }
        }

        AnalyticsState(
            monthlyData       = formattedMonthly,
            perBankExpense    = perBank,
            allCounterparties = counterparties,
            totalIncome       = monthly.sumOf { it.totalIncome },
            totalExpense      = monthly.sumOf { it.totalExpense },
            isLoading         = false
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())

    // Period insights — reactive to date range changes
    val periodInsights: StateFlow<PeriodInsights> = combine(
        _periodFrom,
        _periodTo,
        repo.getAllTransactions()
    ) { from, to, all ->
        val inRange = all.filter { it.dateTime in from..to }
        val expenseTypes = listOf(
            TransactionType.DEBIT.name,
            TransactionType.TRANSFER_OUT.name,
            TransactionType.PAYMENT.name
        )
        val incomeTypes = listOf(TransactionType.CREDIT.name)

        val income  = inRange.filter { it.type in incomeTypes }.sumOf { it.amount }
        val expense = inRange.filter { it.type in expenseTypes }.sumOf { it.amount }
        val days    = TimeUnit.MILLISECONDS.toDays(to - from).coerceAtLeast(1).toInt()

        val topBank = inRange.filter { it.type in expenseTypes }
            .groupBy { it.bankName }
            .maxByOrNull { it.value.sumOf { t -> t.amount } }?.key ?: "—"

        val biggest = inRange.maxByOrNull { it.amount }
        val savings = if (income > 0) ((income - expense) / income * 100).coerceIn(-999.0, 100.0) else 0.0

        PeriodInsights(
            fromMs          = from,
            toMs            = to,
            totalIncome     = income,
            totalExpense    = expense,
            netFlow         = income - expense,
            avgDailySpend   = expense / days,
            txCount         = inRange.size,
            topSpendCategory = topBank,
            biggestTx       = biggest?.amount ?: 0.0,
            biggestTxLabel  = biggest?.counterparty ?: biggest?.bankName ?: "—",
            savingsRate     = savings,
            days            = days
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodInsights())

    fun setPeriod(from: Long, to: Long) {
        _periodFrom.value = from
        _periodTo.value   = to
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
    }

    // -------------------------------------------------------------------------
    // Insights tab — aggregations driving the Insights screen + per-category drill-down.
    // -------------------------------------------------------------------------
    val insights: StateFlow<InsightsState> = repo.getAllTransactions()
        .map { all -> buildInsights(all) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsState())

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
            repo.getAllTransactions()
                .map { all ->
                    runCatching { buildCategoryDetail(all, category) }
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

    private fun buildInsights(all: List<TransactionEntity>): InsightsState {
        val expenses = all.filter { it.type in EXPENSE_TYPES }

        val (todayStart, todayEnd) = todayBounds()
        val monthStart = startOfMonth()

        val todayExpenses = expenses.filter { it.dateTime in todayStart..todayEnd }
        val monthExpenses = expenses.filter { it.dateTime >= monthStart }

        // Aggregate per default category (preserves catalog order).
        val byCategory = monthExpenses
            .filter { !it.category.isNullOrBlank() }
            .groupBy { TransactionCategoryCatalog.normalize(it.category!!) }
        val totals = TransactionCategoryCatalog.defaultCategories.mapNotNull { name ->
            val txs = byCategory[name]
            if (txs.isNullOrEmpty()) {
                // Always include the default categories so the list mirrors the design even when empty.
                CategoryTotal(name, 0.0, 0)
            } else {
                CategoryTotal(name, txs.sumOf { it.amount }, txs.size)
            }
        }
        // Append any custom categories the user has used this month.
        val custom = byCategory.keys
            .filterNot { TransactionCategoryCatalog.defaultCategories.contains(it) }
            .map { name ->
                val txs = byCategory.getValue(name)
                CategoryTotal(name, txs.sumOf { it.amount }, txs.size)
            }
        val categoryTotals = totals + custom

        // Day-of-month spending grid (5×7).
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayValues = DoubleArray(35) { 0.0 }
        monthExpenses.forEach { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.dateTime }
            val d = c.get(Calendar.DAY_OF_MONTH) - 1
            if (d in 0 until daysInMonth.coerceAtMost(35)) {
                dayValues[d] = dayValues[d] + tx.amount
            }
        }

        // Last 7 days expense bars.
        val weekBars = DoubleArray(7) { 0.0 }
        val sevenDaysAgo = todayStart - 6L * 86_400_000L
        expenses.filter { it.dateTime in sevenDaysAgo..todayEnd }.forEach { tx ->
            val dayIdx = ((tx.dateTime - sevenDaysAgo) / 86_400_000L).toInt().coerceIn(0, 6)
            weekBars[dayIdx] = weekBars[dayIdx] + tx.amount
        }

        val topCategory = categoryTotals.maxByOrNull { it.total }?.takeIf { it.total > 0 }?.category ?: ""
        val uncategorized = monthExpenses.count { it.category.isNullOrBlank() }

        val daysSoFar = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val monthSpending = monthExpenses.sumOf { it.amount }

        return InsightsState(
            todaySpending = todayExpenses.sumOf { it.amount },
            todayCount = todayExpenses.size,
            monthSpending = monthSpending,
            monthAvgDaily = monthSpending / daysSoFar,
            topCategory = topCategory,
            categoryTotals = categoryTotals,
            uncategorizedCount = uncategorized,
            monthDayValues = dayValues.toList(),
            monthMaxDay = dayValues.maxOrNull() ?: 0.0,
            weekBars = weekBars.toList(),
            isLoading = false
        )
    }

    private fun buildCategoryDetail(all: List<TransactionEntity>, category: String): CategoryDetailState {
        val normalized = TransactionCategoryCatalog.normalize(category)
        val (todayStart, todayEnd) = todayBounds()
        val monthStart = startOfMonth()

        val monthExpenses = all.filter { it.type in EXPENSE_TYPES && it.dateTime >= monthStart }
        val matchedMonth = monthExpenses.filter {
            it.category?.let(TransactionCategoryCatalog::normalize).equals(normalized, ignoreCase = true)
        }

        val totalMonth = monthExpenses.sumOf { it.amount }
        val catMonth = matchedMonth.sumOf { it.amount }
        val catToday = matchedMonth.filter { it.dateTime in todayStart..todayEnd }.sumOf { it.amount }
        val cal = Calendar.getInstance()
        val daysSoFar = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
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

    private fun startOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun todayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        return start to (start + 86_400_000L - 1)
    }

    private fun currentMonthStart(): Long = startOfMonth()

    companion object {
        private val EXPENSE_TYPES = listOf(
            TransactionType.DEBIT.name,
            TransactionType.TRANSFER_OUT.name,
            TransactionType.PAYMENT.name
        )
    }
}

class AnalyticsViewModelFactory(private val repo: TransactionRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AnalyticsViewModel(repo) as T
    }
}
