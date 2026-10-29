package com.financeapp.ui.kash.components

import com.financeapp.ui.kash.rememberGrow
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberReducedMotion

// ─── Spinner ─────────────────────────────────────────────────────────────

/** Kash.Spinner: a turning ring; still when the phone asks for less motion. */
@Composable
fun KashSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = KashColors.Ink,
    label: String = "Loading",
) {
    val reduced = rememberReducedMotion()
    val angle = if (reduced) 0f else {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin")
        a
    }
    Canvas(
        modifier
            .size(size)
            .rotate(angle)
            .semantics { contentDescription = label }
    ) {
        val stroke = size.toPx() * 2.4f / 24f
        val inset = size.toPx() * 3f / 24f
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(color.copy(alpha = 0.3f * color.alpha), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(color, -90f, 90f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ─── Skeleton ────────────────────────────────────────────────────────────

/** Kash.Skeleton: a still grey shape where content is coming. No shimmer. */
@Composable
fun KashSkeleton(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 12.dp,
    radius: Dp = 6.dp,
    onSlate: Boolean = false,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(if (onSlate) KashColors.SkeletonOnSlate else KashColors.Well)
            .clearAndSetSemantics { }
    )
}

/** Kash.SkeletonRow: one placeholder transaction row. */
@Composable
fun KashSkeletonRow(modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics { }) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = KashSpace.RowY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KashSkeleton(width = 40.dp, height = 40.dp, radius = 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KashSkeleton(Modifier.fillMaxWidth(0.6f), height = 12.dp)
                KashSkeleton(Modifier.fillMaxWidth(0.36f), height = 10.dp)
            }
            KashSkeleton(width = 64.dp, height = 12.dp)
        }
        KashDivider()
    }
}

// ─── Snackbar ────────────────────────────────────────────────────────────

/**
 * Kash.Snackbar: a short message after an action with an optional [action]
 * ("Undo"). Without an action it is the toast style. Message may wrap to two
 * lines; the action never wraps.
 */
@Composable
fun KashSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(KashShapes.Control)
            .background(KashColors.SlateInk)
            .padding(start = 16.dp, end = if (action != null) 8.dp else 16.dp, top = 6.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message, style = KashType.Body, color = KashColors.OnSlate, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = action != null),
        )
        if (action != null) {
            Box(
                Modifier
                    .height(40.dp)
                    .clip(KashShapes.Chip)
                    .kashClickable(onClick = onAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = KashType.LabelL, color = KashColors.Sand, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ─── StepBar ─────────────────────────────────────────────────────────────

/**
 * Kash.StepBar: segmented progress. Light (onboarding): slate segments, 4dp.
 * On slate (review): sand for done, ghost for the rest, 5dp.
 */
@Composable
fun KashStepBar(current: Int, total: Int, modifier: Modifier = Modifier, onSlate: Boolean = false, label: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label ?: "Step $current of $total" },
        horizontalArrangement = Arrangement.spacedBy(KashSpace.GapXs),
    ) {
        repeat(total.coerceAtLeast(1)) { i ->
            val on = i < current
            Box(
                Modifier
                    .weight(1f)
                    .height(if (onSlate) 5.dp else 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            onSlate && on -> KashColors.Sand
                            onSlate -> KashColors.GhostLine
                            on -> KashColors.Slate
                            else -> KashColors.LineStrong
                        }
                    )
            )
        }
    }
}

// ─── WeekChart ───────────────────────────────────────────────────────────

/**
 * Kash.WeekChart: seven bars (values 0–1 scaled to the week's biggest day),
 * today in sand, the week's [total] beside them. On slate, Home hero only.
 */
@Composable
fun KashWeekChart(
    values: List<Float>,
    today: Int,
    total: String,
    modifier: Modifier = Modifier,
    label: String = "This week",
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier
                .weight(1f)
                .height(36.dp)
                .clearAndSetSemantics { contentDescription = "Spending by day this week" },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            values.forEachIndexed { i, v ->
                // Bars rise from the baseline, one after another.
                val grow = rememberGrow(values, delayMs = i * 40, durationMs = 500)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((v.coerceIn(0.08f, 1f) * grow).coerceAtLeast(0.001f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == today) KashColors.Sand else KashColors.ChartBar)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(label, style = KashType.Micro, color = KashColors.OnSlateMuted)
            Text(total, style = KashType.FigureS, color = KashColors.OnSlate)
        }
    }
}

// ─── BankBadge ───────────────────────────────────────────────────────────

/** Kash.BankBadge: placeholder initials on the bank's colour (40 in lists, 28–36 in headers). */
@Composable
fun KashBankBadge(bankName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val bank = kashBank(bankName)
    Box(
        modifier
            .size(size)
            .clip(if (size >= 36.dp) KashShapes.Field else KashShapes.Chip)
            .background(bank.color)
            .clearAndSetSemantics { contentDescription = bank.fullName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            bank.code,
            color = KashColors.OnSlate,
            style = KashType.Caption.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.27f).sp, letterSpacing = 0.01.sp),
            maxLines = 1,
        )
    }
}

// ─── AccountCard ─────────────────────────────────────────────────────────

/**
 * Kash.AccountCard (on slate): badge, full bank name, last four, balance and
 * when the balance was last read from SMS ([updated]).
 */
@Composable
fun KashAccountCard(
    bankName: String,
    last4: String?,
    balance: Double?,
    updated: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Overrides the bank name, e.g. "CBE 1" when the user has several CBE accounts. */
    title: String? = null,
) {
    val bank = kashBank(bankName)
    Column(
        modifier
            .then(if (onClick != null) Modifier.kashClickable(onClick = onClick) else Modifier)
            .width(304.dp)
            .height(196.dp)
            .clip(KashShapes.Card)
            .background(KashColors.SlateInk)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KashBankBadge(bankName, size = 40.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title ?: bank.fullName, style = KashType.BodyStrong, color = KashColors.OnSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!last4.isNullOrBlank()) Text("•••• $last4", style = KashType.Caption.num(), color = KashColors.OnSlateMuted)
            }
        }
        Column {
            Text("Balance", style = KashType.Caption, color = KashColors.OnSlateMuted)
            Spacer(Modifier.height(2.dp))
            if (balance != null) {
                KashFigure(balance, style = KashType.FigureL, countUp = true)
            } else {
                Text("—", style = KashType.FigureL, color = KashColors.OnSlate)
            }
            if (updated != null) {
                Spacer(Modifier.height(8.dp))
                Text(updated, style = KashType.Micro, color = KashColors.OnSlateMuted)
            }
        }
    }
}

// ─── AppIcon ─────────────────────────────────────────────────────────────

/** Kash.AppIcon: three bars on slate, the first in sand. [bare] = bars only. */
@Composable
fun KashAppIcon(modifier: Modifier = Modifier, size: Dp = 48.dp, bare: Boolean = false) {
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .semantics { contentDescription = "Kash" }
    ) {
        val u = this.size.width / 108f
        if (!bare) drawRect(KashColors.Slate)
        fun bar(x: Float, y: Float, h: Float, c: Color) = drawRoundRect(
            color = c, topLeft = Offset(x * u, y * u), size = Size(10 * u, h * u), cornerRadius = CornerRadius(5 * u),
        )
        bar(34f, 30f, 48f, KashColors.Sand)
        bar(50f, 44f, 34f, Color.White)
        bar(66f, 36f, 42f, Color.White.copy(alpha = 0.45f))
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

@Preview(name = "Feedback", widthDp = 360)
@Composable
private fun FeedbackPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            KashSpinner()
            KashStepBar(current = 2, total = 3, modifier = Modifier.weight(1f))
        }
        KashSkeletonRow()
        KashSkeletonRow()
        KashSnackbar("Confirmed bekelu beyene as Family", action = "Undo")
        KashSnackbar("Exported 312 transactions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashBankBadge("Commercial Bank of Ethiopia")
            KashBankBadge("Telebirr")
            KashBankBadge("Bank of Abyssinia")
            KashBankBadge("Dashen Bank", size = 28.dp)
            KashAppIcon(size = 40.dp)
        }
    }
}

@Preview(name = "On slate", widthDp = 390)
@Composable
private fun OnSlatePreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Slate).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        KashStepBar(current = 3, total = 6, onSlate = true)
        KashWeekChart(listOf(0.3f, 0.6f, 0.15f, 0.4f, 0.9f, 0f, 0f), today = 4, total = "−4,120")
        KashAccountCard("Commercial Bank of Ethiopia", "4607", 12340.48, "From today's SMS, 12:36")
        KashAppIcon(size = 72.dp, bare = true)
    }
}
