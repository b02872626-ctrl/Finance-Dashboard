package com.financeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.kash.Haptic
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.accountLine
import com.financeapp.ui.kash.accountShort
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashKeyValue
import com.financeapp.ui.kash.components.KashKeyValueList
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSkeleton
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashSnackbar
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.ui.kash.dayNet
import com.financeapp.ui.kash.displayName
import com.financeapp.ui.kash.groupByDay
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.kashDirection
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberHaptic
import com.financeapp.ui.kash.rememberReducedMotion
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.ReviewListViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Review, the tab (canvas section 6, "Final - Approved flow"): the rows
 * still to sort, newest first, under Today / This month, each with a Sort
 * button that opens the sort card. A row's tap shows its details. Every
 * entry point (Home, Analytics, the notification) lands here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    vm: ReviewListViewModel,
    tabBar: @Composable () -> Unit,
    userName: String,
    customCategories: List<String>,
    recentCategories: List<String>,
    categoryUsage: List<SortCard.Use>,
    onAddCustomCategory: (String) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val cal = LocalCalendarSystem.current
    LaunchedEffect(cal) { vm.setCalendar(cal) }
    val haptic = rememberHaptic()
    // A review session: holds off the backlog nudge for 3 days.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) { com.financeapp.notifications.NotificationPrefs(appContext).lastReviewSessionMs = System.currentTimeMillis() }
    // Admin retention metric: the list was emptied in this visit (opening an
    // already-empty one doesn't count).
    var sawPending by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.count) {
        if (state.isLoading) return@LaunchedEffect
        if (state.count > 0) sawPending = true
        else if (sawPending) {
            sawPending = false
            com.financeapp.remote.Telemetry.track(appContext, com.financeapp.remote.Telemetry.REVIEW_COMPLETED, mapOf("scope" to state.tab.name))
        }
    }

    val (figure, unit) = ReviewList.headline(state.tab, state.count)
    val groups = remember(state.shown) { groupByDay(state.shown) }

    KashScreen(
        tabBar = tabBar,
        heroGap = 14.dp,
        hero = {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Review", style = KashType.TitleM, color = KashColors.OnSlate)
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(figure, style = KashType.FigureXl, color = KashColors.OnSlate, maxLines = 1)
                    Text(unit, style = KashType.FigureUnit, color = KashColors.OnSlateMuted, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(ReviewList.subtitle(state.tab, state.count), style = KashType.Body, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 6.dp))
            }
        },
    ) { pad ->
        val bottom = pad.calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ReviewTabs(
                    tab = state.tab, today = state.today.size, month = state.month.size,
                    onSelect = { haptic(Haptic.TAP); vm.setTab(it) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                when {
                    state.isLoading -> Column {
                        KashSkeleton(Modifier.padding(top = 14.dp, bottom = 6.dp), width = 90.dp, height = 10.dp)
                        repeat(5) { KashSkeletonRow() }
                    }
                    state.shown.isEmpty() -> NothingToSort(
                        older = if (state.tab == ReviewTab.MONTH) state.older.size else 0,
                        onShowOlder = vm::showOlder,
                        modifier = Modifier.fillMaxSize().padding(bottom = bottom),
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        // Room under the last row for the snackbar, so it never covers a Sort button.
                        contentPadding = PaddingValues(bottom = bottom + 76.dp),
                    ) {
                        groups.forEach { (day, rows) ->
                            item(key = "h$day") {
                                KashDayHeader(
                                    KashDates.dayHeader(day, cal),
                                    Modifier.animateItemPlacement(),
                                    net = if (state.tab == ReviewTab.MONTH) dayNet(rows) else null,
                                )
                            }
                            items(rows, key = { it.id }) { tx ->
                                KashTransactionRow(
                                    name = tx.displayName(),
                                    amount = tx.amount,
                                    direction = tx.kashDirection(),
                                    modifier = Modifier.animateItemPlacement(),
                                    account = tx.accountShort(),
                                    time = KashDates.time(tx.dateTime),
                                    onClick = { vm.openDetail(tx) },
                                    trailing = { SortButton(tx.displayName()) { haptic(Haptic.TAP); vm.openSort(tx) } },
                                )
                            }
                        }
                        if (state.tab == ReviewTab.MONTH && state.older.isNotEmpty() && !state.showOlder) {
                            item(key = "older") { OlderRow(state.older.size, vm::showOlder) }
                        }
                    }
                }
            }
            AnimatedVisibility(
                state.snackbar != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 10.dp),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
            ) {
                var last by remember { mutableStateOf("") }
                state.snackbar?.let { last = it.text }
                KashSnackbar(last, Modifier.fillMaxWidth(), action = "Undo", onAction = vm::undo)
            }
        }
    }

    state.detail?.let { tx ->
        CheckThisOneSheet(
            tx = tx,
            onClose = vm::closeSheets,
            onChangeCategory = { vm.openSort(tx) },
            onNotATransaction = { haptic(Haptic.CONFIRM); vm.markNotATransaction(tx) },
        )
    }
    state.sorting?.let { tx ->
        val own = looksOwnAccount(tx, userName)
        val sort: (String) -> Unit = { cat -> haptic(Haptic.CONFIRM); vm.sort(tx, cat) }
        SortCardSheet(
            tx = tx,
            // The suggestion shows picked, so one tap confirms it.
            current = if (own) Spending.OWN_ACCOUNTS else tx.category ?: tx.predictedCategory,
            custom = customCategories,
            usage = categoryUsage,
            recent = recentCategories,
            offerOwnAccounts = own,
            applyToSimilar = state.applyToSimilar,
            onToggleApplyToSimilar = vm::toggleApplyToSimilar,
            onClose = vm::closeSheets,
            onPick = sort,
            onCreate = { name -> onAddCustomCategory(name); sort(TransactionCategoryCatalog.normalize(name)) },
        )
    }
    state.confirmApplyAll?.let { dialog ->
        ApplyAllDialog(
            category = dialog.category,
            name = dialog.counterparty,
            total = dialog.totalCount,
            samples = dialog.samples,
            onAll = { haptic(Haptic.CONFIRM); vm.confirmApplyAll() },
            onJustOne = { vm.confirmApplyJustOne() },
            onDismiss = vm::dismissConfirmApplyAll,
        )
    }
}

/**
 * The own-account check: the parser typed it as an internal transfer, or
 * an outgoing counterparty carries part of the user's name.
 */
internal fun looksOwnAccount(tx: TransactionEntity, userName: String): Boolean {
    if (tx.category != null) return tx.category == Spending.OWN_ACCOUNTS
    if (tx.type == TransactionType.INTERNAL_TRANSFER.name) return true
    val cp = tx.counterparty ?: return false
    if (tx.type == TransactionType.CREDIT.name) return false
    val cpLow = cp.lowercase(Locale.US)
    return userName.split(' ', '.', ',', '\t')
        .map { it.lowercase(Locale.US).trim() }
        .filter { it.length >= 4 }
        .any { it in cpLow }
}

/** Today N / This month N on the well; a check instead of a count once a slice is done. 44dp tall. */
@Composable
private fun ReviewTabs(tab: ReviewTab, today: Int, month: Int, onSelect: (ReviewTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(KashColors.Well)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        ReviewTab.entries.forEach { t ->
            val on = t == tab
            val count = if (t == ReviewTab.TODAY) today else month
            Row(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) KashColors.SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) })
                    .semantics { contentDescription = if (count == 0) "${t.label}, all sorted" else "${t.label}, $count to sort" }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    t.label,
                    style = KashType.Label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) KashColors.Ink else KashColors.InkMuted,
                    maxLines = 1, softWrap = false,
                )
                if (count > 0) Text(count.toString(), style = KashType.Label.num(), color = KashColors.InkMuted, maxLines = 1)
                else KashIconView(KashIcon.Check, size = 14.dp, tint = KashColors.InkMuted, strokeWidth = 2.4f)
            }
        }
    }
}

/** The row's 44dp Sort button: on the well, ink. */
@Composable
private fun SortButton(name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .kashClickable(onClickLabel = "Sort $name", onClick = onClick)
            .height(44.dp)
            .clip(KashShapes.Chip)
            .background(KashColors.Well)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Sort", style = KashType.Caption.copy(fontWeight = FontWeight.SemiBold), color = KashColors.Ink, maxLines = 1, softWrap = false)
    }
}

/** Under This month: the backlog from before, on request. */
@Composable
private fun OlderRow(count: Int, onShow: () -> Unit) {
    KashButton(
        if (count == 1) "Show 1 from before this month" else "Show $count from before this month",
        onShow, Modifier.fillMaxWidth().padding(top = 14.dp), KashButtonVariant.Secondary, compact = true,
    )
}

/** The list is empty: a check in a well, "Nothing to sort". */
@Composable
private fun NothingToSort(older: Int, onShowOlder: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(70.dp))
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(KashColors.Well),
            contentAlignment = Alignment.Center,
        ) { KashIconView(KashIcon.Check, size = 30.dp, tint = KashColors.MoneyIn, strokeWidth = 2.4f) }
        Text("Nothing to sort", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.padding(top = 10.dp))
        Text(
            "New payments to people you’ve sorted sort themselves. Anything new shows up here.",
            style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = KashColors.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 270.dp),
        )
        if (older > 0) {
            KashButton(
                if (older == 1) "Show 1 from before this month" else "Show $older from before this month",
                onShowOlder, Modifier.padding(top = 22.dp).widthIn(max = 300.dp), KashButtonVariant.Secondary, compact = true,
            )
        }
    }
}

/** AppEditTransaction: a row's tap. */
@Composable
private fun CheckThisOneSheet(
    tx: TransactionEntity,
    onClose: () -> Unit,
    onChangeCategory: () -> Unit,
    onNotATransaction: () -> Unit,
) {
    val cal = LocalCalendarSystem.current
    val dir = tx.kashDirection()
    val tone = when (dir) {
        KashDirection.In -> KashColors.MoneyIn
        KashDirection.Out -> KashColors.MoneyOut
        KashDirection.Transfer -> KashColors.MoneyNeutral
    }
    KashSheet("Check this one", onDismiss = onClose) {
        KashFigure(tx.amount, style = KashType.FigureL, color = tone, unitColor = tone, sign = KashMoney.sign(dir))
        KashKeyValueList(
            listOf(
                KashKeyValue(if (dir == KashDirection.In) "From" else "To", tx.displayName()),
                KashKeyValue("Account", tx.accountLine(), numeric = true),
                KashKeyValue("When", KashDates.full(tx.dateTime, cal), numeric = true),
                KashKeyValue("Category", tx.category ?: "Not set yet"),
            )
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The SMS Kash read", style = KashType.Label, color = KashColors.InkMuted)
            Text(
                tx.rawBody, style = KashType.Caption, color = KashColors.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(KashShapes.Field)
                    .background(KashColors.Well)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KashButton("Sort it", onChangeCategory, Modifier.fillMaxWidth())
            KashButton("Not a transaction", onNotATransaction, Modifier.fillMaxWidth(), KashButtonVariant.Secondary)
        }
    }
}

/** AppReviewApplyAll: lists the newest rows from that sender. */
@Composable
private fun ApplyAllDialog(
    category: String,
    name: String,
    total: Int,
    samples: List<TransactionEntity>,
    onAll: () -> Unit,
    onJustOne: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = LocalCalendarSystem.current
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    // "Label all": the rows flash sand as they take the label, then the dialog goes.
    val flash = remember { Animatable(0f) }
    val labelAll = {
        if (reduced) onAll() else scope.launch {
            flash.animateTo(1f, tween(140))
            flash.animateTo(0f, tween(260))
            onAll()
        }
        Unit
    }
    KashDialog(
        title = "Put all $total in $category?",
        description = "Kash found $total transactions with $name. Kash can sort them together.",
        onDismiss = onDismiss,
    ) {
        Column {
            samples.take(3).forEachIndexed { i, tx ->
                KashTransactionRow(
                    modifier = Modifier.background(KashColors.Sand.copy(alpha = 0.55f * flash.value), KashShapes.Field),
                    name = tx.displayName(),
                    amount = tx.amount,
                    direction = tx.kashDirection(),
                    account = tx.accountShort(),
                    time = KashDates.whenShort(tx.dateTime, cal),
                )
            }
            if (total > 3) {
                Text(
                    "and ${total - 3} more", style = KashType.Caption, color = KashColors.InkMuted,
                    modifier = Modifier.padding(top = 10.dp).semantics { contentDescription = "and ${total - 3} more transactions" },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KashButton("Sort all $total", labelAll, Modifier.fillMaxWidth())
            KashButton("Just this one", onJustOne, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        }
    }
}
