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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
 * The last result is kept ([state]) so Home can show an update card and
 * Settings → Updates can show the version, the notes and "Check now". The
 * card works even when notifications are off or never allowed.
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
    private const val KEY_LATEST = "latest_json"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val KEY_FAILED = "last_check_failed"
    private const val KEY_DISMISSED_CODE = "dismissed_version_code"

    data class Latest(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        /** Release notes for testers (`notes` in version.json), newest build only. */
        val notes: List<String> = emptyList(),
    )

    /** What the app knows about updates; drives the Home card and Settings → Updates. */
    data class State(
        val installedCode: Int = BuildConfig.VERSION_CODE,
        val installedName: String = BuildConfig.VERSION_NAME,
        val latest: Latest? = null,
        /** Epoch millis of the last successful check; null before the first. */
        val checkedAt: Long? = null,
        /** The last check couldn't reach the site (offline, timeout). */
        val lastCheckFailed: Boolean = false,
        /** Installed from Google Play: Play handles updates, Kash stays quiet. */
        val fromPlay: Boolean = false,
        /** The user tapped "Later" on the card for this version. */
        val dismissedCode: Int = 0,
        /** A "Check now" is running. */
        val checking: Boolean = false,
    ) {
        val updateAvailable: Boolean get() = !fromPlay && latest != null && latest.versionCode > installedCode
        /** The newer build for the Home card, or null when there's nothing to show. */
        val cardVersion: Latest? get() = latest?.takeIf { updateAvailable && it.versionCode > dismissedCode }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Parses version.json; null when it is missing a field or malformed. */
    internal fun parse(json: String): Latest? = runCatching {
        val o = JSONObject(json)
        val code = o.getInt("versionCode")
        val name = o.getString("versionName").trim()
        val url = o.optString("url", DOWNLOAD_URL).trim()
        val notes = o.optJSONArray("notes")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf(String::isNotEmpty) }
        }.orEmpty()
        if (code <= 0 || name.isEmpty() || !url.startsWith("https://")) null
        else Latest(code, name, url, notes)
    }.getOrNull()

    private fun Latest.toJson(): String = JSONObject()
        .put("versionCode", versionCode).put("versionName", versionName).put("url", url)
        .put("notes", JSONArray(notes)).toString()

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
        load(context)
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

    /** Reads the saved result so the card shows right away, before any network. */
    fun load(context: Context) {
        val p = prefs(context)
        _state.value = State(
            latest = p.getString(KEY_LATEST, null)?.let(::parse),
            checkedAt = p.getLong(KEY_CHECKED_AT, 0L).takeIf { it > 0 },
            lastCheckFailed = p.getBoolean(KEY_FAILED, false),
            fromPlay = installerOf(context) == PLAY_INSTALLER,
            dismissedCode = p.getInt(KEY_DISMISSED_CODE, 0),
        )
    }

    /** Background check (start of the app and twice a day): saves, then notifies once per version. */
    suspend fun check(context: Context) {
        val latest = refresh(context) ?: return
        val installer = installerOf(context)
        val p = prefs(context)
        val notified = p.getInt(KEY_NOTIFIED_CODE, 0)
        if (!shouldNotify(BuildConfig.VERSION_CODE, latest, notified, installer)) return
        if (post(context, latest)) p.edit().putInt(KEY_NOTIFIED_CODE, latest.versionCode).apply()
    }

    /** Settings → Updates "Check now". Never posts a notification. */
    suspend fun checkNow(context: Context) {
        _state.value = _state.value.copy(checking = true)
        refresh(context)
        _state.value = _state.value.copy(checking = false)
    }

    /** "Later" on the Home card: hide it until a newer build than this one. */
    fun dismiss(context: Context, versionCode: Int) {
        prefs(context).edit().putInt(KEY_DISMISSED_CODE, versionCode).apply()
        _state.value = _state.value.copy(dismissedCode = versionCode)
    }

    /** Fetches version.json and saves the outcome; returns the latest build or null. */
    private suspend fun refresh(context: Context): Latest? {
        if (installerOf(context) == PLAY_INSTALLER) {
            _state.value = _state.value.copy(fromPlay = true)
            return null
        }
        val latest = fetch()
        val p = prefs(context)
        if (latest == null) {
            p.edit().putBoolean(KEY_FAILED, true).apply()
            _state.value = _state.value.copy(lastCheckFailed = true)
            return null
        }
        val now = System.currentTimeMillis()
        p.edit().putString(KEY_LATEST, latest.toJson()).putLong(KEY_CHECKED_AT, now).putBoolean(KEY_FAILED, false).apply()
        _state.value = _state.value.copy(latest = latest, checkedAt = now, lastCheckFailed = false)
        return latest
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        val text = latest.notes.firstOrNull()?.let { "$it. " + context.getString(R.string.notif_update_text) }
            ?: context.getString(R.string.notif_update_text)
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
