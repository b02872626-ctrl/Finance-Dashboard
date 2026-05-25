package com.financeapp.viewmodel

import androidx.lifecycle.*
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class TransactionsFilter(
    val sender: String?             = null,
    val type: String?               = null,
    val fromMs: Long                = 0L,
    val toMs: Long                  = Long.MAX_VALUE,
    val searchQuery: String         = ""
)

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

    val transactions: StateFlow<List<DayGroup>> = _filter
        .combine(_limitDays) { f, limit -> f to limit }
        .flatMapLatest { (f, limit) ->
            repo.getFiltered(f.sender, f.type, f.fromMs, f.toMs)
                .map { list ->
                    val q = f.searchQuery.trim()
                    val filtered = if (q.isBlank()) list else list.filter { tx -> matchesSearch(tx, q) }
                    buildDayGroups(filtered, limit, q.isNotBlank() || f.fromMs > 0L)
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    fun setDateFilter(fromMs: Long, toMs: Long) { _filter.update { it.copy(fromMs = fromMs, toMs = toMs) } }
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
        val base = repo.getFiltered(f.sender, f.type, f.fromMs, f.toMs).first()

        val q = f.searchQuery.trim()
        if (q.isBlank()) return base

        return base.filter { tx -> matchesSearch(tx, q) }
    }

    private fun matchesSearch(tx: TransactionEntity, q: String): Boolean =
        tx.counterparty?.contains(q, ignoreCase = true) == true ||
            tx.refNumber?.contains(q, ignoreCase = true) == true ||
            tx.bankName.contains(q, ignoreCase = true)

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
                val income   = txns.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
                val expenses = txns.filter {
                    it.type in listOf(
                        TransactionType.DEBIT.name,
                        TransactionType.TRANSFER_OUT.name,
                        TransactionType.PAYMENT.name
                    )
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
