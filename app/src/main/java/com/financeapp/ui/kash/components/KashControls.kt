package com.financeapp.ui.kash.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num

// ─── Button ──────────────────────────────────────────────────────────────

enum class KashButtonVariant { Primary, Accent, Secondary, Danger, Quiet, QuietOnSlate }

/**
 * Kash.Button. Full width by default; labels never wrap (one line, no shrink).
 * At most one Primary/Accent per screen. [badge] shows a count ("Review 4").
 * [busy] shows a Spinner before the label and keeps the button at full
 * strength; disable the screen's other actions while busy.
 */
@Composable
fun KashButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: KashButtonVariant = KashButtonVariant.Primary,
    icon: KashIcon? = null,
    badge: String? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val quiet = variant == KashButtonVariant.Quiet || variant == KashButtonVariant.QuietOnSlate
    val (bg, fg, border) = when (variant) {
        KashButtonVariant.Primary -> Triple(KashColors.Slate, KashColors.OnSlate, null)
        KashButtonVariant.Accent -> Triple(KashColors.Sand, KashColors.SlateInk, null)
        KashButtonVariant.Secondary -> Triple(KashColors.SurfaceRaised, KashColors.Ink, KashColors.Line)
        KashButtonVariant.Danger -> Triple(KashColors.MoneyOut, KashColors.OnSlate, null)
        KashButtonVariant.Quiet -> Triple(Color.Transparent, KashColors.InkMuted, null)
        KashButtonVariant.QuietOnSlate -> Triple(Color.Transparent, KashColors.OnSlateMuted, null)
    }
    val height = when {
        quiet -> 44.dp
        compact -> KashSize.ButtonHCompact
        else -> KashSize.ButtonH
    }
    val labelStyle = if (quiet) KashType.Body.copy(fontSize = 15.sp) else KashType.LabelL
    Row(
        modifier = modifier
            // First, so the press/disabled fade covers the fill too.
            .kashClickable(enabled = enabled, onClick = { if (!busy) onClick() })
            .height(height)
            .clip(KashShapes.Control)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, KashShapes.Control) else Modifier)
            // Busy keeps full strength (kashClickable dims disabled ones).
            .then(if (busy) Modifier.semantics { contentDescription = text } else Modifier)
            .padding(start = 18.dp, end = if (badge != null) 8.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (badge != null) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) KashSpinner(size = 18.dp, color = fg)
                else if (icon != null) KashIconView(icon, size = 18.dp, tint = fg)
                Text(text, style = labelStyle, color = fg, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            if (badge != null) {
                val (badgeBg, badgeFg) =
                    if (variant == KashButtonVariant.Accent) KashColors.SlateInk to KashColors.Sand
                    else KashColors.Sand to KashColors.SlateInk
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 36.dp)
                        .height(36.dp)
                        .clip(KashShapes.Chip)
                        .background(badgeBg)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(badge, style = KashType.BodyStrong.num(), color = badgeFg, maxLines = 1)
                }
            }
        }
    }
}

// ─── IconButton ──────────────────────────────────────────────────────────

/**
 * Kash.IconButton: 44dp square (52 when [large]); [label] is its accessible
 * name and is required. Light: white with a hairline. On slate: ghost.
 */
@Composable
fun KashIconButton(
    icon: KashIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSlate: Boolean = false,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val side = if (large) KashSize.ButtonHCompact else KashSize.IconButton
    val bg = if (onSlate) KashColors.GhostFill else KashColors.SurfaceRaised
    val line = if (onSlate) KashColors.GhostLine else KashColors.Line
    val fg = if (onSlate) KashColors.OnSlate else KashColors.Ink
    Box(
        modifier = modifier
            .kashClickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .size(side)
            .clip(KashShapes.Control)
            .background(bg)
            .border(1.dp, line, KashShapes.Control)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        KashIconView(icon, size = if (large) 22.dp else 20.dp, tint = fg)
    }
}

// ─── Chip ────────────────────────────────────────────────────────────────

/**
 * Kash.Chip: a noun. On surface the selected chip is slate; on slate
 * ([onSlate]) it is sand — that sand is the screen's one accent.
 * [trailing] adds a chevron ("This month ⌄", "More ›").
 */
@Composable
fun KashChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSlate: Boolean = false,
    leading: KashIcon? = null,
    trailing: KashIcon? = null,
    dashed: Boolean = false,
) {
    val (bg, line, fg) = when {
        onSlate && selected -> Triple(KashColors.Sand, KashColors.Sand, KashColors.SlateInk)
        onSlate -> Triple(KashColors.GhostFill, KashColors.GhostLine, KashColors.OnSlate)
        selected -> Triple(KashColors.Slate, KashColors.Slate, KashColors.OnSlate)
        else -> Triple(KashColors.SurfaceRaised, KashColors.Line, KashColors.Ink)
    }
    Row(
        modifier = modifier
            .kashClickable(role = Role.Button, onClick = onClick)
            .height(KashSize.ChipH)
            .clip(KashShapes.Chip)
            .background(if (dashed) Color.Transparent else bg)
            .then(
                if (dashed) Modifier.dashedBorder(KashColors.LineStrong, KashShapes.Chip)
                else Modifier.border(1.dp, line, KashShapes.Chip)
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) KashIconView(leading, size = 16.dp, tint = if (dashed) KashColors.InkMuted else fg)
        Text(
            text,
            style = KashType.Label,
            color = if (dashed) KashColors.InkMuted else fg,
            maxLines = 1,
            softWrap = false,
        )
        if (trailing != null) KashIconView(trailing, size = 16.dp, tint = fg)
    }
}

// ─── Switch ──────────────────────────────────────────────────────────────

/**
 * Kash.Switch: 46×28, acts at once. On = slate, off = line-strong.
 * [label] is the accessible name ("Track Telebirr").
 */
@Composable
fun KashSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 21.dp else 3.dp, tween(150), label = "knob")
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(KashShapes.Pill)
            .background(if (checked) KashColors.Slate else KashColors.LineStrong)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = 3.dp)
                .size(22.dp)
                .clip(KashShapes.Pill)
                .background(KashColors.SurfaceRaised)
        )
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

@Preview(name = "Buttons", widthDp = 360)
@Composable
private fun ButtonsPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KashButton("Allow SMS access", {}, Modifier.fillMaxWidth())
        KashButton("Open bank receipt", {}, Modifier.fillMaxWidth(), KashButtonVariant.Secondary, icon = KashIcon.External)
        KashButton("Creating your account…", {}, Modifier.fillMaxWidth(), busy = true)
        KashButton("Delete permanently", {}, Modifier.fillMaxWidth(), KashButtonVariant.Danger)
        KashButton("Save changes", {}, Modifier.fillMaxWidth(), enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KashButton("Skip", {}, variant = KashButtonVariant.Secondary)
            KashButton("Confirm as Family", {}, Modifier.weight(1f))
        }
        KashButton("Not now", {}, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
    }
}

@Preview(name = "Buttons on slate", widthDp = 360)
@Composable
private fun ButtonsOnSlatePreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Slate).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashIconButton(KashIcon.Back, "Back", {}, onSlate = true)
            Spacer(Modifier.weight(1f))
            KashIconButton(KashIcon.Plus, "Add transaction", {}, onSlate = true)
            KashIconButton(KashIcon.Settings, "Settings", {}, onSlate = true)
        }
        KashButton("Review", {}, Modifier.fillMaxWidth(), KashButtonVariant.Accent, badge = "4", compact = true)
        KashButton("Get started", {}, Modifier.fillMaxWidth(), KashButtonVariant.Accent)
        KashButton("I already have an account", {}, Modifier.fillMaxWidth(), KashButtonVariant.QuietOnSlate)
    }
}

@Preview(name = "Chips and switch", widthDp = 360)
@Composable
private fun ChipsPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashChip("Food", true, {})
            KashChip("Transport", false, {})
            KashChip("More", false, {}, trailing = KashIcon.ChevronRight)
        }
        KashChip("New category", false, {}, leading = KashIcon.Plus, dashed = true)
        Row(
            Modifier.background(KashColors.Slate).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KashChip("All", true, {}, onSlate = true)
            KashChip("In", false, {}, onSlate = true)
            KashChip("This month", false, {}, onSlate = true, trailing = KashIcon.ChevronDown)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            var a by remember { mutableStateOf(true) }
            var b by remember { mutableStateOf(false) }
            KashSwitch(a, { a = it }, "Track CBE")
            KashSwitch(b, { b = it }, "Track BoA")
        }
    }
}
