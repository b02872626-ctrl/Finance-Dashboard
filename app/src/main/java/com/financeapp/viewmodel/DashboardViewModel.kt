package com.financeapp.viewmodel

import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import androidx.lifecycle.*
import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import android.content.Context
import com.financeapp.sms.SmsIngestWorker
import kotlin.math.sqrt

data class AccountSummary(
    val bankName: String,
    val accountNumber: String,
    val balance: Double,
    val transactionCount: Int,
    /** When the balance was last read from an SMS (null: never). */
    val balanceAtMs: Long? = null,
    /** Spending from this account since the 1st of this month. */
    val monthSpent: Double = 0.0,
    /** Spending from this account in the whole of last month. */
    val lastMonthSpent: Double = 0.0,
    /** Card title, e.g. "CBE 1" when the user has two CBE accounts. */
    val label: String = bankName,
    /** Last 4 digits when this card is one of several accounts at the bank. */
    val accountSuffix: String? = null,
    /** The SMS greet someone else ("Hana"): probably not the user's account. */
    val otherHolder: String? = null,
    /** No SMS for 3 months: the balance is old and stays out of the total. */
    val stale: Boolean = false,
    /** The newest SMS for this account. */
    val lastActivityMs: Long? = null,
) {
    /** Counts in "Total balance": not gone quiet. */
    val inTotal: Boolean get() = !stale
}

data class WeekActivityPoint(
    val label: String,
    val amount: Double
)

enum class ChartTimeRange { MONTHS, YEARS }

/** Today's Daily Review summary, surfaced on Home as DailyReviewCard. */
data class DailyReviewSnapshot(
    val pending: Int = 0,
    val confirmed: Int = 0,
    val changed: Int = 0,
    val skipped: Int = 0,
    val latestTransactionMs: Long? = null
) {
    val total: Int get() = pending + confirmed + changed + skipped
    val done: Int   get() = confirmed + changed
    val isEmpty: Boolean   get() = total == 0
    val isDone:  Boolean   get() = total > 0 && pending == 0
    val isFresh: Boolean   get() = total > 0 && done == 0
}

data class DashboardState(
    val accountBalances: List<AccountSummary> = emptyList(),
    val monthIncome: Double  = 0.0,
    val monthExpenses: Double = 0.0,
    val avgDailySpend: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val weekActivity: List<WeekActivityPoint> = emptyList(),
    val chartData: List<MonthlyAggregate> = emptyList(),
    val chartRange: ChartTimeRange = ChartTimeRange.MONTHS,
    val dailyReview: DailyReviewSnapshot = DailyReviewSnapshot(),
    /** PENDING rows older than today — drives the "review older" Home link. */
    val olderPendingCount: Int = 0,
    val isLoading: Boolean = true
)

class DashboardViewModel(private val repo: TransactionRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    private val _chartRange = MutableStateFlow(ChartTimeRange.MONTHS)
    /** The user's name (Settings / sign-in), to tell their accounts from others'. */
    private val _userName = MutableStateFlow("")
    fun setUserName(name: String) { _userName.value = name }
    /** SMS senders switched off in "Banks Kash follows": their accounts leave Home. */
    private val _offSenders = MutableStateFlow<Set<String>>(emptySet())
    fun setOffSenders(senders: Set<String>) { _offSenders.value = senders }

    init { load() }

    fun load() {
        viewModelScope.launch {
            val monthStart = startOfMonth()

            // Today's Daily Review counts feed the entry card on Home.
            val pendingFlow   = repo.countTodayByReviewStatus(ReviewStatus.PENDING)
            val confirmedFlow = repo.countTodayByReviewStatus(ReviewStatus.CONFIRMED)
            val changedFlow   = repo.countTodayByReviewStatus(ReviewStatus.CHANGED)
            val skippedFlow   = repo.countTodayByReviewStatus(ReviewStatus.SKIPPED)
            val olderFlow     = repo.countOlderPendingReview()
            val reviewFlow    = combine(
                pendingFlow, confirmedFlow, changedFlow, skippedFlow, olderFlow
            ) { pending, confirmed, changed, skipped, older ->
                DailyReviewSnapshot(
                    pending             = pending,
                    confirmed           = confirmed,
                    changed             = changed,
                    skipped             = skipped,
                    latestTransactionMs = null   // filled in below from `all`
                ) to older
            }

            combine(
                repo.getRecentTransactions(40),
                repo.getAllTransactions(),
                _chartRange,
                reviewFlow,
                combine(_userName, _offSenders) { name, off -> name to off },
            ) { recent, all, range, reviewWithOlder, nameAndOff ->
                val (review, olderPending) = reviewWithOlder
                val (userName, offSenders) = nameAndOff
                val aggregatedData = aggregateData(all, range)
                val accountBalances = buildAccountBalances(all, userName, offSenders)
                val monthIncome  = all.filter {
                    (it.isIncome()) && it.dateTime >= monthStart
                }.sumOf { it.amount }
                val monthExpenses = all.filter {
                    it.isSpending() && it.dateTime >= monthStart
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
                    dailyReview       = review.copy(
                        latestTransactionMs = all.maxOfOrNull { it.dateTime }
                    ),
                    olderPendingCount = olderPending,
                    isLoading         = false
                )
            }.collect { _state.value = it }
        }
    }

    fun setChartRange(range: ChartTimeRange) {
        _chartRange.value = range
    }

    /** Hide one account (or show it again) everywhere in the app. */
    fun setAccountHidden(bankName: String, suffix: String, hidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repo.setAccountHidden(bankName, suffix, hidden) }
    }

    fun clearAndReimport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clearAllData()
            SmsIngestWorker.enqueue(context)  // fresh full import after a wipe
        }
    }

    /** Build one summary per detected account — normally one per bank, but a
     *  bank where the user holds several accounts gets one card each. Includes
     *  accounts with zero / unknown balance so the user sees every source
     *  they've imported. */
    private fun buildAccountBalances(
        all: List<TransactionEntity>,
        userName: String = "",
        offSenders: Set<String> = emptySet(),
    ): List<AccountSummary> {
        val owners = com.financeapp.data.accounts.AccountOwnership.ownerNames(all, userName)
        val now = System.currentTimeMillis()
        return AccountGrouping.group(all)
            .filter { showsOnHome(it.bankName, it.transactions.map { t -> t.sender }, offSenders) }
            .map { group ->
                val txns = group.transactions
                val latestWithBalance = txns
                    .filter { it.balance != null && it.balance >= 0.0 }
                    .maxByOrNull { it.dateTime }
                val latestBalance = latestWithBalance?.balance ?: 0.0

                val monthStart = startOfMonth()
                AccountSummary(
                    group.bankName, group.accountNumber, latestBalance, txns.size,
                    balanceAtMs = latestWithBalance?.dateTime,
                    monthSpent = txns.filter { it.isSpending() && it.dateTime >= monthStart }.sumOf { it.amount },
                    lastMonthSpent = txns.filter {
                        it.isSpending() && it.dateTime < monthStart && it.dateTime >= startOfLastMonth()
                    }.sumOf { it.amount },
                    label = group.label,
                    accountSuffix = group.accountSuffix,
                    otherHolder = com.financeapp.data.accounts.AccountOwnership.otherHolder(txns, owners)
                        ?.let(com.financeapp.data.accounts.AccountOwnership::displayName),
                    stale = com.financeapp.data.accounts.AccountOwnership.isStale(txns, now),
                    lastActivityMs = txns.maxOfOrNull { it.dateTime },
                )
            }
            // Active accounts first, then quiet ones.
            .sortedWith(compareBy<AccountSummary> { it.stale }.thenByDescending { it.balance })
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
            val inc = entry.value.filter { it.isIncome() }.sumOf { it.amount }
            val exp = entry.value.filter { it.isSpending() }.sumOf { it.amount }
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
                        transaction.isSpending()
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

    private fun startOfLastMonth(): Long = Calendar.getInstance().apply {
        timeInMillis = startOfMonth()
        add(Calendar.MONTH, -1)
    }.timeInMillis

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

/**
 * Whether an account gets a card (and a share of the total) on Home. Not the
 * hand-entered "Cash" account (owner, Nov 2026: cash isn't tracked as a
 * balance), and not a bank whose every SMS sender is switched off in "Banks
 * Kash follows".
 */
internal fun showsOnHome(bankName: String, senders: List<String>, offSenders: Set<String>): Boolean {
    if (bankName.trim().equals("Cash", ignoreCase = true)) return false
    return senders.isEmpty() || !senders.all { it in offSenders }
}
