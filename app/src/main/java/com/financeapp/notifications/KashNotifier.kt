package com.financeapp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.financeapp.MainActivity
import com.financeapp.R
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.SentNotificationEntity
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.util.KashDates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** One thing Kash wants to tell the user, before the rules decide how. */
data class NotifyCandidate(
    val trigger: String,
    val tier: Int,
    val group: NotifyGroup,
    /** `trigger:subject`, e.g. `income:4812` or `weekly:2026-10-25`. */
    val dedupKey: String,
    val title: String,
    val text: String,
    val route: String? = null,
)

/** A notification shown inside Kash instead of the shade, because Kash is open. */
data class InAppNotice(val title: String, val text: String, val route: String?)

/** Whether a Kash screen is visible. MainActivity sets it in onStart / onStop. */
object AppForeground {
    @Volatile var isForeground: Boolean = false
}

/**
 * The one door every Kash notification goes through: group switches,
 * dedup (sent_notifications), quiet hours, caps, in-app suppression, then
 * the system shade. [NotificationRules] holds the decisions.
 */
object KashNotifier {
    private const val CH_ALERTS = "kash_alerts"
    private const val CH_DIGEST = "kash_digest"
    private const val CH_SUMMARIES = "kash_summaries"
    private const val CH_HEALTH = "kash_health"
    private const val CH_SECURITY = "kash_security"
    private const val DIGEST_ID = 3000
    private const val ID_BASE = 10_000
    private const val WEEK = 7 * 24 * 60 * 60 * 1000L

    private val inApp = MutableSharedFlow<InAppNotice>(extraBufferCapacity = 8)
    val inAppNotices: SharedFlow<InAppNotice> = inApp

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: Int, desc: Int, importance: Int) =
            NotificationChannel(id, context.getString(name), importance).apply { description = context.getString(desc) }
        nm.createNotificationChannels(listOf(
            ch(CH_ALERTS, R.string.notif_channel_alerts, R.string.notif_channel_alerts_desc, NotificationManager.IMPORTANCE_HIGH),
            ch(CH_DIGEST, R.string.notif_channel_digest, R.string.notif_channel_digest_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_SUMMARIES, R.string.notif_channel_summaries, R.string.notif_channel_summaries_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_HEALTH, R.string.notif_channel_health, R.string.notif_channel_health_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_SECURITY, R.string.notif_channel_security, R.string.notif_channel_security_desc, NotificationManager.IMPORTANCE_HIGH),
        ))
    }

    /** Runs [c] through the rules. Returns true when it was shown or queued. */
    suspend fun submit(context: Context, c: NotifyCandidate, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = NotificationPrefs(context)
        if (c.group != NotifyGroup.SECURITY &&
            (!SettingsRepository(context).notificationsEnabledSync() || !prefs.isGroupOn(c.group))
        ) return false

        val dao = AppDatabase.getInstance(context).sentNotificationDao()
        val id = dao.insert(
            SentNotificationEntity(
                dedupKey = c.dedupKey, triggerType = c.trigger, tier = c.tier,
                title = c.title, text = c.text, route = c.route, createdAt = nowMs,
            )
        )
        if (id == -1L) return false

        val delivery = NotificationRules.deliver(
            tier = c.tier,
            trigger = c.trigger,
            foreground = AppForeground.isForeground,
            quiet = NotificationRules.minuteOfDay(nowMs) in prefs.quietHours,
            tier1ShownToday = dao.countShownSince(Tier.IMMEDIATE, KashDates.startOfDay(nowMs), Trigger.SECURITY_LOCKOUT),
            tier3ShownThisWeek = dao.countShownSince(Tier.SCHEDULED, nowMs - WEEK),
        )
        when (delivery) {
            Delivery.SYSTEM -> {
                post(context, ID_BASE + (id % 50_000).toInt(), channelFor(c), c.title, c.text, c.route)
                dao.markShown(id, nowMs, "SYSTEM")
            }
            Delivery.IN_APP -> {
                inApp.tryEmit(InAppNotice(c.title, c.text, c.route))
                dao.markShown(id, nowMs, "IN_APP")
            }
            Delivery.QUEUE -> Unit // DigestWorker picks it up.
        }
        return true
    }

    /**
     * The evening digest: every queued item in one card, at most once a day.
     * Review items lead; the first item's title is the headline.
     */
    suspend fun sendDigest(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val dao = AppDatabase.getInstance(context).sentNotificationDao()
        val items = dao.queued().sortedWith(compareBy({ it.triggerType != Trigger.REVIEW_READY }, { it.tier }, { it.createdAt }))
        if (items.isEmpty()) return false
        val day = dayKey(nowMs)
        val digestKey = "${Trigger.DIGEST}:$day"
        val head = items.first()
        val route = head.route ?: items.firstNotNullOfOrNull { it.route }
        val title = head.title
        val text = if (items.size == 1) head.text
        else context.resources.getQuantityString(R.plurals.notif_digest_summary, items.size, items.size)

        // The digest's own row is what holds it to once a day.
        val id = dao.insert(
            SentNotificationEntity(
                dedupKey = digestKey, triggerType = Trigger.DIGEST, tier = Tier.DIGEST,
                title = title, text = text, route = route, createdAt = nowMs,
            )
        )
        if (id == -1L) return false

        if (AppForeground.isForeground) {
            inApp.tryEmit(InAppNotice(title, text, route))
            dao.markShown(id, nowMs, "IN_APP")
        } else {
            val lines = if (items.size == 1) emptyList() else items.map { it.title }
            post(context, DIGEST_ID, CH_DIGEST, title, text, route, lines)
            dao.markShown(id, nowMs, "SYSTEM")
        }
        dao.markDigested(items.map { it.id }, nowMs, digestKey)
        dao.prune(nowMs - 120L * 24 * 60 * 60 * 1000)
        return true
    }

    private fun channelFor(c: NotifyCandidate): String = when (c.group) {
        NotifyGroup.SECURITY -> CH_SECURITY
        NotifyGroup.ALERTS -> CH_ALERTS
        NotifyGroup.REVIEW -> CH_DIGEST
        NotifyGroup.SUMMARIES -> CH_SUMMARIES
        NotifyGroup.HEALTH -> CH_HEALTH
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        route: String?,
        lines: List<String> = emptyList(),
    ) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(MainActivity.EXTRA_DESTINATION_ROUTE, route)
        }
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = if (lines.isEmpty()) NotificationCompat.BigTextStyle().bigText(text)
        else NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine); s.setSummaryText(text) }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kash)
            .setColor(0xFF3D4859.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(if (channel == CH_ALERTS || channel == CH_SECURITY) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(id, n) }
    }

    /** `2026-10-27`, local time. */
    fun dayKey(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))
}
