package com.financeapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeapp.data.repository.ErrorLogRepository
import java.util.concurrent.TimeUnit

/**
 * Once a day at the digest time: the Tier 2 checks, the Sunday summary,
 * the month-end summary, the backlog and re-engagement nudges, then one
 * digest. Each run books the next.
 */
class DigestWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        try {
            ScheduledTriggers.atDigestTime(applicationContext)
        } catch (e: Exception) {
            ErrorLogRepository.fromContext(applicationContext).log("Digest failed", e)
        }
        NotificationScheduler.scheduleDigest(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/** A few times a day: is SMS access still on? */
class PermissionHealthWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            ScheduledTriggers.checkSmsAccess(applicationContext)
            Result.success()
        } catch (e: Exception) {
            ErrorLogRepository.fromContext(applicationContext).log("SMS health check failed", e)
            Result.retry()
        }
    }
}

object NotificationScheduler {
    private const val DIGEST = "kash_digest"
    private const val HEALTH = "kash_sms_health"

    /** App start: book both jobs, keeping any already booked. */
    fun ensureScheduled(context: Context) {
        scheduleDigest(context, ExistingWorkPolicy.KEEP)
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            HEALTH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PermissionHealthWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    /** Book the next digest. REPLACE after the user changes the time or quiet hours. */
    fun scheduleDigest(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val prefs = NotificationPrefs(context)
        val now = System.currentTimeMillis()
        val at = NotificationRules.nextAt(now, NotificationRules.digestMinute(prefs.digestMinute, prefs.quietHours))
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay(at - now, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(DIGEST, policy, request)
    }
}
