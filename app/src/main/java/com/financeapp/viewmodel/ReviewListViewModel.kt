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
/** [tab] is where the row was, so Undo shows it back where it came from. */
data class ReviewSnackbar(val text: String, val undoId: Long, val bold: String? = null, val tab: ReviewTab? = null)

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
    /** Pending rows from before this month: the Earlier tab, by month. */
    val older: List<TransactionEntity> = emptyList(),
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
            ReviewTab.MONTH -> month
            ReviewTab.EARLIER -> older
        }
    val count: Int get() = shown.size
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
    /** A tab asked for before the list loaded (Home's or Analytics' "older ones"). */
    private var pinned: ReviewTab? = null


    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { repo.backfillMissingMerchantKeys() } }
        viewModelScope.launch {
            combine(repo.getAllPendingReview(), calendar) { pending, cal ->
                val now = System.currentTimeMillis()
                ReviewList.split(pending, KashDates.startOfDay(now), KashDates.monthBounds(now, cal).first)
            }.collect { split ->
                val was = _state.value
                // Opening lands on the first tab with something to sort; when
                // the tab being looked at runs out, Review moves on to the next.
                val tab = when {
                    was.isLoading && pinned == null -> ReviewList.landing(split)
                    was.isLoading -> pinned!!
                    was.count > 0 && ReviewList.count(was.tab, split) == 0 -> ReviewList.landing(split, from = was.tab)
                    else -> was.tab
                }
                val ids = (split.today + split.month + split.older).mapTo(HashSet()) { it.id }
                _state.value = _state.value.copy(
                    tab = tab,
                    today = split.today, month = split.month, older = split.older, isLoading = false,
                    // A row that left the list closes its sheets.
                    sorting = _state.value.sorting?.takeIf { it.id in ids },
                    detail = _state.value.detail?.takeIf { it.id in ids },
                )
            }
        }
    }

    fun setCalendar(cal: String) { calendar.value = cal }
    fun setTab(tab: ReviewTab) { _state.value = _state.value.copy(tab = tab) }
    fun showOlder() {
        if (_state.value.isLoading) pinned = ReviewTab.EARLIER
        _state.value = _state.value.copy(tab = ReviewTab.EARLIER)
    }

    fun openSort(tx: TransactionEntity) { _state.value = _state.value.copy(sorting = tx, detail = null) }
    fun openDetail(tx: TransactionEntity) { _state.value = _state.value.copy(detail = tx) }
    fun closeSheets() { _state.value = _state.value.copy(sorting = null, detail = null) }
    fun toggleApplyToSimilar() { _state.value = _state.value.copy(applyToSimilar = !_state.value.applyToSimilar) }

    /**
     * The sort card's tap. A sender with other rows and the rule switch on
     * asks "Put all N in X?" first; otherwise the row is sorted at once.
     */
    fun sort(tx: TransactionEntity, category: String, applyToSimilar: Boolean = _state.value.applyToSimilar) {
        val from = _state.value.tab
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
            showSnackbar(ReviewList.sortedText(tx.counterparty?.trim()?.takeIf { it.isNotBlank() } ?: tx.bankName, category, rule), tx.id, bold = category, tab = from)
        }
    }

    fun confirmApplyAll() {
        val d = _state.value.confirmApplyAll ?: return
        val from = _state.value.tab
        viewModelScope.launch {
            repo.applyReviewedCategory(d.txId, d.category, applyToSimilar = true)
            _state.value = _state.value.copy(confirmApplyAll = null)
            showSnackbar("${d.totalCount} from ${d.counterparty} are ${d.category}. New ones will be too.", d.txId, long = true, bold = d.category, tab = from)
        }
    }

    fun confirmApplyJustOne() {
        val d = _state.value.confirmApplyAll ?: return
        val from = _state.value.tab
        viewModelScope.launch {
            repo.applyReviewedCategory(d.txId, d.category, applyToSimilar = false)
            _state.value = _state.value.copy(confirmApplyAll = null)
            showSnackbar(ReviewList.sortedText(d.counterparty, d.category, rule = false), d.txId, bold = d.category, tab = from)
        }
    }

    fun dismissConfirmApplyAll() { _state.value = _state.value.copy(confirmApplyAll = null) }

    fun markNotATransaction(tx: TransactionEntity) {
        val from = _state.value.tab
        viewModelScope.launch {
            repo.markNotATransaction(tx.id)
            _state.value = _state.value.copy(sorting = null, detail = null)
            showSnackbar("Marked as not a transaction", tx.id, tab = from)
        }
    }

    fun undo() {
        val snack = _state.value.snackbar ?: return
        // Back to the tab the row came from (Review may have moved on when
        // that tab emptied). Set first, so the row's return doesn't move it.
        snack.tab?.let { _state.value = _state.value.copy(tab = it) }
        viewModelScope.launch {
            repo.undoReview(snack.undoId)
            _state.value = _state.value.copy(snackbar = null)
        }
    }

    private fun showSnackbar(text: String, undoId: Long, long: Boolean = false, bold: String? = null, tab: ReviewTab? = null) {
        _state.value = _state.value.copy(snackbar = ReviewSnackbar(text, undoId, bold, tab))
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
