package com.financeapp.ui.screens

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.util.EthiopianCalendar
import com.financeapp.util.KashDates
import com.financeapp.viewmodel.GoalDraft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The New goal / Edit goal form's rules, kept out of the screen so they can
 * be tested: what each field must hold, and the helper lines it shows.
 */
object GoalForm {

    private const val DAY = 86_400_000L

    /** The quick date choices, in months from today. */
    val QUICK_MONTHS = listOf(3, 6, 12)

    /** The quick safety-net sizes, in months of spending. */
    val NET_MONTHS = listOf(1, 3, 6)

    data class Fields(
        val type: String,
        val name: String = "",
        val emoji: String = "",
        val amount: String = "",
        val months: String = "3",
        /** Start of the chosen day (DATE_BOUND). */
        val deadlineDay: Long? = null,
        /** null: saves by hand. */
        val accountKey: String? = null,
    )

    data class Errors(val name: String? = null, val amount: String? = null, val months: String? = null, val date: String? = null) {
        val none get() = name == null && amount == null && months == null && date == null
    }

    fun amountOf(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0.0 }
    fun monthsOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..60 }

    /** Field problems on the details step. [today] is the start of today. */
    fun check(f: Fields, today: Long): Errors = Errors(
        name = if (f.name.isBlank()) "Give the goal a name" else null,
        amount = when {
            f.type == GoalType.OPEN_ENDED -> null
            amountOf(f.amount) == null -> if (f.type == GoalType.RECURRING) "Enter how much a month" else "Enter how much you want to save"
            else -> null
        },
        months = if (f.type == GoalType.OPEN_ENDED && monthsOf(f.months) == null) "Pick between 1 and 60 months" else null,
        date = when {
            f.type != GoalType.DATE_BOUND -> null
            f.deadlineDay == null -> "Pick a date"
            f.deadlineDay < today -> "Pick a day from today on"
            else -> null
        },
    )

    /** What the form sends. Only call after [check] has no errors. The deadline is the end of the chosen day. */
    fun draft(f: Fields): GoalDraft = GoalDraft(
        name = f.name.trim(),
        type = f.type,
        amount = if (f.type == GoalType.OPEN_ENDED) null else amountOf(f.amount),
        months = if (f.type == GoalType.OPEN_ENDED) monthsOf(f.months) else null,
        deadline = if (f.type == GoalType.DATE_BOUND) f.deadlineDay?.let { it + DAY - 1 } else null,
        accountKey = f.accountKey,
        emoji = f.emoji.trim().ifEmpty { null },
    )

    /** The form filled in from an existing goal (Edit). */
    fun fieldsOf(g: GoalEntity): Fields = Fields(
        type = g.type,
        name = g.name,
        emoji = g.emoji.orEmpty(),
        amount = g.targetAmount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
        months = (g.targetMonths ?: 3).toString(),
        deadlineDay = g.deadline?.let { KashDates.startOfDay(it) },
        accountKey = g.linkedAccountKey,
    )

    /** Start of the day [months] months from [now]. */
    fun monthsFromNow(months: Int, now: Long): Long = KashDates.startOfDay(
        Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, months) }.timeInMillis
    )

    /** "That's about 39,000 ETB" under the safety-net months; null without spending history. */
    fun safetyNetPreview(monthsText: String, averageMonthlySpend: Double): String? {
        val months = monthsOf(monthsText) ?: return null
        val target = GoalProgress.targetOf(
            GoalEntity(name = "", type = GoalType.OPEN_ENDED, targetMonths = months), averageMonthlySpend,
        ) ?: return "Kash works out the amount once it has a month of your spending"
        return "That's about ${com.financeapp.ui.kash.components.KashMoney.whole(target)} ETB at your spending"
    }

    /** "15 June 2027" / "Sene 8, 2019". */
    fun dateLabel(ms: Long, cal: String): String =
        if (cal == "ETHIOPIAN") EthiopianCalendar.fromMillis(ms).let { "${it.monthName} ${it.day}, ${it.year}" }
        else SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date(ms))
}
