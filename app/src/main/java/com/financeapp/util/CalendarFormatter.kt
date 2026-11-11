package com.financeapp.util

import androidx.compose.runtime.compositionLocalOf

/**
 * Composition-scoped calendar preference. Read in any composable via
 * `LocalCalendarSystem.current`. AppNavigation provides the current value
 * from SettingsViewModel at the root, and every date-rendering site
 * reactively re-renders when the user flips the Settings toggle.
 * Dates are formatted by [KashDates].
 *
 * Default "GREGORIAN" — matches behavior before the toggle existed.
 */
val LocalCalendarSystem = compositionLocalOf { "GREGORIAN" }
