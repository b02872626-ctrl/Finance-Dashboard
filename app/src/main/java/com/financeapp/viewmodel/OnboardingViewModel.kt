package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.util.KashDates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** One SMS sender found on the phone (Choose sources). */
data class SmsSource(
    val sender: String,
    val bankName: String,
    val last4: String?,
    val count: Int,
    val enabled: Boolean,
)

/** "Your money at a glance". Null fields mean no data: hide that row. */
data class OnboardingSummary(
    val transactionCount: Int,
    val avgMonthlySpend: Double?,
    val topPayee: String?,
    val avgMonthlyIncome: Double?,
    /** Last six months, oldest first; the last one is the current month. */
    val months: List<MonthBar>,
)

data class MonthBar(val label: String, val spent: Double)

/**
 * Data for the first-launch screens: the senders found in the SMS inbox
 * (their switches write the existing disabled-senders prefs) and the
 * summary numbers, all from the real transactions on this phone.
 */
class OnboardingViewModel(
    repo: TransactionRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val all = repo.getAllTransactions()
    private val disabled = settings.disabledSendersFlow()

    val sources: StateFlow<List<SmsSource>> = combine(all, disabled) { txs, off ->
        txs.groupBy { it.sender }
            .map { (sender, rows) ->
                SmsSource(
                    sender = sender,
                    bankName = rows.first().bankName,
                    last4 = rows.firstNotNullOfOrNull { it.accountNumber?.takeLast(4)?.takeIf { d -> d.any(Char::isDigit) } },
                    count = rows.size,
                    enabled = sender !in off,
                )
            }
            .sortedByDescending { it.count }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalFound: StateFlow<Int> = all.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val summary: StateFlow<OnboardingSummary> = combine(all, disabled, settings.calendarSystemFlow()) { txs, off, cal ->
        summarize(txs.filter { it.sender !in off }, cal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingSummary(0, null, null, null, emptyList()))

    fun setSourceEnabled(sender: String, enabled: Boolean) = settings.toggleSender(sender, enabled)

    companion object {
        fun summarize(txs: List<TransactionEntity>, calendarSystem: String, now: Long = System.currentTimeMillis()): OnboardingSummary {
            // Six Gregorian months, oldest first, keyed by year*12+month.
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val nowKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
            val keys = (5 downTo 0).map { nowKey - it }
            fun keyOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }
                .let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

            val inWindow = txs.filter { keyOf(it.dateTime) in keys }
            val spentByMonth = keys.associateWith { k -> inWindow.filter { keyOf(it.dateTime) == k && it.isSpending() }.sumOf { it.amount } }
            val inByMonth = keys.associateWith { k -> inWindow.filter { keyOf(it.dateTime) == k && it.isIncome() }.sumOf { it.amount } }
            // Average over the months that have any activity; leave the
            // current (partial) month out when there are earlier ones.
            val active = keys.filter { k -> inWindow.any { keyOf(it.dateTime) == k } }
            val basis = active.filter { it != nowKey }.ifEmpty { active }
            val avgSpend = basis.map { spentByMonth.getValue(it) }.average().takeIf { basis.isNotEmpty() && it > 0 }
            val avgIn = basis.map { inByMonth.getValue(it) }.average().takeIf { basis.isNotEmpty() && it > 0 }

            val top = txs.filter { it.isSpending() }
                .groupBy { it.counterparty?.trim().orEmpty() }
                .filterKeys { it.isNotBlank() }
                .maxByOrNull { (_, rows) -> rows.sumOf { it.amount } }?.key

            val months = keys.map { k ->
                val ms = Calendar.getInstance().apply {
                    clear(); set(Calendar.YEAR, k / 12); set(Calendar.MONTH, k % 12); set(Calendar.DAY_OF_MONTH, 15)
                }.timeInMillis
                MonthBar(KashDates.monthShort(ms, calendarSystem), spentByMonth.getValue(k))
            }
            return OnboardingSummary(txs.size, avgSpend, top, avgIn, if (months.any { it.spent > 0 }) months else emptyList())
        }
    }
}

class OnboardingViewModelFactory(
    private val repo: TransactionRepository,
    private val settings: SettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(repo, settings) as T
}
