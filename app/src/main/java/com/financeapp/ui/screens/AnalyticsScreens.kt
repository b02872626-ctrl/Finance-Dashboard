package com.financeapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.financeapp.viewmodel.CategoryTotal
import com.financeapp.viewmodel.PayeeTotal
import com.financeapp.viewmodel.SpendOverview
import com.financeapp.viewmodel.SpendPreset
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
 * Analytics tab, rebuilt for NAH-27 (owner, Nov 2026): most people never
 * label their transactions, so it leads with today and this week, then a
 * daily line for a chosen range (this week, this month, 30 days or picked
 * dates). Payees and accounts follow the range; categories come last and
 * only when there are some.
 */
@Composable
fun AnalyticsTabScreen(
    vm: AnalyticsViewModel,
    tabBar: @Composable () -> Unit,
    onCategory: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onReview: () -> Unit,
    onAccount: (String) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val cal = LocalCalendarSystem.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }
    val o by vm.overview.collectAsState()
    val r by vm.report.collectAsState()
    var pickDates by remember { mutableStateOf(false) }

    KashScreen(
        tabBar = tabBar,
        heroGap = 18.dp,
        hero = {
            Text("Analytics", style = KashType.TitleM, color = KashColors.OnSlate)
            Column {
                Text("Spent today", style = KashType.Body, color = KashColors.OnSlateMuted)
                if (o.isLoading) KashSkeleton(Modifier.padding(top = 8.dp), width = 210.dp, height = 44.dp, radius = 10.dp, onSlate = true)
                else {
                    KashFigure(o.todaySpent, Modifier.padding(top = 4.dp), countUp = true)
                    Text(
                        when (o.todayCount) { 0 -> "Nothing yet today"; 1 -> "1 payment"; else -> "${o.todayCount} payments" },
                        style = KashType.Caption, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "This week", if (o.isLoading) null else KashMoney.whole(o.weekSpent), Modifier.weight(1f),
                    change = o.weekChangePct, changeVs = "last week",
                )
                StatTile(
                    "This month", if (o.isLoading) null else KashMoney.whole(o.monthSpent), Modifier.weight(1f),
                    change = o.monthChangePct, changeVs = "last month",
                )
            }
        },
    ) { pad ->
        Crossfade(o.isLoading, label = "analytics") { loading ->
            if (loading) {
                AnalyticsSkeleton()
                return@Crossfade
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                OverTime(o, cal, onPick = { vm.setPreset(it) }, onPickDates = { pickDates = true })
                if (o.topPayees.isNotEmpty()) WhereYouPayMost(o.topPayees)
                if (o.perAccount.isNotEmpty()) SpentFrom(o.perAccount, onAccount)
                if (!r.isLoading && r.sixMonths.any { it.moneyIn > 0 || it.spent > 0 }) MoneyInOut(r, cal)
                if (o.categories.isNotEmpty()) {
                    WhereItGoes(o.categories, o.fromMs) { cat ->
                        // The category page shows a month: the one the range ends in.
                        vm.setReportMonth(o.toMs - 1)
                        onCategory(cat)
                    }
                }
            }
        }
    }

    if (pickDates) {
        DateRangeDialog(
            calendarSystem = cal,
            initialFrom = o.fromMs.takeIf { o.preset == SpendPreset.CUSTOM },
            initialTo = o.toMs.takeIf { o.preset == SpendPreset.CUSTOM },
            onDismiss = { pickDates = false },
            onApply = { from, to -> vm.setCustomRange(from, to); pickDates = false },
        )
    }
}

/** "↑ 12%" / "↓ 8%" / "Same" — and the words for screen readers. */
private fun changeShort(pct: Int): String = when {
    pct > 0 -> "↑ $pct%"
    pct < 0 -> "↓ ${-pct}%"
    else -> "Same"
}

private fun changeLong(pct: Int, vs: String): String = when {
    pct > 0 -> "$pct% more than $vs"
    pct < 0 -> "${-pct}% less than $vs"
    else -> "About the same as $vs"
}

@Composable
private fun StatTile(label: String, value: String?, modifier: Modifier = Modifier, change: Int? = null, changeVs: String = "") {
    Column(
        modifier
            .clip(KashShapes.Control)
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, KashShapes.Control)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                if (value != null) contentDescription = "$label: $value ETB" + (change?.let { ". ${changeLong(it, changeVs)}" } ?: "")
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = KashType.Micro, color = KashColors.OnSlateMuted, maxLines = 1)
        if (value == null) KashSkeleton(Modifier.padding(top = 4.dp), width = 56.dp, height = 14.dp, onSlate = true)
        else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = KashType.FigureS, color = KashColors.OnSlate, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (change != null) Text(changeShort(change), style = KashType.Micro.num(), color = KashColors.OnSlateMuted, maxLines = 1)
        }
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

/** Range chips, the range's total and change, then the daily line. */
@Composable
private fun OverTime(o: SpendOverview, cal: String, onPick: (SpendPreset) -> Unit, onPickDates: () -> Unit) {
    val vs = when (o.preset) {
        SpendPreset.THIS_WEEK -> "last week"
        SpendPreset.THIS_MONTH -> "last month"
        SpendPreset.LAST_30 -> "the 30 days before"
        SpendPreset.CUSTOM -> "the days before"
    }
    Section("Spending over time") {
        Row(
            Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KashChip("This week", o.preset == SpendPreset.THIS_WEEK, { onPick(SpendPreset.THIS_WEEK) })
            KashChip("This month", o.preset == SpendPreset.THIS_MONTH, { onPick(SpendPreset.THIS_MONTH) })
            KashChip("30 days", o.preset == SpendPreset.LAST_30, { onPick(SpendPreset.LAST_30) })
            KashChip(
                if (o.preset == SpendPreset.CUSTOM) rangeLabel(o.fromMs, o.toMs - 1, cal) else "Pick dates",
                o.preset == SpendPreset.CUSTOM, onPickDates, leading = KashIcon.Calendar,
            )
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                KashFigure(o.total, style = KashType.FigureM, color = KashColors.Ink, unitColor = KashColors.InkMuted, showDecimals = false, countUp = true)
                Text(
                    o.changePct?.let { changeLong(it, vs) } ?: "Nothing to compare with yet",
                    style = KashType.Caption, color = KashColors.InkMuted, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text("${KashMoney.whole(o.avgPerDay)} a day", style = KashType.Caption.num(), color = KashColors.InkMuted)
        }
        SpendLine(o, cal, Modifier.padding(top = 14.dp))
    }
}

/**
 * Daily spending as a line with a soft fill, the daily average dashed, and
 * today's point marked. Touch or drag along it to read a day.
 */
@Composable
private fun SpendLine(o: SpendOverview, cal: String, modifier: Modifier = Modifier) {
    val days = o.days
    if (days.isEmpty()) return
    val max = (days.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val lastIdx = days.indexOfLast { it != null }
    var picked by remember(o.fromMs, o.toMs) { mutableStateOf<Int?>(null) }
    val grow = rememberGrow(o.fromMs + o.toMs, durationMs = 600)
    val lineColor = KashColors.Slate
    val fill = KashColors.Slate.copy(alpha = 0.10f)
    val avgColor = KashColors.InkFaint
    val pointColor = KashColors.Ink
    fun dayMs(i: Int) = o.fromMs + i * 86_400_000L + 43_200_000L

    val shown = picked ?: lastIdx.takeIf { it >= 0 }
    Column(modifier) {
        Text(
            shown?.let { "${KashDates.dayMonth(dayMs(it), cal)} · ${KashMoney.whole(days[it] ?: 0.0)} ETB" } ?: " ",
            style = KashType.Label.num(), color = KashColors.Ink,
        )
        val highest = days.indices.filter { days[it] != null }.maxByOrNull { days[it]!! }
        Canvas(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(o.fromMs, o.toMs) {
                    fun pick(x: Float) {
                        val i = ((x / size.width) * (days.size - 1)).roundToInt().coerceIn(0, days.size - 1)
                        picked = if (days[i] == null) lastIdx.takeIf { it >= 0 } else i
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(o.fromMs, o.toMs, "drag") {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val i = ((change.position.x / size.width) * (days.size - 1)).roundToInt().coerceIn(0, days.size - 1)
                        picked = if (days[i] == null) lastIdx.takeIf { it >= 0 } else i
                    }
                }
                .semantics {
                    contentDescription = "Spending per day, ${rangeLabel(o.fromMs, o.toMs - 1, cal)}. " +
                        "Total ${KashMoney.whole(o.total)} ETB, ${KashMoney.whole(o.avgPerDay)} a day on average." +
                        (highest?.let { " Highest: ${KashDates.dayMonth(dayMs(it), cal)}, ${KashMoney.whole(days[it]!!)} ETB." } ?: "")
                },
        ) {
            val w = size.width
            val h = size.height
            val step = if (days.size > 1) w / (days.size - 1) else w
            fun x(i: Int) = if (days.size > 1) i * step else w / 2
            fun y(v: Double) = h - (v / max).toFloat() * h * 0.9f * grow
            val pts = days.indices.filter { days[it] != null }.map { Offset(x(it), y(days[it]!!)) }
            // Average, dashed.
            val avgY = y(o.avgPerDay)
            drawLine(
                avgColor, Offset(0f, avgY), Offset(w, avgY), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            )
            // Baseline.
            drawLine(avgColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
            if (pts.isNotEmpty()) {
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, h); lineTo(pts.first().x, h); close()
                }
                drawPath(area, fill)
                drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            shown?.let { i ->
                val v = days[i] ?: return@let
                val c = Offset(x(i), y(v))
                drawLine(avgColor, Offset(c.x, 0f), Offset(c.x, h), strokeWidth = 1.dp.toPx())
                drawCircle(pointColor, radius = 4.5.dp.toPx(), center = c)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { }) {
            Text(KashDates.dayMonth(dayMs(0), cal), style = KashType.Micro.num(), color = KashColors.InkMuted, modifier = Modifier.weight(1f))
            Text(KashDates.dayMonth(dayMs(days.lastIndex), cal), style = KashType.Micro.num(), color = KashColors.InkMuted)
        }
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
private fun WhereItGoes(categories: List<CategoryTotal>, key: Long, onCategory: (String) -> Unit) {
    val total = categories.sumOf { it.total }.coerceAtLeast(1.0)
    val max = categories.first().total.coerceAtLeast(1.0)
    Section("Where it goes", "What you've labelled") {
        categories.forEachIndexed { i, c ->
            val pct = (c.total / total * 100).roundToInt()
            Column(
                Modifier
                    .fillMaxWidth()
                    .kashEnter(i, key)
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
private fun WhereYouPayMost(payees: List<PayeeTotal>) {
    Section("Where you pay most") {
        payees.forEach { p ->
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
private fun SpentFrom(perAccount: List<Pair<String, Double>>, onAccount: (String) -> Unit) {
    val max = perAccount.first().second.coerceAtLeast(1.0)
    Section("Spent from") {
        perAccount.forEach { (name, total) ->
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
