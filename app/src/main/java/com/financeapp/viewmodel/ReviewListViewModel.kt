package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.ui.screens.ReviewList
import com.financeapp.ui.screens.ReviewTab
import com.financeapp.util.KashDates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Transient snackbar payload: gone after a few seconds or on Undo. */
data class ReviewSnackbar(val text: String, val undoId: Long)

/**
 * "Put all N in X?": the user sorted a transaction whose sender has other
 * rows. They choose between every row from that sender or just this one.
 */
data class ConfirmApplyAll(
    val txId: Long,
    val category: String,
    val merchantKey: String,
    val counterparty: String,
    /** Rows with this merchant key, this one included. */
    val totalCount: Int,
    /** Newest rows from this sender, shown in the dialog. */
    val samples: List<TransactionEntity> = emptyList(),
)

data class ReviewListState(
    val tab: ReviewTab = ReviewTab.TODAY,
    val today: List<TransactionEntity> = emptyList(),
    /** This month's pending rows, today's included, newest first. */
    val month: List<TransactionEntity> = emptyList(),
    /** Pending rows from before this month; listed under This month on request. */
    val older: List<TransactionEntity> = emptyList(),
    val showOlder: Boolean = false,
    /** The row whose sort card is open. */
    val sorting: TransactionEntity? = null,
    /** The row whose "Check this one" sheet is open. */
    val detail: TransactionEntity? = null,
    val applyToSimilar: Boolean = true,
    val snackbar: ReviewSnackbar? = null,
    val confirmApplyAll: ConfirmApplyAll? = null,
    val isLoading: Boolean = true,
) {
    val shown: List<TransactionEntity>
        get() = when (tab) {
            ReviewTab.TODAY -> today
            ReviewTab.MONTH -> if (showOlder) month + older else month
        }
    val count: Int get() = if (tab == ReviewTab.TODAY) today.size else month.size
}

/**
 * Drives the Review list. The repo is the source of truth: every sort
 * writes at once and the pending flow re-emits, so the row leaves the list
 * on its own and Undo brings it back.
 */
class ReviewListViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReviewListState())
    val state: StateFlow<ReviewListState> = _state.asStateFlow()

    /** The calendar in use (Gregorian or Ethiopian) decides where the month starts. */
    private val calendar = MutableStateFlow("GREGORIAN")
    private var snackbarJob: Job? = null

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { repo.backfillMissingMerchantKeys() } }
        viewModelScope.launch {
            combine(repo.getAllPendingReview(), calendar) { pending, cal ->
                val now = System.currentTimeMillis()
                ReviewList.split(pending, KashDates.startOfDay(now), KashDates.monthBounds(now, cal).first)
            }.collect { split ->
                _state.value = _state.value.copy(
                    today = split.today, month = split.month, older = split.older, isLoading = false,
                    // A row that left the list closes its sheets.
                    sorting = _state.value.sorting?.takeIf { s -> split.month.any { it.id == s.id } || split.older.any { it.id == s.id } },
                    detail = _state.value.detail?.takeIf { d -> split.month.any { it.id == d.id } || split.older.any { it.id == d.id } },
                )
            }
        }
    }

    fun setCalendar(cal: String) { calendar.value = cal }
    fun setTab(tab: ReviewTab) { _state.value = _state.value.copy(tab = tab) }
    fun showOlder() { _state.value = _state.value.copy(tab = ReviewTab.MONTH, showOlder = true) }

    fun openSort(tx: TransactionEntity) { _state.value = _state.value.copy(sorting = tx, detail = null) }
    fun openDetail(tx: TransactionEntity) { _state.value = _state.value.copy(detail = tx) }
    fun closeSheets() { _state.value = _state.value.copy(sorting = null, detail = null) }
    fun toggleApplyToSimilar() { _state.value = _state.value.copy(applyToSimilar = !_state.value.applyToSimilar) }

    /**
     * The sort card's tap. A sender with other rows and the rule switch on
     * asks "Put all N in X?" first; otherwise the row is sorted at once.
     */
    fun sort(tx: TransactionEntity, category: String, applyToSimilar: Boolean = _state.value.applyToSimilar) {
        viewModelScope.launch {
            val key = tx.merchantKey
            val counterparty = tx.counterparty
            if (!key.isNullOrBlank() && !counterparty.isNullOrBlank() && applyToSimilar) {
                val totalCount = repo.countTransactionsByMerchantKey(key)
                if (totalCount > 1) {
                    _state.value = _state.value.copy(
                        sorting = null, detail = null,
                        confirmApplyAll = ConfirmApplyAll(
                            txId = tx.id, category = category, merchantKey = key, counterparty = counterparty,
                            totalCount = totalCount, samples = repo.getByMerchantKey(key, 4),
                        ),
                    )
                    return@launch
                }
            }
            val rule = applyToSimilar && !key.isNullOrBlank()
            repo.applyReviewedCategory(tx.id, category, applyToSimilar = rule)
            _state.value = _state.value.copy(sorting = null, detail = null)
            showSnackbar(ReviewList.sortedText(tx.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: tx.bankName, category, rule), tx.id)
        }
    }

    fun confirmApplyAll() {
        val d = _state.value.confirmApplyAll ?: return
        viewModelScope.launch {
            repo.applyReviewedCategory(d.txId, d.category, applyToSimilar = true)
            _state.value = _state.value.copy(confirmApplyAll = null)
            showSnackbar("${d.totalCount} from ${d.counterparty} are ${d.category}. New ones will be too.", d.txId, long = true)
        }
    }

    fun confirmApplyJustOne() {
        val d = _state.value.confirmApplyAll ?: return
        viewModelScope.launch {
            repo.applyReviewedCategory(d.txId, d.category, applyToSimilar = false)
            _state.value = _state.value.copy(confirmApplyAll = null)
            showSnackbar(ReviewList.sortedText(d.counterparty, d.category, rule = false), d.txId)
        }
    }

    fun dismissConfirmApplyAll() { _state.value = _state.value.copy(confirmApplyAll = null) }

    fun markNotATransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repo.markNotATransaction(tx.id)
            _state.value = _state.value.copy(sorting = null, detail = null)
            showSnackbar("Marked as not a transaction", tx.id)
        }
    }

    fun undo() {
        val snack = _state.value.snackbar ?: return
        viewModelScope.launch {
            repo.undoReview(snack.undoId)
            _state.value = _state.value.copy(snackbar = null)
        }
    }

    private fun showSnackbar(text: String, undoId: Long, long: Boolean = false) {
        _state.value = _state.value.copy(snackbar = ReviewSnackbar(text, undoId))
        snackbarJob?.cancel()
        snackbarJob = viewModelScope.launch {
            kotlinx.coroutines.delay(if (long) 6500L else 3200L)
            _state.value = _state.value.copy(snackbar = null)
        }
    }
}

class ReviewListViewModelFactory(private val repo: TransactionRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ReviewListViewModel(repo) as T
}
