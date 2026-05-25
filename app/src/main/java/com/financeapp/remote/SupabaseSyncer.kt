package com.financeapp.remote

import android.content.Context
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class SupabaseSyncResult {
    data object SkippedNotConfigured : SupabaseSyncResult()
    data object SkippedNotSignedIn : SupabaseSyncResult()
    data object Noop : SupabaseSyncResult()
    data class Success(
        val syncedTransactions: Int,
        val syncedRawMessages: Int
    ) : SupabaseSyncResult()

    data class Failed(val message: String, val retryable: Boolean = false) : SupabaseSyncResult()
}

class SupabaseSyncer(private val context: Context) {

    suspend fun syncPendingData(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val repo = TransactionRepository(AppDatabase.getInstance(context))
        val pendingTransactions = repo.getUnsyncedTransactions()
        val pendingRawMessages = repo.getUnsyncedRawMessages()

        if (pendingTransactions.isEmpty() && pendingRawMessages.isEmpty()) {
            return@withContext SupabaseSyncResult.Noop
        }

        val result = syncData(pendingTransactions, pendingRawMessages)
        if (result is SupabaseSyncResult.Success) {
            repo.markTransactionsSynced(pendingTransactions.map { it.id })
            repo.markRawMessagesSynced(pendingRawMessages.map { it.localId })
        }
        result
    }

    suspend fun syncTransactions(transactions: List<TransactionEntity>): SupabaseSyncResult =
        syncData(transactions = transactions, rawMessages = emptyList())

    private suspend fun syncData(
        transactions: List<TransactionEntity>,
        rawMessages: List<RawSmsSyncPayload>
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (transactions.isEmpty() && rawMessages.isEmpty()) return@withContext SupabaseSyncResult.Noop

        val cfg = SupabaseConfigProvider.fromBuildConfig()
            ?: return@withContext SupabaseSyncResult.SkippedNotConfigured

        try {
            val settingsRepo = SettingsRepository(context)
            var session = settingsRepo.getSupabaseSession()
                ?: return@withContext SupabaseSyncResult.SkippedNotSignedIn

            if (session.isExpired()) {
                val refreshed = SupabaseAuthClient(cfg).refresh(session.refreshToken)
                session = SupabaseSession(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                    userId = refreshed.userId,
                    email = refreshed.email,
                    expiresAtMs = System.currentTimeMillis() + refreshed.expiresInSeconds * 1000L
                )
                settingsRepo.setSupabaseSession(session)
            }

            val userId = session.userId
            val client = SupabaseRestClient(cfg, session.accessToken)

            client.upsertUser(userId)
            client.upsertBanks(
                (transactions.map { it.bankName } + rawMessages.map { it.bankName })
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )

            val syncedRawMessages = client.upsertRawMessages(userId, rawMessages)
            val syncedTransactions = client.upsertTransactions(userId, transactions)

            SupabaseSyncResult.Success(
                syncedTransactions = syncedTransactions,
                syncedRawMessages = syncedRawMessages
            )
        } catch (e: Exception) {
            ErrorLogRepository.fromContext(context).log("Supabase sync failed", e)
            SupabaseSyncResult.Failed(
                message = e.message ?: "Unknown error",
                retryable = e is UnknownHostException ||
                    e is SocketTimeoutException ||
                    e is ConnectException ||
                    e is SSLException
            )
        }
    }
}
