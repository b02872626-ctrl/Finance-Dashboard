package com.financeapp.ui.kash

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptics, fired only in response to the user's own touch:
 *  - TAP      light confirmation (chip pick, keypad digit)
 *  - CONFIRM  a committed action (confirm a category, unlock)
 *  - REJECT   a refused action (wrong PIN)
 *  - LONG     a long-press (open "Check this one")
 */
enum class Haptic { TAP, CONFIRM, REJECT, LONG }

/** Returns a callable that fires a semantic haptic: `haptic(Haptic.CONFIRM)`. */
@Composable
fun rememberHaptic(): (Haptic) -> Unit {
    val raw = LocalHapticFeedback.current
    return { kind ->
        raw.performHapticFeedback(
            if (kind == Haptic.TAP) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
        )
    }
}
