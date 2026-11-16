@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.financeapp.ui.kash.components

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable

/** One tip: the anchor it points at ([Modifier.tipAnchor]) and what it says. */
data class KashTip(val anchor: String, val title: String, val body: String)

/**
 * The tips one screen shows the first time it is opened ("HOME · 1 OF 2").
 * [key] is remembered once the set is finished or skipped, so it never
 * shows again on this phone.
 */
data class KashTipSet(val key: String, val label: String, val tips: List<KashTip>)

/**
 * New-user tips (approved flow, "Tip · …" screens): the screen darkens
 * around the thing a tip explains and a sand card says what it is, with
 * Skip and Next, then Got it. Screens mark what a tip can point at with
 * [Modifier.tipAnchor] and call [offer] once they are ready; the host
 * ([KashTipsHost]) draws it on top of everything.
 */
class KashTipsState internal constructor(private val prefs: SharedPreferences?) {
    internal val rects = mutableStateMapOf<String, Rect>()
    internal val requesters = mutableMapOf<String, BringIntoViewRequester>()

    var active by mutableStateOf<KashTipSet?>(null)
        private set
    var index by mutableIntStateOf(0)
        private set

    /** Where [anchor] is: its own rect joined with any "anchor#2", "anchor#3" parts. */
    internal fun rectOf(anchor: String): Rect? =
        rects.entries.filter { it.key == anchor || it.key.startsWith("$anchor#") }
            .map { it.value }
            .reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }

    fun seen(key: String): Boolean = prefs?.getBoolean(key, false) ?: false

    /** Shows [set] if it was never finished and nothing else is showing. Tips whose anchor isn't on screen are left out. */
    fun offer(set: KashTipSet) {
        if (active != null || seen(set.key)) return
        val tips = set.tips.filter { rectOf(it.anchor) != null }
        if (tips.isEmpty()) return
        index = 0
        active = set.copy(tips = tips)
    }

    fun next() {
        val set = active ?: return
        if (index < set.tips.lastIndex) index++ else finish()
    }

    /** For tips drawn inline (inside a sheet): mark [key] done without showing it here. */
    fun markSeen(key: String) { prefs?.edit()?.putBoolean(key, true)?.apply() }

    /** Got it or Skip: this set never shows again. */
    fun finish() {
        active?.let { prefs?.edit()?.putBoolean(it.key, true)?.apply() }
        active = null
        index = 0
    }

    companion object {
        private const val PREFS = "kash_tips"
        fun of(context: Context) = KashTipsState(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}

/** Null outside the main app (previews, onboarding): anchors then do nothing. */
val LocalKashTips = staticCompositionLocalOf<KashTipsState?> { null }

/**
 * Marks what a tip with this [id] points at. Scrolled into view when its tip
 * shows. A tip spanning two parts marks the second "id#2".
 */
fun Modifier.tipAnchor(id: String): Modifier = composed {
    val tips = LocalKashTips.current ?: return@composed this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(tips, id) {
        tips.requesters[id] = requester
        onDispose {
            if (tips.requesters[id] === requester) tips.requesters.remove(id)
            tips.rects.remove(id)
        }
    }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { tips.rects[id] = it.boundsInRoot() }
}

private val Dim = Color(0x99000000)

/**
 * A 4dp checker tile ([cell] px per quarter) with [quarters] of its four
 * quarters dimmed, clockwise from the top right, like the design's
 * repeating conic gradient.
 */
private fun ditherBrush(quarters: Int, cell: Int): ShaderBrush {
    val c = cell.coerceAtLeast(1)
    val bmp = ImageBitmap(2 * c, 2 * c)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    val paint = androidx.compose.ui.graphics.Paint().apply { color = Dim }
    // Top right, bottom right, bottom left, top left.
    val spots = listOf(c to 0, c to c, 0 to c, 0 to 0)
    spots.take(quarters).forEach { (x, y) ->
        canvas.drawRect(x.toFloat(), y.toFloat(), (x + c).toFloat(), (y + c).toFloat(), paint)
    }
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** Draws the active tip over the whole app. Put it last in a full-screen Box. */
@Composable
fun KashTipsHost(tips: KashTipsState) {
    val set = tips.active ?: return
    val tip = set.tips.getOrNull(tips.index) ?: return
    val last = tips.index == set.tips.lastIndex
    BackHandler { tips.finish() }
    LaunchedEffect(set.key, tips.index) { tips.requesters[tip.anchor]?.bringIntoView() }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = tips.rectOf(tip.anchor)?.translate(-origin)
    // The spotlight glides from one item to the next on a no-bounce spring
    // (it starts where it is, so a quick Next turns it smoothly); the card
    // grows in from 96% each time it changes.
    val reduced = com.financeapp.ui.kash.rememberReducedMotion()
    val l = remember { androidx.compose.animation.core.Animatable(0f) }
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    val r = remember { androidx.compose.animation.core.Animatable(0f) }
    val b = remember { androidx.compose.animation.core.Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val cardIn = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(target) {
        val to = target ?: return@LaunchedEffect
        if (!placed || reduced) {
            l.snapTo(to.left); t.snapTo(to.top); r.snapTo(to.right); b.snapTo(to.bottom); placed = true
            return@LaunchedEffect
        }
        val spec = com.financeapp.ui.kash.KashSpring.settle<Float>(0.4f)
        kotlinx.coroutines.coroutineScope {
            launch { l.animateTo(to.left, spec) }; launch { t.animateTo(to.top, spec) }
            launch { r.animateTo(to.right, spec) }; launch { b.animateTo(to.bottom, spec) }
        }
    }
    LaunchedEffect(set.key, tips.index) {
        if (reduced) { cardIn.snapTo(1f); return@LaunchedEffect }
        cardIn.snapTo(0f); cardIn.animateTo(1f, com.financeapp.ui.kash.KashSpring.settle(0.35f))
    }
    val anchor = if (target != null && placed) Rect(l.value, t.value, r.value, b.value) else target
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Taps outside the card do nothing: the tip is read, then closed.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val density = LocalDensity.current
        // The approved tip's dither: three 8dp bands around the hole, 25%,
        // 50% and 75% of a 4dp checker, then the solid dim.
        val dithers = remember(density) { listOf(1, 2, 3).map { ditherBrush(it, with(density) { 2.dp.roundToPx() }) } }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Dim)
            if (anchor != null) {
                val hole = anchor.inflate(6.dp.toPx())
                val band = 8.dp.toPx()
                val radius = minOf(16.dp.toPx(), minOf(hole.width, hole.height) / 2)
                fun ring(e: Float) = RoundRect(hole.inflate(e), CornerRadius(radius + e))
                // Clear the hole and the three bands, then dither the bands back in.
                drawPath(Path().apply { addRoundRect(ring(3 * band)) }, Color.Black, blendMode = BlendMode.Clear)
                for (k in 1..3) {
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRoundRect(ring(k * band))
                        addRoundRect(ring((k - 1) * band))
                    }
                    drawPath(path, dithers[k - 1])
                }
            }
        }
        // The card sits under the anchor, or above it when there's no room below.
        Layout(
            content = {
                Box(Modifier.graphicsLayer {
                    alpha = cardIn.value
                    val k = 0.96f + 0.04f * cardIn.value
                    scaleX = k; scaleY = k
                }) {
                TipCard(
                    count = if (set.tips.size > 1) "${set.label} · ${tips.index + 1} OF ${set.tips.size}" else set.label,
                    title = tip.title,
                    body = tip.body,
                    next = if (last) "Got it" else "Next",
                    showSkip = set.tips.size > 1,
                    onNext = tips::next,
                    onSkip = tips::finish,
                )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val side = 22.dp.roundToPx()
            val gap = 22.dp.roundToPx()
            val card = measurables.first().measure(
                constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth - 2 * side, minHeight = 0),
            )
            val h = constraints.maxHeight
            val y = when {
                anchor == null -> (h - card.height) / 2
                anchor.bottom + gap + card.height <= h - 80.dp.roundToPx() -> anchor.bottom.toInt() + gap
                else -> (anchor.top.toInt() - gap - card.height).coerceAtLeast(gap)
            }
            layout(constraints.maxWidth, h) { card.place(side, y) }
        }
    }
}

@Composable
private fun TipCard(
    count: String,
    title: String,
    body: String,
    next: String,
    showSkip: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KashColors.Sand)
            .padding(18.dp)
            .semantics { paneTitle = "Tip"; liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(count, style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = KashColors.SlateInk.copy(alpha = 0.7f))
        Text(title, style = KashType.TitleS.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = KashColors.SlateInk)
        Text(body, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.SlateInk.copy(alpha = 0.85f))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (showSkip) {
                Box(
                    Modifier.height(40.dp).kashClickable(onClickLabel = "Skip the tips", onClick = onSkip).padding(horizontal = 4.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { Text("Skip", style = KashType.Label, color = KashColors.SlateInk.copy(alpha = 0.7f)) }
            }
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KashColors.SlateInk)
                    .kashClickable(onClick = onNext)
                    .padding(horizontal = 20.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Text(next, style = KashType.Label.copy(fontWeight = FontWeight.SemiBold), color = Color.White) }
        }
    }
}
