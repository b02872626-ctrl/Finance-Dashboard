package com.financeapp.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.notifications.TransactionTriggers
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.remote.Telemetry

/**
 * Re-runs the SMS-inbox scan in a WorkManager job. Designed to be enqueued
 * from [SmsReceiver] when a fresh SMS arrives — `startForegroundService`
 * from a background BroadcastReceiver is disallowed on Android 12+, but
 * WorkManager handles background scheduling correctly.
 *
 * Also the in-app scan ([scanNow]): opening Kash, pull to refresh, SMS
 * access just granted. No notification either way.
 */
class SmsIngestWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val repo = TransactionRepository(AppDatabase.getInstance(ctx), settings = com.financeapp.data.repository.SettingsRepository(ctx))
            val messages = SmsReader.readFinancialSms(ctx)
            // First run (empty ledger) and upgrade rescans replay history:
            // no transaction-level alerts from those.
            val firstRun = repo.countTransactions() == 0
            // One bank at a time, smallest first, so Home shows a bank done
            // (with its balance) within seconds instead of every bank at the
            // very end. Each bank's SMS stay oldest first.
            val byBank = SmsBatches.byBank(messages) { SmsParserEngine.senderBankName(it.sender) }
            // Home's "Reading your SMS" is for the first full read only; the
            // quick scans after it (app opened, new SMS) stay out of sight.
            val scanPrefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val showProgress = !scanPrefs.getBoolean(KEY_HISTORY_DONE, false)
            val ingested = mutableListOf<com.financeapp.data.model.TransactionEntity>()
            try {
                if (showProgress) progress.value = ScanProgress.start(byBank)
                byBank.forEachIndexed { b, (_, batch) ->
                    ingested += repo.ingestAllReturningInserted(batch) { done ->
                        if (showProgress) progress.value = progress.value?.withBank(b, done)
                    }
                }
                scanPrefs.edit().putBoolean(KEY_HISTORY_DONE, true).apply()
            } finally {
                progress.value = null
            }
            // Moves between the user's own accounts (CBE → CBE Birr, CBE 1 →
            // CBE 2, …) are filed before alerts go out, so a transfer is never
            // announced as spending. Also catches up history on older installs.
            val ownMoves = runCatching { repo.tagOwnTransfers() }.getOrDefault(emptySet())
            val insertedTxs = ingested.map {
                if (it.id in ownMoves) it.copy(category = com.financeapp.data.model.Spending.OWN_ACCOUNTS) else it
            }
            val inserted = insertedTxs.size
            if (inserted > 0 || firstRun) {
                Telemetry.track(ctx, Telemetry.SCAN_COMPLETED, mapOf(
                    "source" to "worker", "messages" to messages.size, "inserted" to inserted, "first_run" to firstRun
                ))
            }
            val pendingTransactions = repo.countUnsyncedTransactions()
            val pendingRawMessages  = repo.countUnsyncedRawMessages()
            TransactionTriggers.onInserted(ctx, insertedTxs, batchIsHistorical = firstRun || inputData.getBoolean(KEY_RESCAN, false))

            if (pendingTransactions + pendingRawMessages > 0) {
                SupabaseSyncScheduler.enqueue(ctx)
            }

            // No notification here: scans and backups stay out of sight.
            // Transactions to review wait for the evening digest (notifications/).
            Result.success()
        } catch (e: Exception) {
            Telemetry.track(applicationContext, Telemetry.SCAN_FAILED, mapOf(
                "source" to "worker", "error" to "${e::class.java.simpleName}: ${e.message.orEmpty()}".take(300)
            ))
            ErrorLogRepository.fromContext(applicationContext).log("SMS ingest worker failed", e)
            Result.retry()
        }
    }

    /** How far the first full read is (Home's "Reading your SMS"); null when none runs. */
    data class ScanProgress(val banks: List<BankProgress>) {
        val done: Int get() = banks.sumOf { it.done }
        val total: Int get() = banks.sumOf { it.total }

        fun withBank(index: Int, done: Int): ScanProgress =
            copy(banks = banks.mapIndexed { i, b -> if (i == index) b.copy(done = done) else b })

        companion object {
            fun start(byBank: List<Pair<String, List<*>>>) =
                ScanProgress(byBank.map { (bank, batch) -> BankProgress(bank, 0, batch.size) })
        }
    }

    /** One bank's part of the read: Waiting (0 done), reading, or Done. */
    data class BankProgress(val bankName: String, val done: Int, val total: Int) {
        val finished: Boolean get() = done >= total
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sms_ingest_now"
        val progress = kotlinx.coroutines.flow.MutableStateFlow<ScanProgress?>(null)
        private const val KEY_RESCAN = "rescan"
        private const val PREFS = "kash_scan"
        private const val KEY_HISTORY_DONE = "history_done"

        /**
         * Scan from inside the app (opening it, pull to refresh, SMS access
         * just granted). Runs quietly in the background — no "Reading your
         * SMS" notification — and lets a scan already running finish.
         */
        /** After a wipe: the next read is a first read again, with Home's progress. */
        fun forgetHistory(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY_DONE).apply()
        }

        fun scanNow(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SmsIngestWorker>().build(),
            )
        }

        /**
         * A new SMS arrived (or an upgrade rescan). Queued after a scan that
         * is already running, never in its place: REPLACE cancelled the
         * first read halfway every time an SMS came in, and it started over
         * from zero (Home's count went backwards).
         */
        fun enqueue(context: Context, rescan: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<SmsIngestWorker>()
                .setInputData(workDataOf(KEY_RESCAN to rescan))
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
