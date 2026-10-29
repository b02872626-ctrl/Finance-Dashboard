package com.financeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.model.netAmount
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTxRow
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashAccountCard
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSearchField
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.dashedBorder
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.ui.kash.groupByDay
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.AccountSummary
import com.financeapp.viewmodel.BankAnalyticsViewModel
import com.financeapp.viewmodel.DashboardViewModel

/** "From today's SMS, 12:36" / "From yesterday's SMS, 18:40" / "From the SMS of Oct 19". */
private fun updatedLine(ms: Long?, cal: String): String? {
    ms ?: return null
    val days = (KashDates.startOfDay(System.currentTimeMillis()) - KashDates.startOfDay(ms)) / 86_400_000L
    return when (days) {
        0L -> "From today's SMS, ${KashDates.time(ms)}"
        1L -> "From yesterday's SMS, ${KashDates.time(ms)}"
        else -> "From the SMS of ${KashDates.monthDay(ms, cal)}"
    }
}

private fun last4(account: String?): String? =
    account?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }

/**
 * Accounts tab (AppAccounts, AppAccountsEmpty). Swipe between account
 * cards; the sheet shows the selected account's month and latest rows.
 * The "Bank SMS" button is hidden until that screen exists.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsTabScreen(
    vm: DashboardViewModel,
    hasSmsAccess: Boolean,
    tabBar: @Composable () -> Unit,
    onOpenAccount: (bankName: String, accountSuffix: String?) -> Unit,
    onAdd: () -> Unit,
    onTxClick: (Long) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val cal = LocalCalendarSystem.current
    val context = LocalContext.current
    val accounts = state.accountBalances
    val pager = rememberPagerState { accounts.size }
    val selected = accounts.getOrNull(pager.currentPage)

    KashScreen(
        tabBar = tabBar,
        heroGap = 18.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts", style = KashType.TitleM, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                if (accounts.isNotEmpty()) KashIconButton(KashIcon.Plus, "Add a transaction", onAdd, onSlate = true)
            }
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(196.dp).clip(KashShapes.Card).background(KashColors.SkeletonOnSlate))
            } else if (accounts.isEmpty()) {
                NoBanksCard()
            } else {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(end = 56.dp),
                    pageSpacing = 12.dp,
                    // Run past the hero's right padding so the next card peeks in at the edge.
                    modifier = Modifier.layout { m, c ->
                        val extra = com.financeapp.ui.kash.KashSpace.InsetApp.roundToPx()
                        val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                        layout(c.maxWidth, p.height) { p.place(0, 0) }
                    },
                ) { page ->
                    val a = accounts[page]
                    KashAccountCard(
                        bankName = a.bankName,
                        title = a.label.takeIf { a.accountSuffix != null },
                        last4 = last4(a.accountNumber),
                        balance = a.balance,
                        updated = updatedLine(a.balanceAtMs, cal),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        onClick = { onOpenAccount(a.bankName, a.accountSuffix) },
                    )
                }
                if (accounts.size > 1) PagerDots(accounts.size, pager.currentPage)
            }
        },
    ) { pad ->
        if (!state.isLoading && accounts.isEmpty()) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("While you wait", style = KashType.TitleS, color = KashColors.Ink)
                if (!hasSmsAccess) {
                    KashListGroup {
                        KashListRow(
                            "Check SMS access", hint = "Kash needs it to find your banks", icon = KashIcon.Sms, showDivider = false,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        )
                    }
                }
                KashListGroup {
                    KashListRow("Add cash by hand", hint = "For money that never sends an SMS", icon = KashIcon.Plus, showDivider = false, onClick = onAdd)
                }
            }
            return@KashScreen
        }
        selected ?: return@KashScreen
        val rows = remember(state.recentTransactions, selected.bankName, selected.accountSuffix) {
            state.recentTransactions.filter {
                it.bankName.equals(selected.bankName, true) &&
                    (selected.accountSuffix == null || AccountGrouping.accountSuffix(it.accountNumber) == selected.accountSuffix)
            }.take(6)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                    KashButton("History", { onOpenAccount(selected.bankName, selected.accountSuffix) }, Modifier.weight(1f), KashButtonVariant.Secondary, icon = KashIcon.History, compact = true)
                }
            }
            item { SpentThisMonth(selected) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Transactions", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f))
                    Text(
                        "See all", style = KashType.Label, color = KashColors.InkMuted,
                        modifier = Modifier.kashClickable { onOpenAccount(selected.bankName, selected.accountSuffix) }.padding(vertical = 6.dp),
                    )
                }
            }
            items(rows, key = { it.id }) { tx ->
                KashTxRow(tx, onClick = { onTxClick(tx.id) }, showAccount = false, time = KashDates.whenShort(tx.dateTime, cal))
            }
        }
    }
}

@Composable
private fun NoBanksCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .dashedBorder(KashColors.GhostLine, KashShapes.Card, width = 1.5.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(44.dp).clip(KashShapes.Field).background(KashColors.GhostFill), contentAlignment = Alignment.Center) {
            KashIconView(KashIcon.Card, size = 20.dp, tint = KashColors.OnSlate)
        }
        Text("No banks yet", style = KashType.BodyStrong, color = KashColors.OnSlate)
        Text(
            "A bank shows up here as soon as Kash reads one of its SMS.",
            style = KashType.Caption, color = KashColors.OnSlateMuted, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 250.dp),
        )
    }
}

@Composable
private fun PagerDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Account ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == current) 18.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == current) KashColors.Sand else KashColors.GhostLine)
            )
        }
    }
}

@Composable
private fun SpentThisMonth(a: AccountSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spent this month", style = KashType.Label, color = KashColors.InkMuted, modifier = Modifier.weight(1f))
            Text("${KashMoney.whole(a.monthSpent)} ETB", style = KashType.FigureS, color = KashColors.Ink)
        }
        if (a.lastMonthSpent > 0) {
            val share = (a.monthSpent / a.lastMonthSpent).coerceIn(0.0, 1.0).toFloat()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(KashColors.Well)
                    .semantics { contentDescription = "${(a.monthSpent / a.lastMonthSpent * 100).toInt()}% of last month's spending so far" },
            ) {
                Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(KashColors.Slate))
            }
        }
    }
}

// ─── Account detail ──────────────────────────────────────────────────────

/** 1W / 1M / 1Y / All on Account detail. */
enum class AccountPeriod(val label: String, val days: Int?) { WEEK("1W", 7), MONTH("1M", 30), YEAR("1Y", 365), ALL("All", null) }

/** Account detail (AppAccountDetail, AppAccountDetailEmpty). */
@Composable
fun AccountDetailScreen(
    vm: BankAnalyticsViewModel,
    onBack: () -> Unit,
    onTxClick: (Long) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val cal = LocalCalendarSystem.current
    var query by rememberSaveable(state.bankName) { mutableStateOf("") }
    var period by rememberSaveable(state.bankName) { mutableStateOf(AccountPeriod.MONTH) }
    val rows = remember(state.transactions, query, period) {
        val from = period.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
        val q = query.trim()
        state.transactions.filter { tx ->
            tx.dateTime >= from && (q.isEmpty() ||
                tx.counterparty?.contains(q, ignoreCase = true) == true ||
                tx.category?.contains(q, ignoreCase = true) == true ||
                tx.refNumber?.contains(q, ignoreCase = true) == true)
        }
    }
    val net = rows.sumOf { it.netAmount() }
    val groups = remember(rows) { groupByDay(rows) }
    val bank = kashBank(state.bankName)
    val last4 = last4(state.accountNumber)

    KashScreen(
        heroGap = 22.dp,
        hero = {
            KashTopBar(
                if (state.label != state.bankName) state.label
                else bank.shortName.let { if (it == "CBE") "Commercial Bank" else bank.fullName },
                onBack = onBack, centerTitle = true,
                leading = { KashBankBadge(state.bankName, size = 32.dp) },
            )
            Column {
                Text(
                    listOfNotNull("Balance", last4?.let { "•••• $it" }).joinToString(" · "),
                    style = KashType.Body.num(), color = KashColors.OnSlateMuted,
                )
                KashFigure(state.totalBalance, Modifier.padding(top = 4.dp), countUp = true)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize()) {
            KashSearchField(query, { query = it }, "Search this account")
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountPeriod.entries.forEach { p -> KashChip(p.label, p == period, { period = p }) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Net " + (if (net < 0) KashMoney.MINUS else "") + KashMoney.whole(net),
                    style = KashType.Label.num(), color = KashColors.InkMuted, maxLines = 1,
                )
            }
            when {
                state.isLoading -> repeat(5) { KashSkeletonRow() }
                rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    val next = AccountPeriod.entries.getOrNull(period.ordinal + 1)
                    KashEmptyState(
                        KashIcon.History,
                        if (query.isNotBlank()) "Nothing matches" else when (period) {
                            AccountPeriod.WEEK -> "Quiet week"
                            AccountPeriod.MONTH -> "Quiet month"
                            AccountPeriod.YEAR -> "Quiet year"
                            AccountPeriod.ALL -> "Nothing here yet"
                        },
                        if (query.isNotBlank()) "No transactions in this account match “${query.trim()}”."
                        else when (period) {
                            AccountPeriod.WEEK -> "No money moved in or out of this account in the last 7 days."
                            AccountPeriod.MONTH -> "No money moved in or out of this account in the last 30 days."
                            AccountPeriod.YEAR -> "No money moved in or out of this account in the last year."
                            AccountPeriod.ALL -> "Kash hasn't read any transactions for this account."
                        },
                    ) {
                        if (query.isBlank() && next != null) {
                            val label = when (next) {
                                AccountPeriod.MONTH -> "Show 1 month"
                                AccountPeriod.YEAR -> "Show 1 year"
                                else -> "Show all"
                            }
                            KashButton(label, { period = next }, Modifier.width(200.dp), KashButtonVariant.Secondary)
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                    groups.forEach { (day, dayRows) ->
                        item(key = "h$day") { KashDayHeader(KashDates.dayHeader(day, cal)) }
                        items(dayRows, key = { it.id }) { tx -> KashTxRow(tx, onClick = { onTxClick(tx.id) }, showAccount = false) }
                    }
                }
            }
        }
    }
}
