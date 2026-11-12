package com.financeapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.rememberReducedMotion
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.GoalsState

// The Goals section: a sand top instead of slate (DECISIONS 2026-11-12), so
// saving reads as a different place from spending. Text on sand is slate-ink.

// ─── Goals tab ───────────────────────────────────────────────────────────

@Composable
fun GoalsScreen(
    state: GoalsState,
    tabBar: @Composable () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
) {
    KashSystemBars(darkIcons = true)
    var showReached by rememberSaveable { mutableStateOf(false) }
    val active = state.active
    val reached = state.reached

    KashScreen(
        heroColor = KashColors.GoalsHero,
        tabBar = tabBar,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Goals", style = KashType.TitleM, color = KashColors.GoalsInk,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                KashIconButton(KashIcon.Plus, "New goal", onNewGoal)
            }
            Column {
                Text("Saved toward your goals", style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.GoalsInkMuted)
                KashFigure(
                    state.summary.totalSaved,
                    color = KashColors.GoalsInk,
                    unitColor = KashColors.GoalsInkMuted,
                    showDecimals = false,
                    countUp = true,
                )
                if (state.summary.active > 0) {
                    Text(
                        "${state.summary.onTrack} of ${state.summary.active} on track",
                        style = KashType.Label, color = KashColors.GoalsInk,
                    )
                }
            }
        },
    ) { pad ->
        when {
            state.isLoading -> Column { repeat(3) { KashSkeletonRow() } }
            active.isEmpty() && reached.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(pad).padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                KashEmptyState(
                    KashIcon.Target,
                    "No goals yet",
                    "Pick something to save for. Link your savings account and Kash counts what lands there.",
                ) { KashButton("Make your first goal", onNewGoal, Modifier.fillMaxWidth()) }
            }
            else -> LazyColumn(
                contentPadding = pad,
                verticalArrangement = Arrangement.spacedBy(KashSpace.GapM),
            ) {
                items(active, key = { it.goal.id }) { r ->
                    GoalCard(r, state.accountLabel(r.goal.linkedAccountKey)) { onOpenGoal(r.goal.id) }
                }
                if (active.isEmpty()) item {
                    Text(
                        "Every goal is reached. Tap + to start the next one.",
                        style = KashType.Body, color = KashColors.InkMuted, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                if (reached.isNotEmpty()) {
                    item(key = "reached-header") {
                        KashListRow(
                            "Reached (${reached.size})",
                            value = if (showReached) "Hide" else "Show",
                            inset = 0.dp,
                            showDivider = false,
                            onClick = { showReached = !showReached },
                        )
                    }
                    if (showReached) items(reached, key = { it.goal.id }) { r ->
                        GoalCard(r, state.accountLabel(r.goal.linkedAccountKey)) { onOpenGoal(r.goal.id) }
                    }
                }
            }
        }
    }
}

/** One goal in the list: name, pace tag, bar, amounts, what to do next. */
@Composable
private fun GoalCard(r: GoalProgress.Result, accountLabel: String?, onClick: () -> Unit) {
    val cal = LocalCalendarSystem.current
    Column(
        Modifier
            .fillMaxWidth()
            .kashClickable(onClickLabel = "Open ${r.goal.name}", onClick = onClick)
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                goalTitle(r.goal), style = KashType.TitleXs, color = KashColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            GoalCopy.paceLabel(r)?.let { PaceTag(it, r.pace) }
        }
        GoalBar(r.fraction, reached = r.reached)
        Text(GoalCopy.amountLine(r), style = KashType.BodyStrong.copy(fontFeatureSettings = "tnum"), color = KashColors.Ink)
        Text(
            GoalCopy.nextStep(r, cal) + (accountLabel?.let { " · from $it" } ?: ""),
            style = KashType.Caption, color = KashColors.InkMuted,
        )
    }
}

private fun goalTitle(g: GoalEntity) = listOfNotNull(g.emoji?.takeIf { it.isNotBlank() }, g.name).joinToString(" ")

@Composable
private fun PaceTag(text: String, pace: Pace) {
    val (bg, fg) = when (pace) {
        Pace.REACHED -> KashColors.GoalsReached to KashColors.OnSlate
        Pace.BEHIND, Pace.MISSED -> KashColors.Well to KashColors.MoneyOut
        else -> KashColors.Sand to KashColors.SlateInk
    }
    Text(
        text,
        style = KashType.Micro.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier.clip(KashShapes.Pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Progress bar: slate-ink fill on a slate-ink 14% track; green once reached. */
@Composable
fun GoalBar(fraction: Double, reached: Boolean, modifier: Modifier = Modifier, height: Int = 8) {
    val reduced = rememberReducedMotion()
    val shown by animateFloatAsState(
        fraction.toFloat().coerceIn(0f, 1f), if (reduced) tween(0) else tween(600), label = "goalBar",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(KashShapes.Pill)
            .background(KashColors.GoalsTrack)
            .semantics { contentDescription = "${(fraction * 100).toInt()}% saved" },
    ) {
        if (shown > 0f) Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceAtLeast(0.02f))
                .clip(KashShapes.Pill)
                .background(if (reached) KashColors.GoalsReached else KashColors.GoalsInk)
        )
    }
}

// ─── Goal page ───────────────────────────────────────────────────────────

@Composable
fun GoalDetailScreen(
    result: GoalProgress.Result,
    accountLabel: String?,
    /** True once: the goal was just reached and the card hasn't been shown. */
    celebrate: Boolean,
    onCelebrated: () -> Unit,
    onBack: () -> Unit,
    onAddSave: (amount: Double, note: String?, atMs: Long) -> Unit,
    onDeleteSave: (String) -> Unit,
    onOpenTx: (Long) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    KashSystemBars(darkIcons = true)
    val cal = LocalCalendarSystem.current
    val goal = result.goal
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var saveToDelete by remember { mutableStateOf<GoalProgress.Movement?>(null) }
    val archived = goal.status == GoalStatus.ARCHIVED

    KashScreen(
        heroColor = KashColors.GoalsHero,
        hero = {
            KashTopBar(
                title = null, onBack = onBack, onSlate = false,
                actions = { KashIconButton(KashIcon.More, "More", { menuOpen = true }) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(goalTitle(goal), style = KashType.TitleM, color = KashColors.GoalsInk, modifier = Modifier.semantics { heading() })
                KashFigure(
                    if (goal.type == GoalType.RECURRING) result.savedThisMonth else result.saved,
                    color = KashColors.GoalsInk, unitColor = KashColors.GoalsInkMuted,
                    showDecimals = false, countUp = true,
                )
                GoalBar(result.fraction, reached = result.reached, height = 10)
                Text(GoalCopy.amountLine(result), style = KashType.Label, color = KashColors.GoalsInkMuted)
            }
            if (!archived) KashButton("Add a save", { addOpen = true }, Modifier.fillMaxWidth(), icon = KashIcon.Plus, compact = true)
        },
    ) { pad ->
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(KashSpace.GapM)) {
            item(key = "status") {
                Column(
                    Modifier.fillMaxWidth().clip(KashShapes.Card).background(KashColors.SurfaceRaised)
                        .border(1.dp, KashColors.Line, KashShapes.Card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(kindLabel(goal), style = KashType.Label, color = KashColors.InkMuted, modifier = Modifier.weight(1f))
                        GoalCopy.paceLabel(result)?.let { PaceTag(it, result.pace) }
                    }
                    Text(GoalCopy.nextStep(result, cal), style = KashType.BodyStrong, color = KashColors.Ink)
                    GoalCopy.streak(result)?.let { Text(it, style = KashType.Body, color = KashColors.Ink) }
                    if (result.averagePerMonth > 0.0) {
                        Text(
                            "You save about ${KashMoney.whole(result.averagePerMonth)} ETB a month",
                            style = KashType.Caption, color = KashColors.InkMuted,
                        )
                    }
                    Text(GoalCopy.sourceLine(accountLabel), style = KashType.Caption, color = KashColors.InkMuted)
                }
            }
            item(key = "history-title") {
                Text(
                    "History", style = KashType.TitleS, color = KashColors.Ink,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            if (result.movements.isEmpty()) item(key = "history-empty") {
                Text(
                    if (accountLabel != null) "Nothing yet. Money that lands in $accountLabel from now on shows here."
                    else "Nothing yet. Tap Add a save when you put money aside.",
                    style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.InkMuted,
                )
            }
            items(result.movements, key = { it.contributionId ?: "tx-${it.transactionId}-${it.atMs}-${it.amount}" }) { m ->
                KashTransactionRow(
                    name = GoalCopy.movementName(m, accountLabel),
                    amount = m.amount,
                    direction = if (m.amount >= 0) KashDirection.In else KashDirection.Out,
                    time = KashDates.whenShort(m.atMs, cal),
                    category = if (m.source == GoalProgress.Source.MANUAL) "Added by you" else "From SMS",
                    onClick = {
                        val tx = m.transactionId
                        if (tx != null) onOpenTx(tx) else saveToDelete = m
                    },
                )
            }
        }
    }

    if (addOpen) AddSaveSheet(onDismiss = { addOpen = false }, onSave = { a, n, at -> onAddSave(a, n, at); addOpen = false })

    if (menuOpen) KashSheet(title = goalTitle(goal), onDismiss = { menuOpen = false }) {
        KashListGroup {
            if (!archived) KashListRow("Archive", hint = "Hide it from your goals and free its account", onClick = { menuOpen = false; onArchive() })
            KashListRow("Delete", labelColor = KashColors.MoneyOut, showDivider = false, onClick = { menuOpen = false; confirmDelete = true })
        }
    }

    if (confirmDelete) KashDialog(
        title = "Delete this goal?",
        description = "Its saves go with it. Your transactions stay as they are.",
        onDismiss = { confirmDelete = false },
    ) {
        KashButton("Delete goal", { confirmDelete = false; onDelete() }, Modifier.fillMaxWidth(), KashButtonVariant.Danger)
        KashButton("Cancel", { confirmDelete = false }, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
    }

    saveToDelete?.let { m ->
        KashDialog(
            title = "Remove this save?",
            description = "${KashMoney.whole(m.amount)} ETB from ${KashDates.whenShort(m.atMs, cal)} comes off the goal.",
            onDismiss = { saveToDelete = null },
        ) {
            KashButton("Remove", { m.contributionId?.let(onDeleteSave); saveToDelete = null }, Modifier.fillMaxWidth(), KashButtonVariant.Danger)
            KashButton("Keep it", { saveToDelete = null }, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        }
    }

    if (celebrate) GoalReachedDialog(
        result,
        onArchive = { onCelebrated(); onArchive() },
        onKeep = onCelebrated,
    )
}

private fun kindLabel(g: GoalEntity): String = when (g.type) {
    GoalType.DATE_BOUND -> "Amount by a date"
    GoalType.OPEN_ENDED -> "Safety net · ${g.targetMonths ?: 0} months of spending"
    else -> "Every month"
}

// ─── Add a save ──────────────────────────────────────────────────────────

/** Amount, an optional note, and the day (today or yesterday). */
@Composable
fun AddSaveSheet(onDismiss: () -> Unit, onSave: (amount: Double, note: String?, atMs: Long) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var yesterday by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        val value = amount.replace(",", "").trim().toDoubleOrNull()
        if (value == null || value <= 0.0) error = "Enter how much you saved"
        else {
            val now = System.currentTimeMillis()
            onSave(value, note.ifBlank { null }, if (yesterday) now - 86_400_000L else now)
        }
    }
    KashSheet(title = "Add a save", onDismiss = onDismiss) {
        KashTextField(
            amount, { amount = it; error = null }, "Amount (ETB)",
            placeholder = "1,000", keyboardType = KeyboardType.Decimal, error = error,
        )
        KashTextField(
            note, { note = it }, "Note (optional)", placeholder = "Cash from Ekub",
            imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
            KashChip("Today", selected = !yesterday, onClick = { yesterday = false })
            KashChip("Yesterday", selected = yesterday, onClick = { yesterday = true })
        }
        KashButton("Save", submit, Modifier.fillMaxWidth())
    }
}

// ─── Goal reached ────────────────────────────────────────────────────────

@Composable
fun GoalReachedDialog(r: GoalProgress.Result, onArchive: () -> Unit, onKeep: () -> Unit) {
    KashDialog(
        title = "You reached it 🎉",
        description = "You saved ${KashMoney.whole(r.saved)} ETB for ${r.goal.name}.",
        onDismiss = onKeep,
    ) {
        GoalBar(1.0, reached = true, height = 10)
        Spacer(Modifier.height(2.dp))
        KashButton("Archive it", onArchive, Modifier.fillMaxWidth())
        KashButton("Keep it on the list", onKeep, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

private fun previewResult(
    name: String, type: String, saved: Double, target: Double?, pace: Pace,
    deadline: Long? = null, perMonth: Double? = null,
) = GoalProgress.Result(
    goal = GoalEntity(name = name, type = type, targetAmount = target, deadline = deadline, linkedAccountKey = "CBE#1234"),
    saved = saved, target = target,
    fraction = if (target == null) 0.0 else (saved / target).coerceAtMost(1.0),
    pace = pace, remaining = (target ?: 0.0) - saved, perMonthNeeded = perMonth, monthsLeft = 6,
    savedThisMonth = saved, averagePerMonth = 4_000.0, projectedFinishMs = null, streakMonths = 3, movements = emptyList(),
)

@Preview(name = "Goals tab", widthDp = 360, heightDp = 760)
@Composable
private fun GoalsTabPreview() = KashTheme {
    val june = 1_812_000_000_000L
    GoalsScreen(
        state = GoalsState(
            summary = GoalProgress.Summary(
                listOf(
                    previewResult("Laptop", GoalType.DATE_BOUND, 12_400.0, 60_000.0, Pace.ON_TRACK, june, 4_800.0),
                    previewResult("Every month", GoalType.RECURRING, 500.0, 2_000.0, Pace.BEHIND, perMonth = 1_500.0),
                )
            ),
            isLoading = false,
        ),
        tabBar = { com.financeapp.ui.kash.components.KashTabBar(com.financeapp.ui.kash.components.KashTab.Goals, {}) },
        onOpenGoal = {}, onNewGoal = {},
    )
}

@Preview(name = "Goals empty", widthDp = 360, heightDp = 760)
@Composable
private fun GoalsEmptyPreview() = KashTheme {
    GoalsScreen(GoalsState(isLoading = false), tabBar = {}, onOpenGoal = {}, onNewGoal = {})
}
