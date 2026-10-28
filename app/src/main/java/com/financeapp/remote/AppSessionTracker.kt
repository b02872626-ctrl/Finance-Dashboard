package com.financeapp.remote

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.financeapp.BuildConfig
import com.financeapp.data.repository.ErrorLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Feeds the admin dashboard's time-spent metrics, mirroring the web app's
 * tracker (Web/app.js): one i_sessions row per foreground stretch, a
 * heartbeat every 30s, and ended_at when the app leaves the foreground.
 *
 * Everything is best-effort — a failed call never surfaces to the user, and
 * signed-out users record nothing.
 */
object AppSessionTracker {

    private const val HEARTBEAT_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Foreground (true) / background (false) events, handled strictly in
    // order by one consumer so a quick background→foreground can't end the
    // new session or skip opening it.
    private val events = Channel<Pair<Boolean, Context>>(Channel.UNLIMITED)

    // Keeps a heartbeat that's mid-request from racing the closing update.
    private val sendLock = Mutex()
    private var heartbeatJob: Job? = null
    private var sessionId: String? = null
    private var startedElapsedMs = 0L

    init {
        scope.launch {
            for ((foreground, context) in events) {
                if (foreground) start(context) else stop(context)
            }
        }
    }

    /** MainActivity.onStart. */
    fun onForeground(context: Context) {
        events.trySend(true to context.applicationContext)
    }

    /** MainActivity.onStop (not for configuration changes). */
    fun onBackground(context: Context) {
        events.trySend(false to context.applicationContext)
    }

    private suspend fun start(context: Context) {
        heartbeatJob?.cancel()
        if (sessionId == null) open(context)
        if (sessionId == null) return
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                sendLock.withLock { send(context, ended = false) }
            }
        }
    }

    private suspend fun stop(context: Context) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        sendLock.withLock {
            send(context, ended = true)
            sessionId = null
        }
    }

    private suspend fun open(context: Context) {
        runCatching {
            val (cfg, session) = SupabaseSyncer(context).activeSession() ?: return
            sessionId = SupabaseRestClient(cfg, session.accessToken).startSession(
                userId = session.userId,
                appVersion = "android-${BuildConfig.VERSION_NAME}",
                userAgent = "Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL}".take(500)
            )
            startedElapsedMs = SystemClock.elapsedRealtime()
        }.onFailure { e -> logUnlessOffline(context, "Session start failed", e) }
    }

    private suspend fun send(context: Context, ended: Boolean) {
        val id = sessionId ?: return
        runCatching {
            val (cfg, session) = SupabaseSyncer(context).activeSession() ?: return
            // Monotonic clock, so a phone clock change mid-session can't
            // produce a negative or inflated duration.
            val seconds = (SystemClock.elapsedRealtime() - startedElapsedMs) / 1000
            SupabaseRestClient(cfg, session.accessToken).updateSession(id, seconds, ended)
        }.onFailure { e -> logUnlessOffline(context, "Session heartbeat failed", e) }
    }

    // Offline is normal; logging it every 30s would bury real errors.
    private suspend fun logUnlessOffline(context: Context, summary: String, e: Throwable) {
        if (e is IOException) return
        ErrorLogRepository.fromContext(context).log(summary, e)
    }
}
