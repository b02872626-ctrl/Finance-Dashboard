package com.financeapp.ui.screens

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.util.KashDates

/**
 * Every sentence the Goals screens say about a goal, in one place so it
 * can be tested. Amounts are whole birr ("4,800"); dates follow the
 * calendar setting ([cal] is "GREGORIAN" or "ETHIOPIAN").
 */
object GoalCopy {

    private fun etb(v: Double) = "${KashMoney.whole(v)} ETB"

    private val GoalProgress.Result.recurring get() = goal.type == GoalType.RECURRING

    /** The short tag on a card: "On track", "Behind", "Reached"… Null when there's no pace to show. */
    fun paceLabel(r: GoalProgress.Result): String? = when (r.pace) {
        Pace.REACHED -> if (r.recurring) "Done this month" else "Reached"
        Pace.AHEAD -> "Ahead"
        Pace.ON_TRACK -> "On track"
        Pace.BEHIND -> "Behind"
        Pace.MISSED -> "Date passed"
        Pace.NO_DEADLINE -> null
    }

    /** "12,400 of 60,000 ETB", "500 of 2,000 ETB this month", or "4,000 ETB saved" with no target yet. */
    fun amountLine(r: GoalProgress.Result): String {
        val target = r.target ?: return "${etb(r.saved)} saved"
        return if (r.recurring) "${KashMoney.whole(r.savedThisMonth)} of ${etb(target)} this month"
        else "${KashMoney.whole(r.saved)} of ${etb(target)}"
    }

    /** One line under the progress bar: what to do next. */
    fun nextStep(r: GoalProgress.Result, cal: String): String {
        val perMonth = r.perMonthNeeded
        return when {
            r.target == null -> "Kash sizes this once it has a full month of your spending"
            r.pace == Pace.REACHED -> if (r.recurring) "Saved for this month" else "You saved ${etb(r.saved)}"
            r.recurring -> "${etb(r.remaining)} left to save this month"
            r.pace == Pace.MISSED -> "${etb(r.remaining)} to go"
            perMonth != null && r.goal.deadline != null ->
                "Save ${etb(perMonth)} a month to make it by ${KashDates.monthYear(r.goal.deadline, cal)}"
            r.projectedFinishMs != null ->
                "${etb(r.remaining)} to go · about ${KashDates.monthYear(r.projectedFinishMs, cal)} at this pace"
            else -> "${etb(r.remaining)} to go"
        }
    }

    /** Where the money comes from, for the goal page. */
    fun sourceLine(accountLabel: String?): String =
        if (accountLabel != null) "Money that lands in $accountLabel counts toward this goal. Saves you add yourself count too."
        else "This goal counts the saves you add yourself."

    /** "3 months in a row", for monthly goals. Null below 2. */
    fun streak(r: GoalProgress.Result): String? =
        if (r.recurring && r.streakMonths >= 2) "${r.streakMonths} months in a row" else null

    /** A history row's name. */
    fun movementName(m: GoalProgress.Movement, accountLabel: String?): String = when (m.source) {
        GoalProgress.Source.MANUAL -> m.note ?: "Added by you"
        GoalProgress.Source.ACCOUNT ->
            if (m.amount >= 0) "Into ${accountLabel ?: "your account"}" else "Out of ${accountLabel ?: "your account"}"
    }
}
