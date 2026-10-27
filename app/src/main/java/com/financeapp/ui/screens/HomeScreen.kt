package com.financeapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import com.financeapp.ui.kash.rememberReducedMotion
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.financeapp.sms.SmsIngestService
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTxRow
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSkeleton
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashWeekChart
import com.financeapp.ui.kash.groupByDay
import com.financeapp.ui.kash.kashClickable
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.DashboardState
import com.financeapp.viewmodel.DashboardViewModel
import java.util.Calendar

/**
 * Home (AppHome, AppHomeEmpty, AppHomeCaughtUp, AppHomeOlder). The slate hero
 * holds the total balance (hidden on every launch), the review button and
 * the week; the sheet lists the latest transactions by day.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: DashboardViewModel,
    tabBar: @Composable () -> Unit,
    onTxClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onReviewToday: () -> Unit,
    onReviewOlder: () -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val cal = LocalCalendarSystem.current
    // Privacy first: hidden on every fresh launch, kept within a session.
    var balanceVisible by rememberSaveable { mutableStateOf(false) }

    // Pull down to rescan the inbox (also the retry path for scan failures).
    val pull = rememberPullToRefreshState()
    if (pull.isRefreshing) {
        LaunchedEffect(true) {
            SmsIngestService.start(context)
            kotlinx.coroutines.delay(1500)
            pull.endRefresh()
        }
    }

    val hasAny = state.recentTransactions.isNotEmpty()
    // Ids already on screen. A row that shows up later (a new SMS) slides in
    // with a sand wash; the first load doesn't count.
    var known by remember { mutableStateOf<Set<Long>?>(null) }
    LaunchedEffect(state.recentTransactions, state.isLoading) {
        if (!state.isLoading) known = (known ?: emptySet()) + state.recentTransactions.map { it.id }
    }
    val groups = remember(state.recentTransactions) { groupByDay(state.recentTransactions).take(3) }

    Box(Modifier.fillMaxSize().nestedScroll(pull.nestedScrollConnection)) {
        KashScreen(
            tabBar = tabBar,
            hero = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KashIconButton(KashIcon.Plus, "Add transaction", onAdd, onSlate = true)
                        KashIconButton(KashIcon.Settings, "Settings", onSettings, onSlate = true)
                    }
                }
                BalanceBlock(state, hasAny, balanceVisible) { balanceVisible = !balanceVisible }
                if (!state.isLoading) {
                    ReviewBlock(state, hasAny, onReviewToday, onReviewOlder)
                    if (hasAny) WeekBlock(state)
                }
            },
        ) { pad ->
            val phase = when {
                state.isLoading -> 0
                !hasAny -> 1
                else -> 2
            }
            Crossfade(phase, label = "home") { p -> Column { when (p) {
                0 -> Column {
                    KashSkeleton(width = 120.dp, height = 16.dp)
                    repeat(5) { KashSkeletonRow() }
                }
                1 -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    KashEmptyState(
                        KashIcon.History,
                        "No transactions yet",
                        "Paid in cash? Add it by hand. Everything from your bank SMS fills in on its own.",
                    ) {
                        KashButton("Add a transaction", onAdd, Modifier.width(220.dp), KashButtonVariant.Secondary, icon = KashIcon.Plus)
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Transactions", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f))
                        Text(
                            "See all", style = KashType.Label, color = KashColors.InkMuted,
                            modifier = Modifier.kashClickable(onClick = onSeeAll).padding(vertical = 6.dp, horizontal = 2.dp),
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                        groups.forEach { (day, rows) ->
                            item(key = "h$day") { KashDayHeader(KashDates.dayHeader(day, cal), Modifier.animateItemPlacement()) }
                            items(rows, key = { it.id }) { tx ->
                                FreshRow(fresh = known != null && tx.id !in known!!, modifier = Modifier.animateItemPlacement()) {
                                    KashTxRow(tx, onClick = { onTxClick(tx.id) })
                                }
                            }
                        }
                    }
                }
            } } }
        }
        PullToRefreshContainer(
            state = pull,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = KashColors.SurfaceRaised,
            contentColor = KashColors.Slate,
        )
    }
}

/** A newly arrived row: expands into place, then its sand wash fades. */
@Composable
private fun FreshRow(fresh: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val show = remember { MutableTransitionState(!fresh || reduced).apply { targetState = true } }
    val wash = remember { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(Unit) { if (fresh) { delay(500); wash.animateTo(0f, tween(1400)) } }
    AnimatedVisibility(show, modifier, enter = expandVertically() + fadeIn()) {
        Box(Modifier.background(KashColors.Sand.copy(alpha = 0.45f * wash.value))) { content() }
    }
}

@Composable
private fun BalanceBlock(state: DashboardState, hasAny: Boolean, visible: Boolean, onToggle: () -> Unit) {
    val total = state.accountBalances.sumOf { it.balance }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total balance", style = KashType.Body, color = KashColors.OnSlateMuted)
            if (hasAny) {
                Box(
                    Modifier
                        .size(28.dp)
                        .kashClickable(onClickLabel = if (visible) "Hide balance" else "Show balance", onClick = onToggle)
                        .semantics { contentDescription = if (visible) "Hide balance" else "Show balance" },
                    contentAlignment = Alignment.Center,
                ) { KashIconView(KashIcon.Eye, size = 18.dp, tint = KashColors.OnSlateMuted) }
            }
        }
        Crossfade(state.isLoading, label = "balance") { loading ->
            if (loading) {
                KashSkeleton(Modifier.padding(top = 8.dp), width = 210.dp, height = 44.dp, radius = 10.dp, onSlate = true)
            } else {
                KashFigure(total, Modifier.padding(top = 4.dp), hidden = hasAny && !visible, countUp = true)
            }
        }
    }
}

/** Review button with the count, or the quiet status when there is nothing to do. */
@Composable
private fun ReviewBlock(state: DashboardState, hasAny: Boolean, onToday: () -> Unit, onOlder: () -> Unit) {
    val today = state.dailyReview
    when {
        today.pending > 0 -> KashButton(
            "Review", onToday, Modifier.fillMaxWidth(), KashButtonVariant.Accent,
            badge = today.pending.toString(), compact = true,
        )
        state.olderPendingCount > 0 -> KashButton(
            "Review older ones", onOlder, Modifier.fillMaxWidth(), KashButtonVariant.Accent,
            badge = state.olderPendingCount.toString(), compact = true,
        )
        today.total > 0 -> GhostStatus(
            icon = KashIcon.Check,
            title = "All caught up",
            text = "${today.done} reviewed today",
            compact = true,
        )
        else -> GhostStatus(
            icon = KashIcon.Sms,
            title = "Listening for bank SMS",
            text = "New transactions show up here as they arrive.",
            compact = false,
        )
    }
}

@Composable
private fun GhostStatus(icon: KashIcon, title: String, text: String, compact: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.height(KashSize.ButtonHCompact) else Modifier)
            .clip(KashShapes.Control)
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, KashShapes.Control)
            .padding(horizontal = 16.dp, vertical = if (compact) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        KashIconView(icon, size = if (compact) 18.dp else 22.dp, tint = KashColors.OnSlate, strokeWidth = if (compact) 2.4f else 2f)
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp)) {
            Text(title, style = KashType.BodyStrong, color = KashColors.OnSlate)
            Text(text, style = if (compact) KashType.Micro else KashType.Caption, color = KashColors.OnSlateMuted)
        }
    }
}

/** This week's spending, Sunday first as the view model builds it; today in sand. */
@Composable
private fun WeekBlock(state: DashboardState) {
    val week = state.weekActivity
    if (week.size != 7) return
    val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val max = week.maxOf { it.amount }.coerceAtLeast(1.0)
    val total = week.sumOf { it.amount }
    KashWeekChart(
        values = week.mapIndexed { i, p -> if (i > todayIndex) 0f else (p.amount / max).toFloat() },
        today = todayIndex,
        total = (if (total > 0) KashMoney.MINUS else "") + KashMoney.whole(total),
    )
}
