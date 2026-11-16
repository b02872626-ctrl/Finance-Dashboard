package com.financeapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.kashSliderIndicator
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.viewmodel.ShowOnly
import com.financeapp.ui.kash.components.LocalKashTips
import com.financeapp.ui.kash.components.tipAnchor
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
import androidx.compose.ui.unit.sp
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
 * All transactions (approved FD_AllTx / FD_Filters), reached from Home's
 * "See all". Search and filters live here and nowhere else: the search
 * box with the filters button, then All / In / Out and the month,
 * account, category and amount chips, then the list by day with each
 * day's net. The chips and the button open the Filters sheet.
 */
@Composable
fun HistoryScreen(
    vm: TransactionsViewModel,
    onBack: () -> Unit,
    onTxClick: (Long) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val groups by vm.transactions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val filter by vm.filter.collectAsState()
    val options by vm.filterOptions.collectAsState()
    val cal = LocalCalendarSystem.current
    val focus = LocalFocusManager.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }

    var periodOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var datesOpen by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    // Load ten more days when the end of the list comes into view.
    val nearEnd by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 3 } == true } }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }
    // First visit: the All transactions tips (approved "Tip · search / filters").
    val tips = LocalKashTips.current
    val tipsLife = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(loaded) { if (loaded) { kotlinx.coroutines.delay(600); tips?.offerOn(tipsLife, AppTips.allTransactions) } }

    KashScreen(
        heroGap = 16.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KashIconButton(KashIcon.Back, "Back", onBack, onSlate = true)
                Text("All transactions", style = KashType.TitleS.copy(fontSize = 20.sp), color = KashColors.OnSlate, modifier = Modifier.weight(1f))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(top = 4.dp).tipAnchor("search"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KashSearchField(filter.searchQuery, vm::setSearch, "Search name, amount or reference", Modifier.weight(1f))
                FiltersButton(on = filter.sheetFilterCount > 0) { focus.clearFocus(); filtersOpen = true }
            }
            Row(
                Modifier.padding(top = 10.dp, bottom = 4.dp).tipAnchor("filters").horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionWell(filter.direction) { focus.clearFocus(); vm.setDirection(it) }
                KashChip(periodChipLabel(filter, cal), filter.range != HistoryRange.MONTH, { focus.clearFocus(); periodOpen = true }, trailing = KashIcon.ChevronDown, showInReplay = true)
                KashChip(pickLabel("Account", filter.accounts.map { kashBank(it).shortName }), filter.accounts.isNotEmpty(), { focus.clearFocus(); filtersOpen = true }, trailing = KashIcon.ChevronDown)
                KashChip(pickLabel("Category", filter.categories.toList()), filter.categories.isNotEmpty(), { focus.clearFocus(); filtersOpen = true }, trailing = KashIcon.ChevronDown)
                KashChip(amountLabel(filter), filter.minAmount != null || filter.maxAmount != null, { focus.clearFocus(); filtersOpen = true }, trailing = KashIcon.ChevronDown)
            }
            when {
                !loaded && groups.isEmpty() -> Column {
                    KashSkeleton(Modifier.padding(top = 14.dp, bottom = 6.dp), width = 64.dp, height = 10.dp)
                    repeat(4) { KashSkeletonRow() }
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
    }

    if (periodOpen) {
        KashSheet("Show transactions from", onDismiss = { periodOpen = false }) {
            Column {
                listOf(HistoryRange.WEEK, HistoryRange.MONTH, HistoryRange.LAST_MONTH, HistoryRange.LAST_3, HistoryRange.YEAR, HistoryRange.ALL).forEach { r ->
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
    if (filtersOpen) {
        FiltersSheet(
            initial = filter,
            banks = options.first,
            categories = options.second,
            count = vm::countFor,
            onApply = { vm.setFilter(it); filtersOpen = false },
            onDismiss = { filtersOpen = false },
        )
    }
}

/** The 46dp filters button beside the search box; a sand dot while any sheet filter is on. */
@Composable
private fun FiltersButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .kashClickable(onClickLabel = "All filters", onClick = onClick)
            .size(46.dp)
            .clip(KashShapes.Field)
            .background(KashColors.Well),
        contentAlignment = Alignment.Center,
    ) {
        KashIconView(KashIcon.Filters, size = 18.dp, tint = KashColors.Ink, contentDescription = "All filters")
        if (on) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(KashShapes.Pill).background(KashColors.Sand))
    }
}

/** All / In / Out on the well (approved FD_AllTx). */
@Composable
private fun DirectionWell(direction: TxDirection?, onPick: (TxDirection?) -> Unit) {
    val slider = com.financeapp.ui.kash.rememberKashSlider()
    val options = listOf<Pair<String, TxDirection?>>("All" to null, "In" to TxDirection.IN, "Out" to TxDirection.OUT)
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(KashColors.Well).padding(3.dp)
            .kashSliderIndicator(slider, options.indexOfFirst { it.second == direction }, KashColors.SurfaceRaised, 9.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, (label, d) ->
            val on = direction == d
            Box(
                with(slider) { Modifier.slot(i) }
                    .height(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { onPick(d) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = KashType.Caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) KashColors.Ink else KashColors.InkMuted, maxLines = 1,
                )
            }
        }
    }
}

/** "Account", "CBE", or "CBE +1". */
internal fun pickLabel(none: String, picked: List<String>): String = when (picked.size) {
    0 -> none
    1 -> picked.first()
    else -> "${picked.first()} +${picked.size - 1}"
}

/** "Amount", "100–500", "From 100", "Up to 500". */
internal fun amountLabel(f: TransactionsFilter): String {
    fun n(v: Double) = KashMoney.whole(v)
    val lo = f.minAmount; val hi = f.maxAmount
    return when {
        lo != null && hi != null -> "${n(lo)}–${n(hi)}"
        lo != null -> "From ${n(lo)}"
        hi != null -> "Up to ${n(hi)}"
        else -> "Amount"
    }
}

/**
 * All filters (approved FD_Filters): when, accounts, category, amount and
 * "show only", applied together with "Show N transactions". Reset clears
 * them back to this month.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(
    initial: TransactionsFilter,
    banks: List<String>,
    categories: List<String>,
    count: suspend (TransactionsFilter) -> Int,
    onApply: (TransactionsFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = LocalCalendarSystem.current
    var draft by remember { mutableStateOf(initial) }
    var allCats by remember { mutableStateOf(false) }
    var datesOpen by remember { mutableStateOf(false) }
    var min by remember { mutableStateOf(initial.minAmount?.let { KashMoney.whole(it).replace(",", "") }.orEmpty()) }
    var max by remember { mutableStateOf(initial.maxAmount?.let { KashMoney.whole(it).replace(",", "") }.orEmpty()) }
    var shown by remember { mutableStateOf<Int?>(null) }
    // Waits for typing to pause before counting again.
    LaunchedEffect(draft) { kotlinx.coroutines.delay(250); shown = count(draft) }
    fun setAmounts(lo: String, hi: String) {
        min = lo.filter { it.isDigit() || it == '.' }; max = hi.filter { it.isDigit() || it == '.' }
        draft = draft.copy(minAmount = min.toDoubleOrNull(), maxAmount = max.toDoubleOrNull())
    }

    KashSheet("Filters", onDismiss = onDismiss) {
        Column(
            Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Reset", style = KashType.Label, color = KashColors.InkMuted,
                modifier = Modifier.align(Alignment.End).kashClickable(onClickLabel = "Reset filters", onClick = {
                    draft = TransactionsFilter(sender = draft.sender, type = draft.type, searchQuery = draft.searchQuery, direction = draft.direction); min = ""; max = ""
                }).padding(vertical = 4.dp),
            )
            FilterLabel("When")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HistoryRange.MONTH, HistoryRange.LAST_MONTH, HistoryRange.LAST_3).forEach { r ->
                    KashChip(r.label, draft.range == r, { draft = draft.copy(range = r) }, showInReplay = true)
                }
                KashChip(
                    if (draft.range == HistoryRange.CUSTOM) rangeLabel(draft.fromMs, draft.toMs - 1, cal) else "Pick dates",
                    draft.range == HistoryRange.CUSTOM, { datesOpen = true }, leading = KashIcon.Calendar,
                )
            }
            if (banks.size > 1) {
                FilterLabel("Accounts")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    banks.forEach { b ->
                        val on = b in draft.accounts
                        KashChip(kashBank(b).shortName, on, { draft = draft.copy(accounts = if (on) draft.accounts - b else draft.accounts + b) }, checkWhenSelected = true)
                    }
                }
            }
            if (categories.isNotEmpty()) {
                FilterLabel("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shownCats = if (allCats) categories else categories.take(5)
                    shownCats.forEach { c ->
                        val on = c in draft.categories
                        KashChip(c, on, { draft = draft.copy(categories = if (on) draft.categories - c else draft.categories + c) }, checkWhenSelected = true)
                    }
                    if (!allCats && categories.size > 5) KashChip("All categories ›", false, { allCats = true }, showInReplay = true)
                }
            }
            FilterLabel("Amount (ETB)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KashTextField(min, { setAmounts(it, max) }, "", Modifier.weight(1f), placeholder = "Min", keyboardType = KeyboardType.Decimal)
                Text("–", color = KashColors.InkFaint)
                KashTextField(max, { setAmounts(min, it) }, "", Modifier.weight(1f), placeholder = "Max", keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
            }
            FilterLabel("Show only")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShowOnly.entries.forEach { o ->
                    val on = o in draft.showOnly
                    KashChip(o.label, on, { draft = draft.copy(showOnly = if (on) draft.showOnly - o else draft.showOnly + o) }, checkWhenSelected = true, showInReplay = true)
                }
            }
        }
        val n = shown
        KashButton(
            when (n) { null -> "Show transactions"; 1 -> "Show 1 transaction"; else -> "Show ${KashMoney.whole(n.toDouble())} transactions" },
            { onApply(draft) }, Modifier.fillMaxWidth().padding(top = 10.dp), KashButtonVariant.Accent,
            enabled = n != 0,
        )
    }
    if (datesOpen) {
        val custom = draft.range == HistoryRange.CUSTOM
        DateRangeDialog(
            calendarSystem = cal,
            initialFrom = if (custom) draft.fromMs else null,
            initialTo = if (custom) draft.toMs else null,
            onDismiss = { datesOpen = false },
            onApply = { from, to -> datesOpen = false; draft = draft.copy(fromMs = from, toMs = to, range = HistoryRange.CUSTOM) },
        )
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(text, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = KashColors.InkMuted, modifier = Modifier.padding(top = 10.dp))
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
        HistoryRange.LAST_3 -> " in the last 3 months"
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
