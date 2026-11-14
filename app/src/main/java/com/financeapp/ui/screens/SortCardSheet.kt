package com.financeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
    offerOwnAccounts: Boolean,
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
    val yours = remember(custom, usage, recent, offerOwnAccounts, current) {
        val list = SortCard.yours(custom, usage, recent, offerOwnAccounts)
        // A suggestion not used anywhere yet still shows, picked, so one tap confirms it.
        if (current != null && list.none { it.equals(current, true) }) listOf(current) + list else list
    }
    val search = remember(query, yours) { SortCard.search(query, yours) }
    val name = tx?.counterparty?.trim()?.takeIf { it.isNotBlank() && !tx.merchantKey.isNullOrBlank() }

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
    KashSheet(title, onDismiss = onClose, compact = true, lead = lead) {
        KashSearchField(query, { query = it }, "Find or add a category")
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    query.isNotBlank() -> "Matches"
                    yours.isEmpty() -> "Your categories"
                    else -> "Your categories · most used first"
                },
                style = KashType.Label, color = KashColors.InkMuted,
            )
            if (yours.isEmpty() && query.isBlank()) {
                Text(
                    "None yet. Type a name above and Kash makes it.",
                    style = KashType.Caption, color = KashColors.InkMuted,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                search.matches.forEach { c ->
                    KashChip(c, c.equals(current, true), { onPick(c) }, checkWhenSelected = true)
                }
                search.addName?.let { add ->
                    KashChip("Add “$add”", false, { onCreate(add) }, leading = KashIcon.Plus, dashed = true)
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
                            "New payments from $name too", style = KashType.BodyStrong, color = KashColors.Ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text("Sorts them as they come in", style = KashType.Caption, color = KashColors.InkMuted)
                    }
                    KashSwitch(applyToSimilar, { onToggleApplyToSimilar() }, "New payments from $name too")
                }
            }
        }
    }
}
