package com.financeapp.ui.kash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent

/*
 * Kash motion helpers shared by the data screens: numbers that count,
 * bars that grow, rows that arrive one after another. Every one of them
 * is instant when the phone asks for less motion.
 */

/**
 * Kash's springs, in Apple's terms (WWDC "Designing Fluid Interfaces"):
 * a damping ratio (1 = no bounce) and a response in seconds (how quickly
 * it gets there). Springs start from wherever the thing is now and keep
 * its speed, so any motion can be grabbed and turned around mid-way.
 *
 * - [settle]: the default for everything. No overshoot.
 * - [flick]: only after a gesture that carried momentum (a fling, a drag
 *   let go): a little bounce, because the user threw it.
 */
object KashSpring {
    /** stiffness = (2π / response)², the same mapping Apple uses. */
    private fun stiffness(response: Float) = (2 * Math.PI / response).let { (it * it).toFloat() }

    fun <T> settle(response: Float = 0.35f): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = stiffness(response))

    fun <T> flick(response: Float = 0.3f): SpringSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = stiffness(response))

    /** The press: lands almost at once (response 0.1s), never wobbles. */
    fun <T> press(): SpringSpec<T> = settle(0.1f)
}

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

/**
 * A segmented control's highlight that slides to the picked segment on a
 * critically damped spring (and turns around mid-way if the pick changes),
 * instead of jumping. Mark each segment with [slot]; draw with [indicator]
 * on the segments' Row, after its padding.
 */
class KashSlider {
    internal val bounds = androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, Float>>()

    fun Modifier.slot(index: Int): Modifier = this.then(
        // A segment that leaves (Review's Earlier) forgets where it was.
        Modifier.composed {
            androidx.compose.runtime.DisposableEffect(index) { onDispose { bounds.remove(index) } }
            Modifier
        }.onPlaced { c ->
            val v = c.positionInParent().x to c.size.width.toFloat()
            if (bounds[index] != v) bounds[index] = v
        },
    )
}

@Composable
fun rememberKashSlider(): KashSlider = remember { KashSlider() }

/** Draws [slider]'s highlight under segment [selected]: [color], corner [radius]. */
fun Modifier.kashSliderIndicator(
    slider: KashSlider,
    selected: Int,
    color: androidx.compose.ui.graphics.Color,
    radius: androidx.compose.ui.unit.Dp,
): Modifier = composed {
    val reduced = rememberReducedMotion()
    val target = slider.bounds[selected]
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    var placed by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(target) {
        val (tx, tw) = target ?: return@LaunchedEffect
        if (!placed || reduced) { x.snapTo(tx); w.snapTo(tw); placed = true; return@LaunchedEffect }
        kotlinx.coroutines.coroutineScope {
            launch { x.animateTo(tx, KashSpring.settle(0.3f)) }
            launch { w.animateTo(tw, KashSpring.settle(0.3f)) }
        }
    }
    this.drawBehind {
        // Before the first snap, draw straight at the segment: no blank frames on entry.
        val (dx, dw) = if (placed) x.value to w.value else target ?: return@drawBehind
        drawRoundRect(
            color,
            topLeft = androidx.compose.ui.geometry.Offset(dx, 0f),
            size = androidx.compose.ui.geometry.Size(dw, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        )
    }
}
