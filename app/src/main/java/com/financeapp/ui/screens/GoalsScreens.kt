package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.ui.goals.GoalIcons
import com.financeapp.ui.goals.GoalsButton
import com.financeapp.ui.goals.GoalsButtonVariant
import com.financeapp.ui.goals.GoalsCard
import com.financeapp.ui.goals.GoalsChip
import com.financeapp.ui.goals.GoalsColors
import com.financeapp.ui.goals.GoalsDayHeader
import com.financeapp.ui.goals.GoalsDialog
import com.financeapp.ui.goals.GoalsDivider
import com.financeapp.ui.goals.GoalsIconButton
import com.financeapp.ui.goals.GoalsIconWell
import com.financeapp.ui.goals.GoalsKindCard
import com.financeapp.ui.goals.GoalsListRow
import com.financeapp.ui.goals.GoalsPaceStrip
import com.financeapp.ui.goals.GoalsPaceTag
import com.financeapp.ui.goals.GoalsProgress
import com.financeapp.ui.goals.GoalsSheet
import com.financeapp.ui.goals.GoalsSkeleton
import com.financeapp.ui.goals.GoalsStreakBadge
import com.financeapp.ui.goals.GoalsTextField
import com.financeapp.ui.goals.GoalsTxRow
import com.financeapp.ui.kash.Haptic
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashPress
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberHaptic
import com.financeapp.ui.kash.rememberReducedMotion
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.GoalsState

// The Goals section is dark (design/goals-handoff, DECISIONS 2026-11-13).
// Sand is the one accent. Pride first, quiet when on track, automatic first.

@Composable
private fun GoalsFrame(
    hero: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    tabBar: (@Composable () -> Unit)? = null,
    heroGap: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    KashSystemBars(darkIcons = false, navDarkIcons = false)
    KashScreen(
        hero = hero,
        tabBar = tabBar,
        heroGap = heroGap,
        heroColor = GoalsColors.Hero,
        sheetColor = GoalsColors.Sheet,
        handleColor = GoalsColors.Handle,
        content = content,
    )
}

// ─── Goals tab ───────────────────────────────────────────────────────────

@Composable
fun GoalsScreen(
    state: GoalsState,
    tabBar: @Composable () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    /** Empty state: a kind card goes straight to step 2 of New goal. */
    onStartKind: (String) -> Unit = { onNewGoal() },
) {
    val active = state.active
    val reached = state.reached
    val empty = !state.isLoading && active.isEmpty() && reached.isEmpty()
    var showReached by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val reachedOpen = showReached ?: active.isEmpty()

    GoalsFrame(
        tabBar = tabBar,
        heroGap = if (empty) 24.dp else 20.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Goals", style = KashType.TitleM, color = GoalsColors.HeroInk, modifier = Modifier.weight(1f).semantics { heading() })
                if (!empty) GoalsIconButton(KashIcon.Plus, "New goal", onNewGoal)
            }
            when {
                empty -> Column {
                    Text("Put money aside\nfor something", style = KashType.TitleL, color = GoalsColors.HeroInk)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Link the account you save in and Kash counts what lands there. Nothing to type.",
                        style = KashType.BodyL, color = GoalsColors.HeroInkMuted,
                    )
                }
                state.isLoading -> {
                    Column {
                        Text("Saved toward your goals", style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.HeroInkMuted)
                        GoalsSkeleton(Modifier.padding(top = 8.dp).width(190.dp).height(40.dp), onHero = true)
                    }
                }
                else -> {
                    Column {
                        Text("Saved toward your goals", style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.HeroInkMuted)
                        KashFigure(
                            state.summary.totalSaved, Modifier.padding(top = 4.dp),
                            color = GoalsColors.HeroInk, unitColor = GoalsColors.HeroInkMuted, showDecimals = false, countUp = true,
                        )
                        GoalCopy.monthPill(state.summary.savedThisMonth)?.let {
                            Text(
                                it, style = KashType.Label.num(), color = GoalsColors.HeroInk,
                                modifier = Modifier.padding(top = 6.dp).clip(KashShapes.Pill).background(GoalsColors.HeroRaised)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (active.size >= 2) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GoalsPaceStrip(state.summary.onTrack, state.summary.active, Modifier.weight(1f))
                        Text("${state.summary.onTrack} of ${state.summary.active} on track", style = KashType.Label.num(), color = GoalsColors.HeroInk, maxLines = 1)
                    }
                }
            }
        },
    ) { pad ->
        when {
            state.isLoading -> Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { GoalCardSkeleton() }
            }
            empty -> Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pick one to start", style = KashType.Label, color = GoalsColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
                KindCards(selected = null, onPick = onStartKind)
            }
            else -> LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (active.size == 1 && reached.isEmpty()) {
                    val r = active.single()
                    item(key = r.goal.id) { GoalCard(r, state.accountLabel(r.goal.linkedAccountKey), large = true) { onOpenGoal(r.goal.id) } }
                } else {
                    items(active, key = { it.goal.id }) { r ->
                        GoalCard(r, state.accountLabel(r.goal.linkedAccountKey)) { onOpenGoal(r.goal.id) }
                    }
                }
                if (active.isEmpty()) item(key = "all-reached") { AllReached(onNewGoal) }
                if (reached.isNotEmpty()) {
                    item(key = "reached-fold") {
                        ReachedFold(reached.size, reachedOpen) { showReached = !reachedOpen }
                    }
                    if (reachedOpen) items(reached, key = { it.goal.id }) { r ->
                        GoalCard(r, null) { onOpenGoal(r.goal.id) }
                    }
                }
            }
        }
    }
}

/** The three kinds of goal (empty Goals tab and New goal step 1). */
@Composable
fun KindCards(selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GoalsKindCard(KashIcon.Calendar, "An amount by a date", "Laptop, 60,000 ETB by June", { onPick(GoalType.DATE_BOUND) }, selected = selected == GoalType.DATE_BOUND)
        GoalsKindCard(KashIcon.Shield, "A safety net", "3 months of spending, for when things go wrong", { onPick(GoalType.OPEN_ENDED) }, selected = selected == GoalType.OPEN_ENDED)
        GoalsKindCard(KashIcon.Repeat, "Every month", "5,000 ETB a month, for good", { onPick(GoalType.RECURRING) }, selected = selected == GoalType.RECURRING)
    }
}

@Composable
private fun AllReached(onNewGoal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(KashShapes.Control).background(GoalsColors.Reached), contentAlignment = Alignment.Center) {
            KashIconView(KashIcon.Check, size = 28.dp, tint = Color.White, strokeWidth = 2.6f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Every goal is reached", style = KashType.TitleS, color = GoalsColors.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Start the next one when you're ready. Accounts from reached goals are free to use again.",
                style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.InkMuted, textAlign = TextAlign.Center,
            )
        }
        GoalsButton("Start a new goal", onNewGoal, variant = GoalsButtonVariant.Secondary, icon = KashIcon.Plus)
    }
}

@Composable
private fun ReachedFold(count: Int, open: Boolean, onToggle: () -> Unit) {
    Column {
        GoalsDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .kashClickable(press = KashPress.Highlight, onClickLabel = if (open) "Hide reached goals" else "Show reached goals", onClick = onToggle)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reached ", style = KashType.BodyStrong, color = GoalsColors.Ink)
            Text("($count)", style = KashType.Body.num(), color = GoalsColors.InkMuted, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = KashType.Label, color = GoalsColors.InkMuted)
            Spacer(Modifier.width(4.dp))
            KashIconView(KashIcon.ChevronDown, size = 16.dp, tint = GoalsColors.InkMuted, modifier = Modifier.scale(1f, if (open) -1f else 1f))
        }
    }
}

/** One goal on the Goals tab. [large]: the single-goal card, percentage first. */
@Composable
private fun GoalCard(r: GoalProgress.Result, accountLabel: String?, large: Boolean = false, onClick: () -> Unit) {
    val cal = LocalCalendarSystem.current
    val g = r.goal
    val monthly = g.type == GoalType.RECURRING
    val reached = r.pace == Pace.REACHED
    val (big, rest) = if (large) GoalCopy.largeCardAmount(r) else GoalCopy.cardAmount(r)
    GoalsCard(onClick = onClick, onClickLabel = "Open ${g.name}", padding = if (large) 20.dp else 16.dp, spacing = if (large) 14.dp else 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalsIconWell(GoalIcons.iconOf(g), reached = reached && !monthly)
            Text(
                g.name, style = if (large) KashType.TitleS else KashType.LabelL, color = GoalsColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            GoalsPaceTag(r.pace, monthly)
        }
        if (large) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(big, style = KashType.FigureM, color = GoalsColors.Ink)
            if (rest != null) Text(rest, style = KashType.Label.num(), color = GoalsColors.InkMuted)
        } else Row(verticalAlignment = Alignment.Bottom) {
            Text(big, style = KashType.TitleS.copy(fontWeight = FontWeight.SemiBold).num(), color = GoalsColors.Ink)
            if (rest != null) Text(" $rest", style = KashType.Label.num(), color = GoalsColors.InkMuted, modifier = Modifier.padding(bottom = 1.dp))
        }
        GoalsProgress(
            r.fraction, mark = progressMark(r), large = large, reached = reached, empty = r.target == null,
            label = "${g.name} progress",
        )
        GoalCopy.cardNext(r, cal)?.let { Text(it, style = KashType.Caption, color = GoalsColors.Ink) }
        if (monthly && r.streakMonths >= 2) GoalsStreakBadge("${r.streakMonths} months in a row")
        if (accountLabel != null && !reached) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KashIconView(KashIcon.Sms, size = 14.dp, tint = GoalsColors.InkMuted)
            Text("Counts money into $accountLabel", style = KashType.Micro, color = GoalsColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Where the pace mark sits on the bar (0..1), or null for no mark. */
private fun progressMark(r: GoalProgress.Result): Double? {
    val even = r.evenPaceNow ?: return null
    val target = r.target ?: return null
    if (target <= 0.0) return null
    return (even / target).coerceIn(0.0, 1.0)
}

@Composable
private fun GoalCardSkeleton() {
    GoalsCard(spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalsSkeleton(Modifier.size(40.dp))
            GoalsSkeleton(Modifier.width(140.dp).height(14.dp))
            Spacer(Modifier.weight(1f))
            GoalsSkeleton(Modifier.width(64.dp).height(22.dp))
        }
        GoalsSkeleton(Modifier.width(150.dp).height(14.dp))
        GoalsSkeleton(Modifier.fillMaxWidth().height(8.dp))
        GoalsSkeleton(Modifier.width(240.dp).height(11.dp))
    }
}

// ─── Goal page ───────────────────────────────────────────────────────────

@Composable
fun GoalDetailScreen(
    result: GoalProgress.Result,
    accountLabel: String?,
    averageMonthlySpend: Double,
    /** True once: the goal was just reached and the card hasn't been shown. */
    celebrate: Boolean,
    onCelebrated: () -> Unit,
    /** True once a month: a monthly goal's amount was just reached. */
    monthDone: Boolean,
    onMonthDoneSeen: () -> Unit,
    onBack: () -> Unit,
    onAddSave: (amount: Double, note: String?, atMs: Long) -> Unit,
    onDeleteSave: (String) -> Unit,
    onOpenTx: (Long) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onChangeAccount: () -> Unit,
) {
    val cal = LocalCalendarSystem.current
    val goal = result.goal
    val monthly = goal.type == GoalType.RECURRING
    val reached = result.pace == Pace.REACHED
    val archived = goal.status == GoalStatus.ARCHIVED
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var saveToRemove by remember { mutableStateOf<GoalProgress.Movement?>(null) }
    val now = remember { System.currentTimeMillis() }

    GoalsFrame(
        heroGap = 18.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalsIconButton(KashIcon.Back, "Back", onBack)
                Spacer(Modifier.weight(1f))
                GoalsIconButton(KashIcon.More, "More options", { menuOpen = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalsIconWell(GoalIcons.iconOf(goal), onHero = true)
                Text(goal.name, style = KashType.TitleS, color = GoalsColors.HeroInk, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(GoalCopy.figureLabel(result), style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.HeroInkMuted)
                    KashFigure(
                        if (monthly) result.savedThisMonth else result.saved, Modifier.padding(top = 2.dp),
                        color = GoalsColors.HeroInk, unitColor = GoalsColors.HeroInkMuted, showDecimals = false, countUp = true,
                    )
                }
                GoalsProgress(
                    result.fraction, mark = progressMark(result), large = true, reached = reached, empty = result.target == null,
                    ground = GoalsColors.Hero, label = "${goal.name} progress",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(GoalCopy.amountLine(result), style = KashType.Label.num(), color = GoalsColors.HeroInk, modifier = Modifier.weight(1f))
                    Text(GoalCopy.barRight(result, cal), style = KashType.Label.num(), color = GoalsColors.HeroInkMuted, maxLines = 1)
                }
            }
            if (!archived) GoalsButton("Add a save", { addOpen = true }, Modifier.fillMaxWidth(), icon = KashIcon.Plus, compact = true)
        },
    ) { pad ->
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "status") { StatusCard(result, accountLabel, averageMonthlySpend, now) }
            item(key = "history-title") {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("History", style = KashType.TitleS, color = GoalsColors.Ink, modifier = Modifier.semantics { heading() })
                    GoalCopy.historyCaption(accountLabel)?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KashIconView(KashIcon.Sms, size = 16.dp, tint = GoalsColors.InkMuted)
                            Text(it, style = KashType.Caption, color = GoalsColors.InkMuted)
                        }
                    }
                }
            }
            if (result.movements.isEmpty()) item(key = "history-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(KashShapes.Control).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
                        KashIconView(KashIcon.History, size = 22.dp, tint = GoalsColors.InkMuted)
                    }
                    Text(
                        GoalCopy.emptyHistory(accountLabel), style = KashType.Body.copy(fontWeight = FontWeight.Normal),
                        color = GoalsColors.InkMuted, textAlign = TextAlign.Center,
                    )
                }
            }
            val byMonth = result.movements.groupBy { GoalProgress.startOfMonth(it.atMs, java.util.TimeZone.getDefault()) }
            byMonth.forEach { (month, moves) ->
                item(key = "m-$month") {
                    GoalsDayHeader(KashDates.monthName(month, cal), GoalCopy.monthNet(moves.sumOf { it.amount }))
                }
                items(moves, key = { it.contributionId ?: "tx-${it.transactionId}-${it.atMs}-${it.amount}" }) { m ->
                    val manual = m.source == GoalProgress.Source.MANUAL
                    GoalsTxRow(
                        name = GoalCopy.movementName(m, accountLabel),
                        amount = m.amount,
                        direction = if (m.amount >= 0) KashDirection.In else KashDirection.Out,
                        time = KashDates.dayMonth(m.atMs, cal) + ", " + KashDates.time(m.atMs),
                        category = if (manual) "Added by you" else "From SMS",
                        onClick = { val tx = m.transactionId; if (tx != null) onOpenTx(tx) else saveToRemove = m },
                    )
                }
            }
        }
    }

    if (addOpen) AddSaveSheet(onDismiss = { addOpen = false }, onSave = { a, n, at -> onAddSave(a, n, at); addOpen = false })

    if (menuOpen) GoalsSheet(title = goal.name, onDismiss = { menuOpen = false }) {
        Column {
            if (!archived) {
                GoalsListRow("Edit", { menuOpen = false; onEdit() }, icon = KashIcon.Edit)
                GoalsListRow("Change account", { menuOpen = false; onChangeAccount() }, icon = KashIcon.Card, value = accountLabel ?: "None")
                GoalsListRow("Archive", { menuOpen = false; onArchive() }, icon = KashIcon.Archive, hint = "Hide it from your goals and free its account")
            }
            GoalsListRow(
                "Delete", { menuOpen = false; confirmDelete = true }, icon = KashIcon.Trash,
                color = GoalsColors.MoneyOut, chevron = false, showDivider = false,
            )
        }
    }

    if (confirmDelete) GoalsDialog(
        onDismiss = { confirmDelete = false },
        title = "Delete this goal?",
        description = "Its saves go with it. Your transactions stay as they are.",
    ) {
        if (!archived) Row(
            Modifier.fillMaxWidth().clip(KashShapes.Card).background(GoalsColors.Raised).border(1.dp, GoalsColors.Line, KashShapes.Card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KashIconView(KashIcon.Archive, size = 20.dp, tint = GoalsColors.InkMuted)
            Text("To keep its history, archive it instead", style = KashType.Body, color = GoalsColors.Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GoalsButton("Delete goal", { confirmDelete = false; onDelete() }, Modifier.fillMaxWidth(), GoalsButtonVariant.Danger)
            GoalsButton("Cancel", { confirmDelete = false }, Modifier.fillMaxWidth(), GoalsButtonVariant.Quiet)
        }
    }

    saveToRemove?.let { m ->
        GoalsDialog(
            onDismiss = { saveToRemove = null },
            title = "Remove this save?",
            description = "${KashMoney.whole(m.amount)} ETB from ${KashDates.dayMonth(m.atMs, cal)}, ${KashDates.time(m.atMs)} comes off the goal.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GoalsButton("Remove", { m.contributionId?.let(onDeleteSave); saveToRemove = null }, Modifier.fillMaxWidth(), GoalsButtonVariant.Danger)
                GoalsButton("Keep it", { saveToRemove = null }, Modifier.fillMaxWidth(), GoalsButtonVariant.Quiet)
            }
        }
    }

    if (celebrate) GoalReachedDialog(result, onArchive = { onCelebrated(); onArchive() }, onKeep = onCelebrated)
    else if (monthDone) MonthDoneSheet(result, onDone = onMonthDoneSeen)
}

@Composable
private fun StatusCard(r: GoalProgress.Result, accountLabel: String?, averageMonthlySpend: Double, now: Long) {
    val cal = LocalCalendarSystem.current
    val facts = GoalCopy.facts(r, accountLabel, averageMonthlySpend, now, cal)
    Column(
        Modifier.fillMaxWidth().clip(KashShapes.Card).background(GoalsColors.Raised).border(1.dp, GoalsColors.Line, KashShapes.Card)
            .semantics { contentDescription = "Status" },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(GoalCopy.kindLine(r, cal), style = KashType.Label, color = GoalsColors.InkMuted, modifier = Modifier.weight(1f))
                GoalsPaceTag(r.pace, r.goal.type == GoalType.RECURRING)
            }
            Text(GoalCopy.statusLine(r, cal), style = KashType.BodyStrong, color = GoalsColors.Ink)
            GoalCopy.evenPaceLine(r)?.let { Text(it, style = KashType.Caption, color = GoalsColors.InkMuted) }
        }
        facts.forEach { f ->
            GoalsDivider()
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KashIconView(f.icon, size = 18.dp, tint = GoalsColors.InkMuted, modifier = Modifier.padding(top = 1.dp))
                Text(f.text, style = KashType.Label.copy(fontWeight = FontWeight.Normal), color = GoalsColors.Ink)
            }
        }
    }
}

// ─── Add a save ──────────────────────────────────────────────────────────

/** Amount, an optional note, and the day (today or yesterday). */
@Composable
fun AddSaveSheet(onDismiss: () -> Unit, onSave: (amount: Double, note: String?, atMs: Long) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var yesterday by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = rememberHaptic()
    val submit = {
        val value = amount.replace(",", "").trim().toDoubleOrNull()
        if (value == null || value <= 0.0) { error = "Enter how much you saved"; haptic(Haptic.REJECT) }
        else {
            haptic(Haptic.CONFIRM)
            val now = System.currentTimeMillis()
            onSave(value, note.ifBlank { null }, if (yesterday) now - 86_400_000L else now)
        }
    }
    GoalsSheet(title = "Add a save", onDismiss = onDismiss) {
        GoalsTextField(
            amount, { t -> amount = t.filter { it.isDigit() || it == '.' || it == ',' }.take(12); error = null }, "Amount",
            placeholder = "0", unit = "ETB", big = true, keyboardType = KeyboardType.Decimal, error = error,
        )
        GoalsTextField(
            note, { note = it.take(60) }, "Note (optional)", placeholder = "Cash from Ekub",
            imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("When", style = KashType.Label, color = GoalsColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
                GoalsChip("Today", selected = !yesterday, onClick = { yesterday = false })
                GoalsChip("Yesterday", selected = yesterday, onClick = { yesterday = true })
            }
        }
        GoalsButton("Save", submit, Modifier.fillMaxWidth())
    }
}

// ─── Moments ─────────────────────────────────────────────────────────────

/**
 * You reached it: the bar fills over ~600ms, then the check scales in (the
 * one bounce in the section) with one success haptic. Reduced motion: both
 * appear in their final state.
 */
@Composable
fun GoalReachedDialog(r: GoalProgress.Result, onArchive: () -> Unit, onKeep: () -> Unit) {
    val reduced = rememberReducedMotion()
    val haptic = rememberHaptic()
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    val check = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            fill.animateTo(1f, tween(600))
            haptic(Haptic.CONFIRM)
            check.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f))
        }
    }
    GoalsDialog(
        onDismiss = onKeep,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).scale(check.value).clip(KashShapes.Control).background(GoalsColors.Reached),
                    contentAlignment = Alignment.Center,
                ) { KashIconView(KashIcon.Check, size = 28.dp, tint = Color.White, strokeWidth = 2.6f) }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KashIconView(GoalIcons.iconOf(r.goal), size = 20.dp, tint = GoalsColors.HeroInk)
                    Text(r.goal.name, style = KashType.Label, color = GoalsColors.HeroInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column {
                Text("You reached it", style = KashType.TitleL, color = GoalsColors.HeroInk, modifier = Modifier.semantics { heading() })
                KashFigure(
                    r.saved, Modifier.padding(top = 10.dp), style = KashType.FigureL,
                    color = GoalsColors.HeroInk, unitColor = GoalsColors.HeroInkMuted, showDecimals = false,
                )
            }
            GoalsProgress(fill.value.toDouble(), large = true, reached = true, ground = GoalsColors.Hero, label = "${r.goal.name}, reached")
        },
    ) {
        Text("You saved ${KashMoney.whole(r.saved)} ETB for ${r.goal.name}.", style = KashType.BodyL, color = GoalsColors.Ink)
        val facts = GoalCopy.reachedFacts(r)
        if (facts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            facts.forEach { (label, value) ->
                Column(
                    Modifier.weight(1f).clip(KashShapes.Field).background(GoalsColors.Raised).border(1.dp, GoalsColors.Line, KashShapes.Field)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(label, style = KashType.Caption, color = GoalsColors.InkMuted)
                    Text(value, style = KashType.BodyStrong.num(), color = GoalsColors.Ink)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GoalsButton("Archive it", onArchive, Modifier.fillMaxWidth())
            GoalsButton("Keep it on the list", onKeep, Modifier.fillMaxWidth(), GoalsButtonVariant.Quiet)
        }
    }
}

/** Month done: a monthly goal reached this month's amount. Once a month. */
@Composable
fun MonthDoneSheet(r: GoalProgress.Result, onDone: () -> Unit) {
    val cal = LocalCalendarSystem.current
    val run = GoalCopy.runWithThisMonth(r)
    val haptic = rememberHaptic()
    LaunchedEffect(Unit) { haptic(Haptic.CONFIRM) }
    GoalsSheet(title = null, onDismiss = onDone, containerColor = GoalsColors.Hero) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalsIconWell(KashIcon.Check, reached = true)
            Text("${r.goal.name} · ${KashDates.monthName(System.currentTimeMillis(), cal)}", style = KashType.Label, color = GoalsColors.HeroInk)
        }
        Column {
            Text("Month done", style = KashType.TitleL, color = GoalsColors.HeroInk, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(GoalCopy.monthDoneLine(r), style = KashType.BodyL, color = GoalsColors.HeroInk)
        }
        if (run >= 2) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.semantics { contentDescription = "$run months in a row" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(run.coerceAtMost(12)) {
                    Box(Modifier.weight(1f).height(8.dp).clip(KashShapes.Pill).background(GoalsColors.Reached))
                }
            }
            Row {
                Text("$run months in a row", style = KashType.BodyStrong.num(), color = GoalsColors.HeroInk, modifier = Modifier.weight(1f))
                if (run >= r.longestStreak) Text("Your longest yet", style = KashType.Label, color = GoalsColors.HeroInkMuted)
            }
        }
        GoalsButton("Done", onDone, Modifier.fillMaxWidth())
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

private fun previewResult(
    name: String, type: String, saved: Double, target: Double?, pace: Pace, icon: String,
    even: Double? = null, perMonth: Double? = null, streak: Int = 0,
) = GoalProgress.Result(
    goal = GoalEntity(name = name, type = type, targetAmount = target, deadline = 1_812_000_000_000L, linkedAccountKey = "CBE#2222", emoji = icon),
    saved = saved, target = target,
    fraction = if (target == null) 0.0 else (saved / target).coerceAtMost(1.0),
    pace = pace, remaining = (target ?: 0.0) - saved, perMonthNeeded = perMonth, monthsLeft = 6,
    savedThisMonth = saved, averagePerMonth = 4_300.0, projectedFinishMs = null, streakMonths = streak,
    movements = emptyList(), evenPaceNow = even,
)

@Preview(name = "Goals tab (dark)", widthDp = 360, heightDp = 760)
@Composable
private fun GoalsTabPreview() = KashTheme {
    GoalsScreen(
        state = GoalsState(
            summary = GoalProgress.Summary(
                listOf(
                    previewResult("Laptop", GoalType.DATE_BOUND, 34_600.0, 60_000.0, Pace.ON_TRACK, "laptop", even = 32_000.0),
                    previewResult("Wedding and new apartment deposit", GoalType.DATE_BOUND, 212_500.0, 1_250_000.0, Pace.BEHIND, "rings", even = 521_300.0, perMonth = 74_107.0),
                    previewResult("Monthly savings", GoalType.RECURRING, 2_000.0, 5_000.0, Pace.ON_TRACK, "repeat", even = 2_166.0, streak = 3),
                )
            ),
            isLoading = false,
        ),
        tabBar = {}, onOpenGoal = {}, onNewGoal = {},
    )
}

@Preview(name = "Goals empty (dark)", widthDp = 360, heightDp = 760)
@Composable
private fun GoalsEmptyPreview() = KashTheme {
    GoalsScreen(GoalsState(isLoading = false), tabBar = {}, onOpenGoal = {}, onNewGoal = {})
}
