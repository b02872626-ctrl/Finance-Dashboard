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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.viewmodel.AccountSummary
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.financeapp.notifications.UpdateChecker
import com.financeapp.sms.SmsIngestWorker
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTxRow
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.num
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
import com.financeapp.ui.kash.groupByDay
import com.financeapp.ui.kash.kashClickable
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.DashboardState
import com.financeapp.viewmodel.DashboardViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import com.financeapp.ui.kash.components.KashSpinner
import com.financeapp.ui.kash.components.LocalKashTips
import com.financeapp.ui.kash.components.tipAnchor

/**
 * Home (AppHome, AppHomeEmpty, AppHomeCaughtUp, AppHomeOlder). The slate hero
 * holds the total balance (hidden on every launch), a strip of the accounts
 * behind it (tap one for its history; NAH-28 merged the Accounts tab in
 * here), the review button and the week; the sheet lists the latest
 * transactions by day.
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
    // Home shows nothing but the balance, the accounts and the transactions
    // (owner, 16 Nov): sorting lives in the Review tab and its badge.
    @Suppress("UNUSED_PARAMETER") onReviewToday: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onReviewOlder: () -> Unit,
    onOpenAccount: (bankName: String, accountSuffix: String?) -> Unit,
    /** Last month, in the first week of a new one (Kash Wrapped, NAH-34). */
    wrappedMonth: Long? = null,
    onOpenWrapped: (Long) -> Unit = {},
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val cal = LocalCalendarSystem.current
    // Privacy first: hidden on every fresh launch, kept within a session.
    var balanceVisible by rememberSaveable { mutableStateOf(false) }
    val update by UpdateChecker.state.collectAsState()
    // Pop-up once per time Kash opens, while a newer tester build is waiting.
    var popupSeen by rememberSaveable { mutableStateOf(false) }
    val popup = if (popupSeen) null else update.popupVersion(System.currentTimeMillis())
    if (popup != null) {
        UpdatePopup(popup, onLater = {
            UpdateChecker.snoozePopup(context, popup.versionCode)
            popupSeen = true
        })
    }

    // Pull down to rescan the inbox (also the retry path for scan failures).
    val pull = rememberPullToRefreshState()
    if (pull.isRefreshing) {
        LaunchedEffect(true) {
            SmsIngestWorker.scanNow(context)
            kotlinx.coroutines.delay(1500)
            pull.endRefresh()
        }
    }

    // The first full read of the inbox (Home, still reading); null otherwise.
    val scan by SmsIngestWorker.progress.collectAsState()
    val reading = scan != null

    // First visit, once the read is done: the Home tips (approved "Tip ·" screens).
    val tips = LocalKashTips.current
    val readyForTips = !state.isLoading && !reading && state.recentTransactions.isNotEmpty() && popup == null
    LaunchedEffect(readyForTips) {
        if (readyForTips) {
            delay(700)
            // The visit after the Home tips: where sorting lives.
            if (tips?.seen(AppTips.home.key) == true) tips.offer(AppTips.reviewTab) else tips?.offer(AppTips.home)
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
                BalanceBlock(state, hasAny, balanceVisible, counting = reading, progress = scan, modifier = Modifier.tipAnchor("balance")) { balanceVisible = !balanceVisible }
                // Home, still reading: one card per bank, done / reading / waiting.
                val p = scan
                if (p != null) {
                    ReadingSmsBlock(p, state.accountBalances, hidden = !balanceVisible)
                } else {
                    if (!state.isLoading && state.accountBalances.isNotEmpty()) {
                        AccountStrip(state.accountBalances, hidden = !balanceVisible, onOpen = onOpenAccount, modifier = Modifier.tipAnchor("accounts"))
                    }
                }
            },
        ) { pad ->
            val phase = when {
                reading -> 3
                state.isLoading -> 0
                !hasAny -> 1
                else -> 2
            }
            Column {
                // A newer tester build: shown on Home even when notifications are off.
                AnimatedVisibility(update.cardVersion != null, enter = fadeIn() + expandVertically()) {
                    val latest = update.cardVersion ?: return@AnimatedVisibility
                    UpdateCard(
                        latest,
                        onLater = { UpdateChecker.dismiss(context, latest.versionCode) },
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }
                // Accounts whose SMS greet someone else: offer to hide them once.
                val minePrefs = remember { context.getSharedPreferences("kash_accounts_mine", android.content.Context.MODE_PRIVATE) }
                var kept by remember { mutableStateOf(minePrefs.getStringSet("kept", emptySet()).orEmpty()) }
                val others = state.accountBalances.filter { it.otherHolder != null && "${it.bankName}#${it.accountNumber}" !in kept }
                // While the card animates out, `others` is already empty: keep
                // drawing the last accounts it showed (an empty list crashed 1.0.31).
                var shownOthers by remember { mutableStateOf(others) }
                if (others.isNotEmpty()) shownOthers = others
                // One at a time, as approved (FD_NewAccount): "Is this account yours?"
                AnimatedVisibility(others.isNotEmpty(), enter = fadeIn() + expandVertically()) {
                    val a = shownOthers.firstOrNull() ?: return@AnimatedVisibility
                    ConfirmAccountCard(
                        a,
                        onMine = {
                            kept = kept + "${a.bankName}#${a.accountNumber}"
                            minePrefs.edit().putStringSet("kept", kept).apply()
                        },
                        onNotMine = {
                            val suffix = a.accountSuffix ?: com.financeapp.data.accounts.AccountGrouping.accountSuffix(a.accountNumber)
                            if (suffix != null) vm.setAccountHidden(a.bankName, suffix, true)
                        },
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    )
                }
                Crossfade(phase, label = "home") { p -> Column { when (p) {
                    3 -> GettingReady(onSeeAll)
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
                    else -> Box(Modifier.fillMaxSize()) { Column {
                        Text("Transactions", style = KashType.TitleS, color = KashColors.Ink)
                        // Room at the end so the last row clears the pinned button.
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 84.dp)) {
                            groups.forEach { (day, rows) ->
                                item(key = "h$day") { KashDayHeader(KashDates.dayHeader(day, cal), Modifier.animateItemPlacement(com.financeapp.ui.kash.KashSpring.settle(0.35f))) }
                                items(rows, key = { it.id }) { tx ->
                                    FreshRow(fresh = known != null && tx.id !in known!!, modifier = Modifier.animateItemPlacement(com.financeapp.ui.kash.KashSpring.settle(0.35f))) {
                                        KashTxRow(tx, onClick = { onTxClick(tx.id) })
                                    }
                                }
                            }
                        }
                        }
                        // "Show all transactions", pinned at the bottom over a fade (approved FD_HomeSearch).
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to androidx.compose.ui.graphics.Color.Transparent, 0.45f to KashColors.Surface,
                                ))
                                .padding(top = 36.dp, bottom = pad.calculateBottomPadding() + 14.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().kashClickable(onClick = onSeeAll).height(48.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                                    .background(KashColors.SurfaceRaised)
                                    .border(1.dp, KashColors.LineStrong, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            ) {
                                Text("Show all transactions", style = KashType.BodyStrong, color = KashColors.Ink)
                                KashIconView(KashIcon.ChevronRight, size = 16.dp, tint = KashColors.Ink, strokeWidth = 2.2f)
                            }
                        }
                    }
                } } }
            }
        }
        PullToRefreshContainer(
            state = pull,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = KashColors.SurfaceRaised,
            contentColor = KashColors.Strong,
        )
    }

    // Kash Wrapped: last month's story, a pop-up once per month in the first
    // week of a new one. Waits for the read, the update pop-up and the Home tips.
    val wrappedPrefs = remember { context.getSharedPreferences("kash_wrapped", android.content.Context.MODE_PRIVATE) }
    var wrappedSeen by remember(wrappedMonth) {
        mutableStateOf(wrappedMonth != null && wrappedPrefs.getLong("popup_seen", 0L) == wrappedMonth)
    }
    val tipsBusy = tips != null && (tips.active != null || !tips.seen(AppTips.home.key))
    val month = wrappedMonth
    if (month != null && !wrappedSeen && !reading && !state.isLoading && popup == null && !tipsBusy) {
        fun seen() { wrappedPrefs.edit().putLong("popup_seen", month).apply(); wrappedSeen = true }
        WrappedPopup(onWatch = { seen(); onOpenWrapped(month) }, onLater = ::seen)
    }
}

/** Home, still reading: the sheet waits with faded rows instead of rows trickling in; the bar is under the balance. */
@Composable
private fun GettingReady(onSeeAll: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Transactions", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f))
            Text(
                "See all", style = KashType.Label, color = KashColors.Accent,
                modifier = Modifier.kashClickable(onClick = onSeeAll).padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }
        Column(Modifier.padding(top = 8.dp).alpha(0.6f)) { repeat(3) { KashSkeletonRow() } }
    }
}

/**
 * "Reading your SMS" with a card per bank, one bank at a time: Done shows
 * its balance (hidden like the total), the bank being read its own small
 * bar, the rest "Waiting".
 */
@Composable
private fun ReadingSmsBlock(p: SmsIngestWorker.ScanProgress, accounts: List<AccountSummary>, hidden: Boolean) {
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = false) { contentDescription = "Reading your SMS" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KashSpinner(size = 18.dp, color = KashColors.OnSlate)
            Text("Reading your SMS", style = KashType.BodyStrong, color = KashColors.OnSlate)
        }
        LazyRow(
            Modifier.layout { m, c ->
                val extra = KashSpace.InsetApp.roundToPx()
                val pl = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
                layout(c.maxWidth, pl.height) { pl.place(0, 0) }
            },
            contentPadding = PaddingValues(end = KashSpace.InsetApp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(p.banks, key = { it.bankName }) { b ->
                ReadingBankCard(b, accounts, hidden)
            }
        }
    }
}

@Composable
private fun ReadingBankCard(b: SmsIngestWorker.BankProgress, accounts: List<AccountSummary>, hidden: Boolean) {
    val fmt = remember { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US) }
    val bank = kashBank(b.bankName)
    val waiting = b.done == 0 && !b.finished
    Column(
        Modifier
            .width(148.dp)
            .alpha(if (waiting) 0.6f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashBankBadge(b.bankName, size = 24.dp)
            Text(bank.shortName, style = KashType.Label, color = KashColors.OnSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            b.finished -> {
                val mine = accounts.filter { kashBank(it.bankName).shortName == bank.shortName }
                if (mine.isEmpty()) Text("Done", style = KashType.Caption, color = KashColors.OnSlateMuted)
                else KashFigure(mine.sumOf { it.balance }, style = KashType.FigureS, showDecimals = false, hidden = hidden)
            }
            waiting -> Text("Waiting", style = KashType.Caption, color = KashColors.OnSlateMuted)
            else -> Text(
                buildAnnotatedString {
                    append(fmt.format(b.done))
                    withStyle(SpanStyle(color = KashColors.OnSlateMuted, fontWeight = FontWeight.Medium, fontSize = KashType.Micro.fontSize)) {
                        append(" of ${fmt.format(b.total)}")
                    }
                },
                style = KashType.FigureS, color = KashColors.OnSlate, maxLines = 1,
            )
        }
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
private fun BalanceBlock(
    state: DashboardState,
    hasAny: Boolean,
    visible: Boolean,
    counting: Boolean = false,
    /** The first read's progress: messages read so far over a bar, instead of the figure. */
    progress: SmsIngestWorker.ScanProgress? = null,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    // Accounts with no SMS for 3 months keep an old balance: left out.
    val total = state.accountBalances.filter { it.inTotal }.sumOf { it.balance }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Total balance", style = KashType.Body, color = KashColors.OnSlateMuted)
            if (hasAny && !counting) {
                Box(
                    Modifier
                        .size(28.dp)
                        .kashClickable(onClickLabel = if (visible) "Hide balance" else "Show balance", onClick = onToggle)
                        .semantics { contentDescription = if (visible) "Hide balance" else "Show balance" },
                    contentAlignment = Alignment.Center,
                ) { KashIconView(KashIcon.Eye, size = 18.dp, tint = KashColors.OnSlateMuted) }
            }
        }
        Crossfade(state.isLoading || counting, label = "balance") { loading ->
            if (loading && progress != null) {
                // Home, still reading: "1,240 of 2,039 messages" over a bar that fills as Kash reads.
                val fmt = remember { java.text.NumberFormat.getIntegerInstance(java.util.Locale.US) }
                val fraction = if (progress.total == 0) 0f else (progress.done.toFloat() / progress.total).coerceIn(0f, 1f)
                Column(
                    Modifier.padding(top = 10.dp).height(44.dp).fillMaxWidth()
                        .semantics { contentDescription = "Reading messages, ${progress.done} of ${progress.total}" },
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        buildAnnotatedString {
                            append(fmt.format(progress.done))
                            withStyle(SpanStyle(color = KashColors.OnSlateMuted)) { append(" of ${fmt.format(progress.total)} messages") }
                        },
                        style = KashType.Body.num(), color = KashColors.OnSlate,
                    )
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(KashShapes.Pill).background(KashColors.GhostFill)) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(KashShapes.Pill).background(KashColors.Sand))
                    }
                }
            } else if (loading) {
                Row(Modifier.padding(top = 10.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KashSkeleton(width = 170.dp, height = 34.dp, radius = 10.dp, onSlate = true)
                    Text("Counting…", style = KashType.Body, color = KashColors.OnSlateMuted)
                }
            } else {
                Column {
                    KashFigure(total, Modifier.padding(top = 4.dp), hidden = hasAny && !visible, countUp = true)
                }
            }
        }
    }
}

/**
 * One small card per account, swiped sideways. Runs past the hero's right
 * padding so the next card peeks in at the edge. Amounts hide with the total.
 */
@Composable
private fun AccountStrip(accounts: List<AccountSummary>, hidden: Boolean, onOpen: (String, String?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.layout { m, c ->
            val extra = KashSpace.InsetApp.roundToPx()
            val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
            layout(c.maxWidth, p.height) { p.place(0, 0) }
        },
        contentPadding = PaddingValues(end = KashSpace.InsetApp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(accounts, key = { "${it.bankName}#${it.accountSuffix}" }) { a ->
            AccountChip(a, hidden) { onOpen(a.bankName, a.accountSuffix) }
        }
    }
}

/** "CBE 1" when the bank has several accounts, else the bank's short name ("BoA"). */
internal fun accountChipName(a: AccountSummary): String =
    a.label.takeIf { a.accountSuffix != null } ?: kashBank(a.bankName).shortName

/** "CBE 1" / "BoA" with its bank mark and balance; opens the account's history. */
@Composable
private fun AccountChip(a: AccountSummary, hidden: Boolean, onClick: () -> Unit) {
    val name = accountChipName(a)
    Column(
        Modifier
            .widthIn(min = 124.dp, max = 168.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(KashColors.GhostFill)
            .border(1.dp, KashColors.GhostLine, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .kashClickable(onClickLabel = "Open $name history", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KashBankBadge(a.bankName, size = 22.dp)
            Text(name, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = KashColors.OnSlate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        KashFigure(a.balance, style = KashType.FigureS.copy(fontSize = 14.sp), showDecimals = false, hidden = hidden,
            color = if (a.stale) KashColors.OnSlateMuted else KashColors.OnSlate)
        val cal = LocalCalendarSystem.current
        when {
            a.otherHolder != null -> Text("${a.otherHolder}'s?", style = KashType.Micro, color = KashColors.Sand, maxLines = 1)
            a.stale && a.lastActivityMs != null -> Text(
                "Last updated ${com.financeapp.util.KashDates.monthYear(a.lastActivityMs, cal)}",
                style = KashType.Micro, color = KashColors.OnSlateMuted, maxLines = 1,
            )
        }
    }
}

/**
 * "Is this account yours?" (approved FD_NewAccount): one account at a time,
 * on sand: the bank, the masked number, "Yes, it’s mine" and "Not mine".
 * Shown for accounts whose SMS greet someone else; Not mine hides it
 * (Settings can show it again).
 */
@Composable
private fun ConfirmAccountCard(a: AccountSummary, onMine: () -> Unit, onNotMine: () -> Unit, modifier: Modifier = Modifier) {
    val ink = KashColors.SlateInk
    Column(
        modifier.fillMaxWidth().clip(KashShapes.Card).background(KashColors.Sand).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Is this account yours?", style = KashType.Caption.copy(fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = 0.75f))
            Text(kashBank(a.bankName).fullName, style = KashType.TitleS.copy(fontWeight = FontWeight.Bold), color = ink, textAlign = TextAlign.Center)
            Text(
                maskAccount(a.accountNumber), style = KashType.TitleM.copy(letterSpacing = 1.5.sp).num(), color = ink,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).kashClickable(onClick = onMine).height(44.dp).clip(RoundedCornerShape(14.dp)).background(ink),
                contentAlignment = Alignment.Center,
            ) { Text("Yes, it’s mine", style = KashType.Label.copy(fontWeight = FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White) }
            Box(
                Modifier.weight(1f).kashClickable(onClick = onNotMine).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ink.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("Not mine", style = KashType.Label.copy(fontWeight = FontWeight.SemiBold), color = ink) }
        }
    }
}

/** "1000123453333" → "1•••••3333"; already masked or short: as is. */
internal fun maskAccount(number: String): String {
    val n = number.trim()
    val digits = n.filter(Char::isDigit)
    if (digits.length < 6) return n
    return digits.take(1) + "•••••" + digits.takeLast(4)
}
