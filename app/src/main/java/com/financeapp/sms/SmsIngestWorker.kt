package com.financeapp.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.NetworkMonitor
import com.financeapp.remote.SupabaseConfigProvider
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.remote.SyncNotificationHelper

/**
 * Re-runs the SMS-inbox scan in a WorkManager job. Designed to be enqueued
 * from [SmsReceiver] when a fresh SMS arrives — `startForegroundService`
 * from a background BroadcastReceiver is disallowed on Android 12+, but
 * WorkManager handles background scheduling correctly.
 *
 * Mirrors what [SmsIngestService.onStartCommand] does (minus the
 * foreground notification, since the worker has its own task lifecycle).
 */
class SmsIngestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val repo = TransactionRepository(AppDatabase.getInstance(ctx))
            val messages = SmsReader.readFinancialSms(ctx)
            val insertedTxs = repo.ingestAllReturningInserted(messages)
            val inserted = insertedTxs.size
            val pendingTransactions = repo.countUnsyncedTransactions()
            val pendingRawMessages  = repo.countUnsyncedRawMessages()

            if (pendingTransactions + pendingRawMessages > 0) {
                SupabaseSyncScheduler.enqueue(ctx)
            }

            val settingsRepo = SettingsRepository(ctx)
            val notificationsEnabled = settingsRepo.notificationsEnabledSync()
            if (notificationsEnabled && inserted > 0) {
                val todayUncategorized = repo.countTodayUncategorizedTransactions()
                if (todayUncategorized > 0) {
                    SyncNotificationHelper.notifyCategorizeToday(
                        context = ctx,
                        userName = settingsRepo.getUserName(),
                        transactionCount = todayUncategorized
                    )
                }
                if (pendingTransactions + pendingRawMessages > 0 &&
                    !NetworkMonitor.isOnline(ctx) &&
                    SupabaseConfigProvider.fromBuildConfig() != null &&
                    settingsRepo.getSupabaseSession() != null
                ) {
                    settingsRepo.markPendingSyncNotification()
                    SyncNotificationHelper.notifyOfflineQueued(
                        context = ctx,
                        transactionCount = pendingTransactions,
                        rawMessageCount = pendingRawMessages
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            ErrorLogRepository.fromContext(applicationContext).log("SMS ingest worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sms_ingest_now"

        /**
         * Enqueue an immediate-ish background scan. Uses REPLACE so rapid
         * back-to-back SMS (e.g. a transfer + its receipt) coalesce into a
         * single scan instead of N redundant ones.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SmsIngestWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
