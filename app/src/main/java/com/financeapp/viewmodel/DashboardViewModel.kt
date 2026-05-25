package com.financeapp.viewmodel

import androidx.lifecycle.*
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import android.content.Context
import com.financeapp.sms.SmsIngestService
import kotlin.math.sqrt

data class AccountSummary(
    val bankName: String,
    val accountNumber: String,
    val balance: Double,
    val transactionCount: Int
)

data class WeekActivityPoint(
    val label: String,
    val amount: Double
)

enum class ChartTimeRange { MONTHS, YEARS }

data class DashboardState(
    val accountBalances: List<AccountSummary> = emptyList(),
    val monthIncome: Double  = 0.0,
    val monthExpenses: Double = 0.0,
    val avgDailySpend: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val weekActivity: List<WeekActivityPoint> = emptyList(),
    val chartData: List<MonthlyAggregate> = emptyList(),
    val chartRange: ChartTimeRange = ChartTimeRange.MONTHS,
    val isLoading: Boolean = true
)

class DashboardViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    private val _chartRange = MutableStateFlow(ChartTimeRange.MONTHS)

    init { load() }

    fun load() {
        viewModelScope.launch {
            val monthStart = startOfMonth()

            combine(
                repo.getRecentTransactions(40),
                repo.getAllTransactions(),
                _chartRange
            ) { recent, all, range ->
                val aggregatedData = aggregateData(all, range)
                val accountBalances = buildAccountBalances(all)
                val monthIncome  = all.filter {
                    (it.type == TransactionType.CREDIT.name) && it.dateTime >= monthStart
                }.sumOf { it.amount }
                val monthExpenses = all.filter {
                    it.type in listOf(
                        TransactionType.DEBIT.name,
                        TransactionType.TRANSFER_OUT.name,
                        TransactionType.PAYMENT.name
                    ) && it.dateTime >= monthStart
                }.sumOf { it.amount }

                val cal = Calendar.getInstance()
                val daysInMonth = cal.get(Calendar.DAY_OF_MONTH)
                val avgDailySpend = if (daysInMonth > 0) monthExpenses / daysInMonth else 0.0

                DashboardState(
                    accountBalances   = accountBalances,
                    monthIncome       = monthIncome,
                    monthExpenses     = monthExpenses,
                    avgDailySpend     = avgDailySpend,
                    recentTransactions= recent,
                    weekActivity      = buildWeekActivity(all),
                    chartData         = aggregatedData,
                    chartRange        = range,
                    isLoading         = false
                )
            }.collect { _state.value = it }
        }
    }

    fun setChartRange(range: ChartTimeRange) {
        _chartRange.value = range
    }

    fun clearAndReimport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clearAllData()
            SmsIngestService.start(context)
        }
    }

    /** Build one summary per detected bank. Includes accounts with zero / unknown
     *  balance so the user sees every source they've imported. */
    private fun buildAccountBalances(all: List<TransactionEntity>): List<AccountSummary> {
        return all.groupBy { it.bankName.ifBlank { it.sender } }
            .map { (bankName, txns) ->
                val latestWithBalance = txns
                    .filter { it.balance != null && it.balance >= 0.0 }
                    .maxByOrNull { it.dateTime }
                val latestBalance = latestWithBalance?.balance ?: 0.0

                val latestWithAccount = txns
                    .filter { !it.accountNumber.isNullOrBlank() }
                    .maxByOrNull { it.dateTime }
                val account = latestWithAccount?.accountNumber ?: ""

                AccountSummary(bankName, account, latestBalance, txns.size)
            }
            .sortedByDescending { it.balance }
    }

    private fun aggregateData(all: List<TransactionEntity>, range: ChartTimeRange): List<MonthlyAggregate> {
        if (all.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()

        val cutoff = when (range) {
            ChartTimeRange.MONTHS -> now - 180L * 24 * 3600 * 1000L
            ChartTimeRange.YEARS  -> 0L
        }

        val filtered = all.filter { it.dateTime >= cutoff }

        val dfKey = when (range) {
            ChartTimeRange.MONTHS -> java.text.SimpleDateFormat("yyyyMM", java.util.Locale.US)
            ChartTimeRange.YEARS  -> java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
        }
        val dfLbl = when (range) {
            ChartTimeRange.MONTHS -> java.text.SimpleDateFormat("MMM", java.util.Locale.US)
            ChartTimeRange.YEARS  -> java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
        }

        val grouped = filtered.groupBy { dfKey.format(java.util.Date(it.dateTime)) }.entries.sortedBy { it.key }
        
        return grouped.map { entry ->
            val lbl = dfLbl.format(java.util.Date(entry.value.first().dateTime))
            val inc = entry.value.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
            val exp = entry.value.filter { it.type in listOf(TransactionType.DEBIT.name, TransactionType.TRANSFER_OUT.name, TransactionType.PAYMENT.name) }.sumOf { it.amount }
            MonthlyAggregate(lbl, inc, exp)
        }
    }

    private fun buildWeekActivity(all: List<TransactionEntity>): List<WeekActivityPoint> {
        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }

        return (0..6).map { dayOffset ->
            val dayStart = (startOfWeek.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val startMs = dayStart.timeInMillis
            val endMs = startMs + 86_400_000L - 1L
            val amount = all
                .asSequence()
                .filter { transaction ->
                    transaction.dateTime in startMs..endMs &&
                        transaction.type in listOf(
                            TransactionType.DEBIT.name,
                            TransactionType.TRANSFER_OUT.name,
                            TransactionType.PAYMENT.name
                        )
                }
                .sumOf { it.amount }

            WeekActivityPoint(
                label = dayLabel(dayStart),
                amount = amount
            )
        }
    }

    private fun dayLabel(calendar: Calendar): String =
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "T"
            Calendar.FRIDAY -> "F"
            else -> "S"
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
}

class DashboardViewModelFactory(private val repo: TransactionRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(repo) as T
    }
}
