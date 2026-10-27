package com.financeapp.ui.kash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.KashPress
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num

/** A hairline divider in `line`. */
@Composable
fun KashDivider(modifier: Modifier = Modifier, color: Color = KashColors.Line) =
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)

// ─── TransactionRow ──────────────────────────────────────────────────────

/**
 * Kash.TransactionRow: arrow well, name, one line of *account · time* plus a
 * category pill, and the signed amount. Sits straight on the sheet, never in
 * a card. Leave out what the screen already says ([account] null on an
 * account's own page, [category] null on a category's page).
 */
@Composable
fun KashTransactionRow(
    name: String,
    amount: Double,
    direction: KashDirection,
    modifier: Modifier = Modifier,
    account: String? = null,
    time: String? = null,
    category: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val tone = when (direction) {
        KashDirection.In -> KashColors.MoneyIn
        KashDirection.Out -> KashColors.MoneyOut
        KashDirection.Transfer -> KashColors.MoneyNeutral
    }
    val icon = when (direction) {
        KashDirection.In -> KashIcon.In
        KashDirection.Out -> KashIcon.Out
        KashDirection.Transfer -> KashIcon.Transfer
    }
    val sub = listOfNotNull(account, time).joinToString(" · ")
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.kashClickable(press = KashPress.Highlight, onClick = onClick) else Modifier)
                .padding(vertical = KashSpace.RowY),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(KashSize.Well).clip(KashShapes.Field).background(KashColors.Well),
                contentAlignment = Alignment.Center,
            ) { KashIconView(icon, size = 17.dp, tint = tone, strokeWidth = 2.2f) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name, style = KashType.Body, color = KashColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sub.isNotEmpty() || category != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Account · time keeps its width; a long category pill truncates.
                        if (sub.isNotEmpty()) {
                            Text(sub, style = KashType.Caption.num(), color = KashColors.InkMuted, maxLines = 1, softWrap = false)
                        }
                        if (category != null) KashPill(category, Modifier.weight(1f, fill = false))
                    }
                }
            }
            Text(
                KashMoney.signed(amount, direction),
                style = KashType.BodyStrong.num(),
                color = tone,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (showDivider) KashDivider()
    }
}

/** The small category pill inside a row. */
@Composable
fun KashPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(20.dp)
            .clip(KashShapes.Pill)
            .background(KashColors.Well)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = KashType.Micro.copy(fontWeight = FontWeight.Medium), color = KashColors.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kash.SkeletonRow lives in KashFeedback.kt; DayHeader is here. */
@Composable
fun KashDayHeader(label: String, modifier: Modifier = Modifier, net: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp)
            .semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = KashType.Caption.copy(fontWeight = FontWeight.Medium)
        Text(label, style = style, color = KashColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (net != null) Text(net, style = style.num(), color = KashColors.InkMuted, maxLines = 1)
    }
}

// ─── KeyValueList ────────────────────────────────────────────────────────

data class KashKeyValue(val label: String, val value: String, val numeric: Boolean = false)

/**
 * Kash.KeyValueList: up to four facts. [card] = white card with a hairline;
 * false = plain rows (inside another card, as on the review card).
 */
@Composable
fun KashKeyValueList(items: List<KashKeyValue>, modifier: Modifier = Modifier, card: Boolean = true) {
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (card) Modifier
                    .clip(KashShapes.Card)
                    .background(KashColors.SurfaceRaised)
                    .border(1.dp, KashColors.Line, KashShapes.Card)
                else Modifier
            )
    ) {
        items.forEachIndexed { i, it ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (card) 16.dp else 0.dp, vertical = if (card) 13.dp else 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(it.label, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.InkMuted)
                Text(
                    it.value,
                    style = (if (it.numeric) KashType.Body.num() else KashType.Body),
                    color = KashColors.Ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
            if (i < items.lastIndex) KashDivider()
        }
    }
}

// ─── ListGroup / ListRow ─────────────────────────────────────────────────

/** Kash.ListGroup: a white card of [KashListRow]s (Settings and menus). */
@Composable
fun KashListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card),
        content = content,
    )
}

/**
 * Kash.ListRow: icon, label, optional [hint] line, optional [value] ("On",
 * "3") and a chevron. [trailing] replaces value + chevron (e.g. a switch).
 */
@Composable
fun KashListRow(
    label: String,
    modifier: Modifier = Modifier,
    icon: KashIcon? = null,
    hint: String? = null,
    value: String? = null,
    showDivider: Boolean = true,
    labelColor: Color = KashColors.Ink,
    /** Side padding; 0 when the row sits straight on a sheet (period sheet). */
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.kashClickable(press = KashPress.Highlight, onClick = onClick) else Modifier)
                .padding(horizontal = inset, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) KashIconView(icon, size = 20.dp, tint = labelColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = KashType.Body, color = labelColor)
                if (hint != null) Text(hint, style = KashType.Caption, color = KashColors.InkMuted)
            }
            if (trailing != null) {
                trailing()
            } else {
                if (value != null) Text(value, style = KashType.Label.copy(fontWeight = FontWeight.Normal).num(), color = KashColors.InkMuted)
                if (onClick != null) KashIconView(KashIcon.ChevronRight, size = 16.dp, tint = KashColors.Ink)
            }
        }
        if (showDivider) KashDivider()
    }
}

// ─── RadioRow ────────────────────────────────────────────────────────────

/** Kash.RadioRow: one choice of many; the picked row is bold with a slate dot. */
@Composable
fun KashRadioRow(label: String, checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = checked,
                    role = Role.RadioButton,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(KashShapes.Pill)
                    .border(2.dp, if (checked) KashColors.Slate else KashColors.LineStrong, KashShapes.Pill),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Box(Modifier.size(10.dp).clip(KashShapes.Pill).background(KashColors.Slate))
            }
            Text(
                label,
                style = if (checked) KashType.BodyStrong else KashType.Body,
                color = KashColors.Ink,
            )
        }
        KashDivider()
    }
}

// ─── Notice ──────────────────────────────────────────────────────────────

/** Kash.Notice: a quiet one- or two-line note on the sheet, in the well colour. */
@Composable
fun KashNotice(text: String, modifier: Modifier = Modifier, icon: KashIcon? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(KashShapes.Field)
            .background(KashColors.Well)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) KashIconView(icon, size = 18.dp, tint = KashColors.InkMuted, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = KashType.Caption, color = KashColors.Ink)
    }
}

// ─── EmptyState ──────────────────────────────────────────────────────────

/**
 * Kash.EmptyState: icon in a well, a plain title, one line of why, and at
 * most one secondary button (about 200 wide). Centre it where the list goes.
 */
@Composable
fun KashEmptyState(
    icon: KashIcon,
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(KashShapes.Control).background(KashColors.Well),
            contentAlignment = Alignment.Center,
        ) { KashIconView(icon, size = 26.dp, tint = KashColors.InkMuted) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = KashType.TitleS, color = KashColors.Ink, textAlign = TextAlign.Center)
            if (text != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text,
                    style = KashType.Body.copy(fontWeight = FontWeight.Normal),
                    color = KashColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (action != null) Box(Modifier.widthIn(min = 200.dp)) { action() }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

@Preview(name = "Transaction rows", widthDp = 360)
@Composable
private fun RowsPreview() = KashTheme {
    Column(Modifier.background(KashColors.Surface).padding(horizontal = 22.dp)) {
        KashDayHeader("Today", net = "−737.00")
        KashTransactionRow("bekelu beyene", 160.0, KashDirection.In, account = "Telebirr", time = "12:36", category = "Family", onClick = {})
        KashTransactionRow("Telecom package: Daily Internet Pack 1GB + 50 min", 100.0, KashDirection.Out, account = "Telebirr", time = "12:36", category = "Airtime", onClick = {})
        KashTransactionRow("CBE → Telebirr", 500.0, KashDirection.Transfer, account = "CBE", time = "9:02", category = "Own accounts", onClick = {})
        KashTransactionRow("Ride Addis", 600.0, KashDirection.Out, account = "Telebirr", time = "18:40", onClick = {})
    }
}

@Preview(name = "Lists, notice, empty", widthDp = 360)
@Composable
private fun ListsPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KashKeyValueList(
            listOf(
                KashKeyValue("To", "Ethio Telecom"),
                KashKeyValue("Account", "Telebirr · •••• 3121", numeric = true),
                KashKeyValue("When", "Today, 12:36"),
                KashKeyValue("Balance after", "616.48", numeric = true),
            )
        )
        KashListGroup {
            KashListRow("Privacy and data", icon = KashIcon.Eye, onClick = {})
            KashListRow("Notifications", icon = KashIcon.Sms, value = "On", onClick = {})
            KashListRow("Email support", icon = KashIcon.External, showDivider = false, onClick = {})
        }
        KashNotice("Looks like a move to your own CBE account. Own-account moves aren't counted as spending.", icon = KashIcon.Transfer)
        Column {
            KashRadioRow("This week", false, {})
            KashRadioRow("This month", true, {})
        }
        KashEmptyState(KashIcon.Search, "Nothing matches", "No money came in from \"Yod Abyssinia\" this month. Try Out, or a longer period.") {
            KashButton("Clear filters", {}, variant = KashButtonVariant.Secondary)
        }
    }
}
