package com.financeapp.ui.kash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/*
 * Kash motion helpers shared by the data screens: numbers that count,
 * bars that grow, rows that arrive one after another. Every one of them
 * is instant when the phone asks for less motion.
 */

/**
 * 0 → 1 once per [key] (a report month, a list), after [delayMs]. Use it to
 * scale a bar or a progress fill from nothing.
 */
@Composable
fun rememberGrow(key: Any?, delayMs: Int = 0, durationMs: Int = 600): Float {
    val reduced = rememberReducedMotion()
    val p = remember(key) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduced) p.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing))
    }
    return p.value
}

/**
 * [value], rolling from what was shown before: from 0 on first load, from
 * the old figure after a refresh.
 */
@Composable
fun animatedAmount(value: Double, durationMs: Int = 700): Double {
    val reduced = rememberReducedMotion()
    if (reduced) return value
    var from by remember { mutableDoubleStateOf(0.0) }
    var to by remember { mutableDoubleStateOf(0.0) }
    val p = remember { Animatable(1f) }
    val shown = from + (to - from) * p.value
    LaunchedEffect(value) {
        from = shown; to = value
        p.snapTo(0f)
        p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return shown
}

/**
 * A row arriving: fades in and rises 8dp, [index] × 30ms after the first.
 * Replays when [key] changes.
 */
fun Modifier.kashEnter(index: Int, key: Any?): Modifier = composed {
    val p = rememberGrow(key, delayMs = (index * 30).coerceAtMost(300), durationMs = 260)
    val rise = 8.dp
    graphicsLayer {
        alpha = p
        translationY = (1f - p) * rise.toPx()
    }
}
