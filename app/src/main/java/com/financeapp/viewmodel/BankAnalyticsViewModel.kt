package com.financeapp.viewmodel

import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import androidx.lifecycle.*
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BankAnalyticsState(
    val bankName: String = "",
    val accountNumber: String? = null,
    val totalBalance: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val txCount: Int = 0,
    val counterparties: List<Pair<String, Double>> = emptyList(),
    val categories: Map<String, Double> = emptyMap(),
    val monthlyTrend: List<MonthlyAggregate> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true
)

class BankAnalyticsViewModel(
    private val repo: TransactionRepository,
    private val bankFilter: String
) : ViewModel() {

    private val _state = MutableStateFlow(BankAnalyticsState(bankName = bankFilter))
    val state: StateFlow<BankAnalyticsState> = _state.asStateFlow()

    init {
        val oneYearAgo = System.currentTimeMillis() - 365L * 24 * 3600 * 1000
        combine(
            repo.getAllTransactions(), // We could optimize by having repo.getTransactionsForBank but this works
            repo.getMonthlyAggregates(oneYearAgo)
        ) { all, rawMonthly ->
            val bankTxns = all.filter { it.bankName.equals(bankFilter, ignoreCase = true) || it.sender.equals(bankFilter, ignoreCase = true) }
            
            val totalBalance = bankTxns.maxByOrNull { it.dateTime }?.balance ?: 0.0
            
            val incomeTxns = bankTxns.filter { it.isIncome() }
            val expenseTxns = bankTxns.filter { 
                it.isSpending() 
            }
            
            val totalIncome = incomeTxns.sumOf { it.amount }
            val totalExpense = expenseTxns.sumOf { it.amount }
            
            val counterparties = expenseTxns
                .filter { !it.counterparty.isNullOrBlank() }
                .groupBy { it.counterparty!! }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }
                
            val categories = expenseTxns
                .groupBy { it.type }
                .mapValues { (_, txns) -> txns.sumOf { it.amount } }
                
            // Custom monthly trend built specifically for this bank since repo aggregate is global
            val fmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val monthlyTrend = bankTxns
                .filter { it.dateTime >= oneYearAgo }
                .groupBy { fmt.format(Date(it.dateTime)) }
                .entries.sortedBy { it.key }
                .map { (monthStr, txns) ->
                    val mInc = txns.filter { it.isIncome() }.sumOf { it.amount }
                    val mExp = txns.filter { 
                        it.isSpending() 
                    }.sumOf { it.amount }
                    MonthlyAggregate(monthStr, mInc, mExp)
                }

            val accountNumber = bankTxns
                .filter { !it.accountNumber.isNullOrBlank() }
                .maxByOrNull { it.dateTime }
                ?.accountNumber
            val sortedTxns = bankTxns.sortedByDescending { it.dateTime }

            BankAnalyticsState(
                bankName = bankFilter,
                accountNumber = accountNumber,
                totalBalance = totalBalance,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                txCount = bankTxns.size,
                counterparties = counterparties,
                categories = categories,
                monthlyTrend = monthlyTrend,
                transactions = sortedTxns,
                isLoading = false
            )
        }.flowOn(Dispatchers.Default).onEach { _state.value = it }.launchIn(viewModelScope)
    }
}

class BankAnalyticsViewModelFactory(private val repo: TransactionRepository, private val bank: String) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BankAnalyticsViewModel(repo, bank) as T
    }
}
