package com.financeapp.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.NetworkMonitor
import com.financeapp.remote.SupabaseConfigProvider
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.remote.SyncNotificationHelper
import kotlinx.coroutines.*

/**
 * Foreground service that reads all historical SMS from the inbox
 * and ingests them through the parsing + dedup pipeline.
 * Stops itself once the scan is complete.
 */
class SmsIngestService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Android 14+ requires a foregroundServiceType arg on startForeground.
        // The manifest already declares `android:foregroundServiceType="dataSync"`;
        // pass the matching constant so the OS doesn't throw
        // MissingForegroundServiceTypeException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val repo = TransactionRepository(AppDatabase.getInstance(applicationContext), settings = com.financeapp.data.repository.SettingsRepository(applicationContext))
                val messages = SmsReader.readFinancialSms(applicationContext)
                // Chunks so the notification can show how far the scan is.
                val insertedTxs = mutableListOf<com.financeapp.data.model.TransactionEntity>()
                // First run reads the whole inbox: no transaction alerts from history.
                val firstRun = repo.countTransactions() == 0
                messages.chunked(100).forEachIndexed { i, chunk ->
                    insertedTxs += repo.ingestAllReturningInserted(chunk)
                    updateProgress(((i + 1) * 100).coerceAtMost(messages.size), messages.size)
                }
                val inserted = insertedTxs.size
                com.financeapp.remote.Telemetry.track(applicationContext, com.financeapp.remote.Telemetry.SCAN_COMPLETED, mapOf(
                    "source" to "service", "messages" to messages.size, "inserted" to inserted, "first_run" to firstRun
                ))
                com.financeapp.notifications.TransactionTriggers.onInserted(applicationContext, insertedTxs, batchIsHistorical = firstRun)
                val pendingTransactions = repo.countUnsyncedTransactions()
                val pendingRawMessages = repo.countUnsyncedRawMessages()
                val totalPending = pendingTransactions + pendingRawMessages

                if (totalPending > 0) {
                    SupabaseSyncScheduler.enqueue(applicationContext)
                }

                val settingsRepo = SettingsRepository(applicationContext)
                val notificationsEnabled = settingsRepo.notificationsEnabledSync()
                // Transactions to review wait for the evening digest (notifications/).
                if (notificationsEnabled) {
                    if (SupabaseConfigProvider.fromBuildConfig() != null &&
                        settingsRepo.getSupabaseSession() != null &&
                        totalPending > 0 && !NetworkMonitor.isOnline(applicationContext)
                    ) {
                        settingsRepo.markPendingSyncNotification()
                        SyncNotificationHelper.notifyOfflineQueued(
                            context = applicationContext,
                            transactionCount = pendingTransactions,
                            rawMessageCount = pendingRawMessages
                        )
                    }
                    if (inserted > 0) {
                        val banks = insertedTxs.map { com.financeapp.ui.kash.components.kashBank(it.bankName).shortName }.distinct()
                        val from = when (banks.size) {
                            1 -> "From ${banks[0]}."
                            2 -> "From ${banks[0]} and ${banks[1]}."
                            else -> "From ${banks.dropLast(1).joinToString(", ")} and ${banks.last()}."
                        }
                        postResult(if (inserted == 1) "Found 1 new transaction" else "Found $inserted new transactions", from)
                    }
                }
            } catch (e: Exception) {
                com.financeapp.remote.Telemetry.track(applicationContext, com.financeapp.remote.Telemetry.SCAN_FAILED, mapOf(
                    "source" to "service", "error" to "${e::class.java.simpleName}: ${e.message.orEmpty()}".take(300)
                ))
                ErrorLogRepository.fromContext(applicationContext).log("SMS ingest failed", e)
                postResult("Couldn't read your SMS", "Pull down on Home to try again.")
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Reading bank SMS", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** "Reading your bank SMS…" with a progress bar once the count is known. */
    private fun buildNotification(done: Int = 0, total: Int = 0): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reading your bank SMS…")
            .setSmallIcon(com.financeapp.R.drawable.ic_stat_kash)
            .setColor(0xFF3D4859.toInt())
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .build()

    private fun updateProgress(done: Int, total: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(done, total))
    }

    /** The scan's outcome, as its own notification (the progress one goes with the service). */
    private fun postResult(title: String, text: String) {
        val open = android.app.PendingIntent.getActivity(
            this, RESULT_ID,
            Intent(this, com.financeapp.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.financeapp.R.drawable.ic_stat_kash)
            .setColor(0xFF3D4859.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(RESULT_ID, n)
    }

    companion object {
        private const val CHANNEL_ID = "sms_sync_channel"
        private const val NOTIF_ID   = 1001
        private const val RESULT_ID  = 1002

        fun start(context: Context) {
            val intent = Intent(context, SmsIngestService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
