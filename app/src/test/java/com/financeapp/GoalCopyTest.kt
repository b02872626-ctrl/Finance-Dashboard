package com.financeapp

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.ui.screens.GoalCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** What the Goals screens say about a goal. Made-up goals and amounts. */
class GoalCopyTest {

    private val day = 86_400_000L
    private val june2027 = Calendar.getInstance(TimeZone.getDefault()).apply { clear(); set(2027, 5, 15, 12, 0) }.timeInMillis

    private fun result(
        type: String = GoalType.DATE_BOUND,
        saved: Double = 12_400.0,
        target: Double? = 60_000.0,
        pace: Pace = Pace.ON_TRACK,
        deadline: Long? = june2027,
        perMonth: Double? = 4_800.0,
        thisMonth: Double = 0.0,
        streak: Int = 0,
        projected: Long? = null,
        months: Int? = null,
        achievedAt: Long? = null,
        created: Long = june2027 - 240 * day,
        average: Double = 0.0,
        even: Double? = null,
        monthDoneAt: Long? = null,
        longest: Int = 0,
        fraction: Double = 0.0,
    ) = GoalProgress.Result(
        goal = GoalEntity(
            name = "Laptop", type = type, targetAmount = target, deadline = deadline, targetMonths = months,
            achievedAt = achievedAt, createdAt = created,
        ),
        saved = saved, target = target, fraction = fraction, pace = pace,
        remaining = if (type == GoalType.RECURRING) (target ?: 0.0) - thisMonth else (target ?: 0.0) - saved,
        perMonthNeeded = perMonth, monthsLeft = 6, savedThisMonth = thisMonth, averagePerMonth = average,
        projectedFinishMs = projected, streakMonths = streak, movements = emptyList(),
        evenPaceNow = even, longestStreak = longest, monthDoneAtMs = monthDoneAt,
    )

    @Test fun goalsTabCardsStayQuietWhenOnTrack() {
        val r = result(fraction = 0.207)
        assertEquals("12,400" to "of 60,000 ETB", GoalCopy.cardAmount(r))
        assertNull(GoalCopy.cardNext(r, "GREGORIAN"))
        assertEquals("21%" to "12,400 of 60,000 ETB · 47,600 to go", GoalCopy.largeCardAmount(r))
        assertEquals("On track", GoalCopy.paceLabel(r))
    }

    @Test fun behindSaysWhatGetsYouThere() {
        val r = result(pace = Pace.BEHIND, perMonth = 74_107.0, fraction = 0.17)
        assertEquals("74,107 ETB a month gets you there by June 2027", GoalCopy.cardNext(r, "GREGORIAN"))
        assertEquals("17%", GoalCopy.barRight(r, "GREGORIAN"))
        assertEquals("Save 74,107 ETB a month to make it by June 2027", GoalCopy.statusLine(r, "GREGORIAN"))
    }

    @Test fun datesFollowTheEthiopianCalendar() {
        val line = GoalCopy.statusLine(result(), "ETHIOPIAN")
        assertTrue(line, line.endsWith("Sene 2019"))
        assertEquals("Amount by a date · Sene 8, 2019", GoalCopy.kindLine(result(), "ETHIOPIAN"))
    }

    @Test fun evenPaceLineOnlyForDatedGoalsOnTheWay() {
        assertEquals(
            "An even pace would be 32,000 ETB today. That’s the mark on the bar.",
            GoalCopy.evenPaceLine(result(even = 32_000.0)),
        )
        assertNull(GoalCopy.evenPaceLine(result(even = null)))
        // A goal made today: 25 ETB of 90,000 is noise, so no line and no mark.
        assertNull(GoalCopy.evenPaceLine(result(target = 90_000.0, even = 25.0)))
        assertNull(GoalCopy.evenPaceLine(result(pace = Pace.MISSED, even = 50_000.0)))
    }

    @Test fun missedSaysHowLongAgo() {
        val r = result(pace = Pace.MISSED, deadline = june2027)
        assertEquals("47,600 ETB to go", GoalCopy.statusLine(r, "GREGORIAN"))
        val facts = GoalCopy.facts(r, "CBE 1", 0.0, june2027 + 13 * day, "GREGORIAN").map { it.text }
        assertEquals("The date passed 13 days ago. Edit the goal to set a new date.", facts.first())
        assertEquals("Date passed", GoalCopy.paceLabel(r))
    }

    @Test fun reachedFactsAndDialog() {
        val at = june2027 - 59 * day
        val r = result(saved = 60_000.0, pace = Pace.REACHED, achievedAt = at, created = at - 244 * day)
        assertEquals("You saved 60,000 ETB", GoalCopy.statusLine(r, "GREGORIAN"))
        val facts = GoalCopy.facts(r, "CBE 2", 0.0, june2027, "GREGORIAN").map { it.text }
        assertTrue(facts[0], facts[0].startsWith("Reached on ") && facts[0].endsWith(", 59 days early"))
        assertTrue(facts[1], facts[1].startsWith("CBE 2 is free for another goal."))
        assertEquals(listOf("Took" to "8 months", "Before the date" to "59 days"), GoalCopy.reachedFacts(r))
    }

    @Test fun monthlyGoal() {
        val r = result(type = GoalType.RECURRING, target = 2_000.0, deadline = null, thisMonth = 500.0, perMonth = 1_500.0, streak = 3, average = 2_100.0)
        assertEquals("500" to "of 2,000 ETB this month", GoalCopy.cardAmount(r))
        assertEquals("500 of 2,000 ETB this month", GoalCopy.amountLine(r))
        assertEquals("1,500 left", GoalCopy.barRight(r, "GREGORIAN"))
        assertEquals("1,500 ETB left to save this month", GoalCopy.statusLine(r, "GREGORIAN"))
        assertEquals("Every month · 2,000 ETB", GoalCopy.kindLine(r, "GREGORIAN"))
        val facts = GoalCopy.facts(r, null, 0.0, june2027, "GREGORIAN").map { it.text }
        assertEquals(listOf("3 months in a row", "You save about 2,100 ETB a month", "This goal counts the saves you add yourself."), facts)
        assertEquals("Done this month", GoalCopy.paceLabel(r.copy(pace = Pace.REACHED)))
    }

    @Test fun monthDone() {
        val c = Calendar.getInstance().apply { clear(); set(2026, 10, 13, 9, 0) }   // 13 Nov: 17 days left
        val r = result(type = GoalType.RECURRING, target = 5_000.0, deadline = null, thisMonth = 5_000.0, pace = Pace.REACHED, streak = 3, monthDoneAt = c.timeInMillis)
        assertEquals("You saved 5,000 ETB this month, 17 days early.", GoalCopy.monthDoneLine(r))
        assertEquals(4, GoalCopy.runWithThisMonth(r))
    }

    @Test fun safetyNet() {
        val r = result(type = GoalType.OPEN_ENDED, target = 38_900.0, saved = 29_900.0, pace = Pace.NO_DEADLINE, deadline = null, perMonth = null, months = 3, projected = june2027)
        assertEquals("9,000 ETB to go · about June 2027 at this pace", GoalCopy.statusLine(r, "GREGORIAN"))
        val facts = GoalCopy.facts(r, "CBE 1", 12_950.0, june2027, "GREGORIAN").map { it.text }
        assertTrue(facts.contains("The target follows your spending: 3 × about 12,950 ETB a month, rounded up."))
        assertNull(GoalCopy.paceLabel(r))
        assertNull(GoalCopy.cardNext(r, "GREGORIAN"))
    }

    @Test fun safetyNetWithoutSpendingYet() {
        val r = result(type = GoalType.OPEN_ENDED, target = null, saved = 4_000.0, pace = Pace.NO_DEADLINE, deadline = null, perMonth = null, months = 3)
        assertEquals("4,000 ETB saved" to null, GoalCopy.cardAmount(r))
        assertEquals("No target yet", GoalCopy.amountLine(r))
        assertEquals("Sized after your first full month", GoalCopy.barRight(r, "GREGORIAN"))
        assertEquals("Kash sizes this once it has a full month of your spending", GoalCopy.cardNext(r, "GREGORIAN"))
        val nov13 = Calendar.getInstance().apply { clear(); set(2026, 10, 13, 9, 0) }.timeInMillis
        val facts = GoalCopy.facts(r, null, 0.0, nov13, "GREGORIAN").map { it.text }
        assertEquals("That happens on 1 Dec, when November is complete.", facts.first())
    }

    @Test fun goalsTabMonthPill() {
        assertEquals("+21,500 ETB this month", GoalCopy.monthPill(21_500.0))
        assertNull(GoalCopy.monthPill(0.0))
    }

    @Test fun historyNames() {
        val manual = GoalProgress.Movement(500.0, 0L, GoalProgress.Source.MANUAL, note = "Cash from Ekub")
        val inn = GoalProgress.Movement(500.0, 0L, GoalProgress.Source.ACCOUNT, transactionId = 1)
        val out = GoalProgress.Movement(-200.0, 0L, GoalProgress.Source.ACCOUNT, transactionId = 2)
        assertEquals("Cash from Ekub", GoalCopy.movementName(manual, "CBE 2"))
        assertEquals("Added by you", GoalCopy.movementName(manual.copy(note = null), "CBE 2"))
        assertEquals("Into CBE 2", GoalCopy.movementName(inn, "CBE 2"))
        assertEquals("Out of CBE 2", GoalCopy.movementName(out, "CBE 2"))
        assertEquals("Counted from your CBE 2 SMS, by itself", GoalCopy.historyCaption("CBE 2"))
        assertEquals("+7,500.00", GoalCopy.monthNet(7_500.0))
    }
}
