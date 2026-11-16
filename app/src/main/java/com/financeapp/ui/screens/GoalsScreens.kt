package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.lazy.itemsIndexed
import com.financeapp.ui.kash.components.LocalKashTips
import com.financeapp.ui.kash.components.tipAnchor
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
internal fun GoalsFrame(
    hero: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    tabBar: (@Composable () -> Unit)? = null,
    heroGap: androidx.compose.ui.unit.Dp = 20.dp,
    expandable: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    KashSystemBars(darkIcons = false, navDarkIcons = false)
    KashScreen(
        hero = hero,
        tabBar = tabBar,
        heroGap = heroGap,
        expandable = expandable,
        heroColor = GoalsColors.Hero,
        sheetColor = GoalsColors.Sheet,
        handleColor = GoalsColors.Handle,
        content = content,
    )
}

// ─── Goals tab (approved flow, section 9) ────────────────────────────────

@Composable
fun GoalsScreen(
    state: GoalsState,
    tabBar: @Composable () -> Unit,
    onOpenGoal: (String) -> Unit,
    onNewGoal: () -> Unit,
    /** Empty state: a kind row goes straight to step 2 of New goal. */
    onStartKind: (String) -> Unit = { onNewGoal() },
) {
    val cal = LocalCalendarSystem.current
    val active = state.active
    val reached = state.reached
    val empty = !state.isLoading && active.isEmpty() && reached.isEmpty()
    var showReached by rememberSaveable { mutableStateOf(true) }
    // First visit: the Goals tips (after the crossing wipe settles).
    val tips = LocalKashTips.current
    val first = active.firstOrNull()
    LaunchedEffect(state.isLoading, empty, first?.goal?.id) {
        if (state.isLoading) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        when {
            empty -> tips?.offer(AppTips.goalsEmpty)
            first != null -> tips?.offer(AppTips.goals(first.goal.name, state.accountLabel(first.goal.linkedAccountKey)))
        }
    }

    GoalsFrame(
        tabBar = tabBar,
        heroGap = 18.dp,
        hero = {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Goals", style = KashType.TitleM, color = GoalsColors.HeroInk, modifier = Modifier.weight(1f).semantics { heading() })
                if (!empty) GoalsIconButton(KashIcon.Plus, "New goal", onNewGoal, Modifier.tipAnchor("newgoal"))
            }
            when {
                empty -> Column {
                    Text(
                        "Put money aside for something",
                        style = KashType.TitleL.copy(fontSize = 30.sp, lineHeight = 36.sp), color = GoalsColors.HeroInk,
                    )
                    Text(
                        "Link the account you save in. Kash counts what lands there.",
                        style = KashType.Body, color = GoalsColors.HeroInkMuted, modifier = Modifier.padding(top = 10.dp),
                    )
                }
                state.isLoading -> Column {
                    Text("Saved toward your goals", style = KashType.Body, color = GoalsColors.HeroInkMuted)
                    GoalsSkeleton(Modifier.padding(top = 8.dp).width(180.dp).height(44.dp), onHero = true)
                    GoalsSkeleton(Modifier.padding(top = 14.dp).width(110.dp).height(14.dp), onHero = true)
                }
                else -> Column {
                    Text("Saved toward your goals", style = KashType.Body, color = GoalsColors.HeroInkMuted)
                    KashFigure(
                        state.summary.totalSaved, Modifier.padding(top = 2.dp),
                        color = GoalsColors.HeroInk, unitColor = GoalsColors.HeroInkMuted, showDecimals = false, countUp = true,
                    )
                    GoalCopy.heroSummary(active, state.summary.onTrack, cal)?.let {
                        Text(it, style = KashType.Body, color = GoalsColors.HeroInkMuted, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        },
    ) { pad ->
        when {
            state.isLoading -> Column { repeat(3) { i -> GoalRowSkeleton(divider = i < 2) } }
            empty -> Column(Modifier.padding(pad)) {
                Text(
                    "Pick one to start", style = KashType.Caption.copy(fontWeight = FontWeight.Medium),
                    color = GoalsColors.InkMuted, modifier = Modifier.padding(top = 4.dp),
                )
                Column(Modifier.tipAnchor("kinds")) { KindRows(onPick = onStartKind) }
            }
            else -> LazyColumn(contentPadding = pad) {
                val single = active.size == 1 && reached.isEmpty()
                itemsIndexed(active, key = { _, it -> it.goal.id }) { i, r ->
                    Box(if (i == 0) Modifier.tipAnchor("goal") else Modifier) {
                        GoalRow(r, thick = single, divider = i < active.lastIndex) { onOpenGoal(r.goal.id) }
                    }
                }
                if (active.isEmpty()) item(key = "all-reached") { AllReached(onNewGoal) }
                if (reached.isNotEmpty()) {
                    item(key = "reached-head") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Reached · ${reached.size}", style = KashType.Caption.copy(fontWeight = FontWeight.Medium),
                                color = GoalsColors.InkMuted, modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            Text(
                                if (showReached) "Hide" else "Show", style = KashType.Caption.copy(fontWeight = FontWeight.Medium),
                                color = GoalsColors.InkMuted,
                                modifier = Modifier
                                    .kashClickable(onClickLabel = if (showReached) "Hide reached goals" else "Show reached goals") { showReached = !showReached }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                            )
                        }
                    }
                    if (showReached) itemsIndexed(reached, key = { _, it -> it.goal.id }) { i, r ->
                        ReachedRow(r, cal, divider = i < reached.lastIndex) { onOpenGoal(r.goal.id) }
                    }
                }
            }
        }
    }
}

/** The three kinds of goal (empty Goals tab and New goal step 1): flat divided rows. */
@Composable
fun KindRows(onPick: (String) -> Unit) {
    Column {
        KindRow(KashIcon.Calendar, "By a date", "A set amount by a set day", divider = true) { onPick(GoalType.DATE_BOUND) }
        KindRow(KashIcon.Repeat, "Every month", "The same amount, month after month", divider = true) { onPick(GoalType.RECURRING) }
        KindRow(KashIcon.Shield, "A safety net", "A few months of your spending", divider = false) { onPick(GoalType.OPEN_ENDED) }
    }
}

@Composable
private fun KindRow(icon: KashIcon, title: String, meta: String, divider: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().kashClickable(press = KashPress.Highlight, onClick = onClick).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalWell(icon)
            Column(Modifier.weight(1f)) {
                Text(title, style = KashType.BodyStrong.copy(fontSize = 16.sp, lineHeight = 22.sp), color = GoalsColors.Ink)
                Text(meta, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted)
            }
            KashIconView(KashIcon.ChevronRight, size = 18.dp, tint = GoalsColors.InkFaint)
        }
        if (divider) GoalsDivider()
    }
}

/** 40dp well, muted icon (rows); [tint] for reached rows. */
@Composable
private fun GoalWell(icon: KashIcon, tint: Color = GoalsColors.InkMuted) {
    Box(Modifier.size(40.dp).clip(KashShapes.Field).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
        KashIconView(icon, size = 20.dp, tint = tint)
    }
}

private fun toneColor(t: GoalCopy.Tone) = when (t) {
    GoalCopy.Tone.GOOD -> GoalsColors.MoneyIn
    GoalCopy.Tone.BAD -> GoalsColors.MoneyOut
    GoalCopy.Tone.NEUTRAL -> GoalsColors.InkMuted
}

/**
 * One goal on the Goals tab (approved FD_GoalsTab): a flat row, not a card.
 * Name and amount, a thin sand bar, then "of 60,000 ETB" and the quiet
 * status ("58%", or "Behind" in red). [thick]: the single-goal row with the
 * percentage inside a 24dp bar.
 */
@Composable
private fun GoalRow(r: GoalProgress.Result, thick: Boolean, divider: Boolean, onClick: () -> Unit) {
    val g = r.goal
    Column {
        Row(
            Modifier.fillMaxWidth()
                .kashClickable(press = KashPress.Highlight, onClickLabel = "Open ${g.name}", onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalWell(GoalIcons.iconOf(g))
            Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        g.name, style = KashType.BodyStrong.copy(fontSize = 16.sp), color = GoalsColors.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(GoalCopy.rowAmount(r), style = KashType.BodyStrong.copy(fontSize = 16.sp).num(), color = GoalsColors.Ink, maxLines = 1)
                }
                if (thick && r.target != null) ThickBar(r.fraction.toFloat(), "${GoalCopy.pct(r)}%")
                else if (r.target != null) ThinBar(r.fraction.toFloat(), GoalsColors.Accent)
                Row {
                    Text(GoalCopy.rowOf(r), style = KashType.Caption.copy(fontWeight = FontWeight.Medium).num(), color = GoalsColors.InkMuted, modifier = Modifier.weight(1f))
                    if (!thick) GoalCopy.rowStatus(r)?.let { (t, tone) ->
                        Text(t, style = KashType.Caption.copy(fontWeight = FontWeight.Medium).num(), color = toneColor(tone))
                    }
                }
            }
        }
        if (divider) GoalsDivider()
    }
}

/** A reached goal under "Reached · 2": muted, a green bar at 60%, "Reached 2 Nov" and a green check. */
@Composable
private fun ReachedRow(r: GoalProgress.Result, cal: String, divider: Boolean, onClick: () -> Unit) {
    val g = r.goal
    Column {
        Row(
            Modifier.fillMaxWidth()
                .kashClickable(press = KashPress.Highlight, onClickLabel = "Open ${g.name}", onClick = onClick)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalWell(GoalIcons.iconOf(g), tint = GoalsColors.MoneyIn)
            Column(Modifier.weight(1f).padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(g.name, style = KashType.BodyStrong.copy(fontSize = 16.sp), color = GoalsColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(KashMoney.whole(r.saved), style = KashType.BodyStrong.copy(fontSize = 16.sp).num(), color = GoalsColors.InkMuted)
                }
                Box(Modifier.alpha(0.6f)) { ThinBar(1f, GoalsColors.MoneyIn) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(GoalCopy.reachedOn(r, cal), style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted, modifier = Modifier.weight(1f))
                    KashIconView(KashIcon.Check, size = 14.dp, tint = GoalsColors.MoneyIn, strokeWidth = 2.4f)
                    Text(" Reached", style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.MoneyIn)
                }
            }
        }
        if (divider) GoalsDivider()
    }
}

/** 4dp bar on the well; fills on a no-bounce spring. */
@Composable
private fun ThinBar(fraction: Float, color: Color) {
    val reduced = rememberReducedMotion()
    val v by animateFloatAsState(fraction.coerceIn(0f, 1f), if (reduced) snap() else com.financeapp.ui.kash.KashSpring.settle(0.5f), label = "thinBar")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(GoalsColors.Well)) {
        if (v > 0f) Box(Modifier.fillMaxWidth(v).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
    }
}

/** The single goal's 24dp bar with the percentage inside the fill. */
@Composable
private fun ThickBar(fraction: Float, label: String) {
    val reduced = rememberReducedMotion()
    val v by animateFloatAsState(fraction.coerceIn(0f, 1f), if (reduced) snap() else com.financeapp.ui.kash.KashSpring.settle(0.5f), label = "thickBar")
    Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(9.dp)).background(GoalsColors.Well)) {
        Box(
            Modifier.fillMaxWidth(v.coerceAtLeast(0.14f)).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(GoalsColors.Accent),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(label, style = KashType.Micro.copy(fontWeight = FontWeight.Bold).num(), color = GoalsColors.OnAccent, modifier = Modifier.padding(end = 9.dp))
        }
    }
}

@Composable
private fun AllReached(onNewGoal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
            KashIconView(KashIcon.Check, size = 24.dp, tint = GoalsColors.MoneyIn, strokeWidth = 2.4f)
        }
        Text("Every goal is reached", style = KashType.BodyStrong.copy(fontSize = 17.sp), color = GoalsColors.Ink, modifier = Modifier.padding(top = 6.dp))
        Text("Start the next one when you’re ready.", style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted, textAlign = TextAlign.Center)
        GoalsButton("Start a new goal", onNewGoal, Modifier.padding(top = 12.dp).height(48.dp), variant = GoalsButtonVariant.Secondary, icon = KashIcon.Plus)
    }
}

@Composable
private fun GoalRowSkeleton(divider: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalsSkeleton(Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    GoalsSkeleton(Modifier.width(120.dp).height(12.dp))
                    Spacer(Modifier.weight(1f))
                    GoalsSkeleton(Modifier.width(56.dp).height(12.dp))
                }
                GoalsSkeleton(Modifier.fillMaxWidth().height(4.dp))
                GoalsSkeleton(Modifier.width(90.dp).height(10.dp))
            }
        }
        if (divider) GoalsDivider()
    }
}

// ─── Goal page (approved flow, section 10) ───────────────────────────────

/** How the user sees a dated goal's pace (Goals "save daily"). Kept on the phone, for every goal. */
internal object GoalPacePref {
    private const val PREFS = "kash_goals"
    private const val KEY = "pace_unit"
    fun get(context: android.content.Context): GoalCopy.PaceUnit =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { GoalCopy.PaceUnit.valueOf(it) }.getOrNull() } ?: GoalCopy.PaceUnit.MONTH
    fun set(context: android.content.Context, unit: GoalCopy.PaceUnit) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, unit.name).apply()
}

@Composable
fun GoalDetailScreen(
    result: GoalProgress.Result,
    accountLabel: String?,
    averageMonthlySpend: Double,
    /** True once: the goal was just reached and the moment hasn't been shown. */
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val goal = result.goal
    val monthly = goal.type == GoalType.RECURRING
    val reached = result.pace == Pace.REACHED
    val reachedForGood = reached && !monthly
    val archived = goal.status == GoalStatus.ARCHIVED
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var saveToRemove by remember { mutableStateOf<GoalProgress.Movement?>(null) }
    val unit = remember { GoalPacePref.get(context) }
    val (status, tone) = GoalCopy.status(result, cal)
    val footer = GoalCopy.footer(result, accountLabel)

    // First time on a goal: where its money comes from.
    val tips = LocalKashTips.current
    LaunchedEffect(goal.id) {
        kotlinx.coroutines.delay(700)
        if (footer.action == GoalCopy.FooterAction.INFO) tips?.offer(AppTips.goalSource(accountLabel))
    }

    GoalsFrame(
        heroGap = 18.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalsIconButton(KashIcon.Back, "Back", onBack)
                Spacer(Modifier.weight(1f))
                GoalsIconButton(KashIcon.More, "More options", { menuOpen = true })
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KashIconView(GoalIcons.iconOf(goal), size = 18.dp, tint = GoalsColors.HeroInkMuted)
                    Text(goal.name, style = KashType.Body, color = GoalsColors.HeroInkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                }
                KashFigure(
                    if (monthly) result.savedThisMonth else result.saved, Modifier.padding(top = 6.dp),
                    color = GoalsColors.HeroInk, unitColor = GoalsColors.HeroInkMuted, showDecimals = false, countUp = true,
                )
                HeroBar(result, Modifier.padding(top = 16.dp))
                Row(Modifier.padding(top = 10.dp)) {
                    Text(GoalCopy.barLeft(result), style = KashType.Label.num(), color = GoalsColors.HeroInkMuted, modifier = Modifier.weight(1f))
                    Text(GoalCopy.barRight(result, cal), style = KashType.Label.num(), color = GoalsColors.HeroInkMuted, maxLines = 1)
                }
            }
            if (reachedForGood && !archived) GoalsWideGhostButton("Archive goal", KashIcon.Archive, onArchive)
        },
    ) { pad ->
        LazyColumn(contentPadding = pad) {
            item(key = "status") {
                StatusCard(
                    status = status, tone = tone,
                    sentence = GoalCopy.statusSentence(result, cal, unit, averageMonthlySpend),
                    footer = footer,
                    onFooter = when (footer.action) {
                        GoalCopy.FooterAction.INFO -> ({ infoOpen = true })
                        GoalCopy.FooterAction.SET_DATE -> onEdit
                        GoalCopy.FooterAction.NONE -> null
                    },
                )
            }
            item(key = "history-title") {
                Text("History", style = KashType.TitleS, color = GoalsColors.Ink, modifier = Modifier.padding(top = 22.dp).semantics { heading() })
            }
            if (result.movements.isEmpty()) item(key = "history-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
                        KashIconView(KashIcon.History, size = 20.dp, tint = GoalsColors.InkMuted)
                    }
                    Text(
                        GoalCopy.emptyHistory(accountLabel), style = KashType.Caption.copy(fontWeight = FontWeight.Medium),
                        color = GoalsColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
            }
            // By day, as approved ("Today · Hidar 4", "+2,500.00").
            val byDay = result.movements.groupBy { KashDates.startOfDay(it.atMs) }
            byDay.forEach { (day, moves) ->
                item(key = "d-$day") { GoalsDayHeader(KashDates.dayHeader(day, cal), GoalCopy.dayNet(moves.sumOf { it.amount })) }
                items(moves, key = { it.contributionId ?: "tx-${it.transactionId}-${it.atMs}-${it.amount}" }) { m ->
                    GoalsTxRow(
                        name = GoalCopy.movementName(m, accountLabel),
                        amount = m.amount,
                        direction = if (m.amount >= 0) KashDirection.In else KashDirection.Out,
                        time = GoalCopy.movementSub(m, accountLabel),
                        category = null,
                        onClick = { val tx = m.transactionId; if (tx != null) onOpenTx(tx) else saveToRemove = m },
                    )
                }
            }
        }
    }

    if (infoOpen) GoalsSheet(title = null, onDismiss = { infoOpen = false }) {
        Text("How ${goal.name} is counted", style = KashType.TitleS, color = GoalsColors.Ink, modifier = Modifier.semantics { heading() })
        Column {
            val rows = GoalCopy.info(result, accountLabel)
            rows.forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(KashShapes.Field).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
                        KashIconView(row.icon, size = 18.dp, tint = GoalsColors.InkMuted)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = KashType.BodyStrong, color = GoalsColors.Ink)
                        Text(row.meta, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted)
                    }
                }
                if (i < rows.lastIndex) GoalsDivider()
            }
        }
        if (!archived) GoalsButton("Change account", { infoOpen = false; onChangeAccount() }, Modifier.fillMaxWidth().height(52.dp), GoalsButtonVariant.Secondary, icon = KashIcon.Transfer)
    }

    if (addOpen) AddSaveSheet(onDismiss = { addOpen = false }, onSave = { a, n, at -> onAddSave(a, n, at); addOpen = false })

    if (menuOpen) GoalsSheet(title = goal.name, onDismiss = { menuOpen = false }) {
        Column {
            if (!archived) {
                GoalsListRow("Edit", { menuOpen = false; onEdit() }, icon = KashIcon.Edit)
                GoalsListRow("Change account", { menuOpen = false; onChangeAccount() }, icon = KashIcon.Card, value = accountLabel ?: "None")
                GoalsListRow("Archive", { menuOpen = false; onArchive() }, icon = KashIcon.Archive, hint = "Hide it and free its account")
                GoalsListRow("Add cash by hand", { menuOpen = false; addOpen = true }, icon = KashIcon.Plus, hint = "Only for money Kash can’t see")
            }
            GoalsListRow(
                "Delete", { menuOpen = false; confirmDelete = true }, icon = KashIcon.Trash,
                color = GoalsColors.MoneyOut, chevron = false, showDivider = false,
            )
        }
    }

    if (confirmDelete) GoalsDialog(
        onDismiss = { confirmDelete = false },
        title = "Delete ${goal.name}?",
        description = "Its saves go with it. Your transactions stay as they are.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalsButton("Delete goal", { confirmDelete = false; onDelete() }, Modifier.fillMaxWidth(), GoalsButtonVariant.Danger)
            if (!archived) GoalsButton("Archive it instead", { confirmDelete = false; onArchive() }, Modifier.fillMaxWidth(), GoalsButtonVariant.Secondary)
            GoalsButton("Cancel", { confirmDelete = false }, Modifier.fillMaxWidth(), GoalsButtonVariant.Quiet)
        }
    }

    saveToRemove?.let { m ->
        GoalsDialog(
            onDismiss = { saveToRemove = null },
            title = "Remove this save?",
            description = "${KashMoney.whole(m.amount)} ETB from ${KashDates.dayMonth(m.atMs, cal)} comes off the goal.",
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

/**
 * The goal page's 32dp bar (approved FD_GoalPage): the percentage inside
 * the fill, green once reached, "0%" beside an empty one, a dashed outline
 * while a safety net has no target yet.
 */
@Composable
private fun HeroBar(r: GoalProgress.Result, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val pct = GoalCopy.pct(r)
    val reached = r.pace == Pace.REACHED
    val v by animateFloatAsState(r.fraction.toFloat().coerceIn(0f, 1f), if (reduced) snap() else com.financeapp.ui.kash.KashSpring.settle(0.6f), label = "heroBar")
    val shape = RoundedCornerShape(12.dp)
    val a11y = Modifier.semantics { contentDescription = if (r.target == null) "No target yet" else "$pct% saved" }
    if (r.target == null) {
        Box(modifier.then(a11y).fillMaxWidth().height(32.dp).drawBehind {
            drawRoundRect(
                GoalsColors.GhostLine, cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
            )
        })
        return
    }
    Box(modifier.then(a11y).fillMaxWidth().height(32.dp).clip(shape).background(GoalsColors.Track)) {
        if (pct == 0) {
            Text("0%", style = KashType.Label.copy(fontWeight = FontWeight.Bold).num(), color = GoalsColors.HeroInkMuted, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        } else {
            Box(
                Modifier.fillMaxWidth(v.coerceAtLeast(0.16f)).fillMaxHeight().clip(shape).background(if (reached) GoalsColors.Reached else GoalsColors.Accent),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("$pct%", style = KashType.Label.copy(fontWeight = FontWeight.Bold).num(), color = GoalsColors.OnAccent, modifier = Modifier.padding(end = 12.dp))
            }
        }
    }
}

/** A reached goal's "Archive goal" on the dark top: wide, ghost. */
@Composable
private fun GoalsWideGhostButton(text: String, icon: KashIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().kashClickable(onClick = onClick).height(56.dp).clip(KashShapes.Control)
            .background(GoalsColors.HeroGhostFill).border(1.dp, GoalsColors.HeroGhostLine, KashShapes.Control),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        KashIconView(icon, size = 20.dp, tint = GoalsColors.HeroInk)
        Text(text, style = KashType.LabelL, color = GoalsColors.HeroInk)
    }
}

/**
 * The status card (approved FD_GoalPage): a coloured status line, one
 * sentence, and one footer row: "Counts money into CBE 2 ›" (how it's
 * counted), "Set a new date ›" in sand (date passed), or a plain line.
 */
@Composable
private fun StatusCard(status: String, tone: GoalCopy.Tone, sentence: String, footer: GoalCopy.Footer, onFooter: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(KashShapes.Card).background(GoalsColors.Raised).semantics(mergeDescendants = true) { }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(status, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = toneColor(tone))
            Text(sentence, style = KashType.BodyStrong, color = GoalsColors.Ink, modifier = Modifier.padding(top = 2.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoalsColors.LineStrong))
        val action = footer.action == GoalCopy.FooterAction.SET_DATE
        val fg = if (action) GoalsColors.Accent else GoalsColors.InkMuted
        Row(
            Modifier.fillMaxWidth()
                .tipAnchor("source")
                .then(if (onFooter != null) Modifier.kashClickable(press = KashPress.Highlight, onClickLabel = if (action) "Set a new date" else "How this goal is counted", onClick = onFooter) else Modifier)
                .height(46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KashIconView(footer.icon, size = 18.dp, tint = fg)
            Text(footer.text, style = KashType.Label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onFooter != null) KashIconView(KashIcon.ChevronRight, size = 16.dp, tint = fg)
        }
    }
}

// ─── Add a save ──────────────────────────────────────────────────────────

/** Amount, an optional note, and the day: Today, Yesterday or a picked day. */
@Composable
fun AddSaveSheet(onDismiss: () -> Unit, onSave: (amount: Double, note: String?, atMs: Long) -> Unit) {
    val cal = LocalCalendarSystem.current
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(0) } // 0 today, 1 yesterday, 2 picked
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = rememberHaptic()
    val submit = {
        val value = amount.replace(",", "").trim().toDoubleOrNull()
        if (value == null || value <= 0.0) { error = "Enter how much you saved"; haptic(Haptic.REJECT) }
        else {
            haptic(Haptic.CONFIRM)
            val now = System.currentTimeMillis()
            val at = when (day) {
                1 -> now - 86_400_000L
                2 -> (picked ?: KashDates.startOfDay(now)) + 12 * 3_600_000L
                else -> now
            }
            onSave(value, note.ifBlank { null }, at.coerceAtMost(now))
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
            Text("When", style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
                GoalsChip("Today", selected = day == 0, onClick = { day = 0 })
                GoalsChip("Yesterday", selected = day == 1, onClick = { day = 1 })
                GoalsChip(
                    if (day == 2 && picked != null) KashDates.dayMonth(picked!!, cal) else "Pick a day",
                    selected = day == 2, onClick = { pickOpen = true }, leading = KashIcon.Calendar,
                )
            }
        }
        GoalsButton("Save", submit, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
    if (pickOpen) GoalsDatePicker(cal, picked, onDismiss = { pickOpen = false }, title = "When", pastOnly = true) {
        picked = it; day = 2; pickOpen = false
    }
}

// ─── Moments (approved flow, section 12) ─────────────────────────────────

/** You reached it: a green check grows in with one success haptic. */
@Composable
fun GoalReachedDialog(r: GoalProgress.Result, onArchive: () -> Unit, onKeep: () -> Unit) {
    val reduced = rememberReducedMotion()
    val haptic = rememberHaptic()
    val check = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        haptic(Haptic.CONFIRM)
        if (!reduced) check.animateTo(1f, com.financeapp.ui.kash.KashSpring.settle(0.4f))
    }
    GoalsDialog(onDismiss = onKeep) {
        Box(
            Modifier.size(52.dp).scale(0.6f + 0.4f * check.value).alpha(check.value).clip(CircleShape).background(GoalsColors.MoneyIn.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { KashIconView(KashIcon.Check, size = 26.dp, tint = GoalsColors.MoneyIn, strokeWidth = 2.6f) }
        Column {
            Text("You reached it", style = KashType.TitleM.copy(fontSize = 26.sp, lineHeight = 32.sp), color = GoalsColors.Ink, modifier = Modifier.semantics { heading() })
            Text(GoalCopy.reachedLine(r), style = KashType.Body, color = GoalsColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    GoalsSheet(title = null, onDismiss = onDone) {
        Box(Modifier.padding(top = 6.dp).size(48.dp).clip(CircleShape).background(GoalsColors.MoneyIn.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            KashIconView(KashIcon.Check, size = 24.dp, tint = GoalsColors.MoneyIn, strokeWidth = 2.6f)
        }
        Column {
            Text("Month done", style = KashType.TitleM.copy(fontSize = 26.sp, lineHeight = 32.sp), color = GoalsColors.Ink, modifier = Modifier.semantics { heading() })
            Text(GoalCopy.monthDoneLine(r, cal), style = KashType.Body, color = GoalsColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
        }
        if (run >= 2) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val slots = maxOf(6, run)
            Row(Modifier.semantics { contentDescription = "$run months in a row" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(slots) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < run) GoalsColors.MoneyIn else GoalsColors.Well))
                }
            }
            Text(GoalCopy.runLine(r), style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted)
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
                    previewResult("Wedding and new apartment", GoalType.DATE_BOUND, 212_500.0, 1_250_000.0, Pace.BEHIND, "rings", even = 521_300.0, perMonth = 74_107.0),
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
