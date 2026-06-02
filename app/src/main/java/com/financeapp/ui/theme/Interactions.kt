package com.financeapp.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Tactile interaction primitives. These exist to make every clickable
 * surface feel like a physical control without re-introducing the kind of
 * decorative animations testers found annoying (entry nudges, chart
 * progressive reveals, screen-transition slides — all removed in beta11).
 *
 * Design rule (set by the user):
 *   - If an animation runs WITHOUT user input → decoration → don't use.
 *   - If an animation runs BECAUSE the user just moved their finger →
 *     feedback → use it, it confirms touch was received.
 *
 * All primitives in this file are response-to-input only.
 */

/**
 * Scale-down-on-press feedback. 1.0 → [pressedScale] when the press
 * begins, springs back when released. Pair with a `clickable(...)` that
 * shares the same [interactionSource]:
 *
 * ```
 * val ix = remember { MutableInteractionSource() }
 * Box(
 *     Modifier
 *         .clickable(interactionSource = ix, indication = null) { onClick() }
 *         .pressScale(ix)
 * )
 * ```
 *
 * Uses graphicsLayer (GPU transform, no recomposition) so the scale is
 * cheap even on long lists.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Convenience: create an [InteractionSource] for a button-shaped element.
 * Just `remember { MutableInteractionSource() }` but named to flag intent.
 */
@Composable
fun rememberTactileSource() =
    remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

// ───────────────────────────── Haptics ─────────────────────────────

/**
 * Semantic haptic kinds the app uses. We map each to a [HapticFeedbackType]
 * so callers don't need to think about Android-platform constants.
 *
 *  - TAP      : light confirmation (chip pick, toggle) — least intrusive
 *  - CONFIRM  : commit action succeeded (apply category, save rule)
 *  - REJECT   : commit refused (validation error, skip-not-allowed)
 *  - LONG     : long-press detected (open detail sheet, etc.)
 */
enum class Haptic { TAP, CONFIRM, REJECT, LONG }

/**
 * Trigger a semantic haptic. Pass the [HapticFeedback] obtained via
 * `LocalHapticFeedback.current`. Made a free function (not a `Modifier`)
 * so it can fire from ViewModels too via UI callbacks.
 *
 * Compose's [HapticFeedbackType] API has only two members at the moment
 * (LongPress, TextHandleMove), so several of our semantic kinds map to
 * the same physical pattern. The mapping is centralized here so we can
 * upgrade to richer Android-level haptics later without touching call
 * sites.
 */
fun HapticFeedback.fire(kind: Haptic) {
    val type = when (kind) {
        Haptic.TAP     -> HapticFeedbackType.TextHandleMove
        Haptic.CONFIRM -> HapticFeedbackType.LongPress
        Haptic.REJECT  -> HapticFeedbackType.LongPress
        Haptic.LONG    -> HapticFeedbackType.LongPress
    }
    performHapticFeedback(type)
}

/**
 * Compose-side helper: returns a callable that fires a semantic haptic.
 * Use when a callback is the cleanest hookup site.
 *
 *  ```
 *  val haptic = rememberHaptic()
 *  Button(onClick = { haptic(Haptic.CONFIRM); vm.confirm() })
 *  ```
 */
@Composable
fun rememberHaptic(): (Haptic) -> Unit {
    val raw = LocalHapticFeedback.current
    return { kind -> raw.fire(kind) }
}
