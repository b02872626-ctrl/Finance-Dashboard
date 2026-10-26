package com.financeapp.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SupabaseSyncScheduler {
    private const val UNIQUE_WORK_NAME = "supabase_sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleInterval(context: Context, delayMin: Long) {
        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun bootstrap(context: Context) {
        // Safety periodic fallback (15 min). Uses SupabaseSyncTriggerWorker,
        // NOT SupabaseSyncWorker directly — the trigger worker only calls
        // enqueue() on the deduplicated "supabase_sync" chain, so this timer
        // can never run syncPendingData() concurrently with the 1-minute
        // chain and race on refreshing the same session (see
        // SupabaseSyncTriggerWorker's kdoc for why that mattered).
        val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<SupabaseSyncTriggerWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()

        // Name bumped to "_v2": devices updating from an older build already
        // have a periodic entry under "_periodic" pointing at the old
        // (buggy) worker persisted in WorkManager's own database — KEEP
        // would leave that stale entry running forever since it never
        // matches this new definition. A fresh name forces every device
        // onto the corrected worker on next launch.
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "${UNIQUE_WORK_NAME}_periodic_v2",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork("${UNIQUE_WORK_NAME}_periodic")

        // Starting the 1-minute chain immediately
        enqueue(context)
    }

    suspend fun enqueueIfPending(context: Context) {
        val hasPending = withContext(Dispatchers.IO) {
            TransactionRepository(AppDatabase.getInstance(context.applicationContext))
                .countPendingCloudSyncItems() > 0
        }
        if (hasPending) enqueue(context)
    }
}
