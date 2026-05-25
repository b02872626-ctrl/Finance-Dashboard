package com.financeapp.remote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository

class SupabaseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncer = SupabaseSyncer(applicationContext)
        val settingsRepo = SettingsRepository(applicationContext)
        val result = syncer.syncPendingData()

        // Schedule next check in 1 minute regardless of success/failure 
        // as long as the user is signed in and we have config.
        if (settingsRepo.getSupabaseSession() != null && SupabaseConfigProvider.fromBuildConfig() != null) {
            SupabaseSyncScheduler.scheduleInterval(applicationContext, 1)
        }

        return when (result) {
            SupabaseSyncResult.SkippedNotConfigured,
            SupabaseSyncResult.SkippedNotSignedIn,
            SupabaseSyncResult.Noop -> Result.success()

            is SupabaseSyncResult.Success -> {
                if (settingsRepo.shouldNotifyWhenPendingSyncCompletes()) {
                    if (settingsRepo.notificationsEnabledSync()) {
                        SyncNotificationHelper.notifySynced(
                            context = applicationContext,
                            transactionCount = result.syncedTransactions,
                            rawMessageCount = result.syncedRawMessages
                        )
                    }
                    settingsRepo.clearPendingSyncNotification()
                }

                val repo = TransactionRepository(AppDatabase.getInstance(applicationContext))
                if (repo.countPendingCloudSyncItems() > 0) {
                    SupabaseSyncScheduler.enqueue(applicationContext)
                }
                Result.success()
            }

            is SupabaseSyncResult.Failed -> {
                if (result.retryable) Result.retry() else Result.failure()
            }
        }
    }
}
