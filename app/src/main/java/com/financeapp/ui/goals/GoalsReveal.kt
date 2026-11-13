package com.financeapp.ui.goals

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashSpace
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Going into or out of the Goals section, the dark screen spreads out of the
 * Goals tab icon in a circle and pulls back into it on the way out. Nothing
 * scales: it's a pixel wipe. The edge is a band of square pixels switched on
 * in an ordered (Bayer 4×4) dither, so it reads as a soft, grainy edge.
 *
 * Motion (Apple's fluid-interface rules): anchored to what was tapped, the
 * same path in and out, one critically damped spring (no bounce, ~0.55 s),
 * never blocks a tap. Reduced motion: a 200 ms fade, no wipe.
 *
 * NavHost transitions are hard cuts; each tab screen wraps itself in
 * [GoalsCrossingFrame], which reads [GoalsCrossing] (set by NavHost's
 * transition lambdas) to know whether this change crosses into or out of Goals.
 */
class GoalsCrossing {
    /** true: into Goals, false: out of Goals, null: neither. */
    var into: Boolean? = null
    /** The change is a pop (system Back): the leaving screen stays on top. */
    var pop: Boolean = false
}

/** Side of a pixel in the wipe, and the width of the dithered edge. */
private val PIXEL = 6.dp
private val BAND = 54.dp

/** Bayer 4×4 thresholds, 0..1. */
private val BAYER = floatArrayOf(
    0f, 8f, 2f, 10f,
    12f, 4f, 14f, 6f,
    3f, 11f, 1f, 9f,
    15f, 7f, 13f, 5f,
).map { (it + 0.5f) / 16f }.toFloatArray()

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.GoalsCrossingFrame(
    isGoals: Boolean,
    crossing: GoalsCrossing,
    reduced: Boolean,
    content: @Composable () -> Unit,
) {
    val into = crossing.into
    val crossingNow = into != null
    // 0 before entering / after leaving, 1 when on screen. Also keeps the
    // leaving screen composed underneath until the wipe ends.
    val progress by transition.animateFloat(
        transitionSpec = {
            when {
                !crossingNow -> snap()
                reduced -> tween(200)
                else -> spring(dampingRatio = 1f, stiffness = 130f, visibilityThreshold = 0.002f)
            }
        },
        label = "goalsCrossing",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }

    val entering = transition.targetState == EnterExitState.Visible
    val pop = crossing.pop
    // Which side draws the wipe, and how:
    //  - into Goals: the arriving Goals screen, inside a growing circle;
    //  - out of Goals by tab: the arriving screen, outside a shrinking circle;
    //  - out of Goals by Back: the leaving Goals screen (it stays on top), inside a shrinking circle.
    val mode: WipeMode? = when {
        !crossingNow || progress >= 1f && entering -> null
        reduced -> null
        into == true && isGoals && entering && !pop -> WipeMode.Inside
        into == false && !isGoals && entering && !pop -> WipeMode.Outside
        into == false && isGoals && !entering && pop -> WipeMode.Inside
        else -> null
    }
    val fadeAlpha = if (reduced && crossingNow && entering) progress else 1f

    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val inset = with(density) { KashSpace.InsetApp.toPx() }
    val tabCentre = with(density) { 31.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = fadeAlpha
                compositingStrategy = if (mode != null) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                if (mode == null) { drawContent(); return@drawWithContent }
                // The Goals tab is the 4th of 4 in the tab bar.
                val origin = Offset(inset + (size.width - 2 * inset) * (3.5f / 4f), size.height - navBottom - tabCentre)
                drawWipe(mode, progress, origin)
            }
    ) { content() }
}

private enum class WipeMode { Inside, Outside }

/**
 * [Inside]: content shows inside a circle of radius [progress] × reach.
 * [Outside]: content shows outside a circle of radius (1 − [progress]) × reach.
 * The circle is stepped on the pixel grid; its outer [BAND] is dithered.
 */
private fun ContentDrawScope.drawWipe(mode: WipeMode, progress: Float, origin: Offset) {
    val px = PIXEL.toPx()
    val band = BAND.toPx()
    val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
        .maxOf { hypot(it.x - origin.x, it.y - origin.y) }
    val reach = far + band + px
    val r = (if (mode == WipeMode.Inside) progress else 1f - progress).coerceIn(0f, 1f) * reach

    val cols = ceil(size.width / px).toInt()
    val rows = ceil(size.height / px).toInt()

    // Inside: show cells whose centre is within r. Outside: hide cells within r − band.
    val solid = if (mode == WipeMode.Inside) r else r - band
    val stepped = steppedCircle(origin, solid, px, cols, rows)
    clipPath(stepped, if (mode == WipeMode.Inside) ClipOp.Intersect else ClipOp.Difference) {
        this@drawWipe.drawContent()
    }

    // The dithered band: cells between r − band and r. A cell is on when its
    // depth into the band passes its Bayer threshold.
    val inner = max(0f, r - band)
    val rowFrom = max(0, floor((origin.y - r) / px).toInt())
    val rowTo = minOf(rows - 1, ceil((origin.y + r) / px).toInt())
    for (j in rowFrom..rowTo) {
        val cy = (j + 0.5f) * px
        val dy = cy - origin.y
        if (dy * dy > r * r) continue
        val half = sqrt(r * r - dy * dy)
        val c0 = max(0, floor((origin.x - half) / px).toInt())
        val c1 = minOf(cols - 1, ceil((origin.x + half) / px).toInt())
        for (i in c0..c1) {
            val cx = (i + 0.5f) * px
            val d = hypot(cx - origin.x, dy)
            if (d <= inner || d > r) continue
            val depth = (r - d) / band              // 0 at the outer edge, 1 at the inner
            val threshold = BAYER[(j and 3) * 4 + (i and 3)]
            val shown = if (mode == WipeMode.Inside) depth >= threshold else depth < threshold
            val rect = Rect(Offset(i * px, j * px), Size(px, px))
            if (mode == WipeMode.Inside && !shown) {
                drawRect(Color.Black, rect.topLeft, rect.size, blendMode = BlendMode.Clear)
            } else if (mode == WipeMode.Outside && !shown) {
                drawRect(Color.Black, rect.topLeft, rect.size, blendMode = BlendMode.Clear)
            }
        }
    }
}

/** A circle of radius [r] made of whole pixel cells (one rectangle per row). */
private fun steppedCircle(origin: Offset, r: Float, px: Float, cols: Int, rows: Int): Path {
    val path = Path()
    if (r <= 0f) return path
    val rowFrom = max(0, floor((origin.y - r) / px).toInt())
    val rowTo = minOf(rows - 1, ceil((origin.y + r) / px).toInt())
    for (j in rowFrom..rowTo) {
        val cy = (j + 0.5f) * px
        val dy = cy - origin.y
        if (dy * dy > r * r) continue
        val half = sqrt(r * r - dy * dy)
        val c0 = max(0, ceil((origin.x - half) / px - 0.5f).toInt())
        val c1 = minOf(cols - 1, floor((origin.x + half) / px - 0.5f).toInt())
        if (c1 < c0) continue
        path.addRect(Rect(c0 * px, j * px, (c1 + 1) * px, (j + 1) * px))
    }
    return path
}
