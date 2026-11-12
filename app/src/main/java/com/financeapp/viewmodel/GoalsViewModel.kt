package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalRules
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.data.repository.AccountInUseException
import com.financeapp.data.repository.GoalRepository
import com.financeapp.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An account a goal can follow, for the picker. [usedBy]: the active goal it already feeds. */
data class GoalAccountOption(
    val key: String,
    val label: String,
    val bankName: String,
    val accountNumber: String,
    val usedBy: GoalEntity? = null,
)

data class GoalsState(
    val summary: GoalProgress.Summary = GoalProgress.Summary(emptyList()),
    val accounts: List<GoalAccountOption> = emptyList(),
    val averageMonthlySpend: Double = 0.0,
    val isLoading: Boolean = true,
) {
    val active get() = summary.results.filter { it.goal.status == GoalStatus.ACTIVE }
    val reached get() = summary.results.filter { it.goal.status == GoalStatus.ACHIEVED }
    fun result(id: String) = summary.results.firstOrNull { it.goal.id == id }
    fun accountLabel(key: String?) = accounts.firstOrNull { it.key == key }?.label
}

/** What the "New goal" form sends. */
data class GoalDraft(
    val name: String,
    val type: String,
    val amount: Double?,
    val months: Int?,
    val deadline: Long?,
    val accountKey: String?,
    val note: String? = null,
    val emoji: String? = null,
)

class GoalsViewModel(
    private val goals: GoalRepository,
    private val txRepo: TransactionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state.asStateFlow()

    init {
        combine(goals.goals(), goals.contributions(), txRepo.getAllTransactions()) { g, c, rows ->
            val now = clock()
            val spend = GoalProgress.averageMonthlySpend(rows, now)
            val accounts = AccountGrouping.group(rows).map {
                GoalAccountOption(it.key, it.label, it.bankName, it.accountNumber, GoalRules.goalUsing(g, it.key))
            }
            GoalsState(GoalProgress.compute(g, c, rows, spend, now), accounts, spend, isLoading = false)
        }.flowOn(Dispatchers.Default).onEach { s ->
            _state.value = s
            markReached(s)
        }.launchIn(viewModelScope)
    }

    /**
     * A goal with an end (a total, by a date or not) that reaches its target
     * is marked reached, so money after that flows to the next goal. Monthly
     * goals never end.
     */
    private fun markReached(s: GoalsState) {
        val done = s.summary.results.filter {
            it.goal.status == GoalStatus.ACTIVE && it.goal.type != GoalType.RECURRING && it.reached
        }
        if (done.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { done.forEach { goals.markAchieved(it.goal, clock()) } }
    }

    /**
     * [onDone] gets the new goal's id, or null plus [AccountInUseException]
     * when the account already feeds another goal.
     */
    fun create(d: GoalDraft, onDone: (id: String?, error: AccountInUseException?) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = runCatching {
                goals.create(
                    GoalEntity(
                        name = d.name.trim(),
                        emoji = d.emoji,
                        type = d.type,
                        targetAmount = if (d.type == GoalType.OPEN_ENDED) null else d.amount,
                        targetMonths = if (d.type == GoalType.OPEN_ENDED) d.months else null,
                        deadline = if (d.type == GoalType.DATE_BOUND) d.deadline else null,
                        linkedAccountKey = d.accountKey,
                        note = d.note?.trim()?.ifBlank { null },
                        createdAt = clock(),
                    )
                )
            }
            withContext(Dispatchers.Main) {
                outcome.fold(
                    onSuccess = { onDone(it.id, null) },
                    onFailure = { e -> if (e is AccountInUseException) onDone(null, e) else throw e },
                )
            }
        }
    }

    fun logSave(goalId: String, amount: Double, note: String?, atMs: Long = clock()) {
        if (amount <= 0.0) return
        viewModelScope.launch(Dispatchers.IO) { goals.logSave(goalId, amount, note, atMs) }
    }

    fun deleteSave(id: String) = viewModelScope.launch(Dispatchers.IO) { goals.deleteSave(id) }

    /**
     * Edit keeps the kind and when the goal started; the rest comes from [d].
     * [onDone] gets null on success, or why the account can't be used.
     */
    fun edit(goal: GoalEntity, d: GoalDraft, onDone: (AccountInUseException?) -> Unit = {}) {
        val updated = goal.copy(
            name = d.name.trim(),
            emoji = d.emoji,
            targetAmount = if (goal.type == GoalType.OPEN_ENDED) null else d.amount,
            targetMonths = if (goal.type == GoalType.OPEN_ENDED) d.months else null,
            deadline = if (goal.type == GoalType.DATE_BOUND) d.deadline else null,
            linkedAccountKey = d.accountKey,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val error = try { goals.update(updated); null } catch (e: AccountInUseException) { e }
            withContext(Dispatchers.Main) { onDone(error) }
        }
    }

    /** [onDone] gets null on success, or why the account can't be used. */
    fun setAccount(goal: GoalEntity, key: String?, onDone: (AccountInUseException?) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = try { goals.update(goal.copy(linkedAccountKey = key)); null } catch (e: AccountInUseException) { e }
            withContext(Dispatchers.Main) { onDone(error) }
        }
    }

    fun archive(goal: GoalEntity) = viewModelScope.launch(Dispatchers.IO) { goals.archive(goal) }

    fun delete(goal: GoalEntity) = viewModelScope.launch(Dispatchers.IO) { goals.delete(goal.id) }
}

class GoalsViewModelFactory(
    private val goals: GoalRepository,
    private val txRepo: TransactionRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GoalsViewModel(goals, txRepo) as T
    }
}
