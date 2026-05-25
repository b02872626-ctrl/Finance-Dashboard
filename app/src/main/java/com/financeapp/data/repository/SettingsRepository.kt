package com.financeapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.financeapp.data.model.TransactionCategoryCatalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import com.financeapp.remote.SupabaseSession

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("finance_settings", Context.MODE_PRIVATE)

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
            .putString(KEY_SUPABASE_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_SUPABASE_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_SUPABASE_USER_ID, session.userId)
            .putString(KEY_SUPABASE_EMAIL, session.email ?: "")
            .putLong(KEY_SUPABASE_EXPIRES_AT_MS, session.expiresAtMs)
            .apply()
    }

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
            else -> defaultValue
        }
        trySend(initialValue)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"
        const val KEY_SUPABASE_REFRESH_TOKEN = "supabase_refresh_token"
        const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        const val KEY_SUPABASE_EMAIL = "supabase_email"
        const val KEY_SUPABASE_EXPIRES_AT_MS = "supabase_expires_at_ms"
        const val KEY_PENDING_SYNC_NOTIFICATION = "pending_sync_notification"
        const val KEY_DISABLED_SENDERS = "disabled_senders"
        const val KEY_CUSTOM_CATEGORIES = "custom_categories"
    }
}
