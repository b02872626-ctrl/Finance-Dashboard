package com.financeapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTxRow
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashRadioRow
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSearchField
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSkeleton
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.dayNet
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.HistoryRange
import com.financeapp.viewmodel.TransactionsFilter
import com.financeapp.viewmodel.TransactionsViewModel
import com.financeapp.viewmodel.TxDirection

/**
 * History tab (AppHistory, AppHistoryLoading, AppHistoryEmpty,
 * AppHistoryPeriod, AppHistoryDate, AppHistoryEC, AppSmallHistory).
 * Export moved to Settings.
 */
@Composable
fun HistoryScreen(
    vm: TransactionsViewModel,
    tabBar: @Composable () -> Unit,
    onTxClick: (Long) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val groups by vm.transactions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val filter by vm.filter.collectAsState()
    val cal = LocalCalendarSystem.current
    val focus = LocalFocusManager.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }

    var periodOpen by remember { mutableStateOf(false) }
    var datesOpen by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    // Load ten more days when the end of the list comes into view.
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    KashScreen(
        tabBar = tabBar,
        heroGap = 16.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = KashType.TitleM, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                KashIconButton(KashIcon.Calendar, "Pick dates", { datesOpen = true }, onSlate = true)
            }
            KashSearchField(filter.searchQuery, vm::setSearch, "Search name, category, reference", onSlate = true)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KashChip("All", filter.direction == null, { focus.clearFocus(); vm.setDirection(null) }, onSlate = true)
                KashChip("In", filter.direction == TxDirection.IN, { focus.clearFocus(); vm.setDirection(TxDirection.IN) }, onSlate = true)
                KashChip("Out", filter.direction == TxDirection.OUT, { focus.clearFocus(); vm.setDirection(TxDirection.OUT) }, onSlate = true)
                KashChip(
                    periodChipLabel(filter, cal), false, { focus.clearFocus(); periodOpen = true },
                    onSlate = true, trailing = KashIcon.ChevronDown,
                )
            }
        },
    ) { pad ->
        when {
            !loaded && groups.isEmpty() -> Column {
                KashSkeleton(Modifier.padding(top = 14.dp, bottom = 6.dp), width = 64.dp, height = 10.dp)
                repeat(4) { KashSkeletonRow() }
                KashSkeleton(Modifier.padding(top = 14.dp, bottom = 6.dp), width = 80.dp, height = 10.dp)
                repeat(2) { KashSkeletonRow() }
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                KashEmptyState(KashIcon.Search, "Nothing matches", emptyText(filter, cal)) {
                    KashButton("Clear filters", { vm.clearFilters() }, Modifier.width(200.dp), KashButtonVariant.Secondary)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = pad) {
                groups.forEach { g ->
                    val day = g.transactions.first().dateTime
                    item(key = "h${g.dateKey}") { KashDayHeader(KashDates.dayHeader(day, cal), net = dayNet(g.transactions)) }
                    items(g.transactions, key = { it.id }) { tx -> KashTxRow(tx, onClick = { onTxClick(tx.id) }) }
                }
            }
        }
    }

    if (periodOpen) {
        KashSheet("Show transactions from", onDismiss = { periodOpen = false }) {
            Column {
                listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.LAST_MONTH, HistoryRange.YEAR, HistoryRange.ALL).forEach { r ->
                    KashRadioRow(r.label, filter.range == r, { vm.setRange(r); periodOpen = false })
                }
                KashListRow(
                    "Pick dates…", icon = KashIcon.Calendar, showDivider = false, inset = 0.dp,
                    onClick = { periodOpen = false; datesOpen = true },
                )
            }
        }
    }
    if (datesOpen) {
        val custom = filter.range == HistoryRange.CUSTOM
        DateRangeDialog(
            calendarSystem = cal,
            initialFrom = if (custom) filter.fromMs else null,
            initialTo = if (custom) filter.toMs else null,
            onDismiss = { datesOpen = false },
            onApply = { from, to -> datesOpen = false; vm.setDateFilter(from, to) },
        )
    }
}

/** "This month" (Gregorian) / "Tikimt" (Ethiopian) / "20 – 26 Oct". */
private fun periodChipLabel(f: TransactionsFilter, cal: String): String = when (f.range) {
    HistoryRange.MONTH -> if (cal == "ETHIOPIAN") KashDates.monthName(System.currentTimeMillis(), cal) else "This month"
    HistoryRange.CUSTOM -> rangeLabel(f.fromMs, f.toMs - 1, cal)
    else -> f.range.label
}

/** Says what the filters ruled out, and what to try. */
private fun emptyText(f: TransactionsFilter, cal: String): String {
    val q = f.searchQuery.trim()
    val what = when (f.direction) {
        TxDirection.IN -> "No money came in" + if (q.isNotEmpty()) " from “$q”" else ""
        TxDirection.OUT -> "No money went out" + if (q.isNotEmpty()) " to “$q”" else ""
        null -> if (q.isNotEmpty()) "Nothing matches “$q”" else "No transactions"
    }
    val period = when (f.range) {
        HistoryRange.WEEK -> " this week"
        HistoryRange.MONTH -> " this month"
        HistoryRange.LAST_MONTH -> " last month"
        HistoryRange.YEAR -> " this year"
        HistoryRange.ALL -> ""
        HistoryRange.CUSTOM -> " on these days"
    }
    val tip = when {
        f.direction == TxDirection.IN -> " Try Out, or a longer period."
        f.direction == TxDirection.OUT -> " Try In, or a longer period."
        f.range != HistoryRange.ALL -> " Try a longer period."
        else -> ""
    }
    return "$what$period.$tip"
}
