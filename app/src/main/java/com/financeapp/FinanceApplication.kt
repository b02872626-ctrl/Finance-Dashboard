package com.financeapp

import android.app.Application
import android.app.NotificationManager
import android.os.Build

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.financeapp.remote.CrashReporting.init(this)
        com.financeapp.remote.ProductAnalytics.init(this)
        // Kash never tells people it is scanning, syncing or backing up
        // (owner, Nov 2026). Remove those old channels from phones that had them.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.let { nm ->
                nm.deleteNotificationChannel("sms_sync_channel")
                nm.deleteNotificationChannel("cloud_sync_channel")
            }
        }
        com.financeapp.notifications.KashNotifier.ensureChannels(this)
        com.financeapp.notifications.NotificationScheduler.ensureScheduled(this)
        com.financeapp.notifications.UpdateChecker.ensureScheduled(this)
        com.financeapp.remote.Telemetry.onAppStart(this)
    }
}
