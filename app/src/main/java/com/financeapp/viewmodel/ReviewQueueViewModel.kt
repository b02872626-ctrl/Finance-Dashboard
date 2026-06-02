package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Modal sheet currently open on the review queue. */
enum class ReviewSheet { None, Picker, Detail }

/**
 * What slice of PENDING transactions the queue is working on.
 *  TODAY → today's parsed SMS only. The "Daily Review" experience.
 *  ALL   → every PENDING row in history, newest-first. Used for catching
 *          up on the backlog after the migration first installs.
 */
enum class ReviewScope { TODAY, ALL }

/** Transient snackbar payload — disappears after 2.6 s or on Undo. */
data class ReviewSnackbar(
    val text: String,
    val undoId: Long
)

/**
 * Confirmation popup shown when a user picks a category for a transaction
 * whose merchant has other transactions in the database. Lets the user
 * decide whether to recategorize the entire history for that merchant or
 * only the current row.
 */
data class ConfirmApplyAll(
    val txId: Long,
    val category: String,
    val merchantKey: String,
    val counterparty: String,
    /** Total transactions with this merchant_key — current row included. */
    val totalCount: Int,
    /**
     * Snapshot of the row's [predictedCategory] when the popup opened.
     * Captured here so the snackbar label ("Confirmed as X" vs "Changed to X")
     * stays accurate even if the cursor moves before the user picks an action.
     */
    val predictedCategory: String?
)

data class ReviewQueueState(
    val items: List<TransactionEntity> = emptyList(),
    /** Index into [items] of the card currently being reviewed. */
    val currentIdx: Int = 0,
    /**
     * Total of reviewable rows. TODAY scope: pending + already-done today.
     * ALL scope: the initial pending snapshot when the queue opened, so the
     * progress bar denominator stays stable across the session.
     */
    val totalToday: Int = 0,
    /** Confirmed/changed/skipped count fed by the same scope as [totalToday]. */
    val doneToday: Int = 0,
    val scope: ReviewScope = ReviewScope.TODAY,
    val applyToSimilar: Boolean = true,
    val showRaw: Boolean = false,
    val sheet: ReviewSheet = ReviewSheet.None,
    val snackbar: ReviewSnackbar? = null,
    /** Non-null when the "Apply to all '{name}' transactions?" popup is showing. */
    val confirmApplyAll: ConfirmApplyAll? = null,
    val complete: Boolean = false,
    val isLoading: Boolean = true
) {
    val current: TransactionEntity? get() = items.getOrNull(currentIdx)
}

/**
 * Drives the swipe-categorize queue. The repo is the single source of
 * truth — every action persists immediately, then the cold-Flow combine()
 * re-emits a fresh state so Undo / re-categorize stay consistent.
 */
class ReviewQueueViewModel(
    private val repo: TransactionRepository,
    private val scope: ReviewScope = ReviewScope.TODAY
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewQueueState(scope = scope))
    val state: StateFlow<ReviewQueueState> = _state.asStateFlow()

    private var snackbarJob: Job? = null

    /**
     * Snapshot of the initial pending count when the queue opened. Used as
     * the stable progress denominator for ALL scope (TODAY scope reads it
     * live from the day's counts instead).
     */
    private var allScopeInitialTotal: Int? = null

    init { load() }

    fun load() {
        // Kick off the merchant-key backfill in parallel — it shouldn't
        // block the queue's first render. Wrapped in withTransaction inside
        // the repo, so 3k+ writes commit as one batch (~50× faster than the
        // per-row UPDATE loop the audit flagged).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { repo.backfillMissingMerchantKeys() }
        }
        viewModelScope.launch {
            when (scope) {
                ReviewScope.TODAY -> loadToday()
                ReviewScope.ALL   -> loadAll()
            }
        }
    }

    private suspend fun loadToday() {
        combine(
            repo.getTodayPendingReview(),
            repo.countTodayByReviewStatus(ReviewStatus.CONFIRMED),
            repo.countTodayByReviewStatus(ReviewStatus.CHANGED),
            repo.countTodayByReviewStatus(ReviewStatus.SKIPPED)
        ) { pending, confirmed, changed, skipped ->
            val done  = confirmed + changed + skipped
            val total = pending.size + done
            val current = _state.value
            current.copy(
                items      = pending,
                currentIdx = current.currentIdx.coerceIn(0, maxOf(0, pending.size - 1)),
                doneToday  = done,
                totalToday = total,
                complete   = pending.isEmpty() && total > 0,
                isLoading  = false
            )
        }.collect { _state.value = it }
    }

    private suspend fun loadAll() {
        repo.getAllPendingReview().collect { pending ->
            // Snapshot the initial pending count once so the progress bar
            // denominator doesn't shrink as the user works through items.
            if (allScopeInitialTotal == null) allScopeInitialTotal = pending.size
            val total = allScopeInitialTotal ?: pending.size
            val done  = (total - pending.size).coerceIn(0, total)
            val current = _state.value
            _state.value = current.copy(
                items      = pending,
                currentIdx = current.currentIdx.coerceIn(0, maxOf(0, pending.size - 1)),
                doneToday  = done,
                totalToday = total,
                complete   = pending.isEmpty() && total > 0,
                isLoading  = false
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // User actions
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Right-swipe / Confirm button — accepts the predicted category. Goes
     * through [pickCategory] so the "Apply to all {merchant}?" popup fires
     * symmetrically whether the user confirms or explicitly picks.
     */
    fun confirm() {
        val t = state.value.current ?: return
        val cat = t.predictedCategory ?: "Other"
        pickCategory(cat, advance = true)
    }

    /** Left-swipe / Skip button — defer until tomorrow (TODAY) / later (ALL). */
    fun skip() {
        val t = state.value.current ?: return
        viewModelScope.launch {
            repo.skipReview(t.id)
            val msg = when (scope) {
                ReviewScope.TODAY -> "Skipped — we'll ask again tomorrow"
                ReviewScope.ALL   -> "Skipped — we'll resurface this later"
            }
            showSnackbar(msg, t.id)
            advanceCursor()
        }
    }

    /**
     * Quick-pick chip OR picker sheet. If the row has a [merchantKey] and
     * other transactions share it, we open the "Apply to all {name}
     * transactions?" popup instead of applying immediately. Otherwise we
     * apply to the single row.
     */
    fun pickCategory(category: String, advance: Boolean) {
        val t = state.value.current ?: return
        viewModelScope.launch {
            val key = t.merchantKey
            val counterparty = t.counterparty
            if (!key.isNullOrBlank() && !counterparty.isNullOrBlank()) {
                val totalCount = repo.countTransactionsByMerchantKey(key)
                if (totalCount > 1) {
                    // Show the popup, defer the actual write until the user
                    // chooses "Apply to all" or "Just this one".
                    _state.value = _state.value.copy(
                        sheet = ReviewSheet.None,
                        confirmApplyAll = ConfirmApplyAll(
                            txId = t.id,
                            category = category,
                            merchantKey = key,
                            counterparty = counterparty,
                            totalCount = totalCount,
                            predictedCategory = t.predictedCategory
                        )
                    )
                    return@launch
                }
            }
            // No merchant key, or no other transactions to match — apply
            // to just this row and create a rule (so future SMS still
            // auto-categorize correctly even though there are 0 past rows).
            applyAndSnack(t.id, category, applyToSimilar = !key.isNullOrBlank(), advance = advance, predicted = t.predictedCategory)
        }
    }

    /** "Apply to all" button on the confirmation popup. */
    fun confirmApplyAll() {
        val dialog = state.value.confirmApplyAll ?: return
        viewModelScope.launch {
            repo.applyReviewedCategory(dialog.txId, dialog.category, applyToSimilar = true)
            showSnackbar(
                "Applied ${dialog.category} to ${dialog.totalCount} ${dialog.counterparty} ${if (dialog.totalCount == 1) "transaction" else "transactions"}",
                dialog.txId
            )
            _state.value = _state.value.copy(confirmApplyAll = null, sheet = ReviewSheet.None)
            advanceCursor()
        }
    }

    /** "Just this one" button on the confirmation popup. */
    fun confirmApplyJustOne() {
        val dialog = state.value.confirmApplyAll ?: return
        viewModelScope.launch {
            repo.applyReviewedCategory(dialog.txId, dialog.category, applyToSimilar = false)
            // Compare against the prediction CAPTURED at popup-open time, not
            // state.value.current (which may already point to the next row).
            showSnackbar(
                if (dialog.category == dialog.predictedCategory) "Confirmed as ${dialog.category}"
                else "Changed to ${dialog.category}",
                dialog.txId
            )
            _state.value = _state.value.copy(confirmApplyAll = null, sheet = ReviewSheet.None)
            advanceCursor()
        }
    }

    fun dismissConfirmApplyAll() {
        _state.value = _state.value.copy(confirmApplyAll = null)
    }

    /** Single-row apply path used when there's no merchant-key or no peers. */
    private suspend fun applyAndSnack(
        id: Long,
        category: String,
        applyToSimilar: Boolean,
        advance: Boolean,
        predicted: String?
    ) {
        repo.applyReviewedCategory(id, category, applyToSimilar = applyToSimilar)
        val msg = if (category == predicted) "Confirmed as $category"
                  else "Changed to $category"
        showSnackbar(msg, id)
        closeSheet()
        if (advance) advanceCursor()
    }

    fun markNotATransaction() {
        val t = state.value.current ?: return
        viewModelScope.launch {
            repo.markNotATransaction(t.id)
            showSnackbar("Marked as not a transaction", t.id)
            closeSheet()
            advanceCursor()
        }
    }

    fun undo() {
        val snack = state.value.snackbar ?: return
        viewModelScope.launch {
            repo.undoReview(snack.undoId)
            _state.value = _state.value.copy(snackbar = null)
            // The flow update will reinstate the row at its place; we don't
            // forcibly seek because the user may have moved on.
        }
    }

    fun openPickerSheet() { _state.value = _state.value.copy(sheet = ReviewSheet.Picker) }
    fun openDetailSheet() { _state.value = _state.value.copy(sheet = ReviewSheet.Detail) }
    fun closeSheet()      { _state.value = _state.value.copy(sheet = ReviewSheet.None) }

    fun toggleRaw() { _state.value = _state.value.copy(showRaw = !state.value.showRaw) }
    fun toggleApplyToSimilar() {
        _state.value = _state.value.copy(applyToSimilar = !state.value.applyToSimilar)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    /**
     * After a confirm/skip, the row falls out of the PENDING list on the
     * next flow tick — the flow re-emission shifts the next row into our
     * cursor's position automatically. So we DO NOT add +1 here; doing so
     * would skip the very next row (especially noticeable when many rows
     * look alike, e.g. 1795 'Unknown merchant' entries from old data).
     *
     * We only reset transient per-card state (raw-SMS expansion).
     */
    private fun advanceCursor() {
        _state.value = _state.value.copy(showRaw = false)
    }

    private fun showSnackbar(text: String, undoId: Long) {
        // Bulk-apply messages ("Applied X to 47 transactions") need longer
        // to read + react. Single-row actions stay short to keep the queue
        // feeling responsive.
        val durationMs = if (text.startsWith("Applied ", ignoreCase = true)) 6500L else 2600L
        _state.value = _state.value.copy(snackbar = ReviewSnackbar(text, undoId))
        snackbarJob?.cancel()
        snackbarJob = viewModelScope.launch {
            kotlinx.coroutines.delay(durationMs)
            _state.value = _state.value.copy(snackbar = null)
        }
    }
}

class ReviewQueueViewModelFactory(
    private val repo: TransactionRepository,
    private val scope: ReviewScope = ReviewScope.TODAY
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ReviewQueueViewModel(repo, scope) as T
}
