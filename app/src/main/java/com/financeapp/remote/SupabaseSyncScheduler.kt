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
        // Safety periodic fallback (15 min)
        val periodicRequest = androidx.work.PeriodicWorkRequestBuilder<SupabaseSyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "${UNIQUE_WORK_NAME}_periodic",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

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
