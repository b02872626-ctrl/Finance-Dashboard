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
                "Cloud Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Cloud sync status and completion updates"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun notifyOfflineQueued(context: Context, transactionCount: Int, rawMessageCount: Int = 0) {
        ensureChannel(context)
        val total = transactionCount + rawMessageCount
        notify(
            context = context,
            id = OFFLINE_ID,
            title = "Cloud sync pending",
            text = when {
                total <= 0 -> "Cloud data is saved locally and will sync when internet returns."
                transactionCount > 0 && rawMessageCount > 0 ->
                    "$transactionCount transactions and $rawMessageCount raw messages are saved locally and will sync when internet returns."
                transactionCount > 0 && transactionCount == 1 ->
                    "1 transaction is saved locally and will sync when internet returns."
                transactionCount > 0 ->
                    "$transactionCount transactions are saved locally and will sync when internet returns."
                rawMessageCount == 1 ->
                    "1 raw message is saved locally and will sync when internet returns."
                else ->
                    "$rawMessageCount raw messages are saved locally and will sync when internet returns."
            }
        )
    }

    fun notifySynced(context: Context, transactionCount: Int, rawMessageCount: Int = 0) {
        ensureChannel(context)
        val total = transactionCount + rawMessageCount
        notify(
            context = context,
            id = SYNCED_ID,
            title = "Cloud sync complete",
            text = when {
                total <= 0 -> "Pending cloud data was synced to Supabase."
                transactionCount > 0 && rawMessageCount > 0 ->
                    "$transactionCount transactions and $rawMessageCount raw messages were synced to Supabase."
                transactionCount > 0 && transactionCount == 1 ->
                    "1 pending transaction was synced to Supabase."
                transactionCount > 0 ->
                    "$transactionCount pending transactions were synced to Supabase."
                rawMessageCount == 1 ->
                    "1 raw message was synced to Supabase."
                else ->
                    "$rawMessageCount raw messages were synced to Supabase."
            }
        )
    }

    fun notifyCategorizeToday(context: Context, userName: String, transactionCount: Int) {
        if (transactionCount <= 0) return

        ensureChannel(context)
        val text = "Hey ${userName.ifBlank { "there" }}, you've had $transactionCount transaction${if (transactionCount == 1) "" else "s"} today, let's categorize them."
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
            title = "Categorize today's transactions",
            text = text,
            pendingIntent = pendingIntent
        )
    }

    private fun notify(
        context: Context,
        id: Int,
        title: String,
        text: String,
        pendingIntent: PendingIntent? = null
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }

    const val CATEGORY_ROUTE = "categorize_today"
}
