package com.financeapp.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 4-digit app PIN. Only a salted PBKDF2 hash is stored — never the digits.
 *
 * Lives in its own prefs file so it is NOT touched by
 * SettingsRepository.clearAllExceptSession() ordering; account deletion and
 * log out call [clear] explicitly. The file is covered by the app's
 * backup exclusions (allowBackup=false + data_extraction_rules).
 *
 * Lockout: after [MAX_ATTEMPTS] wrong PINs the next attempt is blocked for
 * [LOCKOUT_MS]; the counter resets on success or when the lockout ends.
 */
class PinManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed class Result {
        data object Ok : Result()
        data class Wrong(val attemptsLeft: Int) : Result()
        data class LockedOut(val untilMs: Long) : Result()
    }

    fun hasPin(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be 4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash(pin, salt)))
            .remove(KEY_FAILED)
            .remove(KEY_LOCKED_UNTIL)
            .commit()
        com.financeapp.remote.Telemetry.track(context, com.financeapp.remote.Telemetry.PIN_SET)
    }

    /** Remaining lockout end time, or 0 when not locked out. */
    fun lockedUntil(now: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return if (until > now) until else 0L
    }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Result {
        lockedUntil(now).takeIf { it > 0 }?.let { return Result.LockedOut(it) }
        // Lockout expired: start a fresh round of attempts.
        if (prefs.getLong(KEY_LOCKED_UNTIL, 0L) in 1..now) {
            prefs.edit().remove(KEY_LOCKED_UNTIL).putInt(KEY_FAILED, 0).commit()
        }
        val salt = prefs.getString(KEY_SALT, null)?.let(::unb64) ?: return Result.Wrong(0)
        val stored = prefs.getString(KEY_HASH, null)?.let(::unb64) ?: return Result.Wrong(0)

        if (MessageDigest.isEqual(stored, hash(pin, salt))) {
            prefs.edit().remove(KEY_FAILED).remove(KEY_LOCKED_UNTIL).commit()
            return Result.Ok
        }
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        return if (failed >= MAX_ATTEMPTS) {
            val until = now + LOCKOUT_MS
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKED_UNTIL, until).commit()
            Result.LockedOut(until)
        } else {
            prefs.edit().putInt(KEY_FAILED, failed).commit()
            Result.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    /** Forgot PIN / log out / account deletion. */
    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS = "app_pin"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FAILED = "pin_failed"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"

        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        /** Re-lock when the app has been in the background longer than this. */
        const val RELOCK_AFTER_MS = 30_000L
        private const val ITERATIONS = 60_000

        fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

        /** PBKDF2-HMAC-SHA256, 256-bit output. Pure function — unit-tested. */
        fun hash(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
        private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    }
}
