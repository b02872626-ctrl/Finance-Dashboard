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
                buildNotification("Scanning SMS history…"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, buildNotification("Scanning SMS history…"))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                val repo = TransactionRepository(AppDatabase.getInstance(applicationContext))
                val messages = SmsReader.readFinancialSms(applicationContext)
                val insertedTxs = repo.ingestAllReturningInserted(messages)
                val inserted = insertedTxs.size
                val pendingTransactions = repo.countUnsyncedTransactions()
                val pendingRawMessages = repo.countUnsyncedRawMessages()
                val totalPending = pendingTransactions + pendingRawMessages

                if (totalPending > 0) {
                    SupabaseSyncScheduler.enqueue(applicationContext)
                }

                val settingsRepo = SettingsRepository(applicationContext)
                val notificationsEnabled = settingsRepo.notificationsEnabledSync()
                if (notificationsEnabled) {
                    if (inserted > 0) {
                        val todayUncategorized = repo.countTodayUncategorizedTransactions()
                        if (todayUncategorized > 0) {
                            SyncNotificationHelper.notifyCategorizeToday(
                                context = applicationContext,
                                userName = settingsRepo.getUserName(),
                                transactionCount = todayUncategorized
                            )
                        }
                    }

                    val cloud = when {
                        SupabaseConfigProvider.fromBuildConfig() == null -> " (cloud sync not configured)"
                        settingsRepo.getSupabaseSession() == null -> " (sign in to sync)"
                        totalPending > 0 && !NetworkMonitor.isOnline(applicationContext) -> {
                            settingsRepo.markPendingSyncNotification()
                            SyncNotificationHelper.notifyOfflineQueued(
                                context = applicationContext,
                                transactionCount = pendingTransactions,
                                rawMessageCount = pendingRawMessages
                            )
                            " (no internet, sync queued)"
                        }
                        totalPending > 0 -> " (cloud sync scheduled)"
                        else -> ""
                    }
                    updateNotification("Done! Found $inserted new transactions.$cloud")
                }
            } catch (e: Exception) {
                ErrorLogRepository.fromContext(applicationContext).log("SMS ingest failed", e)
                updateNotification("Scan failed (see Settings > Errors).")
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
                CHANNEL_ID, "SMS Sync", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Finance App")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "sms_sync_channel"
        private const val NOTIF_ID   = 1001

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
