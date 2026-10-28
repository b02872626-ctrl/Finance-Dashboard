package com.financeapp.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.financeapp.BuildConfig
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.security.PinManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Feeds the admin dashboard (supabase/admin_health_metrics.sql):
 *  - i_installs: one anonymous row per install, sent on first launch with
 *    the anon key, before anyone signs in;
 *  - i_events: funnel / retention / error events, queued on disk and sent
 *    by the next sync pass so nothing is lost offline or before sign-in;
 *  - i_device_status: sync backlog, app version, PIN and SMS state.
 *
 * Best-effort throughout — telemetry never surfaces an error to the user.
 */
object Telemetry {

    const val REVIEW_COMPLETED       = "review_completed"
    const val SMS_PERMISSION_GRANTED = "sms_permission_granted"
    const val SCAN_COMPLETED         = "scan_completed"
    const val SCAN_FAILED            = "scan_failed"
    const val PIN_SET                = "pin_set"
    const val ERROR                  = "error"

    private const val PREFS = "kash_telemetry"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_INSTALL_SENT = "install_sent"
    private const val KEY_QUEUE = "queue"
    private const val KEY_STATUS_SENT_AT = "status_sent_at"
    private const val KEY_RAW_RESYNC_DONE = "raw_resync_v1"

    /** Oldest events are dropped past this, so a crash loop can't grow it forever. */
    private const val MAX_QUEUE = 200
    private const val STATUS_EVERY_MS = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val appVersion get() = "android-${BuildConfig.VERSION_NAME}"

    fun installId(context: Context): String = synchronized(lock) {
        val p = prefs(context)
        p.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
            p.edit().putString(KEY_INSTALL_ID, it).apply()
        }
    }

    /** FinanceApplication.onCreate. Sends the install row once. */
    fun onAppStart(context: Context) {
        val app = context.applicationContext
        if (prefs(app).getBoolean(KEY_INSTALL_SENT, false)) return
        scope.launch {
            runCatching {
                val cfg = SupabaseConfigProvider.fromBuildConfig() ?: return@launch
                SupabaseRestClient(cfg, cfg.anonKey).insertInstall(
                    installId = installId(app),
                    appVersion = appVersion,
                    device = "Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL}".take(200)
                )
                prefs(app).edit().putBoolean(KEY_INSTALL_SENT, true).apply()
            }
        }
    }

    /** Queues an event. Sent with the user's id on the next sync. */
    fun track(context: Context, name: String, props: Map<String, Any?> = emptyMap()) {
        val event = JSONObject()
            .put("name", name)
            .put("props", JSONObject(props.filterValues { it != null }))
            .put("app_version", appVersion)
            .put("occurred_at", System.currentTimeMillis())
        synchronized(lock) {
            val p = prefs(context)
            val queue = JSONArray(p.getString(KEY_QUEUE, "[]"))
            queue.put(event)
            val trimmed = if (queue.length() <= MAX_QUEUE) queue else JSONArray().apply {
                for (i in queue.length() - MAX_QUEUE until queue.length()) put(queue.get(i))
            }
            p.edit().putString(KEY_QUEUE, trimmed.toString()).apply()
        }
    }

    /**
     * Raw SMS uploaded before 1.0.9 have no parse_status / received_at.
     * Marks every raw row unsynced once so the next sync re-sends them with
     * both (the upsert merges into the existing rows).
     */
    suspend fun resyncRawOnce(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_RAW_RESYNC_DONE, false)) return
        AppDatabase.getInstance(context).rawSmsDao().markAllUnsynced()
        p.edit().putBoolean(KEY_RAW_RESYNC_DONE, true).apply()
    }

    /**
     * Sends queued events and, at most every 10 minutes (or when [force]),
     * the device status. Called by SupabaseSyncer at the end of every pass.
     */
    suspend fun flush(context: Context, lastSyncError: String?, force: Boolean = false) {
        val (cfg, session) = SupabaseSyncer(context).activeSession() ?: return
        val client = SupabaseRestClient(cfg, session.accessToken)

        val queued = synchronized(lock) { JSONArray(prefs(context).getString(KEY_QUEUE, "[]")) }
        if (queued.length() > 0) {
            runCatching {
                client.insertEvents(session.userId, queued)
                // Drop only what was sent; anything tracked meanwhile stays.
                synchronized(lock) {
                    val p = prefs(context)
                    val now = JSONArray(p.getString(KEY_QUEUE, "[]"))
                    val rest = JSONArray()
                    for (i in queued.length() until now.length()) rest.put(now.get(i))
                    p.edit().putString(KEY_QUEUE, rest.toString()).apply()
                }
            }
        }

        val p = prefs(context)
        val due = System.currentTimeMillis() - p.getLong(KEY_STATUS_SENT_AT, 0) >= STATUS_EVERY_MS
        if (!force && !due && lastSyncError == null) return
        runCatching {
            val db = AppDatabase.getInstance(context)
            val settings = SettingsRepository(context)
            client.upsertDeviceStatus(
                userId = session.userId,
                installId = installId(context),
                appVersion = appVersion,
                unsyncedTransactions = db.transactionDao().countUnsynced(),
                unsyncedRaw = db.rawSmsDao().countUnsynced(),
                oldestUnsyncedAtMs = db.transactionDao().oldestUnsyncedCreatedAt(),
                lastSyncOkAtMs = settings.lastSyncSuccessAt().takeIf { it > 0 },
                lastSyncError = lastSyncError,
                hasPin = PinManager(context).hasPin(),
                smsPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
            )
            p.edit().putLong(KEY_STATUS_SENT_AT, System.currentTimeMillis()).apply()
        }
    }

    /** Settings → Delete account: nothing queued should outlive the account. */
    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit()
                .remove(KEY_QUEUE)
                .remove(KEY_STATUS_SENT_AT)
                .apply()
        }
    }
}
