package com.financeapp.ui.screens

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.util.KashDates
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Every sentence the Goals screens say about a goal, in one place so it
 * can be tested. Wording from design/goals-handoff. Amounts are whole birr
 * ("4,800"); dates follow the calendar setting ([cal] is "GREGORIAN" or
 * "ETHIOPIAN").
 */
object GoalCopy {

    private const val DAY = 86_400_000L

    private fun etb(v: Double) = "${KashMoney.whole(v)} ETB"
    private fun whole(v: Double) = KashMoney.whole(v)

    private val GoalProgress.Result.recurring get() = goal.type == GoalType.RECURRING

    /** The pace tag's words. Null when there's no pace to show (safety nets, no target). */
    fun paceLabel(r: GoalProgress.Result): String? = when (r.pace) {
        Pace.REACHED -> if (r.recurring) "Done this month" else "Reached"
        Pace.AHEAD -> "Ahead"
        Pace.ON_TRACK -> "On track"
        Pace.BEHIND -> "Behind"
        Pace.MISSED -> "Date passed"
        Pace.NO_DEADLINE -> null
    }

    /** True when the goal is where it should be, so the card stays quiet. */
    fun onPace(r: GoalProgress.Result): Boolean = r.target != null && r.pace in setOf(Pace.AHEAD, Pace.ON_TRACK, Pace.NO_DEADLINE)

    // ── Goals tab ───────────────────────────────────────────────────────────

    /** "+21,500 ETB this month" under the total; null when nothing came in. */
    fun monthPill(savedThisMonth: Double): String? =
        if (savedThisMonth >= 0.5) "+${etb(savedThisMonth)} this month" else null

    /** The card's big number and what follows it: ("34,600", "of 60,000 ETB"). */
    fun cardAmount(r: GoalProgress.Result): Pair<String, String?> {
        val target = r.target ?: return "${etb(r.saved)} saved" to null
        return if (r.recurring) whole(r.savedThisMonth) to "of ${etb(target)} this month"
        else whole(r.saved) to "of ${etb(target)}"
    }

    /** The one large card (a single goal): percentage first, then "34,600 of 60,000 ETB · 25,400 to go". */
    fun largeCardAmount(r: GoalProgress.Result): Pair<String, String?> {
        if (r.target == null) return cardAmount(r)
        val pct = "${(r.fraction * 100).roundToInt()}%"
        return pct to amountLine(r) + if (r.remaining > 0.5) " · ${whole(r.remaining)} to go" else ""
    }

    /**
     * The card's next-step line. Quiet when on track: only a goal that
     * needs something (behind, date passed, no target yet, reached) says more.
     */
    fun cardNext(r: GoalProgress.Result, cal: String): String? {
        val perMonth = r.perMonthNeeded
        return when {
            r.target == null -> "Kash sizes this once it has a full month of your spending"
            r.pace == Pace.REACHED && !r.recurring -> "You saved ${etb(r.saved)}"
            r.pace == Pace.MISSED -> "${etb(r.remaining)} to go"
            r.pace == Pace.BEHIND && r.recurring -> "${etb(r.remaining)} left to save this month"
            r.pace == Pace.BEHIND && perMonth != null && r.goal.deadline != null ->
                "${etb(perMonth)} a month gets you there by ${KashDates.monthYear(r.goal.deadline, cal)}"
            else -> null
        }
    }

    // ── Goal page: top ──────────────────────────────────────────────────────

    fun figureLabel(r: GoalProgress.Result) = if (r.recurring) "Saved this month" else "Saved"

    /** Under the bar, left: "34,600 of 60,000 ETB", "2,000 of 5,000 ETB this month", "No target yet". */
    fun amountLine(r: GoalProgress.Result): String {
        val target = r.target ?: return "No target yet"
        return if (r.recurring) "${whole(r.savedThisMonth)} of ${etb(target)} this month"
        else "${whole(r.saved)} of ${etb(target)}"
    }

    /** Under the bar, right: "25,400 to go", "17%" when behind, "3,000 left", "Reached 2 Nov". */
    fun barRight(r: GoalProgress.Result, cal: String): String = when {
        r.target == null -> "Sized after your first full month"
        r.pace == Pace.REACHED && !r.recurring ->
            r.goal.achievedAt?.let { "Reached ${KashDates.dayMonth(it, cal)}" } ?: "Reached"
        r.recurring -> if (r.remaining > 0.5) "${whole(r.remaining)} left" else "Done"
        r.pace == Pace.BEHIND -> "${(r.fraction * 100).roundToInt()}%"
        else -> "${whole(r.remaining)} to go"
    }

    // ── Goal page: status card ──────────────────────────────────────────────

    /** "Amount by a date · 15 June 2027", "Every month · 5,000 ETB", "Safety net · 3 months of spending". */
    fun kindLine(r: GoalProgress.Result, cal: String): String = when (r.goal.type) {
        GoalType.OPEN_ENDED -> "Safety net · ${r.goal.targetMonths ?: 0} months of spending"
        GoalType.RECURRING -> "Every month · ${etb(r.goal.targetAmount ?: 0.0)}"
        else -> "Amount by a date" + (r.goal.deadline?.let { " · ${GoalForm.dateLabel(it, cal)}" } ?: "")
    }

    /** The status card's main line: what to do next. */
    fun statusLine(r: GoalProgress.Result, cal: String): String {
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

    /** "An even pace would be 32,000 ETB today. That's the mark on the bar." (dated goals on the way). */
    fun evenPaceLine(r: GoalProgress.Result): String? {
        val even = r.evenPaceNow ?: return null
        if (r.goal.type != GoalType.DATE_BOUND || r.pace == Pace.MISSED) return null
        return "An even pace would be ${etb(even)} today. That’s the mark on the bar."
    }

    data class Fact(val icon: KashIcon, val text: String)

    /** The rows under the status card's top, in order. */
    fun facts(
        r: GoalProgress.Result,
        accountLabel: String?,
        averageMonthlySpend: Double,
        now: Long,
        cal: String,
    ): List<Fact> {
        val out = mutableListOf<Fact>()
        val g = r.goal
        val reachedTotal = r.pace == Pace.REACHED && !r.recurring
        if (r.pace == Pace.MISSED && g.deadline != null) {
            val days = ((now - g.deadline) / DAY).coerceAtLeast(1)
            out += Fact(KashIcon.Calendar, "The date passed ${plural(days, "day")} ago. Edit the goal to set a new date.")
        }
        if (reachedTotal) {
            val at = g.achievedAt
            if (at != null) {
                val early = g.deadline?.let { ((it - at) / DAY).takeIf { d -> d >= 1 } }
                out += Fact(KashIcon.Check, "Reached on ${GoalForm.dateLabel(at, cal)}" + (early?.let { ", ${plural(it, "day")} early" } ?: ""))
                if (accountLabel != null) {
                    out += Fact(KashIcon.Card, "$accountLabel is free for another goal. Money after ${KashDates.dayMonth(at, cal)} doesn’t count here.")
                }
            }
            return out
        }
        if (r.recurring && r.streakMonths >= 2) out += Fact(KashIcon.Check, "${r.streakMonths} months in a row")
        if (r.averagePerMonth >= 0.5) out += Fact(KashIcon.Chart, "You save about ${etb(r.averagePerMonth)} a month")
        if (g.type == GoalType.OPEN_ENDED) {
            if (r.target != null) {
                out += Fact(KashIcon.Info, "The target follows your spending: ${g.targetMonths ?: 0} × about ${etb(averageMonthlySpend)} a month, rounded up.")
            } else {
                val next = Calendar.getInstance().apply {
                    timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1)
                }.timeInMillis
                out += Fact(
                    KashIcon.Calendar,
                    "That happens on ${KashDates.dayMonth(next, cal)}, when ${KashDates.monthName(now, cal)} is complete.",
                )
            }
        }
        out += sourceFact(accountLabel)
        return out
    }

    fun sourceFact(accountLabel: String?): Fact =
        if (accountLabel != null) Fact(KashIcon.Sms, "Money that lands in $accountLabel counts toward this goal. Saves you add yourself count too.")
        else Fact(KashIcon.Edit, "This goal counts the saves you add yourself.")

    // ── History ─────────────────────────────────────────────────────────────

    fun historyCaption(accountLabel: String?): String? = accountLabel?.let { "Counted from your $it SMS, by itself" }

    fun emptyHistory(accountLabel: String?): String =
        if (accountLabel != null) "Nothing yet. Money that lands in $accountLabel from now on shows here."
        else "Nothing yet. Tap Add a save when you put money aside."

    /** A history row's name. */
    fun movementName(m: GoalProgress.Movement, accountLabel: String?): String = when (m.source) {
        GoalProgress.Source.MANUAL -> m.note ?: "Added by you"
        GoalProgress.Source.ACCOUNT ->
            if (m.amount >= 0) "Into ${accountLabel ?: "your account"}" else "Out of ${accountLabel ?: "your account"}"
    }

    /** A month's net in the history header: "+7,500.00". */
    fun monthNet(total: Double): String = (if (total < 0) KashMoney.MINUS else KashMoney.PLUS) + KashMoney.amount(total)

    // ── Moments ─────────────────────────────────────────────────────────────

    /** "You reached it" facts: ("Took", "8 months") and ("Before the date", "53 days") when early. */
    fun reachedFacts(r: GoalProgress.Result): List<Pair<String, String>> {
        val at = r.goal.achievedAt ?: return emptyList()
        val days = ((at - r.goal.createdAt) / DAY).coerceAtLeast(1)
        val took = if (days < 45) plural(days, "day") else plural((days / 30.44).roundToInt().toLong(), "month")
        val out = mutableListOf("Took" to took)
        val early = r.goal.deadline?.let { (it - at) / DAY }
        if (early != null && early >= 1) out += "Before the date" to plural(early, "day")
        return out
    }

    /** "You saved 5,000 ETB this month, 17 days early." */
    fun monthDoneLine(r: GoalProgress.Result): String {
        val at = r.monthDoneAtMs
        val early = at?.let {
            val c = Calendar.getInstance().apply { timeInMillis = it }
            (c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH)).toLong()
        } ?: 0L
        return "You saved ${etb(r.savedThisMonth)} this month" + if (early >= 1) ", ${plural(early, "day")} early." else "."
    }

    /** Months in a row including this one, once this month is done. */
    fun runWithThisMonth(r: GoalProgress.Result): Int = r.streakMonths + 1

    private fun plural(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"
}
