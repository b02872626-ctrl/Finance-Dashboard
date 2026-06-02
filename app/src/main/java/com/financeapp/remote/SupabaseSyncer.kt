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
        val syncedRawMessages: Int,
        /** Local rows updated from cloud state (web→mobile pull). */
        val pulledTransactions: Int = 0
    ) : SupabaseSyncResult()

    data class Failed(val message: String, val retryable: Boolean = false) : SupabaseSyncResult()
}

class SupabaseSyncer(private val context: Context) {

    suspend fun syncPendingData(): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val repo = TransactionRepository(AppDatabase.getInstance(context))
        val pendingTransactions = repo.getUnsyncedTransactions()
        val pendingRawMessages = repo.getUnsyncedRawMessages()

        val didPushWork = pendingTransactions.isNotEmpty() || pendingRawMessages.isNotEmpty()

        // Always try a pull pass even when there's nothing to push — that's
        // what surfaces Web → Mobile categorizations.
        val pushResult = if (didPushWork) {
            syncData(pendingTransactions, pendingRawMessages)
        } else {
            null
        }

        if (pushResult is SupabaseSyncResult.Success) {
            repo.markTransactionsSynced(pendingTransactions.map { it.id })
            repo.markRawMessagesSynced(pendingRawMessages.map { it.localId })
        }
        if (pushResult is SupabaseSyncResult.Failed && !pushResult.retryable) {
            // Hard failure on push — skip the pull this round so we don't
            // mask a configuration / auth error.
            return@withContext pushResult
        }

        val pulled = runCatching { pullRemoteStates(repo) }
            .getOrElse { e ->
                ErrorLogRepository.fromContext(context).log("Supabase pull failed", e)
                0
            }

        return@withContext when {
            pushResult is SupabaseSyncResult.Success -> pushResult.copy(pulledTransactions = pulled)
            pushResult == null && pulled == 0       -> SupabaseSyncResult.Noop
            pushResult == null                       -> SupabaseSyncResult.Success(
                syncedTransactions = 0,
                syncedRawMessages = 0,
                pulledTransactions = pulled
            )
            else                                      -> pushResult
        }
    }

    /**
     * Reads i_transactions for the signed-in user and adopts categorizations
     * the web app (or another client) made. Skips local rows whose
     * `isSynced = 0` — those have pending local edits and would lose the
     * user's work if we overwrote them.
     */
    private suspend fun pullRemoteStates(repo: TransactionRepository): Int {
        val cfg = SupabaseConfigProvider.fromBuildConfig() ?: return 0
        val settingsRepo = SettingsRepository(context)
        var session = settingsRepo.getSupabaseSession() ?: return 0

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

        val client = SupabaseRestClient(cfg, session.accessToken)
        // Last 90 days bounds the bandwidth without missing anything fresh.
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 3600 * 1000L
        val sinceIso = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(ninetyDaysAgo))

        val remote = client.fetchTransactionStates(session.userId, sinceIso)
        if (remote.isEmpty()) return 0

        // Local rows that have nothing pending — eligible for adoption.
        val locals = repo.getSyncedTransactions()
        val localByKey: Map<Long, com.financeapp.data.model.TransactionEntity> = buildMap {
            for (tx in locals) put(SupabaseTransactionKey.from(tx), tx)
        }

        var updated = 0
        for (r in remote) {
            val local = localByKey[r.id] ?: continue
            val cloudCat = r.category?.takeIf { it.isNotBlank() }
            val cloudStatus = r.reviewStatus ?: continue
            val sameCategory = cloudCat == local.category
            val sameStatus   = cloudStatus == local.reviewStatus
            if (sameCategory && sameStatus) continue

            repo.applyRemoteState(
                id = local.id,
                category = cloudCat,
                reviewStatus = cloudStatus,
                predictedCategory = r.predictedCategory,
                categoryConfidence = r.categoryConfidence,
                merchantKey = r.merchantKey
            )
            updated++
        }
        return updated
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
