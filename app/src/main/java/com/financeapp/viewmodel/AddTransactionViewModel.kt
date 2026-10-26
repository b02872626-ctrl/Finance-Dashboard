package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backing state for the "Add transaction" form.
 *
 *  - [amountText]   raw user input — parsed to Double on save.
 *  - [type]         CREDIT (income), DEBIT (expense), TRANSFER_OUT.
 *  - [dateTimeMs]   epoch millis the user picked; defaults to "now".
 *  - [counterparty] free-text. Trimmed on save; empty blocks Save.
 *  - [bankName]     free-text or one of the suggested banks/Cash.
 *  - [category]     optional — picked from CategorySelector.
 *  - [note]         optional free-text appended to the synthetic rawBody.
 *  - [saving]       true while the DB insert is in flight; disables Save.
 *  - [savedId]      non-null after a successful save; the UI watches this
 *                   to close the screen.
 */
data class AddTransactionState(
    val type: String = TransactionType.DEBIT.name,
    val amountText: String = "",
    val counterparty: String = "",
    val bankName: String = "",
    val dateTimeMs: Long = System.currentTimeMillis(),
    val category: String? = null,
    val note: String = "",
    val saving: Boolean = false,
    val savedId: Long? = null,
    val errorMessage: String? = null
) {
    /** Amount as Double when parseable; null otherwise. */
    val amount: Double?
        get() = amountText.trim().replace(",", "").toDoubleOrNull()

    /** Save button enabled when amount > 0 and counterparty non-empty. */
    val canSave: Boolean
        get() = !saving
            && amount.let { it != null && it > 0.0 }
            && counterparty.trim().isNotBlank()
            && bankName.trim().isNotBlank()
}

class AddTransactionViewModel(
    private val repo: TransactionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AddTransactionState())
    val state: StateFlow<AddTransactionState> = _state.asStateFlow()

    fun setType(type: String)                = _state.update { it.copy(type = type) }
    fun setAmount(text: String)              = _state.update { it.copy(amountText = text) }
    fun setCounterparty(name: String)        = _state.update { it.copy(counterparty = name) }
    fun setBank(name: String)                = _state.update { it.copy(bankName = name) }
    fun setDateTime(ms: Long)                = _state.update { it.copy(dateTimeMs = ms) }
    fun setCategory(cat: String?)            = _state.update { it.copy(category = cat?.ifBlank { null }) }
    fun setNote(text: String)                = _state.update { it.copy(note = text) }
    fun clearError()                         = _state.update { it.copy(errorMessage = null) }

    fun save() {
        val snap = _state.value
        if (!snap.canSave) return
        val amount = snap.amount ?: return

        _state.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val id = repo.insertManual(
                    type         = snap.type,
                    amount       = amount,
                    counterparty = snap.counterparty,
                    bankName     = snap.bankName,
                    dateTimeMs   = snap.dateTimeMs,
                    category     = snap.category,
                    note         = snap.note
                )
                _state.update { it.copy(saving = false, savedId = id) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        saving = false,
                        errorMessage = t.message ?: "Could not save — try again"
                    )
                }
            }
        }
    }

    /**
     * Local mutate helper. Inlined so we don't pull in extension utilities;
     * keeps the file self-contained.
     */
    private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
        value = block(value)
    }
}

class AddTransactionViewModelFactory(
    private val repo: TransactionRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AddTransactionViewModel::class.java)) {
            "Unknown ViewModel: ${'$'}modelClass"
        }
        return AddTransactionViewModel(repo) as T
    }
}
