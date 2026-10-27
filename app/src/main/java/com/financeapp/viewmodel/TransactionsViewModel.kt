package com.financeapp.viewmodel

import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.data.model.isOwnAccountTransfer
import androidx.lifecycle.*
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import com.financeapp.util.KashDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/** History's period sheet. Bounds follow the calendar setting. */
enum class HistoryRange(val label: String) {
    WEEK("This week"), MONTH("This month"), LAST_MONTH("Last month"), YEAR("This year"), ALL("All time"), CUSTOM("Pick dates")
}

/** History's In / Out chips. Null = All (own-account moves only show under All). */
enum class TxDirection { IN, OUT }

data class TransactionsFilter(
    val sender: String?             = null,
    val type: String?               = null,
    /** Used when [range] is CUSTOM (inclusive start, exclusive end). */
    val fromMs: Long                = 0L,
    val toMs: Long                  = Long.MAX_VALUE,
    val searchQuery: String         = "",
    val range: HistoryRange         = HistoryRange.MONTH,
    val direction: TxDirection?     = null
) {
    /** Effective [from, to) for the period, in the given calendar. */
    fun bounds(calendarSystem: String, now: Long = System.currentTimeMillis()): Pair<Long, Long> = when (range) {
        HistoryRange.WEEK -> KashDates.weekStart(now) to Long.MAX_VALUE
        HistoryRange.MONTH -> KashDates.monthBounds(now, calendarSystem).first to Long.MAX_VALUE
        HistoryRange.LAST_MONTH -> KashDates.monthBounds(KashDates.monthBounds(now, calendarSystem).first - 1, calendarSystem)
        HistoryRange.YEAR -> KashDates.yearBounds(now, calendarSystem).first to Long.MAX_VALUE
        HistoryRange.ALL -> 0L to Long.MAX_VALUE
        HistoryRange.CUSTOM -> fromMs to toMs
    }
}

data class DayGroup(
    val dateLabel: String,
    val dateKey: String,
    val transactions: List<TransactionEntity>,
    val income: Double,
    val expenses: Double,
    val netFlow: Double
)

data class BulkCategorySuggestion(
    val counterparty: String,
    val category: String,
    val transactionCount: Int,
    val totalAmount: Double
)

class TransactionsViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionsFilter())
    val filter: StateFlow<TransactionsFilter> = _filter.asStateFlow()

    private val _limitDays = MutableStateFlow(10) // Pagination limit
    private val _calendar = MutableStateFlow("GREGORIAN")
    private val _loaded = MutableStateFlow(false)
    /** False until the first result for the current filter arrives (skeleton rows). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val transactions: StateFlow<List<DayGroup>> = combine(_filter, _limitDays, _calendar) { f, limit, cal -> Triple(f, limit, cal) }
        .flatMapLatest { (f, limit, cal) ->
            _loaded.value = false
            val (from, to) = f.bounds(cal)
            repo.getFiltered(f.sender, f.type, from, to - 1)
                .map { list ->
                    val q = f.searchQuery.trim()
                    val filtered = list.filter { tx ->
                        (q.isBlank() || matchesSearch(tx, q)) && matchesDirection(tx, f.direction)
                    }
                    buildDayGroups(filtered, limit, q.isNotBlank() || f.range != HistoryRange.ALL)
                        .also { _loaded.value = true }
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The calendar setting decides where "this month" starts. */
    fun setCalendar(calendarSystem: String) { _calendar.value = calendarSystem }

    val availableTypes = TransactionType.values().toList()

    val todayUncategorizedTransactions: StateFlow<List<TransactionEntity>> = repo
        .getTodayUncategorizedTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bulkCategorySuggestion = MutableStateFlow<BulkCategorySuggestion?>(null)
    val bulkCategorySuggestion: StateFlow<BulkCategorySuggestion?> = _bulkCategorySuggestion.asStateFlow()
    private val dismissedBulkSuggestionKeys = mutableSetOf<String>()

    fun setFilter(newFilter: TransactionsFilter) { _filter.value = newFilter }
    fun setSearch(q: String)   { _filter.update { it.copy(searchQuery = q) } }
    fun setSender(s: String?)  { _filter.update { it.copy(sender = s) } }
    fun setType(t: String?)    { _filter.update { it.copy(type = t) } }
    /** Custom range from the date dialog: [fromMs, toMs). */
    fun setDateFilter(fromMs: Long, toMs: Long) { _filter.update { it.copy(fromMs = fromMs, toMs = toMs, range = HistoryRange.CUSTOM) } }
    fun setRange(r: HistoryRange) { _filter.update { it.copy(range = r) } }
    fun setDirection(d: TxDirection?) { _filter.update { it.copy(direction = d) } }
    fun clearFilters()         { _filter.value = TransactionsFilter() }

    fun updateCategory(id: Long, category: String?, allowBulkSuggestion: Boolean = false) {
        viewModelScope.launch {
            repo.updateCategory(id, category)
            if (allowBulkSuggestion) {
                maybeSuggestBulkCategory(id, category)
            }
        }
    }

    fun dismissBulkCategorySuggestion() {
        _bulkCategorySuggestion.value?.let { suggestion ->
            dismissedBulkSuggestionKeys += suggestion.counterparty.trim().lowercase()
        }
        _bulkCategorySuggestion.value = null
    }

    fun applyBulkCategorySuggestion() {
        val suggestion = _bulkCategorySuggestion.value ?: return
        viewModelScope.launch {
            repo.updateCategoryForCounterparty(suggestion.counterparty, suggestion.category)
            dismissedBulkSuggestionKeys += suggestion.counterparty.trim().lowercase()
            _bulkCategorySuggestion.value = null
        }
    }
    
    fun loadMore() {
        _limitDays.value += 10
    }

    /**
     * Returns the full (unpaginated) list of transactions matching the current filter,
     * suitable for exporting.
     */
    suspend fun getExportTransactions(): List<TransactionEntity> {
        val f = _filter.value
        val (from, to) = f.bounds(_calendar.value)
        val base = repo.getFiltered(f.sender, f.type, from, to - 1).first()
            .filter { matchesDirection(it, f.direction) }

        val q = f.searchQuery.trim()
        if (q.isBlank()) return base

        return base.filter { tx -> matchesSearch(tx, q) }
    }

    private fun matchesDirection(tx: TransactionEntity, d: TxDirection?): Boolean = when (d) {
        null -> true
        TxDirection.IN -> tx.isIncome()
        TxDirection.OUT -> tx.isSpending() || (tx.type != TransactionType.CREDIT.name && !tx.isOwnAccountTransfer())
    }

    private fun matchesSearch(tx: TransactionEntity, q: String): Boolean =
        tx.counterparty?.contains(q, ignoreCase = true) == true ||
            tx.refNumber?.contains(q, ignoreCase = true) == true ||
            tx.bankName.contains(q, ignoreCase = true) ||
            tx.category?.contains(q, ignoreCase = true) == true

    private suspend fun maybeSuggestBulkCategory(id: Long, category: String?) {
        val normalizedCategory = category?.trim().orEmpty()
        if (normalizedCategory.isBlank()) return

        val tx = repo.getById(id) ?: return
        val counterparty = tx.counterparty?.trim().orEmpty()
        if (counterparty.isBlank()) return
        if (tx.type !in setOf(
                TransactionType.DEBIT.name,
                TransactionType.TRANSFER_OUT.name,
                TransactionType.PAYMENT.name
            )
        ) return

        val suggestionKey = counterparty.lowercase()
        if (suggestionKey in dismissedBulkSuggestionKeys) return

        val aggregate = repo.getOutgoingCounterpartyAggregate(counterparty)
        if (aggregate.transactionCount < 2) return

        _bulkCategorySuggestion.value = BulkCategorySuggestion(
            counterparty = counterparty,
            category = normalizedCategory,
            transactionCount = aggregate.transactionCount,
            totalAmount = aggregate.totalAmount
        )
    }

    private fun buildDayGroups(
        transactions: List<TransactionEntity>, 
        limit: Int,
        isFiltering: Boolean
    ): List<DayGroup> {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        // If the user uses detailed search or date filter, don't limit days. Return everything matching.
        // If purely scrolling, limit to 10 days for performance.
        val actualLimit = if (isFiltering) Int.MAX_VALUE else limit

        return transactions
            .groupBy { dayFmt.format(Date(it.dateTime)) }
            .entries
            .sortedByDescending { it.key }
            .take(actualLimit)
            .map { (key, txns) ->
                val income   = txns.filter { it.isIncome() }.sumOf { it.amount }
                val expenses = txns.filter {
                    it.isSpending()
                }.sumOf { it.amount }
                DayGroup(
                    dateLabel    = formatLabel(key, dayFmt),
                    dateKey      = key,
                    transactions = txns.sortedByDescending { it.dateTime },
                    income       = income,
                    expenses     = expenses,
                    netFlow      = income - expenses
                )
            }
    }

    private fun formatLabel(key: String, fmt: SimpleDateFormat): String {
        val date = fmt.parse(key) ?: return key
        val todayCal     = Calendar.getInstance()
        val dateCal      = Calendar.getInstance().apply { time = date }
        val isToday      = todayCal.get(Calendar.YEAR)         == dateCal.get(Calendar.YEAR) &&
                           todayCal.get(Calendar.DAY_OF_YEAR)  == dateCal.get(Calendar.DAY_OF_YEAR)
        val isYesterday  = run {
            todayCal.add(Calendar.DAY_OF_YEAR, -1)
            val y = todayCal.get(Calendar.YEAR)         == dateCal.get(Calendar.YEAR) &&
                    todayCal.get(Calendar.DAY_OF_YEAR)  == dateCal.get(Calendar.DAY_OF_YEAR)
            todayCal.add(Calendar.DAY_OF_YEAR, 1)   // restore
            y
        }
        return when {
            isToday     -> "Today"
            isYesterday -> "Yesterday"
            else        -> SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(date)
        }
    }
}

class TransactionsViewModelFactory(private val repo: TransactionRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TransactionsViewModel(repo) as T
    }
}
