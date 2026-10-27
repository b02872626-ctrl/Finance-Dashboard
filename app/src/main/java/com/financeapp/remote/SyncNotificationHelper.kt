package com.financeapp.remote

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.financeapp.R
import com.financeapp.MainActivity

object SyncNotificationHelper {
    private const val CHANNEL_ID = "cloud_sync_channel"
    private const val SYNCED_ID = 2001
    private const val OFFLINE_ID = 2002
    private const val CATEGORIZE_ID = 2003

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

    /** "4 new transactions to sort" → opens Review (the old Categorize Today). */
    fun notifyCategorizeToday(context: Context, userName: String, transactionCount: Int) {
        if (transactionCount <= 0) return

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DESTINATION_ROUTE, CATEGORY_ROUTE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CATEGORIZE_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notify(
            context = context,
            id = CATEGORIZE_ID,
            title = if (transactionCount == 1) "1 new transaction to sort" else "$transactionCount new transactions to sort",
            text = "Takes about 30 seconds. Tap to start your review.",
            pendingIntent = pendingIntent,
            action = "Review now"
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
