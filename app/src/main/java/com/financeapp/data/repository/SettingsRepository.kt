package com.financeapp.data.repository

import com.financeapp.data.model.Spending
import android.content.Context
import android.content.SharedPreferences
import com.financeapp.data.model.TransactionCategoryCatalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import com.financeapp.remote.SupabaseSession

class SettingsRepository(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)

    /** Removes the app PIN (log out / forgot PIN). The next sign-in asks for a new one. */
    fun clearPin() = com.financeapp.security.PinManager(appContext).clear()

    fun getUserName(): String = prefs.getString("user_name", "") ?: ""
    fun setUserName(name: String) = prefs.edit().putString("user_name", name).apply()

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(completed: Boolean) =
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, completed).apply()
    fun onboardingCompleteFlow(): Flow<Boolean> = prefFlow(KEY_ONBOARDING_COMPLETE, false)

    fun getIsDarkMode(): Boolean = prefs.getBoolean("dark_mode", false)
    fun isDarkMode(): Flow<Boolean> = prefFlow("dark_mode", false)
    fun setIsDarkMode(enabled: Boolean) = prefs.edit().putBoolean("dark_mode", enabled).apply()

    fun getNotifications(): Boolean = prefs.getBoolean("notifications", true)
    fun setNotifications(enabled: Boolean) = prefs.edit().putBoolean("notifications", enabled).apply()
    fun notificationsEnabledSync(): Boolean = prefs.getBoolean("notifications", true)
    fun notificationsFlow(): Flow<Boolean> = prefFlow("notifications", true)

    /**
     * Highest [BuildConfig.VERSION_CODE] we've completed an ingest scan with.
     * MainActivity compares this against the current versionCode on launch
     * and re-runs the scan if the user just installed a new build that has
     * parser improvements over the previous one. This is what fixes the
     * "I installed beta3 but my old missing CBE rows are still missing"
     * complaint — beta3's relaxed parser now catches rows that beta2's
     * parse() rejected, but it can only do that if we re-scan the inbox.
     */
    fun getLastIngestedVersion(): Int = prefs.getInt(KEY_LAST_INGESTED_VERSION, 0)
    fun setLastIngestedVersion(code: Int) =
        prefs.edit().putInt(KEY_LAST_INGESTED_VERSION, code).apply()

    /**
     * Date display preference: "GREGORIAN" (default) or "ETHIOPIAN".
     * Time is always 24-hour Gregorian for now — the Ethiopian dawn-anchored
     * 12-hour clock is a separate decision (would need explicit opt-in to
     * avoid confusing users who write timestamps in 24h Gregorian habit).
     */
    fun getCalendarSystem(): String = prefs.getString(KEY_CALENDAR_SYSTEM, "GREGORIAN") ?: "GREGORIAN"
    fun setCalendarSystem(value: String) =
        prefs.edit().putString(KEY_CALENDAR_SYSTEM, value).apply()
    fun calendarSystemFlow(): Flow<String> = prefFlow(KEY_CALENDAR_SYSTEM, "GREGORIAN")

    fun getSupabaseSession(): SupabaseSession? {
        val accessToken = prefs.getString(KEY_SUPABASE_ACCESS_TOKEN, "") ?: ""
        val refreshToken = prefs.getString(KEY_SUPABASE_REFRESH_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_SUPABASE_USER_ID, "") ?: ""
        val email = (prefs.getString(KEY_SUPABASE_EMAIL, "") ?: "").ifBlank { null }
        val expiresAtMs = prefs.getLong(KEY_SUPABASE_EXPIRES_AT_MS, 0L)

        if (accessToken.isBlank() || refreshToken.isBlank() || userId.isBlank() || expiresAtMs <= 0L) return null

        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = email,
            expiresAtMs = expiresAtMs
        )
    }

    fun setSupabaseSession(session: SupabaseSession) {
        prefs.edit()
            .putBoolean(KEY_SESSION_EXPIRED, false)
            .putString(KEY_SUPABASE_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_SUPABASE_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_SUPABASE_USER_ID, session.userId)
            .putString(KEY_SUPABASE_EMAIL, session.email ?: "")
            .putLong(KEY_SUPABASE_EXPIRES_AT_MS, session.expiresAtMs)
            .apply()
    }

    /**
     * Account deletion: drop every local preference (name, categories,
     * onboarding, …) except the session keys, which the caller clears last
     * via [clearSupabaseSession] so observers see the sign-out.
     */
    fun clearAllExceptSession() {
        val keep = setOf(
            KEY_SUPABASE_ACCESS_TOKEN,
            KEY_SUPABASE_REFRESH_TOKEN,
            KEY_SUPABASE_USER_ID,
            KEY_SUPABASE_EMAIL,
            KEY_SUPABASE_EXPIRES_AT_MS
        )
        val editor = prefs.edit()
        prefs.all.keys.filter { it !in keep }.forEach { editor.remove(it) }
        editor.commit()
    }

    // ── Sync status (shown in Settings) ────────────────────────────────────
    fun recordSyncSuccess() {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_OK_MS, System.currentTimeMillis())
            .putString(KEY_SYNC_PROBLEM, "")
            .apply()
    }
    fun recordSyncProblem(message: String) =
        prefs.edit().putString(KEY_SYNC_PROBLEM, message).apply()
    fun lastSyncSuccessAt(): Long = prefs.getLong(KEY_LAST_SYNC_OK_MS, 0L)
    fun lastSyncOkFlow(): Flow<Long> = prefFlow(KEY_LAST_SYNC_OK_MS, 0L)
    fun syncProblemFlow(): Flow<String> = prefFlow(KEY_SYNC_PROBLEM, "")

    /** Set when sync had to drop a dead session; the sign-in screen explains why. */
    fun markSessionExpired() = prefs.edit().putBoolean(KEY_SESSION_EXPIRED, true).apply()
    fun isSessionExpired(): Boolean = prefs.getBoolean(KEY_SESSION_EXPIRED, false)

    fun clearSupabaseSession() {
        prefs.edit()
            .remove(KEY_SUPABASE_ACCESS_TOKEN)
            .remove(KEY_SUPABASE_REFRESH_TOKEN)
            .remove(KEY_SUPABASE_USER_ID)
            .remove(KEY_SUPABASE_EMAIL)
            .remove(KEY_SUPABASE_EXPIRES_AT_MS)
            .apply()
    }

    fun supabaseEmailFlow(): Flow<String> = prefFlow(KEY_SUPABASE_EMAIL, "")

    fun isSupabaseSignedInFlow(): Flow<Boolean> =
        prefFlow(KEY_SUPABASE_ACCESS_TOKEN, "").map { it.isNotBlank() }

    fun getDisabledSenders(): Set<String> = 
        prefs.getStringSet(KEY_DISABLED_SENDERS, emptySet()) ?: emptySet()

    fun toggleSender(sender: String, isEnabled: Boolean) {
        val current = getDisabledSenders().toMutableSet()
        if (isEnabled) {
            current.remove(sender)
        } else {
            current.add(sender)
        }
        prefs.edit().putStringSet(KEY_DISABLED_SENDERS, current).apply()
    }

    fun disabledSendersFlow(): Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == KEY_DISABLED_SENDERS) {
                trySend(getDisabledSenders())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getDisabledSenders())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getCustomCategories(): List<String> =
        (prefs.getStringSet(KEY_CUSTOM_CATEGORIES, emptySet()) ?: emptySet())
            .map(TransactionCategoryCatalog::normalize)
            .filter { it.isNotBlank() }
            // Old hand-made self-transfer categories now live in the built-in
            // Spending.OWN_ACCOUNTS (rows migrated in AppDatabase 9→10).
            .filter { it.lowercase() !in Spending.LEGACY_OWN_ACCOUNT_NAMES }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun customCategoriesFlow(): Flow<List<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == KEY_CUSTOM_CATEGORIES) {
                trySend(getCustomCategories())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getCustomCategories())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun addCustomCategory(rawName: String): String? {
        val normalized = TransactionCategoryCatalog.normalize(rawName)
        if (normalized.isBlank()) return null
        if (normalized.lowercase() in Spending.LEGACY_OWN_ACCOUNT_NAMES) return Spending.OWN_ACCOUNTS

        val existing = getCustomCategories()
        if (TransactionCategoryCatalog.defaultCategories.any { it.equals(normalized, ignoreCase = true) } ||
            existing.any { it.equals(normalized, ignoreCase = true) }
        ) {
            return existing.firstOrNull { it.equals(normalized, ignoreCase = true) }
                ?: TransactionCategoryCatalog.defaultCategories.firstOrNull { it.equals(normalized, ignoreCase = true) }
                ?: normalized
        }

        prefs.edit()
            .putStringSet(KEY_CUSTOM_CATEGORIES, (existing + normalized).toSet())
            .apply()
        return normalized
    }

    /**
     * Recent-categories list — populated every time the user actually
     * picks a category (in Daily Review, the picker sheet, or via Add
     * Transaction). Stored as a comma-separated string so order is
     * preserved (StringSet would lose it). Capped at [RECENT_CAP] entries.
     *
     * The picker sheet's "Recent" chip row reads from here so it reflects
     * what the user *actually* uses, not a hardcoded ["Food","Transport","Transfer"].
     */
    fun getRecentCategories(): List<String> {
        val raw = prefs.getString(KEY_RECENT_CATEGORIES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(RECENT_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.lowercase() in Spending.LEGACY_OWN_ACCOUNT_NAMES) Spending.OWN_ACCOUNTS else it }
            .distinct()
            .take(RECENT_CAP)
    }

    /**
     * Push a category to the front of the recent list. Dedupes (existing
     * entry is moved to position 0, not duplicated) and caps at [RECENT_CAP].
     * Empty / blank names are silently ignored.
     */
    fun pushRecentCategory(category: String) {
        val normalized = TransactionCategoryCatalog.normalize(category)
        if (normalized.isBlank()) return
        val current = getRecentCategories().toMutableList()
        // Drop existing case-insensitive match — we'll re-add at the front.
        current.removeAll { it.equals(normalized, ignoreCase = true) }
        current.add(0, normalized)
        val capped = current.take(RECENT_CAP)
        prefs.edit()
            .putString(KEY_RECENT_CATEGORIES, capped.joinToString(RECENT_SEPARATOR.toString()))
            .apply()
    }

    fun recentCategoriesFlow(): Flow<List<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == KEY_RECENT_CATEGORIES) trySend(getRecentCategories())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getRecentCategories())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun markPendingSyncNotification() {
        prefs.edit().putBoolean(KEY_PENDING_SYNC_NOTIFICATION, true).apply()
    }

    fun clearPendingSyncNotification() {
        prefs.edit().putBoolean(KEY_PENDING_SYNC_NOTIFICATION, false).apply()
    }

    fun shouldNotifyWhenPendingSyncCompletes(): Boolean =
        prefs.getBoolean(KEY_PENDING_SYNC_NOTIFICATION, false)

    private inline fun <reified T> prefFlow(key: String, defaultValue: T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) {
                val value = when(T::class) {
                    Boolean::class -> prefs.getBoolean(key, defaultValue as Boolean) as T
                    String::class -> prefs.getString(key, defaultValue as String) as T
                    Int::class -> prefs.getInt(key, defaultValue as Int) as T
                    Long::class -> prefs.getLong(key, defaultValue as Long) as T
                    else -> defaultValue
                }
                trySend(value)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val initialValue = when(T::class) {
            Boolean::class -> prefs.getBoolean(key, defaultValue as Boolean) as T
            String::class -> prefs.getString(key, defaultValue as String) as T
            Int::class -> prefs.getInt(key, defaultValue as Int) as T
            Long::class -> prefs.getLong(key, defaultValue as Long) as T
            else -> defaultValue
        }
        trySend(initialValue)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_LAST_INGESTED_VERSION = "last_ingested_version"
        const val KEY_CALENDAR_SYSTEM = "calendar_system"
        const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"
        const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
        const val KEY_LAST_SYNC_OK_MS = "last_sync_ok_ms"
        const val KEY_SYNC_PROBLEM = "sync_problem"
        const val KEY_SESSION_EXPIRED = "session_expired"
        const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        const val KEY_SUPABASE_EMAIL = "supabase_email"
        const val KEY_SUPABASE_EXPIRES_AT_MS = "supabase_expires_at_ms"
        const val KEY_PENDING_SYNC_NOTIFICATION = "pending_sync_notification"
        const val KEY_DISABLED_SENDERS = "disabled_senders"
        const val KEY_CUSTOM_CATEGORIES = "custom_categories"
        const val KEY_RECENT_CATEGORIES = "recent_categories"
        /** Max length of the recents row in the picker (kept tight to fit on one line). */
        const val RECENT_CAP = 6
        /** Char-class-free separator so it never collides with category names. */
        const val RECENT_SEPARATOR = '' // ASCII record-separator
    }
}
