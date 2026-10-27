package com.financeapp.ui.kash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num

/*
 * Kash.PinPad, always on slate. Split into dots, message and keys so a PIN
 * screen can centre the dots and pin the keypad to the bottom (AppUnlock);
 * [KashPinPad] stacks all three for simple uses.
 */

/** The PIN dots: filled white, empty rings. [offset] shakes them on a wrong PIN. */
@Composable
fun KashPinDots(filled: Int, modifier: Modifier = Modifier, length: Int = 4, offset: Dp = 0.dp) {
    Row(
        modifier
            .offset(x = offset)
            .clearAndSetSemantics { contentDescription = "$filled of $length digits entered" },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(length) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(KashShapes.Pill)
                    .then(
                        if (i < filled) Modifier.background(KashColors.OnSlate)
                        else Modifier.border(2.dp, KashColors.GhostLine, KashShapes.Pill)
                    )
            )
        }
    }
}

/** An error line under the dots ("PINs didn't match. Start again."). */
@Composable
fun KashPinMessage(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KashIconView(KashIcon.Info, size = 16.dp, tint = KashColors.OnSlate, strokeWidth = 2.2f)
        Text(message, style = KashType.Label, color = KashColors.OnSlate, textAlign = TextAlign.Center)
    }
}

/** The lockout countdown chip that replaces the message ("Try again in 0:24"). */
@Composable
fun KashPinCountdown(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(KashShapes.Chip)
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, KashShapes.Chip)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) { Text(text, style = KashType.Label.num(), color = KashColors.OnSlate) }
}

/** The 3×4 keypad. [disabled] fades it to 35% and ignores taps (lockout). */
@Composable
fun KashPinKeys(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.35f else 1f)
            .semantics { contentDescription = if (disabled) "Keypad, locked" else "Keypad" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("123", "456", "789", " 0<").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val keyModifier = Modifier.weight(1f).height(64.dp)
                    when (k) {
                        ' ' -> Spacer(keyModifier)
                        '<' -> Box(
                            keyModifier
                                .then(if (disabled) Modifier else Modifier.kashClickable(onClickLabel = "Delete last digit", onClick = onDelete))
                                .clip(KashShapes.Control)
                                .semantics { contentDescription = "Delete last digit" },
                            contentAlignment = Alignment.Center,
                        ) { KashIconView(KashIcon.Backspace, size = 26.dp, tint = KashColors.OnSlate) }
                        else -> Box(
                            keyModifier
                                .then(if (disabled) Modifier else Modifier.kashClickable { onDigit(k) })
                                .clip(KashShapes.Control)
                                .background(KashColors.GhostFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k.toString(),
                                style = KashType.TitleM.copy(fontSize = 24.sp, letterSpacing = 0.sp).num(),
                                color = KashColors.OnSlate,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dots, message (or countdown) and keypad stacked. */
@Composable
fun KashPinPad(
    filled: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
    message: String? = null,
    countdown: String? = null,
    disabled: Boolean = false,
    dotsOffset: Dp = 0.dp,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        KashPinDots(filled, length = length, offset = dotsOffset)
        Box(Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                countdown != null -> KashPinCountdown(countdown)
                message != null -> KashPinMessage(message)
            }
        }
        KashPinKeys(onDigit, onDelete, disabled = disabled)
    }
}

@Preview(name = "PIN pad", widthDp = 390)
@Composable
private fun PinPadPreview() = KashTheme {
    Column(Modifier.background(KashColors.Slate).padding(22.dp)) {
        KashPinPad(filled = 4, onDigit = {}, onDelete = {}, message = "PINs didn't match. Start again.")
    }
}

@Preview(name = "PIN pad locked", widthDp = 390)
@Composable
private fun PinPadLockedPreview() = KashTheme {
    Column(Modifier.background(KashColors.Slate).padding(22.dp)) {
        KashPinPad(filled = 0, onDigit = {}, onDelete = {}, countdown = "Try again in 0:24", disabled = true)
    }
}
