package com.financeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.sms.SmsIngestService
import com.financeapp.ui.components.DailyReviewCard
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.PureWhite
import com.financeapp.ui.theme.pressScale
import com.financeapp.ui.theme.rememberTactileSource
import com.financeapp.viewmodel.DashboardViewModel
import com.financeapp.viewmodel.WeekActivityPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// Dashboard (Home) — pixel-faithful reproduction of
//   design/home/Home_Dark.png
//
// Sections, top → bottom:
//   1. Header           — greeting + name + profile button
//   2. Total Balance    — coral total + weekly bar chart + View all
//   3. Banks            — two side-by-side bank cards with teal accent rail
//   4. History          — label + search field + filter pills
//   5. Today section    — title + net + scrollable transaction rows
//   6. See more…        — outline pill
//   7. Bottom tab bar lives in AppNavigation (rendered separately).
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)   // warm dark brown card
private val InnerDark  = Color(0xFF15110F)   // recessed surface — search field, eye well
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)
private val BarGrey    = Color(0xFF7E7A73)
private val DividerCol = Color(0x1FFFFFFF)
private val IncomeGreen = Color(0xFF4CD495)

private data class HomeFilterChip(val label: String, val type: String?)
private data class HomeDayGroup(val title: String, val transactions: List<TransactionEntity>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    userName: String,
    onTxClick: (Long) -> Unit,
    onAccountsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onQuickFilterClick: (String?) -> Unit,
    onSettingsClick: () -> Unit,
    onStartDailyReview: () -> Unit = {},
    onStartOlderReview: () -> Unit = {}
) {
    val state = vm.state.collectAsState().value
    val context = LocalContext.current
    // Privacy-first default: hide the running balance on every fresh launch
    // (one tap to reveal). rememberSaveable keeps the user's choice within
    // a session, but it resets to hidden when the process is killed/relaunched.
    var balanceVisible by rememberSaveable { mutableStateOf(false) }
    // Show a few day groups on Home by default (V2 testers reported the
    // History section looked empty when capped at 1). The "See more" footer
    // navigates to the full History tab — no in-page pagination.
    var visibleDayGroups by rememberSaveable { mutableStateOf(3) }

    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            SmsIngestService.start(context)
            kotlinx.coroutines.delay(1500)
            pullRefreshState.endRefresh()
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Headline)
        }
        return
    }

    val homeGroups = remember(state.recentTransactions) { buildHomeGroups(state.recentTransactions) }
    LaunchedEffect(homeGroups.size) {
        visibleDayGroups = visibleDayGroups.coerceAtMost(homeGroups.size.coerceAtLeast(1))
    }

    val totalBalance = state.accountBalances.sumOf { it.balance }

    Box(Modifier.fillMaxSize().background(PageBg).nestedScroll(pullRefreshState.nestedScrollConnection)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                HomeHeader(userName = userName, onSettingsClick = onSettingsClick)
            }

            item {
                TotalBalanceCard(
                    totalBalance = totalBalance,
                    weekActivity = state.weekActivity,
                    balanceVisible = balanceVisible,
                    onToggleVisibility = { balanceVisible = !balanceVisible }
                )
            }

            // V2 single review-CTA card. Replaces the prior two-card setup
            // (Daily Review status card + "Catch up on older transactions"
            // link) with one card matching the V2 mock:
            //   "Review today's transactions"
            //   "N transactions need confirmation"          [→ coral arrow]
            //
            // State routing:
            //  - today.pending > 0          → Today CTA, tap → onStartDailyReview
            //  - today done but older > 0   → Older CTA, tap → onStartOlderReview
            //  - everything reviewed        → "All caught up" tile (no arrow)
            item {
                DailyReviewCard(
                    snapshot = state.dailyReview,
                    olderPending = state.olderPendingCount,
                    onStartToday = onStartDailyReview,
                    onStartOlder = onStartOlderReview,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { BanksSection(state.accountBalances, onAccountsClick) }

            item { HistorySection(onHistoryClick = onHistoryClick, onQuickFilterClick = onQuickFilterClick) }

            if (homeGroups.isEmpty()) {
                item { InfoCard("No transactions yet. Once messages are imported, your activity will appear here.") }
            } else {
                items(
                    items = homeGroups.take(visibleDayGroups),
                    key = { group -> "${group.title}-${group.transactions.firstOrNull()?.id ?: 0L}" }
                ) { group ->
                    HomeDaySection(group = group, onTxClick = onTxClick)
                }

                // "See more" footer — routes to the full History tab.
                // Always shown, even when fewer day groups exist than the
                // visible cap (gives a consistent terminus to the scroll).
                item { SeeMoreFooter(onClick = onHistoryClick) }
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = CardBg,
            contentColor = Headline
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun HomeHeader(userName: String, onSettingsClick: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // Extra breathing room below the OEM status bar — Samsung's
            // status icons are heavy and butt right up against the greeting
            // on plain statusBarsPadding alone.
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = greeting, color = Headline, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (userName.isBlank()) "Welcome" else userName,
                color = Headline,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp
            )
        }
        // V2 design: settings gear in a rounded-square tile (vs the
        // previous round profile avatar). Material Outlined Settings to
        // keep the strokes crisp on dark bg.
        val gearIx = rememberTactileSource()
        Box(
            modifier = Modifier
                .size(52.dp)
                .pressScale(gearIx)
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .clickable(
                    interactionSource = gearIx,
                    indication = null,
                    onClick = onSettingsClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Headline,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Total Balance card
// ---------------------------------------------------------------------------
@Composable
private fun TotalBalanceCard(
    totalBalance: Double,
    weekActivity: List<WeekActivityPoint>,
    balanceVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    // V2 hero treatment: coral background, white-on-coral headline + bars.
    // "View all" link and divider removed per V2 design — accounts deck is
    // reached via the bottom-nav Accounts tab instead.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Coral)
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Total Balance", color = PureWhite.copy(alpha = 0.92f), fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (balanceVisible) "ETB ${moneyShort(totalBalance)}" else "ETB ••••••",
                    color = PureWhite,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.0).sp,
                    lineHeight = 48.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "All accounts", color = PureWhite.copy(alpha = 0.78f), fontSize = 13.sp)
            }

            val eyeIx = rememberTactileSource()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pressScale(eyeIx)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = eyeIx,
                        indication = null,
                        onClick = onToggleVisibility
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 150ms crossfade between open-eye (when hidden) and eye-off
                // (when visible). The icon represents the ACTION on tap, not
                // the current state. Crossfade — not instant swap — because
                // the swap IS triggered by user input (interactive feedback,
                // not decoration).
                androidx.compose.animation.Crossfade(
                    targetState = balanceVisible,
                    animationSpec = androidx.compose.animation.core.tween(150),
                    label = "eyeIconCrossfade"
                ) { visible ->
                    Icon(
                        painter = painterResource(
                            id = if (visible) R.drawable.ic_eye_off_dark
                                 else R.drawable.ic_eye_dark
                        ),
                        contentDescription = if (visible) "Hide balance" else "Show balance",
                        tint = PureWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // White solid bars on coral — see V2 design.
        WeeklyBarChart(
            points = weekActivity,
            barColor = PureWhite.copy(alpha = 0.55f),
            highlightColor = PureWhite,
            labelColor = PureWhite.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun WeeklyBarChart(
    points: List<WeekActivityPoint>,
    barColor: Color = BarGrey,
    highlightColor: Color = PureWhite.copy(alpha = 0.92f),
    labelColor: Color = BodyMuted
) {
    val pts = if (points.isEmpty()) {
        // Visual fallback so the card looks right on a fresh install.
        listOf(
            WeekActivityPoint("S", 350.0),
            WeekActivityPoint("M", 720.0),
            WeekActivityPoint("T", 480.0),
            WeekActivityPoint("W", 690.0),
            WeekActivityPoint("T", 540.0),
            WeekActivityPoint("F", 600.0),
            WeekActivityPoint("S", 240.0)
        )
    } else points

    val todayIndex = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0; Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3; Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5
            else -> 6
        }
    }
    val maxAmount = pts.maxOf { it.amount }.coerceAtLeast(1.0)

    // V2 mock: taller bars (~150dp), wider columns, pill TOP only (flat
    // bottom so the bar sits on the baseline). Top corners use 50% rounding
    // (= bar_width / 2) which gives a full half-circle dome regardless of
    // bar height. Bottom corners are 0 so the bar visually grounds.
    //
    // Future-day rule: today is e.g. Tuesday → only S, M, T render. Days
    // after today (W, T, F, S) are blank (no placeholder bar), since we
    // can't have spending data for days that haven't happened. Days at or
    // before today with zero amount also stay blank (real zero, not a 20%
    // floor that fakes activity).
    val barCap = RoundedCornerShape(
        topStartPercent = 100, topEndPercent = 100,
        bottomStartPercent = 0, bottomEndPercent = 0
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pts.forEachIndexed { i, p ->
                val isFuture = i > todayIndex
                val hasData  = p.amount > 0.0
                val ratio    = (p.amount / maxAmount).coerceIn(0.0, 1.0)
                val isHighlight = i == todayIndex

                if (isFuture || !hasData) {
                    // Reserve the column width so labels stay aligned, but
                    // render no bar — empty/future days are visually silent.
                    Spacer(modifier = Modifier.width(32.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height((140 * ratio).dp.coerceAtLeast(14.dp))
                            .clip(barCap)
                            .background(if (isHighlight) highlightColor else barColor)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pts.forEachIndexed { i, p ->
                val isFuture = i > todayIndex
                Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = p.label,
                        // Future days get a dimmer label so they don't compete
                        // visually with active days.
                        color = if (isFuture) labelColor.copy(alpha = 0.45f) else labelColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Banks
// ---------------------------------------------------------------------------
@Composable
private fun BanksSection(
    accounts: List<com.financeapp.viewmodel.AccountSummary>,
    onAccountsClick: () -> Unit
) {
    Column {
        Text(text = "Banks", color = Headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        val visible = if (accounts.isNotEmpty()) accounts.take(2) else listOf(
            com.financeapp.viewmodel.AccountSummary("Commercial Bank", "", 939.58, 0),
            com.financeapp.viewmodel.AccountSummary("Bank of Abyssinia", "", 31.65, 0)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            visible.forEach { acc ->
                Box(modifier = Modifier.weight(1f)) {
                    BankCard(name = acc.bankName, balance = acc.balance, onClick = onAccountsClick)
                }
            }
            if (visible.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BankCard(name: String, balance: Double, onClick: () -> Unit) {
    val ix = rememberTactileSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(ix)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable(
                interactionSource = ix,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Headline)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = name,
                color = Headline,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = moneyShort(balance),
                    color = Headline,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "ETB", color = Headline, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// History (search + filter pills)
// ---------------------------------------------------------------------------
@Composable
private fun HistorySection(
    onHistoryClick: () -> Unit,
    onQuickFilterClick: (String?) -> Unit
) {
    val chips = listOf(
        HomeFilterChip("ALL", null),
        HomeFilterChip("CREDITED", TransactionType.CREDIT.name),
        HomeFilterChip("DEBIT",    TransactionType.DEBIT.name),
        HomeFilterChip("UNKNOWN",  TransactionType.UNKNOWN.name)
    )

    Column {
        Text(text = "Transactions", color = FooterGrey, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = "History", color = Headline, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(InnerDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onHistoryClick
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search_dark),
                contentDescription = "Search",
                tint = Coral,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Search by name, bank, date ...", color = FooterGrey, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))

        // Filter pills
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onQuickFilterClick(chip.type) }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(text = chip.label, color = Headline, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Day group (header + rows)
// ---------------------------------------------------------------------------
@Composable
private fun HomeDaySection(group: HomeDayGroup, onTxClick: (Long) -> Unit) {
    val net = group.transactions.sumOf { transactionNetValue(it) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = group.title, color = Headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${group.transactions.size} transactions",
                    color = FooterGrey,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Net", color = FooterGrey, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = signedShort(net),
                    color = if (net < 0) Coral else IncomeGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
        Spacer(Modifier.height(4.dp))

        group.transactions.forEachIndexed { i, tx ->
            HomeTransactionRow(tx = tx, onClick = { onTxClick(tx.id) })
            if (i != group.transactions.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
            }
        }
    }
}

@Composable
private fun HomeTransactionRow(tx: TransactionEntity, onClick: () -> Unit) {
    val outgoing = tx.type != TransactionType.CREDIT.name
    val color = if (outgoing) Coral else IncomeGreen
    val iconRes = if (outgoing) R.drawable.ic_expense else R.drawable.ic_income

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.counterparty?.takeIf { it.isNotBlank() } ?: tx.sender,
                color = Headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            // Subtitle line: bank name, dot separator, then category pill
            // when the txn has been categorized. The pill is missing for
            // PENDING/uncategorized rows — that's intentional, signals
            // "needs review" to the eye without a separate badge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.bankName,
                    color = FooterGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                tx.category?.takeIf { it.isNotBlank() }?.let { cat ->
                    Text(
                        text = "  •  ",
                        color = FooterGrey,
                        fontSize = 11.sp
                    )
                    com.financeapp.ui.components.CategoryPill(category = cat)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (outgoing) "-" else "+"} ${moneyShort(tx.amount)}",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            tx.balance?.let { bal ->
                Spacer(Modifier.height(2.dp))
                Text(text = "bal ${moneyShort(bal)}", color = FooterGrey, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// See more pill + info card
// ---------------------------------------------------------------------------
@Composable
private fun SeeMorePill(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(text = "See more...", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(18.dp)
    ) {
        Text(text = message, color = BodyMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
private fun buildHomeGroups(transactions: List<TransactionEntity>): List<HomeDayGroup> {
    if (transactions.isEmpty()) return emptyList()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return transactions
        .groupBy { fmt.format(Date(it.dateTime)) }
        .entries
        .sortedByDescending { it.key }
        .map { (key, txs) -> HomeDayGroup(sectionTitleFor(key), txs.sortedByDescending { it.dateTime }) }
}

private fun sectionTitleFor(key: String): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val target = fmt.parse(key) ?: return key
    val today = fmt.format(Date())
    val yesterday = fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
    return when (key) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(target)
    }
}

private fun transactionNetValue(tx: TransactionEntity): Double =
    if (tx.type == TransactionType.CREDIT.name) tx.amount else -tx.amount

private fun moneyShort(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

private fun signedShort(amount: Double): String {
    val a = moneyShort(abs(amount))
    return if (amount < 0) "-$a" else "+$a"
}

// OlderPendingLink removed in V2 — the single DailyReviewCard now handles
// the older-pending state when today is clean.

/**
 * "See more →" footer rendered after the last visible day group. Tapping
 * navigates to the full History tab. Sized to be a clear terminal element
 * without competing with the cards above it.
 */
@Composable
private fun SeeMoreFooter(onClick: () -> Unit) {
    val rowBg     = Color(0xFF231E1A)
    val text      = Color(0xFFF5F2EB)
    val coral     = Color(0xFFFB5D53)
    val ix        = rememberTactileSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(ix)
            .clip(RoundedCornerShape(16.dp))
            .background(rowBg)
            .clickable(
                interactionSource = ix,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "See more",
            color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        Text("→", color = coral, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

