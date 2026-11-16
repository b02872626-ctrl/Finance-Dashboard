package com.financeapp.ui.screens

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.util.KashDates
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Every sentence the Goals screens say about a goal, word for word from the
 * approved flow (canvas "Final - Approved flow", sections 9 to 12), in one
 * place so it can be tested. Amounts are whole birr ("4,800"); dates
 * follow the calendar setting ([cal] is "GREGORIAN" or "ETHIOPIAN").
 */
object GoalCopy {

    private const val DAY = 86_400_000L
    private const val MONTH_DAYS = 30.44

    private fun etb(v: Double) = "${KashMoney.whole(v)} ETB"
    private fun whole(v: Double) = KashMoney.whole(v)

    private val GoalProgress.Result.recurring get() = goal.type == GoalType.RECURRING
    private val GoalProgress.Result.net get() = goal.type == GoalType.OPEN_ENDED

    /** Percent for bars and the list: 0..100. */
    fun pct(r: GoalProgress.Result): Int = (r.fraction * 100).roundToInt().coerceIn(0, 100)

    /** Nothing has come in yet. */
    fun justStarted(r: GoalProgress.Result): Boolean =
        r.movements.isEmpty() && r.saved <= 0.0 && r.pace !in setOf(Pace.MISSED, Pace.REACHED)

    // ── Status: one coloured line, no pills ─────────────────────────────────

    enum class Tone { GOOD, BAD, NEUTRAL }

    /** "On track", "On track · 3 months in a row", "Behind", "Date passed", "Reached", "Done for Hidar", "Just started", "Waiting for a full month". */
    fun status(r: GoalProgress.Result, cal: String, now: Long = System.currentTimeMillis()): Pair<String, Tone> = when {
        r.target == null -> "Waiting for a full month" to Tone.NEUTRAL
        r.pace == Pace.REACHED && r.recurring -> "Done for ${KashDates.monthName(now, cal)}" to Tone.GOOD
        r.pace == Pace.REACHED -> "Reached" to Tone.GOOD
        r.pace == Pace.MISSED -> "Date passed" to Tone.BAD
        justStarted(r) -> "Just started" to Tone.NEUTRAL
        r.pace == Pace.BEHIND -> "Behind" to Tone.BAD
        r.recurring && r.streakMonths >= 2 -> "On track · ${r.streakMonths} months in a row" to Tone.GOOD
        else -> "On track" to Tone.GOOD
    }

    // ── Goals tab ───────────────────────────────────────────────────────────

    /** Under the total: "On track" (one goal), "3 of 4 on track", "All goals reached". */
    fun heroSummary(active: List<GoalProgress.Result>, onTrack: Int, cal: String): String? = when {
        active.isEmpty() -> "All goals reached"
        active.size == 1 -> status(active.single(), cal).first
        else -> "$onTrack of ${active.size} on track"
    }

    /** A row's amount, top right: "34,600" (this month for a monthly goal). */
    fun rowAmount(r: GoalProgress.Result): String = whole(if (r.recurring) r.savedThisMonth else r.saved)

    /** A row's meta, left: "of 60,000 ETB", "of 5,000 this month", "No target yet". */
    fun rowOf(r: GoalProgress.Result): String {
        val target = r.target ?: return "No target yet"
        return if (r.recurring) "of ${whole(target)} this month" else "of ${etb(target)}"
    }

    /** A row's status, right: "58%" quietly when on pace, "Behind", "Date passed". */
    fun rowStatus(r: GoalProgress.Result): Pair<String, Tone>? = when {
        r.target == null -> null
        r.pace == Pace.BEHIND && !justStarted(r) -> "Behind" to Tone.BAD
        r.pace == Pace.MISSED -> "Date passed" to Tone.BAD
        else -> "${pct(r)}%" to Tone.NEUTRAL
    }

    /** A reached row's meta: "Reached 2 Nov". */
    fun reachedOn(r: GoalProgress.Result, cal: String): String =
        r.goal.achievedAt?.let { "Reached ${KashDates.dayMonth(it, cal)}" } ?: "Reached"

    // ── Goal page: top ──────────────────────────────────────────────────────

    /** Under the bar, left: "of 60,000 ETB", "of 5,000 this month", "No target yet". */
    fun barLeft(r: GoalProgress.Result): String = rowOf(r)

    /** Under the bar, right: "25,400 to go", "3,000 left", "Done", "Reached 2 Nov", "Set on 1 December". */
    fun barRight(r: GoalProgress.Result, cal: String, now: Long = System.currentTimeMillis()): String = when {
        r.target == null -> "Set on ${KashDates.dayMonth(nextMonthStart(now), cal)}"
        r.pace == Pace.REACHED && !r.recurring -> reachedOn(r, cal)
        r.recurring -> if (r.remaining > 0.5) "${whole(r.remaining)} left" else "Done"
        else -> "${whole(r.remaining.coerceAtLeast(0.0))} to go"
    }

    // ── Goal page: status card ──────────────────────────────────────────────

    /** How the user likes to see a dated goal's pace (Goals "save daily"). */
    enum class PaceUnit(val word: String) { DAY("day"), WEEK("week"), MONTH("month") }

    /** [perMonth] shown per [unit]: "330 ETB a day". */
    fun perUnit(perMonth: Double, unit: PaceUnit): String = etb(
        when (unit) {
            PaceUnit.DAY -> perMonth / MONTH_DAYS
            PaceUnit.WEEK -> perMonth * 7 / MONTH_DAYS
            PaceUnit.MONTH -> perMonth
        }
    ) + " a ${unit.word}"

    /** The status card's sentence. */
    fun statusSentence(
        r: GoalProgress.Result,
        cal: String,
        unit: PaceUnit = PaceUnit.MONTH,
        averageMonthlySpend: Double = 0.0,
    ): String {
        val g = r.goal
        val perMonth = r.perMonthNeeded
        return when {
            r.target == null -> "Kash sizes your safety net once it has a full month of your spending."
            r.pace == Pace.REACHED && r.recurring -> "A new month starts at 0 on the 1st."
            r.pace == Pace.REACHED -> "You saved ${etb(r.saved)}" + (earlyDays(r)?.let { ", ${plural(it, "day")} early." } ?: ".")
            r.recurring -> "A new month starts at 0 on the 1st."
            r.pace == Pace.MISSED && g.deadline != null -> "${KashDates.dayMonth(g.deadline, cal)} has passed. Pick a new date to keep going."
            r.net -> {
                val months = g.targetMonths ?: 0
                val at = r.projectedFinishMs?.let { "About ${KashDates.monthYear(it, cal)} at your pace. " } ?: ""
                at + "That’s ${plural(months.toLong(), "month")} of your spending."
            }
            perMonth != null && g.deadline != null ->
                "${perUnit(perMonth, unit)} gets you there by ${KashDates.monthYear(g.deadline, cal)}"
            else -> "${etb(r.remaining)} to go"
        }
    }

    /** What the card's footer row says and does. */
    enum class FooterAction { INFO, SET_DATE, NONE }
    data class Footer(val icon: KashIcon, val text: String, val action: FooterAction)

    fun footer(r: GoalProgress.Result, accountLabel: String?): Footer = when {
        r.pace == Pace.MISSED -> Footer(KashIcon.Calendar, "Set a new date", FooterAction.SET_DATE)
        r.pace == Pace.REACHED && !r.recurring && accountLabel != null ->
            Footer(KashIcon.Card, "$accountLabel is free for another goal", FooterAction.NONE)
        accountLabel != null -> Footer(KashIcon.Info, "Counts money into $accountLabel", FooterAction.INFO)
        else -> Footer(KashIcon.Edit, "Counts saves you add", FooterAction.INFO)
    }

    // ── How it's counted ────────────────────────────────────────────────────

    data class InfoRow(val icon: KashIcon, val title: String, val meta: String)

    fun info(r: GoalProgress.Result, accountLabel: String?): List<InfoRow> = buildList {
        if (accountLabel != null) {
            add(InfoRow(KashIcon.In, "Money into $accountLabel counts as saved", "Read from the bank SMS, by itself"))
            add(InfoRow(KashIcon.Out, "Money out of $accountLabel comes off", "So the total matches what’s really there"))
        }
        add(InfoRow(KashIcon.Edit, "Saves you add by hand always count", "For cash, or money Kash can’t read"))
        if (r.averagePerMonth >= 0.5) add(InfoRow(KashIcon.Calendar, "You save about ${etb(r.averagePerMonth)} a month", "Over the last 3 months"))
    }

    // ── History ─────────────────────────────────────────────────────────────

    fun emptyHistory(accountLabel: String?): String =
        if (accountLabel != null) "Nothing yet. Money into $accountLabel shows up here."
        else "Nothing yet. Saves you add show up here."

    /** A history row's name: "Into CBE 2", "Out of CBE 2", or the note on a save added by hand. */
    fun movementName(m: GoalProgress.Movement, accountLabel: String?): String = when (m.source) {
        GoalProgress.Source.MANUAL -> m.note ?: "Added by you"
        GoalProgress.Source.ACCOUNT ->
            if (m.amount >= 0) "Into ${accountLabel ?: "your account"}" else "Out of ${accountLabel ?: "your account"}"
    }

    /** A history row's sub line: "CBE 2 · 9:10", "Added by you · 7:40". */
    fun movementSub(m: GoalProgress.Movement, accountLabel: String?): String =
        (if (m.source == GoalProgress.Source.MANUAL) "Added by you" else accountLabel ?: "From SMS") + " · " + KashDates.time(m.atMs)

    /** A day's net in the history header: "+2,500.00". */
    fun dayNet(total: Double): String = (if (total < 0) KashMoney.MINUS else KashMoney.PLUS) + KashMoney.amount(total)

    // ── Moments ─────────────────────────────────────────────────────────────

    /** Days before the date the goal was reached, when early. */
    fun earlyDays(r: GoalProgress.Result): Long? {
        val at = r.goal.achievedAt ?: return null
        return r.goal.deadline?.let { (it - at) / DAY }?.takeIf { it >= 1 }
    }

    /** "60,000 ETB for Laptop, 53 days early." */
    fun reachedLine(r: GoalProgress.Result): String =
        "${etb(r.saved)} for ${r.goal.name}" + (earlyDays(r)?.let { ", ${plural(it, "day")} early." } ?: ".")

    /** "5,000 ETB saved in Hidar, 17 days early." */
    fun monthDoneLine(r: GoalProgress.Result, cal: String, now: Long = System.currentTimeMillis()): String {
        val at = r.monthDoneAtMs
        val early = at?.let {
            val c = Calendar.getInstance().apply { timeInMillis = it }
            (c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH)).toLong()
        } ?: 0L
        return "${etb(r.savedThisMonth)} saved in ${KashDates.monthName(now, cal)}" + if (early >= 1) ", ${plural(early, "day")} early." else "."
    }

    /** Months in a row including this one, once this month is done. */
    fun runWithThisMonth(r: GoalProgress.Result): Int = r.streakMonths + 1

    /** "4 months in a row, your longest yet" (the suffix only when it is). */
    fun runLine(r: GoalProgress.Result): String {
        val run = runWithThisMonth(r)
        return plural(run.toLong(), "month") + " in a row" + if (run >= r.longestStreak) ", your longest yet" else ""
    }

    /** Edit goal's line: "By a date · started 15 March 2026". */
    fun startedLine(type: String, createdAt: Long, cal: String): String =
        kindName(type) + " · started " + GoalForm.dateLabel(createdAt, cal)

    fun kindName(type: String): String = when (type) {
        GoalType.OPEN_ENDED -> "A safety net"
        GoalType.RECURRING -> "Every month"
        else -> "By a date"
    }

    fun isArchived(r: GoalProgress.Result) = r.goal.status == GoalStatus.ARCHIVED

    private fun nextMonthStart(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, 1)
    }.timeInMillis

    private fun plural(n: Long, word: String) = "$n $word" + if (n == 1L) "" else "s"
}
