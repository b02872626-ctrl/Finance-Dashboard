package com.financeapp.ui.kash

/**
 * Settings → Appearance. Stored by name in SettingsRepository; SYSTEM
 * follows the phone's dark setting ("Match phone"), which is also what a
 * fresh install gets.
 */
enum class KashThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("Match phone");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        val DEFAULT = SYSTEM

        /** A stored name, or [DEFAULT] for anything else (a fresh install, an old build's value). */
        fun fromStored(value: String?): KashThemeMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
