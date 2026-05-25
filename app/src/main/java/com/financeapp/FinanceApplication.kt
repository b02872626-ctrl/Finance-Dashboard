package com.financeapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.financeapp.remote.SyncNotificationHelper

class FinanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sms_sync_channel",
                "SMS Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Scanning SMS for financial transactions" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        SyncNotificationHelper.ensureChannel(this)
    }
}
