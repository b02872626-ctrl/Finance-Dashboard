package com.financeapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.accountLine
import com.financeapp.ui.kash.accountShort
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashKeyValue
import com.financeapp.ui.kash.components.KashKeyValueList
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashNotice
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSearchField
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSkeleton
import com.financeapp.ui.kash.components.KashSnackbar
import com.financeapp.ui.kash.components.KashStepBar
import com.financeapp.ui.kash.components.KashSwitch
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.ui.kash.displayName
import com.financeapp.ui.kash.kashDirection
import com.financeapp.ui.kash.num
import com.financeapp.ui.theme.Haptic
import com.financeapp.ui.theme.rememberHaptic
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.ReviewQueueState
import com.financeapp.viewmodel.ReviewQueueViewModel
import com.financeapp.viewmodel.ReviewScope
import com.financeapp.viewmodel.ReviewSheet
import java.util.Locale

/**
 * Review (AppReview, AppReviewTransfer, AppReviewUndo, AppEditTransaction,
 * AppReviewCategory, AppNewCategory, AppReviewApplyAll, AppReviewDone).
 * Also replaces "Categorize Today": every entry point opens this screen.
 */
@Composable
fun ReviewScreen(
    vm: ReviewQueueViewModel,
    userName: String,
    customCategories: List<String>,
    recentCategories: List<String>,
    onAddCustomCategory: (String) -> Unit,
    onClose: () -> Unit,
    onReviewOlder: () -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val haptic = rememberHaptic()
    val current = state.current
    val allCategories = remember(customCategories) { TransactionCategoryCatalog.allCategories(customCategories) }
    // The category picked on the card; starts at the prediction (or Own
    // accounts when the own-account check fires).
    var selected by remember(current?.id) {
        mutableStateOf(current?.let { if (looksOwnAccount(it, userName)) Spending.OWN_ACCOUNTS else it.category ?: it.predictedCategory })
    }
    var newCategoryOpen by remember { mutableStateOf(false) }

    val total = state.totalToday.coerceAtLeast(state.doneToday)
    val position = (state.doneToday + if (state.complete || current == null) 0 else 1).coerceAtMost(total)

    KashScreen(
        heroGap = 18.dp,
        heroBottom = 22.dp,
        hero = {
            KashTopBar(
                "Review", onBack = onClose,
                actions = {
                    if (total > 0) Text("$position of $total", style = KashType.Label.num(), color = KashColors.OnSlateMuted)
                },
            )
            if (total > 0) {
                // Long backlogs (older scope) get a proportional bar, not 900 segments.
                val segments = total.coerceAtMost(12)
                val filled = if (total <= 12) state.doneToday else (state.doneToday * 12 / total)
                KashStepBar(filled, segments, onSlate = true, label = "${state.doneToday} of $total reviewed")
            }
        },
    ) { pad ->
        when {
            state.isLoading -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KashSkeleton(Modifier.fillMaxWidth(), height = 220.dp, radius = 20.dp)
                KashSkeleton(width = 90.dp)
                KashSkeleton(width = 240.dp, height = 38.dp, radius = 12.dp)
            }
            current == null -> DoneView(state, pad, onClose, onReviewOlder)
            else -> Column(Modifier.fillMaxSize().padding(pad)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ReviewCard(current, onLongPress = { haptic(Haptic.LONG); vm.openDetailSheet() })
                    CategoryChoice(
                        tx = current,
                        userName = userName,
                        selected = selected,
                        recent = recentCategories,
                        all = allCategories,
                        onSelect = { haptic(Haptic.TAP); selected = it },
                        onMore = vm::openPickerSheet,
                    )
                }
                state.snackbar?.let {
                    KashSnackbar(it.text, Modifier.fillMaxWidth().padding(bottom = 10.dp), action = "Undo", onAction = vm::undo)
                }
                ReviewButtons(
                    selected = selected,
                    onSkip = { haptic(Haptic.TAP); vm.skip() },
                    onConfirm = { cat -> haptic(Haptic.CONFIRM); vm.pickCategory(cat, advance = true) },
                    onPick = vm::openPickerSheet,
                )
            }
        }
    }

    if (current != null && state.sheet == ReviewSheet.Detail) {
        CheckThisOneSheet(
            tx = current,
            onClose = vm::closeSheet,
            onChangeCategory = vm::openPickerSheet,
            onNotATransaction = { haptic(Haptic.CONFIRM); vm.markNotATransaction() },
        )
    }
    if (current != null && state.sheet == ReviewSheet.Picker) {
        CategoryPicker(
            tx = current,
            initial = selected,
            recent = recentCategories,
            custom = customCategories,
            applyToSimilar = state.applyToSimilar,
            onToggleApplyToSimilar = vm::toggleApplyToSimilar,
            onClose = vm::closeSheet,
            onNewCategory = { newCategoryOpen = true },
            onUse = { cat ->
                haptic(Haptic.CONFIRM)
                selected = cat
                vm.pickCategory(cat, advance = true, applyToSimilar = state.applyToSimilar)
            },
        )
    }
    if (newCategoryOpen) {
        NewCategoryDialog(
            onCancel = { newCategoryOpen = false },
            onCreate = { name ->
                newCategoryOpen = false
                onAddCustomCategory(name)
                selected = TransactionCategoryCatalog.normalize(name)
            },
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
 * The existing own-account check: the parser typed it as an internal
 * transfer, or an outgoing counterparty carries part of the user's name.
 */
private fun looksOwnAccount(tx: TransactionEntity, userName: String): Boolean {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReviewCard(tx: TransactionEntity, onLongPress: () -> Unit) {
    val cal = LocalCalendarSystem.current
    val dir = tx.kashDirection()
    val tone = when (dir) {
        KashDirection.In -> KashColors.MoneyIn
        KashDirection.Out -> KashColors.MoneyOut
        KashDirection.Transfer -> KashColors.MoneyNeutral
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClickLabel = "Check this one",
                onLongClick = onLongPress,
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KashBankBadge(tx.bankName, size = 32.dp)
                Text(tx.accountShort(), style = KashType.Label, color = KashColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(KashDates.whenShort(tx.dateTime, cal), style = KashType.Label.num(), color = KashColors.InkMuted)
        }
        Column {
            KashFigure(
                tx.amount, style = KashType.FigureL, color = tone, unitColor = tone,
                sign = KashMoney.sign(dir),
            )
            Text(
                buildAnnotatedString {
                    append(if (dir == KashDirection.In) "from " else "to ")
                    withStyle(SpanStyle(color = KashColors.Ink, fontWeight = FontWeight.SemiBold)) { append(tx.displayName()) }
                },
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 6.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        val facts = buildList {
            add(KashKeyValue(if (dir == KashDirection.In) "To" else "From", tx.accountLine(), numeric = true))
            tx.balance?.let { add(KashKeyValue("Balance after", KashMoney.amount(it), numeric = true)) }
        }
        KashKeyValueList(facts, card = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChoice(
    tx: TransactionEntity,
    userName: String,
    selected: String?,
    recent: List<String>,
    all: List<String>,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (looksOwnAccount(tx, userName) && tx.category == null) {
            val bank = tx.counterparty?.takeIf { tx.type == TransactionType.INTERNAL_TRANSFER.name }
            KashNotice(
                (if (bank != null) "Looks like a move to your own $bank account." else "Looks like a move to your own account.") +
                    " Own-account moves aren't counted as spending.",
                icon = KashIcon.Transfer,
            )
        }
        Text("What was it?", style = KashType.Label, color = KashColors.InkMuted)
        val chips = remember(selected, recent, all) {
            (listOfNotNull(selected) + recent + listOf("Food", "Transport", "Bills", "Shopping"))
                .filter { c -> all.any { it.equals(c, true) } || c == selected }
                .distinctBy { it.lowercase() }
                .take(4)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { c -> KashChip(c, c == selected, { onSelect(c) }) }
            KashChip("More", false, onMore, trailing = KashIcon.ChevronRight)
        }
    }
}

/** Skip + Confirm side by side; stacked when the confirm label is long. */
@Composable
private fun ReviewButtons(selected: String?, onSkip: () -> Unit, onConfirm: (String) -> Unit, onPick: () -> Unit) {
    val label = selected?.let { "Confirm as $it" } ?: "Pick a category"
    val action = { if (selected != null) onConfirm(selected) else onPick() }
    if (label.length > 24) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KashButton(label, action, Modifier.fillMaxWidth())
            KashButton("Skip", onSkip, Modifier.fillMaxWidth(), KashButtonVariant.Secondary)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KashButton("Skip", onSkip, Modifier.widthIn(min = 104.dp), KashButtonVariant.Secondary)
            KashButton(label, action, Modifier.weight(1f))
        }
    }
}

/** AppReviewDone (and the empty queue). */
@Composable
private fun DoneView(
    state: ReviewQueueState,
    pad: androidx.compose.foundation.layout.PaddingValues,
    onHome: () -> Unit,
    onOlder: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(pad)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(64.dp).clip(KashShapes.Control).background(KashColors.Slate),
                contentAlignment = Alignment.Center,
            ) { KashIconView(KashIcon.Check, size = 26.dp, tint = KashColors.OnSlate) }
            Spacer(Modifier.height(20.dp))
            Text("All caught up", style = KashType.TitleM, color = KashColors.Ink)
            Text(
                when {
                    state.scope == ReviewScope.ALL -> "Every transaction has a category now."
                    state.totalToday == 0 -> "Nothing new today.\nWe'll nudge you when new SMS arrive."
                    state.totalToday == 1 -> "Today's transaction is sorted.\nWe'll nudge you when new SMS arrive."
                    else -> "Today's ${state.totalToday} transactions are sorted.\nWe'll nudge you when new SMS arrive."
                },
                style = KashType.BodyL, color = KashColors.InkMuted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (state.scope == ReviewScope.TODAY && state.olderPending > 0) {
            KashListGroup(Modifier.padding(bottom = 22.dp)) {
                KashListRow(
                    label = if (state.olderPending == 1) "1 older one needs a category" else "${state.olderPending} older ones need a category",
                    hint = "From before today",
                    icon = KashIcon.History,
                    showDivider = false,
                    onClick = onOlder,
                )
            }
        }
        KashButton("Back to home", onHome, Modifier.fillMaxWidth())
    }
}

/** AppEditTransaction: long-press on the card. */
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
            KashButton("Change category", onChangeCategory, Modifier.fillMaxWidth())
            KashButton("Not a transaction", onNotATransaction, Modifier.fillMaxWidth(), KashButtonVariant.Secondary)
        }
    }
}

/**
 * AppReviewCategory. Sections come from the real category catalog plus the
 * user's own; the bottom switch is the "apply to similar" rule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    tx: TransactionEntity?,
    initial: String?,
    recent: List<String>,
    custom: List<String>,
    applyToSimilar: Boolean,
    onToggleApplyToSimilar: (() -> Unit)?,
    onClose: () -> Unit,
    onNewCategory: () -> Unit,
    onUse: (String) -> Unit,
    title: String = "Pick a category",
) {
    var query by remember { mutableStateOf("") }
    var pick by remember(initial) { mutableStateOf(initial) }
    val sectionNames = mapOf("Common" to "Everyday", "Lifestyle" to "Lifestyle", "Money in/out" to "Money in and between accounts")
    val sections = buildList {
        val rec = recent.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(6)
        if (rec.isNotEmpty()) add("Recent" to rec)
        TransactionCategoryCatalog.sections.forEach { (name, cats) -> add((sectionNames[name] ?: name) to cats) }
        val own = custom.filter { c -> TransactionCategoryCatalog.defaultCategories.none { it.equals(c, true) } }
        if (own.isNotEmpty()) add("Yours" to own)
    }
    val all = TransactionCategoryCatalog.allCategories(custom)
    KashSheet(title, onDismiss = onClose) {
        KashSearchField(query, { query = it }, "Search categories")
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (query.isNotBlank()) {
                val hits = all.filter { it.contains(query.trim(), ignoreCase = true) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hits.forEach { c -> KashChip(c, c == pick, { pick = c }) }
                    KashChip("New category", false, onNewCategory, leading = KashIcon.Plus, dashed = true)
                }
            } else {
                sections.forEachIndexed { i, (name, cats) ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(name, style = KashType.Label, color = KashColors.InkMuted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cats.forEach { c -> KashChip(c, c == pick, { pick = c }) }
                            if (i == sections.lastIndex) KashChip("New category", false, onNewCategory, leading = KashIcon.Plus, dashed = true)
                        }
                    }
                }
            }
        }
        val name = tx?.counterparty?.trim()?.takeIf { it.isNotBlank() && !tx.merchantKey.isNullOrBlank() }
        if (name != null && onToggleApplyToSimilar != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KashShapes.Card)
                    .background(KashColors.SurfaceRaised)
                    .border(1.dp, KashColors.Line, KashShapes.Card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Use for $name from now on", style = KashType.BodyStrong, color = KashColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Future SMS from this sender land in ${pick ?: "this category"}",
                        style = KashType.Caption, color = KashColors.InkMuted,
                    )
                }
                KashSwitch(applyToSimilar, { onToggleApplyToSimilar() }, "Use for $name from now on")
            }
        }
        KashButton(
            pick?.let { "Use $it" } ?: "Pick one above",
            { pick?.let(onUse) },
            Modifier.fillMaxWidth(),
            enabled = pick != null,
        )
    }
}

/** AppNewCategory. */
@Composable
fun NewCategoryDialog(onCancel: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    KashDialog(title = "New category", description = "A short name to group transactions by.", onDismiss = onCancel) {
        KashTextField(name, { name = it.take(32) }, "Name", placeholder = "e.g. Gym")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KashButton("Create", { if (name.isNotBlank()) onCreate(name.trim()) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            KashButton("Cancel", onCancel, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
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
    KashDialog(
        title = "Put all $total in $category?",
        description = "Kash found $total transactions with $name. Kash can label them together.",
        onDismiss = onDismiss,
    ) {
        Column {
            samples.take(3).forEach { tx ->
                KashTransactionRow(
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
            KashButton("Label all $total", onAll, Modifier.fillMaxWidth())
            KashButton("Just this one", onJustOne, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        }
    }
}
