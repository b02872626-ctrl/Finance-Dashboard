package com.financeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.ui.goals.GoalIcons
import com.financeapp.ui.goals.GoalsButton
import com.financeapp.ui.goals.GoalsButtonVariant
import com.financeapp.ui.goals.GoalsChip
import com.financeapp.ui.goals.GoalsColors
import com.financeapp.ui.goals.GoalsDialog
import com.financeapp.ui.goals.GoalsIconButton
import com.financeapp.ui.goals.GoalsTextField
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberReducedMotion
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.GoalAccountOption
import com.financeapp.viewmodel.GoalDraft
import com.financeapp.ui.goals.GoalsSheet
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Which part of the form to open on. */
enum class GoalFormStep { Kind, Details, Account }

/**
 * New goal (kind → details → account), Edit goal (details → account; the
 * kind can't change) and Change account (Edit opened on
 * [GoalFormStep.Account], a page of its own). Dark, like the rest of Goals.
 * [onSave] gets the draft and a callback for an error to show on the
 * account step ("Laptop already uses this account…").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalFormScreen(
    accounts: List<GoalAccountOption>,
    averageMonthlySpend: Double,
    onClose: () -> Unit,
    onSave: (GoalDraft, onError: (String) -> Unit) -> Unit,
    editing: GoalEntity? = null,
    /** What the goal being edited has saved, so the monthly figure counts only what's left. */
    savedSoFar: Double = 0.0,
    /** A kind already picked (empty Goals tab): skip step 1. */
    initialType: String? = null,
    startAt: GoalFormStep = if (editing == null && initialType == null) GoalFormStep.Kind else GoalFormStep.Details,
) {
    val cal = LocalCalendarSystem.current
    val now = remember { System.currentTimeMillis() }
    val today = remember { KashDates.startOfDay(now) }
    val changeAccountOnly = editing != null && startAt == GoalFormStep.Account
    val steps = when {
        changeAccountOnly -> listOf(GoalFormStep.Account)
        // Edit goal is one page with "Save changes" (approved FD_GoalEdit); the account has its own page.
        editing != null -> listOf(GoalFormStep.Details)
        initialType == null -> GoalFormStep.entries
        else -> listOf(GoalFormStep.Details, GoalFormStep.Account)
    }

    var step by rememberSaveable { mutableStateOf(startAt) }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: initialType?.takeIf { it in GoalType.ALL } ?: GoalType.DATE_BOUND) }
    val start = remember(editing) { editing?.let(GoalForm::fieldsOf) }
    var name by rememberSaveable { mutableStateOf(start?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(start?.emoji ?: GoalIcons.defaultKey(type)) }
    var iconPicked by rememberSaveable { mutableStateOf(editing != null) }
    var amount by rememberSaveable { mutableStateOf(start?.amount.orEmpty()) }
    var months by rememberSaveable { mutableStateOf(start?.months ?: "3") }
    var deadline by rememberSaveable { mutableStateOf(start?.deadlineDay) }
    var account by rememberSaveable { mutableStateOf(start?.accountKey) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var unit by remember { mutableStateOf(GoalPacePref.get(context)) }

    val fields = GoalForm.Fields(type, name, icon, amount, months, deadline, account)
    val errors = GoalForm.check(fields, today, keepDay = start?.deadlineDay)
    val shown = if (tried) errors else GoalForm.Errors()

    val index = steps.indexOf(step).coerceAtLeast(0)
    fun back() { if (index > 0) step = steps[index - 1] else onClose() }
    BackHandler(onBack = ::back)

    fun pickKind(t: String) {
        type = t
        if (!iconPicked) icon = GoalIcons.defaultKey(t)
        step = GoalFormStep.Details
    }

    fun save() {
        if (saving) return
        if (!errors.none) { tried = true; if (!changeAccountOnly) step = GoalFormStep.Details; return }
        saving = true
        saveError = null
        onSave(GoalForm.draft(fields)) { message -> saving = false; saveError = message }
    }

    GoalsFrame(
        expandable = false,
        hero = {
            if (editing == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GoalsIconButton(KashIcon.Back, if (index == 0) "Close" else "Back", ::back)
                    GoalsStepBar(index + 1, steps.size, Modifier.weight(1f))
                    Spacer(Modifier.width(KashSize.IconButton))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GoalsIconButton(KashIcon.Back, "Back", ::back)
                    Text(
                        if (changeAccountOnly) "Change account" else "Edit goal",
                        style = KashType.TitleS.copy(fontSize = 20.sp), color = GoalsColors.HeroInk,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            if (changeAccountOnly) Text(
                "The account you pick counts from today. Saves already on the goal stay.",
                style = KashType.Body, color = GoalsColors.HeroInkMuted,
            )
            if (editing == null) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (step) {
                        GoalFormStep.Kind -> "What are you saving for?"
                        GoalFormStep.Details -> when (type) {
                            GoalType.OPEN_ENDED -> "How big a safety net?"
                            GoalType.RECURRING -> "How much each month?"
                            else -> "How much, and by when?"
                        }
                        GoalFormStep.Account -> "Which account do you save in?"
                    },
                    style = KashType.TitleM, color = GoalsColors.HeroInk, modifier = Modifier.semantics { heading() },
                )
                if (step == GoalFormStep.Account) Text(
                    "Money into it counts as saved. Money out comes off.",
                    style = KashType.Body, color = GoalsColors.HeroInkMuted,
                )
            }
        },
    ) { pad ->
        val reduced = rememberReducedMotion()
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // Forward slides in from the right; Back slides it out to the right (same path).
                val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                if (reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                else {
                    val motion = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = 400f)
                    val fade = tween<Float>(180)
                    if (forward) (slideInHorizontally(motion) { it } + fadeIn(fade)) togetherWith (slideOutHorizontally(motion) { -it / 4 } + fadeOut(fade))
                    else (slideInHorizontally(motion) { -it / 4 } + fadeIn(fade)) togetherWith (slideOutHorizontally(motion) { it } + fadeOut(fade))
                }
            },
            label = "goalFormStep",
            modifier = Modifier.fillMaxSize(),
        ) { shownStep ->
            StepBody(pad) {
                when (shownStep) {
                    GoalFormStep.Kind -> KindStep(::pickKind)

                    GoalFormStep.Details -> DetailsStep(
                        editing = editing, type = type, cal = cal, now = now,
                        icon = icon, onPickIcon = { iconPickerOpen = true },
                        unit = unit, onUnit = { unit = it; GoalPacePref.set(context, it) },
                        name = name, onName = { name = it.take(GoalForm.NAME_MAX) },
                        amount = amount, onAmount = { t -> amount = t.filter { it.isDigit() || it == '.' }.take(12) },
                        months = months, onMonths = { months = it },
                        deadline = deadline, onDeadline = { deadline = it }, onPickDate = { datePickerOpen = true },
                        savedSoFar = savedSoFar, averageMonthlySpend = averageMonthlySpend, errors = shown,
                        nextLabel = if (editing != null) "Save changes" else "Next",
                        onNext = { if (!errors.none) tried = true else if (editing != null) save() else step = GoalFormStep.Account },
                        busy = saving, error = if (editing != null) saveError else null,
                    )

                    GoalFormStep.Account -> AccountStep(
                        accounts = accounts, editing = editing, selected = account,
                        error = saveError, saving = saving,
                        saveLabel = if (editing == null) "Save goal" else "Save changes",
                        onPick = { account = it; saveError = null }, onSave = ::save,
                    )
                }
            }
        }
    }

    if (iconPickerOpen) GoalsSheet(title = "Pick an icon", onDismiss = { iconPickerOpen = false }) {
        GoalIconPicker(icon, GoalIcons.defaultKey(editing?.type ?: type)) { icon = it; iconPicked = true; iconPickerOpen = false }
    }

    if (datePickerOpen) GoalsDatePicker(
        calendarSystem = cal,
        initial = deadline,
        onDismiss = { datePickerOpen = false },
        onPick = { deadline = it; datePickerOpen = false },
    )
}

/** A step's sheet: what scrolls, then the button held at the bottom (above the keyboard). */
@Composable
private fun StepBody(pad: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                // At least the sheet's height, so a weighted spacer can hold the button at the bottom.
                .heightIn(min = maxHeight)
                .padding(pad)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(KashSpace.GapL),
            content = content,
        )
    }
}

/** Lets a row run [by] past each side of its parent (to the screen edges). */
private fun Modifier.bleed(by: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth),
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

// ─── Step 1: kind ────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.KindStep(onPick: (String) -> Unit) {
    KindRows(onPick = onPick)
}

// ─── Step 2: details ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.DetailsStep(
    editing: GoalEntity?,
    type: String,
    cal: String,
    now: Long,
    icon: String,
    onPickIcon: () -> Unit,
    unit: GoalCopy.PaceUnit,
    onUnit: (GoalCopy.PaceUnit) -> Unit,
    name: String,
    onName: (String) -> Unit,
    amount: String,
    onAmount: (String) -> Unit,
    months: String,
    onMonths: (String) -> Unit,
    deadline: Long?,
    onDeadline: (Long) -> Unit,
    onPickDate: () -> Unit,
    savedSoFar: Double,
    averageMonthlySpend: Double,
    errors: GoalForm.Errors,
    nextLabel: String,
    onNext: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
) {
    if (editing != null) Text(
        GoalCopy.startedLine(editing.type, editing.createdAt, cal),
        style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted,
    )

    // The goal's icon sits in the Name field (approved FD_New2); tapping it picks another.
    GoalsTextField(
        name, onName, "Name",
        placeholder = when (type) {
            GoalType.OPEN_ENDED -> "e.g. Safety net"
            GoalType.RECURRING -> "e.g. Monthly savings"
            else -> "e.g. Laptop"
        },
        error = errors.name,
        leading = {
            Box(
                Modifier.kashClickable(onClickLabel = "Change icon", onClick = onPickIcon).size(32.dp)
                    .clip(RoundedCornerShape(9.dp)).background(GoalsColors.Raised),
                contentAlignment = Alignment.Center,
            ) { KashIconView(GoalIcons.icon(icon), size = 18.dp, tint = GoalsColors.Ink) }
        },
    )

    when (type) {
        GoalType.OPEN_ENDED -> {
            val other = GoalForm.NET_MONTHS.none { "$it" == months }
            var otherOpen by rememberSaveable { mutableStateOf(other) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Months of your spending")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalForm.NET_MONTHS.forEach { m ->
                        GoalsChip("$m ${if (m == 1) "month" else "months"}", months == "$m" && !otherOpen, { otherOpen = false; onMonths("$m") })
                    }
                    GoalsChip("Other", otherOpen, { otherOpen = true })
                }
            }
            if (otherOpen) GoalsTextField(
                months.takeIf { other }.orEmpty(), { onMonths(it.filter(Char::isDigit).take(2)) }, "",
                placeholder = "1 to 60", unit = "months", keyboardType = KeyboardType.Number, error = errors.months,
            )
            GoalForm.safetyNetPreview(months, averageMonthlySpend)?.let { p ->
                StrongLine(p.text, p.bold)
            }
        }

        GoalType.RECURRING -> {
            GoalsTextField(
                amount, onAmount, "Amount a month", placeholder = "0", unit = "ETB", big = true, visualTransformation = AmountGrouping,
                keyboardType = KeyboardType.Decimal, error = errors.amount,
            )
            Text("Starts this month. Each month begins at 0 on the 1st.", style = KashType.Label, color = GoalsColors.InkMuted)
        }

        else -> {
            GoalsTextField(
                amount, onAmount, "Amount", placeholder = "0", unit = "ETB", big = true, visualTransformation = AmountGrouping,
                keyboardType = KeyboardType.Decimal, error = errors.amount,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("By when")
                val quick = GoalForm.QUICK_MONTHS.associateWith { GoalForm.monthsFromNow(it, now) }
                val custom = deadline != null && quick.values.none { it == deadline }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { (m, day) ->
                        GoalsChip(GoalForm.quickLabel(m), deadline == day, { onDeadline(day) })
                    }
                    GoalsChip(
                        if (custom && deadline != null) GoalForm.dateLabel(deadline, cal) else "Pick a date",
                        custom, onPickDate, leading = KashIcon.Calendar,
                    )
                }
            }
            val helper = GoalForm.datedHelper(amount, deadline, savedSoFar, now, cal, unit, editing = editing != null)
            when {
                errors.date != null -> ErrorLine(errors.date)
                helper != null -> {
                    StrongLine(helper.text, helper.bold)
                    // Save daily: see the pace per day, week or month (kept for every goal).
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Show it per", style = KashType.Caption, color = GoalsColors.InkFaint)
                        GoalCopy.PaceUnit.entries.forEach { u ->
                            Text(
                                u.word, style = KashType.Caption.copy(fontWeight = if (u == unit) FontWeight.SemiBold else FontWeight.Medium),
                                color = if (u == unit) GoalsColors.OnAccent else GoalsColors.InkMuted,
                                modifier = Modifier.clip(KashShapes.Pill).background(if (u == unit) GoalsColors.Accent else GoalsColors.Well)
                                    .kashClickable(onClickLabel = "Show the pace per ${u.word}") { onUnit(u) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.weight(1f))
    if (error != null) ErrorLine(error)
    GoalsButton(if (busy) "Saving…" else nextLabel, onNext, Modifier.fillMaxWidth().padding(top = 4.dp), busy = busy)
}

/** A helper line with its key figure in ink, semi-bold: "About **5,000 ETB a month**, done by …". */
@Composable
private fun StrongLine(text: String, bold: String?) {
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        val i = if (bold != null) text.indexOf(bold) else -1
        if (i < 0) append(text) else {
            append(text.substring(0, i))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = GoalsColors.Ink, fontWeight = FontWeight.SemiBold))
            append(bold!!)
            pop()
            append(text.substring(i + bold.length))
        }
    }
    Text(annotated, style = KashType.Label.num(), color = GoalsColors.InkMuted)
}

/** Edit goal: the kind and start date, which can't change. */
@Composable
private fun EditStrip(goal: GoalEntity, cal: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Field)
            .background(GoalsColors.Raised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (goal.type) {
                GoalType.OPEN_ENDED -> "Safety net"
                GoalType.RECURRING -> "Every month"
                else -> "Amount by a date"
            },
            style = KashType.Label, color = GoalsColors.Ink, modifier = Modifier.weight(1f),
        )
        Text("Started ${GoalForm.dateLabel(goal.createdAt, cal)}", style = KashType.Caption, color = GoalsColors.InkMuted)
    }
}

/**
 * GoalIconPicker: one row of 48dp squares that scrolls sideways and runs to
 * the screen edges. The chosen one is sand with a slate-ink ring.
 */
@Composable
private fun GoalIconPicker(selected: String, first: String, onPick: (String) -> Unit) {
    Row(
        Modifier
            .bleed(KashSpace.InsetApp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = KashSpace.InsetApp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The kind's own icon leads, so the starting choice is in view.
        GoalIcons.ALL.sortedBy { if (it.key == first) 0 else 1 }.forEach { o ->
            val on = o.key == selected
            Box(
                Modifier
                    .kashClickable(role = Role.RadioButton) { onPick(o.key) }
                    .size(48.dp)
                    .clip(KashShapes.Field)
                    .background(if (on) GoalsColors.Accent else GoalsColors.Raised)
                    .border(if (on) 2.dp else 1.dp, if (on) GoalsColors.OnAccent else GoalsColors.Line, KashShapes.Field)
                    .semantics { this.selected = on; contentDescription = o.name },
                contentAlignment = Alignment.Center,
            ) { KashIconView(o.icon, size = 22.dp, tint = if (on) GoalsColors.OnAccent else GoalsColors.Ink) }
        }
    }
}

// ─── Step 3: account ─────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AccountStep(
    accounts: List<GoalAccountOption>,
    editing: GoalEntity?,
    selected: String?,
    error: String?,
    saving: Boolean,
    saveLabel: String,
    onPick: (String?) -> Unit,
    onSave: () -> Unit,
) {
    // Tapping an account another goal counts says so, and Save waits (approved FD_NewTaken).
    var takenTapped by remember { mutableStateOf<String?>(null) }
    Column(Modifier.selectableGroup()) {
        accounts.forEach { a ->
            val usedElsewhere = a.usedBy != null && a.usedBy.id != editing?.id
            AccountOption(
                label = a.label,
                detail = GoalForm.maskNumber(a.accountNumber),
                bankName = a.bankName,
                checked = selected == a.key && takenTapped == null,
                dimmed = (usedElsewhere && takenTapped != a.key) || saving,
                usedBy = if (usedElsewhere && takenTapped != a.key) "Used by ${a.usedBy!!.name}" else null,
                error = if (takenTapped == a.key) "${a.usedBy?.name} already counts this one. Pick another." else null,
            ) { if (usedElsewhere) takenTapped = a.key else { takenTapped = null; onPick(a.key) } }
            GoalsRowLine()
        }
        AccountOption(
            label = "None, I’ll add saves myself",
            detail = "For cash, or an account Kash can’t read",
            bankName = null,
            checked = selected == null && takenTapped == null,
            dimmed = saving,
            usedBy = null,
            error = null,
        ) { takenTapped = null; onPick(null) }
    }
    if (error != null) ErrorLine(error)
    Spacer(Modifier.weight(1f))
    GoalsButton(
        if (saving && editing == null) "Saving goal…" else if (saving) "Saving…" else saveLabel,
        onSave, Modifier.fillMaxWidth().padding(top = 4.dp), busy = saving, enabled = takenTapped == null,
    )
}

/** "Kash counts it for you": in is saved, out is taken back. */
@Composable
private fun CountsCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(GoalsColors.Raised)
            .border(1.dp, GoalsColors.Line, KashShapes.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Kash counts it for you", style = KashType.BodyStrong, color = GoalsColors.Ink)
        CountsRow(KashIcon.In, "Money into the account", "+ saved", GoalsColors.MoneyIn)
        CountsRow(KashIcon.Out, "Money out of it", "− taken back", GoalsColors.MoneyOut)
        Text(
            "From today on, straight from the bank SMS. Saves you add by hand never come off. One account feeds one goal.",
            style = KashType.Caption, color = GoalsColors.InkMuted,
        )
    }
}

@Composable
private fun CountsRow(icon: KashIcon, text: String, value: String, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(KashShapes.Chip).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
            KashIconView(icon, size = 16.dp, tint = tone, strokeWidth = 2.2f)
        }
        Text(text, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = GoalsColors.Ink, modifier = Modifier.weight(1f))
        Text(value, style = KashType.BodyStrong.num(), color = tone)
    }
}

/**
 * An account row (approved FD_New3): 36dp round bank badge, name and the
 * masked number, the radio on the right. Another goal's account is dimmed
 * with "Used by Monthly savings" where the radio would be.
 */
@Composable
private fun AccountOption(
    label: String,
    detail: String?,
    bankName: String?,
    checked: Boolean,
    dimmed: Boolean,
    usedBy: String?,
    error: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .kashClickable(role = Role.RadioButton, onClick = onClick)
            .alpha(if (dimmed) 0.45f else 1f)
            .padding(vertical = 14.dp)
            .semantics {
                selected = checked
                if (usedBy != null) contentDescription = "$label, $usedBy"
            },
        verticalAlignment = if (error != null) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bankName != null) KashBankBadge(bankName, size = 36.dp)
        else Box(Modifier.size(36.dp).clip(CircleShape).background(GoalsColors.Well), contentAlignment = Alignment.Center) {
            KashIconView(KashIcon.Edit, size = 18.dp, tint = GoalsColors.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = KashType.BodyStrong.copy(fontSize = 16.sp, lineHeight = 22.sp), color = GoalsColors.Ink, maxLines = 1)
            if (!detail.isNullOrBlank()) Text(detail, style = KashType.Caption.copy(fontWeight = FontWeight.Medium).num(), color = GoalsColors.InkMuted, maxLines = 1)
            if (error != null) Text(error, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.MoneyOut, modifier = Modifier.padding(top = 2.dp))
        }
        when {
            error != null -> {}
            usedBy != null -> Text(usedBy, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted, maxLines = 1)
            else -> Radio(checked, Modifier)
        }
    }
}

@Composable
private fun Radio(checked: Boolean, modifier: Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (checked) GoalsColors.Accent else GoalsColors.LineStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (checked) Box(Modifier.size(10.dp).clip(CircleShape).background(GoalsColors.Accent)) }
}

// ─── Date picker ─────────────────────────────────────────────────────────

/** "Save it by": one day from today on, in the calendar setting. [onPick] gets the start of that day. */
@Composable
internal fun GoalsDatePicker(
    calendarSystem: String,
    initial: Long?,
    onDismiss: () -> Unit,
    /** "Save it by" for a goal's date; "When" for a save added by hand. */
    title: String = "Save it by",
    /** True: only today and earlier can be picked (a save that already happened). */
    pastOnly: Boolean = false,
    onPick: (Long) -> Unit,
) {
    val today = KashDates.startOfDay(System.currentTimeMillis())
    var monthStart by remember { mutableLongStateOf(KashDates.monthBounds(initial ?: today, calendarSystem).first) }
    var day by remember { mutableStateOf(initial?.let(KashDates::startOfDay)) }
    // A bottom sheet, as approved (FD_NewDate), not a centred dialog.
    GoalsSheet(title = null, onDismiss = onDismiss) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(title, style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = GoalsColors.InkMuted)
            Text(
                day?.let { GoalForm.dateLabel(it, calendarSystem) } ?: "Pick a day",
                style = KashType.TitleM.num(), color = GoalsColors.Ink, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val prev = KashDates.monthBounds(monthStart - DAY, calendarSystem).first
            val next = KashDates.monthBounds(monthStart, calendarSystem).second
            MonthNavButton(KashIcon.Back, KashDates.monthYear(prev + DAY, calendarSystem)) { monthStart = prev }
            Text(
                KashDates.monthYear(monthStart + DAY, calendarSystem),
                style = KashType.BodyStrong, color = GoalsColors.Ink,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            MonthNavButton(KashIcon.ChevronRight, KashDates.monthYear(next + DAY, calendarSystem)) { monthStart = next }
        }
        DayGrid(monthStart, calendarSystem, today, day, pastOnly) { day = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            GoalsButton("Cancel", onDismiss, Modifier.weight(1f), GoalsButtonVariant.Secondary)
            GoalsButton("Use this date", { day?.let(onPick) }, Modifier.weight(1.6f), enabled = day != null)
        }
    }
}

/** 36dp month arrows on the well (approved FD_NewDate). */
@Composable
private fun MonthNavButton(icon: KashIcon, label: String, onClick: () -> Unit) {
    Box(
        Modifier.kashClickable(onClickLabel = label, onClick = onClick).size(36.dp).clip(KashShapes.Control).background(GoalsColors.Well),
        contentAlignment = Alignment.Center,
    ) { KashIconView(icon, size = 18.dp, tint = GoalsColors.Ink) }
}

private const val DAY = 86_400_000L

/** A month of days, Monday first. Past days are faint and can't be picked. */
@Composable
private fun DayGrid(monthStart: Long, calendarSystem: String, today: Long, picked: Long?, pastOnly: Boolean = false, onPick: (Long) -> Unit) {
    val monthEnd = KashDates.monthBounds(monthStart, calendarSystem).second
    val days = ((monthEnd - monthStart + DAY / 2) / DAY).toInt()
    val dow = Calendar.getInstance().apply { timeInMillis = monthStart }.get(Calendar.DAY_OF_WEEK)
    val offset = (dow - Calendar.MONDAY + 7) % 7
    val rows = (offset + days + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.clearAndSetSemantics { }.padding(bottom = 2.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = KashType.Micro, color = GoalsColors.InkMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        repeat(rows) { r ->
            Row {
                repeat(7) { c ->
                    val i = r * 7 + c - offset + 1
                    if (i < 1 || i > days) Spacer(Modifier.weight(1f).height(40.dp))
                    else {
                        val d = KashDates.startOfDay(monthStart + (i - 1) * DAY + DAY / 2)
                        val on = d == picked
                        val past = if (pastOnly) d > today else d < today
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(40.dp)
                                .padding(horizontal = 1.dp)
                                .clip(shape)
                                .then(
                                    when {
                                        on -> Modifier.background(GoalsColors.Accent)
                                        d == today -> Modifier.border(1.5.dp, GoalsColors.Ink, shape)
                                        else -> Modifier
                                    }
                                )
                                .kashClickable(enabled = !past) { onPick(d) }
                                .semantics {
                                    selected = on
                                    contentDescription = KashDates.monthDay(d, calendarSystem)
                                    role = Role.Button
                                    if (past) disabled()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$i",
                                style = KashType.Body.num().copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                                color = when {
                                    on -> GoalsColors.OnAccent
                                    past -> GoalsColors.InkFaint
                                    else -> GoalsColors.Ink
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Small pieces ────────────────────────────────────────────────────────

/** The dark step bar on the top: sand up to the current step, white 16% after. */
@Composable
private fun GoalsStepBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Step $current of $total" },
        horizontalArrangement = Arrangement.spacedBy(KashSpace.GapXs),
    ) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < current) GoalsColors.Accent else GoalsColors.HeroTrack)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) = Text(text, style = KashType.Label, color = GoalsColors.InkMuted)

@Composable
private fun HelperLine(icon: KashIcon, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KashIconView(icon, size = 16.dp, tint = GoalsColors.InkMuted)
        Text(text, style = KashType.Caption.num(), color = GoalsColors.Ink)
    }
}

@Composable
private fun ErrorLine(text: String, modifier: Modifier = Modifier) = Text(
    text, style = KashType.Caption, color = GoalsColors.MoneyOut,
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
)

@Composable
private fun GoalsRowLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(GoalsColors.Line))

/** A quiet note on the dark sheet; [bold] (a part of [text]) is set stronger. */
@Composable
private fun GoalsNotice(icon: KashIcon, text: String, bold: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Field)
            .background(GoalsColors.Raised)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KashIconView(icon, size = 18.dp, tint = GoalsColors.InkMuted, modifier = Modifier.padding(top = 1.dp))
        val styled = buildAnnotatedString {
            val at = bold?.let { text.indexOf(it) } ?: -1
            if (bold == null || at < 0) append(text)
            else {
                append(text.substring(0, at))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                append(text.substring(at + bold.length))
            }
        }
        Text(styled, style = KashType.Caption.num(), color = GoalsColors.Ink)
    }
}

/** Shows "60000" as "60,000" while the text itself stays plain digits (the cursor stays put). */
private object AmountGrouping : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val out = GoalForm.groupAmount(raw)
        if (out.length == raw.length || out.filter { it != ',' } != raw) {
            return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        }
        // Where each raw character lands in the grouped text.
        val toOut = IntArray(raw.length + 1)
        var o = 0
        for (i in raw.indices) {
            while (out[o] == ',') o++
            toOut[i] = o
            o++
        }
        toOut[raw.length] = out.length
        val map = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = toOut[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = out.take(offset.coerceIn(0, out.length)).count { it != ',' }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), map)
    }
}

// ─── Previews (made-up data) ─────────────────────────────────────────────

private val previewAccounts = listOf(
    GoalAccountOption("CBE#1111", "CBE 1", "Commercial Bank of Ethiopia", "1*****1111"),
    GoalAccountOption("CBE#2222", "CBE 2", "Commercial Bank of Ethiopia", "1*****2222"),
    GoalAccountOption(
        "Telebirr", "Telebirr", "Telebirr", "09****5678",
        usedBy = GoalEntity(name = "Monthly savings", type = GoalType.RECURRING),
    ),
)

@Preview(name = "New goal: kind", widthDp = 390, heightDp = 844)
@Composable
private fun GoalFormKindPreview() = KashTheme {
    GoalFormScreen(accounts = emptyList(), averageMonthlySpend = 0.0, onClose = {}, onSave = { _, _ -> })
}

@Preview(name = "New goal: dated", widthDp = 390, heightDp = 844)
@Composable
private fun GoalFormDatedPreview() = KashTheme {
    GoalFormScreen(accounts = emptyList(), averageMonthlySpend = 0.0, onClose = {}, onSave = { _, _ -> }, initialType = GoalType.DATE_BOUND)
}

@Preview(name = "New goal: account", widthDp = 390, heightDp = 844)
@Composable
private fun GoalFormAccountPreview() = KashTheme {
    GoalFormScreen(
        accounts = previewAccounts, averageMonthlySpend = 12_950.0, onClose = {}, onSave = { _, _ -> },
        startAt = GoalFormStep.Account,
    )
}
