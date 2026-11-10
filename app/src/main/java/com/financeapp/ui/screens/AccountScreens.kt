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
import com.financeapp.ui.kash.components.KashDialog
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

/** Last 4 digits of an account number, for "•••• 1234". */
private fun last4(account: String?): String? =
    account?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }

// ─── Account detail ──────────────────────────────────────────────────────

/** 1W / 1M / 1Y / All on Account detail. */
enum class AccountPeriod(val label: String, val days: Int?) { WEEK("1W", 7), MONTH("1M", 30), YEAR("1Y", 365), ALL("All", null) }

/** Account detail (AppAccountDetail, AppAccountDetailEmpty). */
@Composable
fun AccountDetailScreen(
    vm: BankAnalyticsViewModel,
    onBack: () -> Unit,
    onTxClick: (Long) -> Unit,
    /** Set when this is one of several accounts at the bank; hides it and leaves. */
    onHide: (() -> Unit)? = null,
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
    // Only a split account (label "CBE 3", not the bank name) can be hidden.
    val canHide = onHide != null && !state.isLoading && state.label != state.bankName
    var confirmHide by rememberSaveable { mutableStateOf(false) }

    if (confirmHide && onHide != null) {
        KashDialog(
            title = "Hide ${state.label}?",
            description = listOfNotNull(last4?.let { "•••• $it. " }).joinToString("") +
                "Its balance and transactions leave every screen and total. " +
                "Use this for an account that isn't yours. Show it again from Settings › Banks Kash follows.",
            onDismiss = { confirmHide = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KashButton("Hide account", { confirmHide = false; onHide() }, Modifier.fillMaxWidth())
                KashButton("Cancel", { confirmHide = false }, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
            }
        }
    }

    KashScreen(
        heroGap = 22.dp,
        hero = {
            KashTopBar(
                if (state.label != state.bankName) state.label
                else bank.shortName.let { if (it == "CBE") "Commercial Bank" else bank.fullName },
                onBack = onBack, centerTitle = true,
                leading = { KashBankBadge(state.bankName, size = 32.dp) },
                actions = {
                    if (canHide) KashIconButton(KashIcon.EyeOff, "Hide this account", { confirmHide = true }, onSlate = true)
                },
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
