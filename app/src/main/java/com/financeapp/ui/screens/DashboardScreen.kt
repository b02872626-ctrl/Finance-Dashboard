package com.financeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.PureWhite
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
    onSettingsClick: () -> Unit
) {
    val state = vm.state.collectAsState().value
    val context = LocalContext.current
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    var visibleDayGroups by rememberSaveable { mutableStateOf(1) }

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
                    onToggleVisibility = { balanceVisible = !balanceVisible },
                    onViewAllClick = onAccountsClick
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

                if (visibleDayGroups < homeGroups.size) {
                    item {
                        SeeMorePill(onClick = {
                            visibleDayGroups = (visibleDayGroups + 1).coerceAtMost(homeGroups.size)
                        })
                    }
                }
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
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = greeting, color = Headline, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (userName.isBlank()) "Welcome" else userName,
                color = Headline,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSettingsClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_dark),
                contentDescription = "Profile",
                tint = Headline,
                modifier = Modifier.size(32.dp)
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
    onToggleVisibility: () -> Unit,
    onViewAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Total Balance", color = Headline, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (balanceVisible) "ETB ${moneyShort(totalBalance)}" else "ETB ••••••",
                    color = Coral,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(text = "All accounts", color = FooterGrey, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleVisibility
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_eye_dark),
                    contentDescription = if (balanceVisible) "Hide balance" else "Show balance",
                    tint = Headline,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        WeeklyBarChart(weekActivity)

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x227FE3CB)))
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onViewAllClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "View all", color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeeklyBarChart(points: List<WeekActivityPoint>) {
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

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pts.forEachIndexed { i, p ->
                val ratio = (p.amount / maxAmount).coerceIn(0.25, 1.0)
                val isHighlight = i == todayIndex
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height((100 * ratio).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (isHighlight) PureWhite.copy(alpha = 0.92f) else BarGrey)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pts.forEach { p ->
                Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    Text(text = p.label, color = BodyMuted, fontSize = 12.sp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = tx.bankName,
                color = FooterGrey,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

