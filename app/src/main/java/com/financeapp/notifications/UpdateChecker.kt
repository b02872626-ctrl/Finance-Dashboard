package com.financeapp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.financeapp.BuildConfig
import com.financeapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * "Kash 1.0.x is available": testers install the APK from the download page,
 * so nothing else tells them about a new build. Reads `Web/version.json` from
 * the site twice a day (and at app start) and posts one notification per new
 * version. Tapping it opens the download page.
 *
 * Off for installs from Google Play: Play updates the app itself and does
 * not allow apps to point users at other update sources.
 */
object UpdateChecker {
    const val VERSION_URL = "https://kash-140.pages.dev/version.json"
    const val DOWNLOAD_URL = "https://kash-140.pages.dev/download"
    private const val CHANNEL = "kash_updates"
    private const val NOTIFICATION_ID = 4000
    private const val PREFS = "kash_update_check"
    private const val KEY_NOTIFIED_CODE = "notified_version_code"
    private const val PERIODIC = "kash_update_check"
    private const val NOW = "kash_update_check_now"
    private const val PLAY_INSTALLER = "com.android.vending"

    data class Latest(val versionCode: Int, val versionName: String, val url: String)

    /** Parses version.json; null when it is missing a field or malformed. */
    internal fun parse(json: String): Latest? = runCatching {
        val o = JSONObject(json)
        val code = o.getInt("versionCode")
        val name = o.getString("versionName").trim()
        val url = o.optString("url", DOWNLOAD_URL).trim()
        if (code <= 0 || name.isEmpty() || !url.startsWith("https://")) null
        else Latest(code, name, url)
    }.getOrNull()

    /** Notify only for a newer build we haven't told the user about yet. */
    internal fun shouldNotify(
        installedCode: Int,
        latest: Latest?,
        alreadyNotifiedCode: Int,
        installer: String?,
    ): Boolean =
        latest != null &&
            installer != PLAY_INSTALLER &&
            latest.versionCode > installedCode &&
            latest.versionCode > alreadyNotifiedCode

    /** App start: check now (when online) and book the twice-daily check. */
    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
        )
        wm.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(online).build(),
        )
    }

    suspend fun check(context: Context) {
        val installer = installerOf(context)
        if (installer == PLAY_INSTALLER) return
        val latest = fetch() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notified = prefs.getInt(KEY_NOTIFIED_CODE, 0)
        if (!shouldNotify(BuildConfig.VERSION_CODE, latest, notified, installer)) return
        if (post(context, latest)) prefs.edit().putInt(KEY_NOTIFIED_CODE, latest.versionCode).apply()
    }

    private suspend fun fetch(): Latest? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (conn.responseCode != 200) null
                else parse(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun post(context: Context, latest: Latest): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.notif_channel_updates),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_updates_desc) }
            )
        }
        if (!nm.areNotificationsEnabled()) return false
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(latest.url))
        val pi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.getString(R.string.notif_update_title, latest.versionName)
        val text = context.getString(R.string.notif_update_text)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kash)
            .setColor(0xFF3D4859.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        return runCatching { nm.notify(NOTIFICATION_ID, n); true }.getOrDefault(false)
    }

    private fun installerOf(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()
}

class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        UpdateChecker.check(applicationContext)
        return Result.success()
    }
}
