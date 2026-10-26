package com.financeapp.remote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic safety-net worker: it does not sync anything itself, it just
 * re-enqueues the deduplicated "supabase_sync" chain in case that chain
 * died (e.g. app process killed). It must not run [SupabaseSyncWorker]
 * directly under its own unique work name — doing so let this 15-minute
 * timer and the 1-minute self-rescheduling chain call
 * [SupabaseSyncer.syncPendingData] concurrently, and both would refresh
 * the same stored Supabase session at once. Supabase refresh tokens are
 * single-use, so the loser of that race got a permanent
 * "refresh_token_already_used" error with no user-visible symptom other
 * than sync silently never making progress again.
 */
class SupabaseSyncTriggerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        SupabaseSyncScheduler.enqueue(applicationContext)
        return Result.success()
    }
}
