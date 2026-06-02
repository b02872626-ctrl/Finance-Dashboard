package com.financeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import androidx.lifecycle.viewModelScope
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.SmsParserEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val txRepo: TransactionRepository
) : ViewModel() {
    private val _userName = MutableStateFlow(repo.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    val supabaseEmail: StateFlow<String> = repo.supabaseEmailFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            repo.getSupabaseSession()?.email.orEmpty()
        )

    val disabledSenders: StateFlow<Set<String>> = repo.disabledSendersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.getDisabledSenders())

    /** "GREGORIAN" or "ETHIOPIAN" — drives date rendering across the UI. */
    val calendarSystem: StateFlow<String> = repo.calendarSystemFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.getCalendarSystem())

    fun toggleCalendarSystem() {
        val current = repo.getCalendarSystem()
        repo.setCalendarSystem(if (current == "ETHIOPIAN") "GREGORIAN" else "ETHIOPIAN")
    }

    val categories: StateFlow<List<String>> = repo.customCategoriesFlow()
        .map(TransactionCategoryCatalog::allCategories)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TransactionCategoryCatalog.allCategories(repo.getCustomCategories())
        )

    private val _allSenders = MutableStateFlow<List<String>>(emptyList())
    val financialSenders: StateFlow<List<String>> = _allSenders.map { list ->
        list.filter { SmsParserEngine.isFinancialSms(it) }.distinctBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _allSenders.value = txRepo.getAllSenders()
        }
    }

    fun updateUserName(name: String) {
        repo.setUserName(name)
        _userName.value = name
    }

    fun toggleSource(sender: String, isEnabled: Boolean) {
        repo.toggleSender(sender, isEnabled)
    }

    fun addCustomCategory(name: String): String? = repo.addCustomCategory(name)

    fun signOut() {
        repo.clearPendingSyncNotification()
        repo.clearSupabaseSession()
    }
}

class SettingsViewModelFactory(
    private val repo: SettingsRepository,
    private val txRepo: TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(repo, txRepo) as T
    }
}
