package com.financeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashStepBar
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.GoalAccountOption
import com.financeapp.viewmodel.GoalDraft

/** Which part of the form to open on. */
enum class GoalFormStep { Kind, Details, Account }

/**
 * New goal (kind → details → account) and Edit goal (details → account;
 * the kind can't change). "Change account" opens Edit on [GoalFormStep.Account].
 * [onSave] gets the draft and a callback for an error to show on the
 * account step ("Used by Laptop"), or null when it saved.
 */
@Composable
fun GoalFormScreen(
    accounts: List<GoalAccountOption>,
    averageMonthlySpend: Double,
    onClose: () -> Unit,
    onSave: (GoalDraft, onError: (String) -> Unit) -> Unit,
    editing: GoalEntity? = null,
    startAt: GoalFormStep = if (editing == null) GoalFormStep.Kind else GoalFormStep.Details,
) {
    KashSystemBars(darkIcons = true)
    val cal = LocalCalendarSystem.current
    val today = remember { KashDates.startOfDay(System.currentTimeMillis()) }
    val steps = if (editing == null) GoalFormStep.entries else listOf(GoalFormStep.Details, GoalFormStep.Account)

    var step by rememberSaveable { mutableStateOf(startAt) }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: GoalType.DATE_BOUND) }
    val start = remember(editing) { editing?.let(GoalForm::fieldsOf) }
    var name by rememberSaveable { mutableStateOf(start?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(start?.emoji.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(start?.amount.orEmpty()) }
    var months by rememberSaveable { mutableStateOf(start?.months ?: "3") }
    var deadline by rememberSaveable { mutableStateOf(start?.deadlineDay) }
    var account by rememberSaveable { mutableStateOf(start?.accountKey) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val fields = GoalForm.Fields(type, name, emoji, amount, months, deadline, account)
    val errors = GoalForm.check(fields, today)
    val shown = if (tried) errors else GoalForm.Errors()

    val index = steps.indexOf(step)
    fun back() { if (index > 0 && !(editing != null && startAt == GoalFormStep.Account)) step = steps[index - 1] else onClose() }
    BackHandler(onBack = ::back)

    fun save() {
        if (!errors.none) { tried = true; step = GoalFormStep.Details; return }
        saving = true
        saveError = null
        onSave(GoalForm.draft(fields)) { message -> saving = false; saveError = message }
    }

    KashScreen(
        heroColor = KashColors.GoalsHero,
        expandable = false,
        hero = {
            KashTopBar(if (editing == null) "New goal" else "Edit goal", onBack = ::back, onSlate = false)
            if (steps.size > 1 && !(editing != null && startAt == GoalFormStep.Account)) {
                KashStepBar(current = index + 1, total = steps.size, label = "Step ${index + 1} of ${steps.size}")
            }
            Text(
                when (step) {
                    GoalFormStep.Kind -> "What are you saving for?"
                    GoalFormStep.Details -> when (type) {
                        GoalType.OPEN_ENDED -> "How big a safety net?"
                        GoalType.RECURRING -> "How much each month?"
                        else -> "How much, and by when?"
                    }
                    GoalFormStep.Account -> "Where does the money go?"
                },
                style = KashType.TitleM, color = KashColors.GoalsInk, modifier = Modifier.semantics { heading() },
            )
        },
    ) { pad ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(pad).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(KashSpace.GapL),
        ) {
            when (step) {
                GoalFormStep.Kind -> {
                    KindCard("An amount by a date", "Laptop, 60,000 ETB by June", type == GoalType.DATE_BOUND) {
                        type = GoalType.DATE_BOUND; step = GoalFormStep.Details
                    }
                    KindCard("A safety net", "3 months of spending, for when things go wrong", type == GoalType.OPEN_ENDED) {
                        type = GoalType.OPEN_ENDED; step = GoalFormStep.Details
                    }
                    KindCard("Every month", "5,000 ETB a month, for good", type == GoalType.RECURRING) {
                        type = GoalType.RECURRING; step = GoalFormStep.Details
                    }
                }

                GoalFormStep.Details -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapM)) {
                        KashTextField(
                            emoji, { emoji = it.take(4) }, "Emoji", placeholder = "💻",
                            modifier = Modifier.width(86.dp),
                        )
                        KashTextField(
                            name, { name = it.take(40) }, "Name",
                            placeholder = when (type) {
                                GoalType.OPEN_ENDED -> "Safety net"
                                GoalType.RECURRING -> "Monthly savings"
                                else -> "Laptop"
                            },
                            error = shown.name, modifier = Modifier.weight(1f),
                        )
                    }
                    when (type) {
                        GoalType.OPEN_ENDED -> {
                            Text("Months of spending", style = KashType.Label, color = KashColors.InkMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
                                GoalForm.NET_MONTHS.forEach { m ->
                                    KashChip("$m ${if (m == 1) "month" else "months"}", months == "$m", { months = "$m" })
                                }
                            }
                            KashTextField(
                                months, { months = it.filter(Char::isDigit).take(2) }, "Or type a number",
                                keyboardType = KeyboardType.Number, error = shown.months,
                                hint = GoalForm.safetyNetPreview(months, averageMonthlySpend),
                            )
                        }
                        else -> KashTextField(
                            amount, { t -> amount = t.filter { it.isDigit() || it == '.' || it == ',' }.take(12) },
                            if (type == GoalType.RECURRING) "Amount a month (ETB)" else "Amount (ETB)",
                            placeholder = if (type == GoalType.RECURRING) "5,000" else "60,000",
                            keyboardType = KeyboardType.Decimal, error = shown.amount,
                        )
                    }
                    if (type == GoalType.DATE_BOUND) {
                        Text("By when", style = KashType.Label, color = KashColors.InkMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
                            GoalForm.QUICK_MONTHS.forEach { m ->
                                val day = GoalForm.monthsFromNow(m, System.currentTimeMillis())
                                KashChip(if (m == 12) "1 year" else "$m months", deadline == day, { deadline = day })
                            }
                        }
                        val custom = deadline?.takeIf { d -> GoalForm.QUICK_MONTHS.none { GoalForm.monthsFromNow(it, System.currentTimeMillis()) == d } }
                        KashChip(
                            custom?.let { GoalForm.dateLabel(it, cal) } ?: "Pick a date",
                            selected = custom != null, onClick = { datePickerOpen = true }, leading = KashIcon.Calendar,
                        )
                        deadline?.let {
                            Text("Save it by ${GoalForm.dateLabel(it, cal)}", style = KashType.Caption, color = KashColors.InkMuted)
                        }
                        shown.date?.let { Text(it, style = KashType.Caption, color = KashColors.MoneyOut) }
                    }
                    KashButton("Next", {
                        if (errors.none) step = GoalFormStep.Account else tried = true
                    }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }

                GoalFormStep.Account -> {
                    Text(
                        "Money that lands in this account from now on counts toward the goal. " +
                            "One account feeds one goal.",
                        style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.InkMuted,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(KashSpace.GapS)) {
                        accounts.forEach { a ->
                            val usedElsewhere = a.usedBy != null && a.usedBy.id != editing?.id
                            AccountChoice(
                                title = a.label,
                                hint = if (usedElsewhere) "Used by ${a.usedBy!!.name}" else a.accountNumber.ifBlank { null },
                                selected = account == a.key,
                                enabled = !usedElsewhere,
                            ) { account = a.key; saveError = null }
                        }
                        AccountChoice("None, I'll add saves myself", hint = "For cash or an account Kash can't read", selected = account == null, enabled = true) {
                            account = null; saveError = null
                        }
                    }
                    saveError?.let { Text(it, style = KashType.Caption, color = KashColors.MoneyOut) }
                    KashButton(
                        if (editing == null) "Save goal" else "Save changes", ::save,
                        Modifier.fillMaxWidth().padding(top = 6.dp), busy = saving,
                    )
                }
            }
        }
    }

    if (datePickerOpen) DayPickDialog(
        calendarSystem = cal,
        initial = deadline,
        onDismiss = { datePickerOpen = false },
        onPick = { deadline = it; datePickerOpen = false },
    )
}

/** One of the three kinds of goal: a big tappable card. */
@Composable
private fun KindCard(title: String, example: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .kashClickable(role = Role.Button, onClick = onClick)
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) KashColors.Slate else KashColors.Line, KashShapes.Card)
            .padding(18.dp)
            .semantics { this.selected = selected },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = KashType.TitleXs, color = KashColors.Ink)
        Text(example, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.InkMuted)
    }
}

/** A radio-style row for the account step. */
@Composable
private fun AccountChoice(title: String, hint: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .kashClickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .clip(KashShapes.Control)
            .background(KashColors.SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) KashColors.Slate else KashColors.Line, KashShapes.Control)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = KashType.BodyStrong, color = KashColors.Ink)
            hint?.let { Text(it, style = KashType.Caption, color = KashColors.InkMuted) }
        }
        if (selected) KashIconView(KashIcon.Check, size = 20.dp, tint = KashColors.Slate, strokeWidth = 2.4f)
    }
}

@Preview(name = "New goal: kind", widthDp = 360, heightDp = 760)
@Composable
private fun GoalFormKindPreview() = KashTheme {
    GoalFormScreen(accounts = emptyList(), averageMonthlySpend = 0.0, onClose = {}, onSave = { _, _ -> })
}

@Preview(name = "New goal: account", widthDp = 360, heightDp = 760)
@Composable
private fun GoalFormAccountPreview() = KashTheme {
    GoalFormScreen(
        accounts = listOf(
            GoalAccountOption("CBE#1111", "CBE 1", "Commercial Bank of Ethiopia", "1*****1111"),
            GoalAccountOption(
                "CBE#2222", "CBE 2", "Commercial Bank of Ethiopia", "1*****2222",
                usedBy = GoalEntity(name = "Laptop", type = GoalType.DATE_BOUND),
            ),
            GoalAccountOption("Telebirr", "Telebirr", "Telebirr", ""),
        ),
        averageMonthlySpend = 13_000.0, onClose = {}, onSave = { _, _ -> },
        startAt = GoalFormStep.Account,
    )
}
