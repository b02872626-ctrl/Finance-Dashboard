package com.financeapp.ui.screens

import com.financeapp.ui.kash.components.dashedBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.LocalKashTips
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.TransactionEntity
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.accountShort
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDivider
import com.financeapp.ui.kash.components.KashSearchField
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSwitch
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.ui.kash.displayName
import com.financeapp.ui.kash.kashDirection
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem

/**
 * The sort card (Review, a transaction's category, Add transaction): the
 * transaction being sorted, a search box, the user's own categories most
 * used first, and "Add '<name>'" when nothing matches. One tap sorts.
 * Ordering and search rules live in [SortCard].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortCardSheet(
    /** The transaction being sorted; null on Add transaction. */
    tx: TransactionEntity?,
    /** The category it has now (or the suggestion), shown picked. */
    current: String?,
    custom: List<String>,
    usage: List<SortCard.Use>,
    recent: List<String>,
    /** Offer "Own accounts" at the end: the move looks like one between the user's accounts. */
    /** The "New payments from X too" switch; null hides it. */
    applyToSimilar: Boolean,
    onToggleApplyToSimilar: (() -> Unit)?,
    onClose: () -> Unit,
    /** An existing category was tapped. */
    onPick: (String) -> Unit,
    /** "Add 'name'" was tapped: make the category, then sort with it. */
    onCreate: (String) -> Unit,
    title: String = "Change category",
) {
    val cal = LocalCalendarSystem.current
    var query by remember { mutableStateOf("") }
    val yours = remember(custom, usage, recent, current) {
        val list = SortCard.yours(custom, usage, recent)
        // A suggestion not used anywhere yet still shows, picked, so one tap confirms it.
        if (current != null && list.none { it.equals(current, true) }) listOf(current) + list else list
    }
    val search = remember(query, yours) { SortCard.search(query, yours) }
    val name = tx?.counterparty?.trim()?.takeIf { it.isNotBlank() && !tx.merchantKey.isNullOrBlank() }
    // Money you sent goes "to" someone; money in comes "from" them.
    val similarLabel = if (tx?.kashDirection() == com.financeapp.ui.kash.components.KashDirection.In) "New payments from $name too" else "New payments to $name too"

    val lead: (@Composable ColumnScope.() -> Unit)? = if (tx == null) null else {
        {
            KashTransactionRow(
                name = tx.displayName(),
                amount = tx.amount,
                direction = tx.kashDirection(),
                account = tx.accountShort(),
                time = KashDates.whenShort(tx.dateTime, cal),
                showDivider = false,
            )
        }
    }
    // First time on the sort card: two tips, drawn here (the sheet is its own window).
    val tips = LocalKashTips.current
    var tipStep by remember { mutableStateOf(if (tips != null && !tips.seen(AppTips.SORTING_KEY)) 0 else -1) }
    val showSwitch = name != null && onToggleApplyToSimilar != null
    val tipCount = if (showSwitch) 2 else 1
    fun endTips() { tips?.markSeen(AppTips.SORTING_KEY); tipStep = -1 }

    KashSheet(title, onDismiss = onClose, compact = true, lead = lead) {
        if (tipStep >= 0) {
            val last = tipStep >= tipCount - 1
            InlineTip(
                count = if (tipCount > 1) "SORTING · ${tipStep + 1} OF $tipCount" else "SORTING",
                title = if (tipStep == 0) "Your own categories" else "Same person next time",
                body = if (tipStep == 0) "Type any name. If it’s new, Kash adds it."
                else "Leave this on and new payments to $name sort themselves.",
                next = if (last) "Got it" else "Next",
                onNext = { if (last) endTips() else tipStep++ },
                onSkip = if (tipCount > 1) ::endTips else null,
            )
        }
        // Typed a name that isn't one yet: make it and sort in one tap, right by the box.
        val create: (() -> Unit)? = search.addName?.let { add -> { onCreate(add) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashSearchField(query, { query = it }, "Find or add a category", Modifier.weight(1f))
            if (create != null) {
                KashButton("Create and sort", create, variant = KashButtonVariant.Accent, compact = true)
            }
        }
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (yours.isEmpty() && query.isBlank()) {
                // First time (approved FD_RvSortFirst): a dashed box, no list label.
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append("No categories yet. Type one, like ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = KashColors.Ink, fontWeight = FontWeight.SemiBold)); append("Rent"); pop()
                        append(" or ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = KashColors.Ink, fontWeight = FontWeight.SemiBold)); append("Food"); pop()
                        append(".")
                    },
                    style = KashType.Label, color = KashColors.InkMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .dashedBorder(KashColors.LineStrong, RoundedCornerShape(16.dp)).padding(16.dp),
                )
            } else Text(
                if (query.isNotBlank()) "Matches" else "Your categories · most used first",
                style = KashType.Label, color = KashColors.InkMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                search.matches.forEach { c ->
                    KashChip(c, c.equals(current, true), { onPick(c) }, checkWhenSelected = true)
                }
            }
        }
        if (name != null && onToggleApplyToSimilar != null) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KashDivider()
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            similarLabel, style = KashType.BodyStrong, color = KashColors.Ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text("Sorts them as they come in", style = KashType.Caption, color = KashColors.InkMuted)
                    }
                    KashSwitch(applyToSimilar, { onToggleApplyToSimilar() }, similarLabel)
                }
            }
        }
    }
}

/** A tip drawn inside a sheet: the sand card with Skip / Next, then Got it. */
@Composable
private fun InlineTip(count: String, title: String, body: String, next: String, onNext: () -> Unit, onSkip: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(KashColors.Sand).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(count, style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold), color = KashColors.SlateInk.copy(alpha = 0.7f))
        Text(title, style = KashType.BodyStrong.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = KashColors.SlateInk)
        Text(body, style = KashType.Body.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp), color = KashColors.SlateInk.copy(alpha = 0.85f))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onSkip != null) {
                Text(
                    "Skip", style = KashType.Label, color = KashColors.SlateInk.copy(alpha = 0.7f),
                    modifier = Modifier.kashClickable(onClickLabel = "Skip the tips", onClick = onSkip).padding(vertical = 10.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                next, style = KashType.Label.copy(fontWeight = FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(KashColors.SlateInk)
                    .kashClickable(onClick = onNext).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}
