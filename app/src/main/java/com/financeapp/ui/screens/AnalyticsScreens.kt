package com.financeapp.ui.screens

import com.financeapp.ui.kash.rememberGrow
import com.financeapp.ui.kash.kashEnter
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTxRow
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashDivider
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashRadioRow
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSkeleton
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.dayNet
import com.financeapp.ui.kash.groupByDay
import com.financeapp.ui.kash.KashPress
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.MonthReport
import kotlin.math.roundToInt

/**
 * Analytics tab (AppAnalytics, AppAnalyticsMore, AppAnalyticsLoading,
 * AppAnalyticsEmpty). The "See all" links are left out by request.
 */
@Composable
fun AnalyticsTabScreen(
    vm: AnalyticsViewModel,
    tabBar: @Composable () -> Unit,
    onCategory: (String) -> Unit,
    onReview: () -> Unit,
    onAccount: (String) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val cal = LocalCalendarSystem.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }
    val r by vm.report.collectAsState()
    val months by vm.availableMonths.collectAsState()
    var monthPicker by remember { mutableStateOf(false) }
    val monthName = KashDates.monthName(r.fromMs + 86_400_000L, cal)

    KashScreen(
        tabBar = tabBar,
        heroGap = 18.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Analytics", style = KashType.TitleM, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                if (!r.isLoading) KashChip(monthName, false, { monthPicker = true }, onSlate = true, trailing = KashIcon.ChevronDown)
            }
            Column {
                Text(if (r.isLoading) "Spent this month" else "Spent in $monthName", style = KashType.Body, color = KashColors.OnSlateMuted)
                if (r.isLoading) KashSkeleton(Modifier.padding(top = 8.dp), width = 210.dp, height = 44.dp, radius = 10.dp, onSlate = true)
                else KashFigure(r.spent, Modifier.padding(top = 4.dp), countUp = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Money in", if (r.isLoading) null else KashMoney.whole(r.moneyIn), Modifier.weight(1f))
                StatTile("Left over", if (r.isLoading) null else leftOver(r), Modifier.weight(1f))
            }
        },
    ) { pad ->
        Crossfade(r.isLoading, label = "analytics") { loading ->
            if (loading) {
                AnalyticsSkeleton()
                return@Crossfade
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                if (r.categories.isEmpty()) {
                    WhereItGoesEmpty(r.uncategorizedAllTime, onReview)
                    MoneyInOut(r, cal)
                } else {
                    MoneyInOut(r, cal)
                    WhereItGoes(r, onCategory)
                }
                if (r.spent > 0) EveryDay(r, cal)
                if (r.topPayees.isNotEmpty()) WhereYouPayMost(r)
                if (r.perAccount.isNotEmpty()) SpentFrom(r, onAccount)
                if (r.uncategorized > 0 && r.categories.isNotEmpty()) {
                    KashListGroup {
                        KashListRow(
                            if (r.uncategorized == 1) "1 transaction needs a category" else "${r.uncategorized} transactions need a category",
                            hint = "They're left out of “Where it goes” until then",
                            showDivider = false,
                            onClick = onReview,
                        )
                    }
                }
            }
        }
    }

    if (monthPicker) {
        KashSheet("Show month", onDismiss = { monthPicker = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                months.forEach { m ->
                    KashRadioRow(
                        KashDates.monthYear(m + 86_400_000L, cal), m == r.fromMs,
                        { vm.setReportMonth(m + 86_400_000L); monthPicker = false },
                    )
                }
            }
        }
    }
}

private fun leftOver(r: MonthReport): String {
    val v = r.moneyIn - r.spent
    return (if (v < 0) KashMoney.MINUS else "") + KashMoney.whole(v)
}

@Composable
private fun StatTile(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(KashShapes.Control)
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, KashShapes.Control)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = KashType.Micro, color = KashColors.OnSlateMuted, maxLines = 1)
        if (value == null) KashSkeleton(Modifier.padding(top = 4.dp), width = 56.dp, height = 14.dp, onSlate = true)
        else Text(value, style = KashType.FigureS, color = KashColors.OnSlate, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Section(title: String, aside: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            if (aside != null) Text(aside, style = KashType.Caption.num(), color = KashColors.InkMuted)
        }
        content()
    }
}

@Composable
private fun MoneyInOut(r: MonthReport, cal: String) {
    val max = r.sixMonths.maxOfOrNull { maxOf(it.moneyIn, it.spent) }?.coerceAtLeast(1.0) ?: 1.0
    val last = r.sixMonths.lastOrNull()
    Section("Money in and out", "Last 6 months") {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(KashColors.MoneyIn, "In")
            Legend(KashColors.Slate, "Out")
        }
        Column(
            Modifier
                .padding(top = 12.dp)
                .semantics {
                    contentDescription = "Money in and out per month for the last six months." +
                        (last?.let { " ${KashDates.monthName(it.startMs + 86_400_000L, cal)}: ${KashMoney.whole(it.moneyIn)} in, ${KashMoney.whole(it.spent)} out." } ?: "")
                },
        ) {
            Row(Modifier.fillMaxWidth().height(112.dp)) {
                r.sixMonths.forEachIndexed { i, m ->
                    val grow = rememberGrow(r.fromMs, delayMs = i * 50, durationMs = 550)
                    Row(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Bar((m.moneyIn / max).toFloat(), KashColors.MoneyIn, grow)
                        Bar((m.spent / max).toFloat(), KashColors.Slate, grow)
                    }
                }
            }
            KashDivider(color = KashColors.LineStrong)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { }) {
                r.sixMonths.forEachIndexed { i, m ->
                    val current = i == r.sixMonths.lastIndex
                    Text(
                        KashDates.monthShort(m.startMs + 86_400_000L, cal),
                        style = KashType.Micro.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (current) KashColors.Ink else KashColors.InkMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, grow: Float = 1f) {
    Box(
        Modifier
            .width(14.dp)
            .fillMaxHeight((fraction.coerceIn(0.02f, 1f) * grow).coerceAtLeast(0.001f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color)
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = KashType.Caption, color = KashColors.InkMuted)
    }
}

@Composable
private fun Progress(fraction: Float, label: String, delayMs: Int = 0) {
    // Fills left to right when the section appears.
    val grow = rememberGrow(fraction, delayMs = delayMs, durationMs = 600)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(KashColors.Well)
            .semantics { contentDescription = label },
    ) { Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f) * grow).fillMaxHeight().background(KashColors.Slate)) }
}

@Composable
private fun WhereItGoes(r: MonthReport, onCategory: (String) -> Unit) {
    val total = r.categories.sumOf { it.total }.coerceAtLeast(1.0)
    val max = r.categories.first().total.coerceAtLeast(1.0)
    Section("Where it goes") {
        r.categories.forEachIndexed { i, c ->
            val pct = (c.total / total * 100).roundToInt()
            Column(
                Modifier
                    .fillMaxWidth()
                    .kashEnter(i, r.fromMs)
                    .kashClickable(press = KashPress.Highlight) { onCategory(c.category) }
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.category, style = KashType.Body, color = KashColors.Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(KashMoney.whole(c.total) + " ", style = KashType.BodyStrong.num(), color = KashColors.Ink)
                    Text("· $pct%", style = KashType.Caption.num(), color = KashColors.InkMuted)
                }
                Progress((c.total / max).toFloat(), "${c.category}: $pct% of categorised spending", delayMs = (i * 30).coerceAtMost(300))
            }
            KashDivider()
        }
    }
}

@Composable
private fun WhereItGoesEmpty(uncategorized: Int, onReview: () -> Unit) {
    Section("Where it goes") {
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(KashShapes.Card)
                .background(KashColors.SurfaceRaised)
                .border(1.dp, KashColors.Line, KashShapes.Card)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 0.7f, 0.45f).forEach { w ->
                    Box(Modifier.fillMaxWidth(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(KashColors.Well))
                }
            }
            Column {
                Text("Nothing has a category yet", style = KashType.BodyStrong, color = KashColors.Ink)
                Text(
                    "Review your transactions and this fills in: food, transport, bills and the rest.",
                    style = KashType.Caption, color = KashColors.InkMuted, modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (uncategorized > 0) {
                KashButton("Start reviewing", onReview, Modifier.fillMaxWidth(), badge = uncategorized.toString(), compact = true)
            }
        }
    }
}

@Composable
private fun EveryDay(r: MonthReport, cal: String) {
    val max = r.days.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val avg = r.spent / r.days.size.coerceAtLeast(1)
    val highIdx = r.days.indices.maxByOrNull { r.days[it] } ?: 0
    val highMs = r.fromMs + highIdx * 86_400_000L + 43_200_000L
    Section("Every day", "${KashMoney.whole(avg)} a day on average") {
        Column(
            Modifier.padding(top = 10.dp).semantics {
                contentDescription = "Spending per day. Highest: ${KashDates.dayMonth(highMs, cal)}, ${KashMoney.whole(r.days[highIdx])}."
            },
        ) {
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                r.days.forEachIndexed { i, v ->
                    val last = r.isCurrent && i == r.days.lastIndex
                    val grow = rememberGrow(r.fromMs, delayMs = i * 12, durationMs = 450)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(((v / max).toFloat().coerceIn(0.03f, 1f) * grow).coerceAtLeast(0.001f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (last) KashColors.Ink else KashColors.InkFaint)
                    )
                }
            }
            KashDivider(color = KashColors.LineStrong)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clearAndSetSemantics { }) {
                Text(KashDates.dayMonth(r.fromMs + 43_200_000L, cal), style = KashType.Micro.num(), color = KashColors.InkMuted)
                Text(
                    "Highest: ${KashDates.dayMonth(highMs, cal)} · ${KashMoney.whole(r.days[highIdx])}",
                    style = KashType.Micro.num(), color = KashColors.InkMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (r.isCurrent) "Today" else KashDates.dayMonth(r.toMs - 43_200_000L, cal),
                    style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold), color = KashColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun WhereYouPayMost(r: MonthReport) {
    Section("Where you pay most") {
        r.topPayees.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = KashType.Body, color = KashColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (p.count == 1) "1 payment" else "${p.count} payments", style = KashType.Caption, color = KashColors.InkMuted)
                }
                Text(KashMoney.whole(p.total), style = KashType.BodyStrong.num(), color = KashColors.Ink)
            }
            KashDivider()
        }
    }
}

@Composable
private fun SpentFrom(r: MonthReport, onAccount: (String) -> Unit) {
    val max = r.perAccount.first().second.coerceAtLeast(1.0)
    Section("Spent from") {
        r.perAccount.forEach { (name, total) ->
            Column(
                Modifier.fillMaxWidth().kashClickable(press = KashPress.Highlight) { onAccount(name) }.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KashBankBadge(name, size = 28.dp)
                    Text(com.financeapp.ui.kash.components.kashBank(name).shortName, style = KashType.Body, color = KashColors.Ink, modifier = Modifier.weight(1f))
                    Text(KashMoney.whole(total), style = KashType.BodyStrong.num(), color = KashColors.Ink)
                }
                Progress((total / max).toFloat(), "$name: ${KashMoney.whole(total)}")
            }
            KashDivider()
        }
    }
}

@Composable
private fun AnalyticsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        KashSkeleton(width = 150.dp, height = 14.dp)
        Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            listOf(0.6f, 0.85f, 0.55f, 0.95f, 0.7f, 0.8f).forEach { h ->
                KashSkeleton(Modifier.weight(1f).fillMaxHeight(h), radius = 6.dp)
            }
        }
        KashSkeleton(width = 120.dp, height = 14.dp)
        KashSkeleton(Modifier.fillMaxWidth(), height = 6.dp)
        KashSkeleton(Modifier.fillMaxWidth(0.6f), height = 6.dp)
    }
}

// ─── Category detail ─────────────────────────────────────────────────────

/** Category detail (AppCategory, AppCategoryEmpty). Rows open Transaction. */
@Composable
fun CategoryScreen(
    vm: AnalyticsViewModel,
    category: String,
    onBack: () -> Unit,
    onTxClick: (Long) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val cal = LocalCalendarSystem.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }
    val state by remember(category) { vm.categoryDetail(category) }.collectAsState()
    val report by vm.report.collectAsState()
    val months by vm.availableMonths.collectAsState()
    var monthPicker by remember { mutableStateOf(false) }
    val monthName = KashDates.monthName(report.fromMs + 86_400_000L, cal)
    val groups = remember(state.transactions) { groupByDay(state.transactions) }

    KashScreen(
        heroGap = 20.dp,
        hero = {
            KashTopBar(
                category, onBack = onBack, centerTitle = true,
                actions = { KashIconButton(KashIcon.Calendar, "Change month", { monthPicker = true }, onSlate = true) },
            )
            Column {
                Text("Spent in $monthName", style = KashType.Body, color = KashColors.OnSlateMuted)
                KashFigure(state.monthSpending, Modifier.padding(top = 4.dp), countUp = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.isCurrent) StatTile("Today", KashMoney.whole(state.todaySpending), Modifier.weight(1f))
                else StatTile("Payments", state.transactions.size.toString(), Modifier.weight(1f))
                StatTile("A day, avg", KashMoney.whole(state.avgDailySpend), Modifier.weight(1f))
                StatTile("Of spending", "${state.percentOfTotal.roundToInt()}%", Modifier.weight(1f))
            }
        },
    ) { pad ->
        when {
            state.isLoading -> Column { repeat(5) { KashSkeletonRow() } }
            state.transactions.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                KashEmptyState(
                    KashIcon.Chart,
                    "No $category spending in $monthName",
                    "When you label a payment $category, it shows up here.",
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                groups.forEach { (day, rows) ->
                    item(key = "h$day") { KashDayHeader(KashDates.dayHeader(day, cal), net = dayNet(rows)) }
                    items(rows, key = { it.id }) { tx -> KashTxRow(tx, onClick = { onTxClick(tx.id) }, showCategory = false) }
                }
            }
        }
    }

    if (monthPicker) {
        KashSheet("Show month", onDismiss = { monthPicker = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                months.forEach { m ->
                    KashRadioRow(
                        KashDates.monthYear(m + 86_400_000L, cal), m == report.fromMs,
                        { vm.setReportMonth(m + 86_400_000L); monthPicker = false },
                    )
                }
            }
        }
    }
}
