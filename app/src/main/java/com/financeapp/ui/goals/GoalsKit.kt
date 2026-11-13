package com.financeapp.ui.goals

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashPress
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashHandle
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashSpinner
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberReducedMotion

/**
 * The Goals section is dark (design/goals-handoff, "All-dark Goals"): a
 * near-black top, a sheet one step lighter, cards a step above that. Sand
 * is the one accent: primary buttons, progress fills, the current step,
 * icon wells, the streak badge. Text on sand is slate-ink.
 *
 * The rest of Kash is light and its components read [KashColors] directly,
 * so Goals has its own small kit here instead of re-theming every component.
 */
object GoalsColors {
    val Hero = Color(0xFF161B22)
    val HeroInk = Color(0xFFF1F3F6)
    val HeroInkMuted = Color(0xFFAAB3BF)
    /** Pills and icon wells on the top ("+21,500 ETB this month"). */
    val HeroRaised = Color(0xFF29313C)
    val HeroGhostFill = Color(0x0FFFFFFF)  // white @ 6%
    val HeroGhostLine = Color(0x38FFFFFF)  // white @ 22%
    val HeroTrack = Color(0x29FFFFFF)      // white @ 16%: steps and the pace strip
    val HeroSkeleton = Color(0x1AFFFFFF)   // white @ 10%

    val Sheet = Color(0xFF1F252E)
    val Raised = Color(0xFF29313C)
    val Well = Color(0xFF343D49)
    val Line = Color(0xFF39424F)
    val LineStrong = Color(0xFF4A5361)
    val Handle = Color(0xFF4A5361)
    val Ink = Color(0xFFF1F3F6)
    val InkMuted = Color(0xFFAEB6C2)
    val InkFaint = Color(0xFF8A939F)
    val MoneyIn = Color(0xFF7CC48B)
    val MoneyOut = Color(0xFFF2907A)
    val Reached = Color(0xFF5AA36A)
    val Track = Color(0x24FFFFFF)          // white @ 14%
    val GhostLine = Color(0x3DFFFFFF)      // white @ 24%: the dashed "no target" bar

    val Accent = KashColors.Sand
    val OnAccent = KashColors.SlateInk
    val Backdrop = Color(0xE60B0E12)
}

// ─── Buttons ─────────────────────────────────────────────────────────────

enum class GoalsButtonVariant { Accent, Secondary, Danger, Quiet }

/** Goals button: sand (the accent), a dark outlined secondary, danger, or quiet. Labels never wrap. */
@Composable
fun GoalsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GoalsButtonVariant = GoalsButtonVariant.Accent,
    icon: KashIcon? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val (bg, fg) = when (variant) {
        GoalsButtonVariant.Accent -> GoalsColors.Accent to GoalsColors.OnAccent
        GoalsButtonVariant.Secondary -> GoalsColors.Raised to GoalsColors.Ink
        GoalsButtonVariant.Danger -> GoalsColors.MoneyOut to GoalsColors.OnAccent
        GoalsButtonVariant.Quiet -> Color.Transparent to GoalsColors.InkMuted
    }
    val quiet = variant == GoalsButtonVariant.Quiet
    Row(
        modifier
            .kashClickable(enabled = enabled, onClick = { if (!busy) onClick() })
            .height(if (quiet) 44.dp else if (compact) KashSize.ButtonHCompact else KashSize.ButtonH)
            .clip(KashShapes.Control)
            .background(bg)
            .then(if (variant == GoalsButtonVariant.Secondary) Modifier.border(1.dp, GoalsColors.Line, KashShapes.Control) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) KashSpinner(size = 18.dp, color = fg)
        else if (icon != null) KashIconView(icon, size = 18.dp, tint = fg)
        Text(
            text, style = if (quiet) KashType.Body else KashType.LabelL, color = fg,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        )
    }
}

/** 44dp icon button on the dark top: faint fill, a white 22% outline. */
@Composable
fun GoalsIconButton(icon: KashIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .kashClickable(onClickLabel = label, onClick = onClick)
            .size(KashSize.IconButton)
            .clip(KashShapes.Control)
            .background(GoalsColors.HeroGhostFill)
            .border(1.dp, GoalsColors.HeroGhostLine, KashShapes.Control)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { KashIconView(icon, size = 20.dp, tint = GoalsColors.HeroInk) }
}

// ─── Progress ────────────────────────────────────────────────────────────

/**
 * GoalProgress: sand fill on a white 14% track (green once reached), with an
 * optional pace mark where an even pace would be today. [empty]: no target
 * yet, a dashed outline. Animates from the value on screen (no bounce).
 */
@Composable
fun GoalsProgress(
    value: Double,
    modifier: Modifier = Modifier,
    mark: Double? = null,
    large: Boolean = false,
    reached: Boolean = false,
    empty: Boolean = false,
    /** Colour around the mark, so it reads on fill and track: the ground the bar sits on. */
    ground: Color = GoalsColors.Raised,
    label: String? = null,
) {
    val reduced = rememberReducedMotion()
    val motion = if (reduced) snap<Float>() else spring(dampingRatio = 1f, stiffness = 250f)
    val v by animateFloatAsState(value.toFloat().coerceIn(0f, 1f), motion, label = "goalValue")
    val m by animateFloatAsState((mark ?: 0.0).toFloat().coerceIn(0f, 1f), motion, label = "goalMark")
    val height = if (large) 12.dp else 8.dp
    val markOut = if (large) 6.dp else 5.dp
    val showMark = mark != null && !reached && !empty
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + markOut * 2)
            .semantics { contentDescription = label ?: "${(value * 100).toInt()}% saved" }
    ) {
        val h = height.toPx()
        val top = markOut.toPx()
        val r = CornerRadius(h / 2, h / 2)
        if (empty) {
            val w = 1.5.dp.toPx()
            drawRoundRect(
                GoalsColors.GhostLine, topLeft = Offset(w / 2, top + w / 2), size = Size(size.width - w, h - w),
                cornerRadius = r, style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
            )
            return@Canvas
        }
        drawRoundRect(GoalsColors.Track, topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = r)
        if (v > 0f) {
            val fill = maxOf(size.width * v, 8.dp.toPx())
            drawRoundRect(
                if (reached) GoalsColors.Reached else GoalsColors.Accent,
                topLeft = Offset(0f, top), size = Size(fill, h), cornerRadius = r,
            )
        }
        if (showMark) {
            val x = (size.width * m).coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())
            val line = 2.dp.toPx()
            val cut = 2.dp.toPx()
            drawRect(ground, topLeft = Offset(x - line / 2 - cut, 0f), size = Size(line + cut * 2, size.height))
            drawRoundRect(
                GoalsColors.Ink, topLeft = Offset(x - line / 2, 0f), size = Size(line, size.height),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

/** The pace strip on the Goals tab: one segment per active goal, filled when it's on pace. */
@Composable
fun GoalsPaceStrip(onPace: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$onPace of $total on track" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i < onPace) GoalsColors.Accent else GoalsColors.HeroTrack)
            )
        }
    }
}

// ─── Tags, wells, pills ──────────────────────────────────────────────────

/** PaceTag: green with a check when reached; a neutral pill otherwise. Nothing for no pace. */
@Composable
fun GoalsPaceTag(pace: Pace, monthly: Boolean, modifier: Modifier = Modifier) {
    val text = when (pace) {
        Pace.REACHED -> if (monthly) "Done this month" else "Reached"
        Pace.AHEAD -> "Ahead"
        Pace.ON_TRACK -> "On track"
        Pace.BEHIND -> "Behind"
        Pace.MISSED -> "Date passed"
        Pace.NO_DEADLINE -> return
    }
    val reached = pace == Pace.REACHED
    Row(
        modifier
            .height(24.dp)
            .clip(KashShapes.Pill)
            .background(if (reached) GoalsColors.Reached else GoalsColors.Well)
            .padding(start = if (reached) 8.dp else 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (reached) KashIconView(KashIcon.Check, size = 13.dp, tint = Color.White, strokeWidth = 2.6f)
        Text(
            text, style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold),
            color = if (reached) Color.White else GoalsColors.Ink, maxLines = 1, softWrap = false,
        )
    }
}

/** A goal's icon in a sand well (green once reached). [onHero]: the dark raised well on the top. */
@Composable
fun GoalsIconWell(
    icon: KashIcon,
    modifier: Modifier = Modifier,
    size: Dp = KashSize.Well,
    reached: Boolean = false,
    onHero: Boolean = false,
) {
    val (bg, fg) = when {
        reached -> GoalsColors.Reached to Color.White
        onHero -> GoalsColors.HeroRaised to GoalsColors.HeroInk
        else -> GoalsColors.Accent to GoalsColors.OnAccent
    }
    Box(
        modifier.size(size).clip(if (size >= 48.dp) KashShapes.Control else KashShapes.Field).background(bg),
        contentAlignment = Alignment.Center,
    ) { KashIconView(icon, size = if (size >= 48.dp) 22.dp else 20.dp, tint = fg) }
}

/** Sand badge with a check: "3 months in a row". */
@Composable
fun GoalsStreakBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(24.dp).clip(KashShapes.Pill).background(GoalsColors.Accent).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        KashIconView(KashIcon.Check, size = 13.dp, tint = GoalsColors.OnAccent, strokeWidth = 2.6f)
        Text(text, style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold), color = GoalsColors.OnAccent, maxLines = 1)
    }
}

// ─── Cards and rows ──────────────────────────────────────────────────────

/** A raised dark card with a hairline. Pressing it shrinks it a little. */
@Composable
fun GoalsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: Dp = 16.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.kashClickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .clip(KashShapes.Card)
            .background(GoalsColors.Raised)
            .border(1.dp, GoalsColors.Line, KashShapes.Card)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** A kind of goal: 48dp sand well, title, example, chevron. The whole card is the target. */
@Composable
fun GoalsKindCard(icon: KashIcon, title: String, example: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Row(
        modifier
            .fillMaxWidth()
            .kashClickable(onClick = onClick)
            .clip(KashShapes.Card)
            .background(GoalsColors.Raised)
            .border(if (selected) 2.dp else 1.dp, if (selected) GoalsColors.Accent else GoalsColors.Line, KashShapes.Card)
            .padding(16.dp)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GoalsIconWell(icon, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = KashType.TitleXs, color = GoalsColors.Ink)
            Text(example, style = KashType.Caption, color = GoalsColors.InkMuted, maxLines = 2)
        }
        KashIconView(KashIcon.ChevronRight, size = 18.dp, tint = GoalsColors.InkFaint)
    }
}

/** A history row: arrow well, name, time and a pill, signed amount in money colours. */
@Composable
fun GoalsTxRow(
    name: String,
    amount: Double,
    direction: KashDirection,
    time: String,
    category: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val tone = if (direction == KashDirection.In) GoalsColors.MoneyIn else GoalsColors.MoneyOut
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.kashClickable(press = KashPress.Highlight, onClick = onClick) else Modifier)
                .padding(vertical = KashSpace.RowY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(KashSize.Well).clip(KashShapes.Field).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
                KashIconView(if (direction == KashDirection.In) KashIcon.In else KashIcon.Out, size = 17.dp, tint = tone, strokeWidth = 2.2f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = KashType.Body, color = GoalsColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(time, style = KashType.Caption.num(), color = GoalsColors.InkMuted, maxLines = 1, softWrap = false)
                    Box(
                        Modifier.weight(1f, fill = false).height(20.dp).clip(KashShapes.Pill).background(GoalsColors.Well).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(category, style = KashType.Micro.copy(fontWeight = FontWeight.Medium), color = GoalsColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(KashMoney.signed(amount, direction), style = KashType.BodyStrong.num(), color = tone, maxLines = 1, softWrap = false)
        }
        if (showDivider) GoalsDivider()
    }
}

@Composable
fun GoalsDivider(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(GoalsColors.Line))

/** Month header above history rows, with the month's net on the right. */
@Composable
fun GoalsDayHeader(label: String, net: String?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp).semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = KashType.Caption.copy(fontWeight = FontWeight.Medium)
        Text(label, style = style, color = GoalsColors.InkMuted, maxLines = 1)
        if (net != null) Text(net, style = style.num(), color = GoalsColors.InkMuted, maxLines = 1)
    }
}

/** A menu row: icon, label (+ hint), optional value and chevron. */
@Composable
fun GoalsListRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: KashIcon? = null,
    hint: String? = null,
    value: String? = null,
    color: Color = GoalsColors.Ink,
    chevron: Boolean = true,
    showDivider: Boolean = true,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().kashClickable(press = KashPress.Highlight, onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) KashIconView(icon, size = 20.dp, tint = if (color == GoalsColors.Ink) GoalsColors.InkMuted else color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = KashType.Body, color = color)
                if (hint != null) Text(hint, style = KashType.Caption, color = GoalsColors.InkMuted)
            }
            if (value != null) Text(value, style = KashType.Body, color = GoalsColors.InkMuted, maxLines = 1)
            if (chevron) KashIconView(KashIcon.ChevronRight, size = 16.dp, tint = GoalsColors.InkFaint)
        }
        if (showDivider) GoalsDivider()
    }
}

/** A selectable chip: sand when on, a dark outlined pill when off. */
@Composable
fun GoalsChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: KashIcon? = null) {
    val (bg, fg, line) = if (selected) Triple(GoalsColors.Accent, GoalsColors.OnAccent, GoalsColors.Accent)
    else Triple(GoalsColors.Raised, GoalsColors.Ink, GoalsColors.Line)
    Row(
        modifier
            .kashClickable(onClick = onClick)
            .height(KashSize.ChipH)
            .clip(KashShapes.Chip)
            .background(bg)
            .border(1.dp, line, KashShapes.Chip)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) KashIconView(leading, size = 16.dp, tint = fg)
        Text(text, style = KashType.Label, color = fg, maxLines = 1, softWrap = false)
    }
}

/**
 * A dark text field: label above, optional unit on the right ("ETB"), an
 * error line in money-out. [big] uses a 22sp figure for amounts.
 */
@Composable
fun GoalsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    unit: String? = null,
    big: Boolean = false,
    hint: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingLabel: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val style = if (big) KashType.TitleM.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold).num() else KashType.BodyL
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = KashType.Label, color = GoalsColors.InkMuted, modifier = Modifier.weight(1f))
            if (trailingLabel != null) Text(trailingLabel, style = KashType.Caption.num(), color = GoalsColors.InkFaint)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(KashSize.FieldH + if (big) 4.dp else 0.dp)
                .clip(KashShapes.Field)
                .background(GoalsColors.Raised)
                .border(1.dp, if (error != null) GoalsColors.MoneyOut else GoalsColors.Line, KashShapes.Field)
                .padding(horizontal = 14.dp)
                .then(if (error != null) Modifier.semantics { this.error(error) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = style, color = GoalsColors.InkFaint, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = style.copy(color = GoalsColors.Ink),
                    cursorBrush = SolidColor(GoalsColors.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (unit != null) Text(unit, style = KashType.Body, color = GoalsColors.InkMuted)
        }
        val line = error ?: hint
        if (line != null) Text(line, style = KashType.Caption, color = if (error != null) GoalsColors.MoneyOut else GoalsColors.InkMuted)
    }
}

/** Skeleton block on the dark sheet. */
@Composable
fun GoalsSkeleton(modifier: Modifier, onHero: Boolean = false) =
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(if (onHero) GoalsColors.HeroSkeleton else GoalsColors.Well))

// ─── Sheet and dialog ────────────────────────────────────────────────────

/** A dark bottom sheet that follows the finger; a flick down closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSheet(
    title: String?,
    onDismiss: () -> Unit,
    containerColor: Color = GoalsColors.Sheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = KashShapes.Sheet,
        containerColor = containerColor,
        contentColor = GoalsColors.Ink,
        tonalElevation = 0.dp,
        scrimColor = GoalsColors.Backdrop,
        dragHandle = { KashHandle(Modifier.padding(top = 10.dp), color = GoalsColors.Handle) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = KashSpace.InsetApp, end = KashSpace.InsetApp, top = 4.dp, bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    title, style = KashType.TitleM, color = GoalsColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Box(
                    Modifier.kashClickable(onClickLabel = "Close", onClick = onDismiss).size(KashSize.IconButton)
                        .clip(KashShapes.Control).background(GoalsColors.Raised).border(1.dp, GoalsColors.Line, KashShapes.Control)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) { KashIconView(KashIcon.ChevronDown, size = 20.dp, tint = GoalsColors.Ink) }
            }
            content()
        }
    }
}

/**
 * A dark centred dialog. [header] (optional) is drawn edge to edge above the
 * title, on the dark top colour (the "You reached it" card).
 */
@Composable
fun GoalsDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    description: String? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .background(GoalsColors.Backdrop)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(GoalsColors.Sheet)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                if (header != null) Column(
                    Modifier.fillMaxWidth().background(GoalsColors.Hero).padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = header,
                )
                Column(
                    Modifier.padding(start = 22.dp, end = 22.dp, top = if (header != null) 20.dp else 24.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (title != null || description != null) Column {
                        if (title != null) Text(title, style = KashType.TitleM, color = GoalsColors.Ink, modifier = Modifier.semantics { heading() })
                        if (description != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(description, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.InkMuted)
                        }
                    }
                    content()
                }
            }
        }
    }
}
