package com.financeapp.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The groups a user can switch off in Settings → Notifications. Security has none. */
enum class NotifyGroup(val key: String) {
    ALERTS("alerts"),
    REVIEW("review"),
    SUMMARIES("summaries"),
    HEALTH("health"),
    SECURITY("security"),
}

/**
 * Notification preferences and the bits of state the triggers need
 * (last app open, last review session, when SMS access was lost). Its own
 * prefs file; the master switch stays in SettingsRepository ("notifications").
 */
class NotificationPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isGroupOn(group: NotifyGroup): Boolean =
        group == NotifyGroup.SECURITY || prefs.getBoolean("group_${group.key}", true)

    fun setGroupOn(group: NotifyGroup, on: Boolean) {
        if (group != NotifyGroup.SECURITY) prefs.edit().putBoolean("group_${group.key}", on).apply()
    }

    /** User-set low-balance floor in ETB; null means "2× average daily spend". */
    var balanceFloor: Double?
        get() = prefs.getFloat(KEY_FLOOR, -1f).takeIf { it >= 0f }?.toDouble()
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_FLOOR) else putFloat(KEY_FLOOR, v.toFloat()) }.apply()

    /** Fees above this share of one transaction's amount, in percent. */
    var feePercent: Int
        get() = prefs.getInt(KEY_FEE_PCT, DEFAULT_FEE_PERCENT)
        set(v) = prefs.edit().putInt(KEY_FEE_PCT, v).apply()

    /** Fees above this total in one day, in ETB. */
    var feeDailyTotal: Int
        get() = prefs.getInt(KEY_FEE_DAY, DEFAULT_FEE_DAILY)
        set(v) = prefs.edit().putInt(KEY_FEE_DAY, v).apply()

    /** Minutes after midnight. */
    var digestMinute: Int
        get() = prefs.getInt(KEY_DIGEST, DEFAULT_DIGEST)
        set(v) = prefs.edit().putInt(KEY_DIGEST, v).apply()

    var quietStartMinute: Int
        get() = prefs.getInt(KEY_QUIET_START, DEFAULT_QUIET_START)
        set(v) = prefs.edit().putInt(KEY_QUIET_START, v).apply()

    var quietEndMinute: Int
        get() = prefs.getInt(KEY_QUIET_END, DEFAULT_QUIET_END)
        set(v) = prefs.edit().putInt(KEY_QUIET_END, v).apply()

    var quietHoursOn: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ON, true)
        set(v) = prefs.edit().putBoolean(KEY_QUIET_ON, v).apply()

    val quietHours: QuietHours
        get() = QuietHours(quietHoursOn, quietStartMinute, quietEndMinute)

    var lastAppOpenMs: Long
        get() = prefs.getLong(KEY_LAST_OPEN, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_OPEN, v).apply()

    /** Last time the user opened Review. 0 until the first time. */
    var lastReviewSessionMs: Long
        get() = prefs.getLong(KEY_LAST_REVIEW, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_REVIEW, v).apply()

    /** When READ_SMS / RECEIVE_SMS was first seen missing; 0 while granted. */
    var smsLostSinceMs: Long
        get() = prefs.getLong(KEY_SMS_LOST, 0L)
        set(v) = prefs.edit().putLong(KEY_SMS_LOST, v).apply()

    /** Ticks on every change, so Compose can re-read the values above. */
    fun changes(): Flow<Unit> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        trySend(Unit)
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS = "kash_notifications"
        private const val KEY_FLOOR = "balance_floor"
        private const val KEY_FEE_PCT = "fee_percent"
        private const val KEY_FEE_DAY = "fee_daily_total"
        private const val KEY_DIGEST = "digest_minute"
        private const val KEY_QUIET_ON = "quiet_on"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_LAST_OPEN = "last_app_open"
        private const val KEY_LAST_REVIEW = "last_review_session"
        private const val KEY_SMS_LOST = "sms_lost_since"

        const val DEFAULT_FEE_PERCENT = 5
        const val DEFAULT_FEE_DAILY = 100
        const val DEFAULT_DIGEST = 20 * 60
        const val DEFAULT_QUIET_START = 22 * 60
        const val DEFAULT_QUIET_END = 7 * 60
    }
}
