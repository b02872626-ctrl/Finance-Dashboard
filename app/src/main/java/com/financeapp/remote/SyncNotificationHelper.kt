package com.financeapp.remote

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.financeapp.R

object SyncNotificationHelper {
    private const val CHANNEL_ID = "cloud_sync_channel"
    private const val SYNCED_ID = 2001
    private const val OFFLINE_ID = 2002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backups and reviews",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Transactions to sort and backup updates"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun notifyOfflineQueued(context: Context, transactionCount: Int, rawMessageCount: Int = 0) {
        ensureChannel(context)
        notify(
            context = context,
            id = OFFLINE_ID,
            title = "Waiting for internet to back up",
            text = when (transactionCount) {
                0 -> "Your transactions are safe on this phone. They'll back up when you're online."
                1 -> "1 transaction is safe on this phone. It'll back up when you're online."
                else -> "$transactionCount transactions are safe on this phone. They'll back up when you're online."
            }
        )
    }

    fun notifySynced(context: Context, transactionCount: Int, rawMessageCount: Int = 0) {
        ensureChannel(context)
        notify(
            context = context,
            id = SYNCED_ID,
            title = "Backed up",
            text = when (transactionCount) {
                0 -> "Everything on this phone is in your Kash account."
                1 -> "Your new transaction is in your Kash account."
                else -> "Your $transactionCount new transactions are in your Kash account."
            }
        )
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        pendingIntent: PendingIntent? = null,
        action: String? = null
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kash)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(0xFF3D4859.toInt())
            .setContentIntent(pendingIntent)
            .apply { if (action != null && pendingIntent != null) addAction(0, action, pendingIntent) }
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    const val CATEGORY_ROUTE = "categorize_today"
}
