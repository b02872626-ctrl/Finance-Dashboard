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
    ) = GoalProgress.Result(
        goal = GoalEntity(name = "Laptop", type = type, targetAmount = target, deadline = deadline, targetMonths = months),
        saved = saved, target = target, fraction = 0.0, pace = pace,
        remaining = if (type == GoalType.RECURRING) (target ?: 0.0) - thisMonth else (target ?: 0.0) - saved,
        perMonthNeeded = perMonth, monthsLeft = 6, savedThisMonth = thisMonth, averagePerMonth = 0.0,
        projectedFinishMs = projected, streakMonths = streak, movements = emptyList(),
    )

    @Test fun amountByADate() {
        val r = result()
        assertEquals("12,400 of 60,000 ETB", GoalCopy.amountLine(r))
        assertEquals("Save 4,800 ETB a month to make it by June 2027", GoalCopy.nextStep(r, "GREGORIAN"))
        assertEquals("On track", GoalCopy.paceLabel(r))
    }

    @Test fun datesFollowTheEthiopianCalendar() {
        val line = GoalCopy.nextStep(result(), "ETHIOPIAN")
        assertTrue(line, line.endsWith("Sene 2019"))
    }

    @Test fun reachedAndMissed() {
        assertEquals("You saved 60,000 ETB", GoalCopy.nextStep(result(saved = 60_000.0, pace = Pace.REACHED), "GREGORIAN"))
        assertEquals("Reached", GoalCopy.paceLabel(result(pace = Pace.REACHED)))
        val missed = result(pace = Pace.MISSED)
        assertEquals("47,600 ETB to go", GoalCopy.nextStep(missed, "GREGORIAN"))
        assertEquals("Date passed", GoalCopy.paceLabel(missed))
    }

    @Test fun monthlyGoal() {
        val r = result(type = GoalType.RECURRING, target = 2_000.0, deadline = null, thisMonth = 500.0, perMonth = 1_500.0, streak = 3)
        assertEquals("500 of 2,000 ETB this month", GoalCopy.amountLine(r))
        assertEquals("1,500 ETB left to save this month", GoalCopy.nextStep(r, "GREGORIAN"))
        assertEquals("3 months in a row", GoalCopy.streak(r))
        assertEquals("Done this month", GoalCopy.paceLabel(r.copy(pace = Pace.REACHED)))
        assertNull(GoalCopy.streak(r.copy(streakMonths = 1)))
    }

    @Test fun safetyNetWithoutSpendingYet() {
        val r = result(type = GoalType.OPEN_ENDED, target = null, saved = 4_000.0, pace = Pace.NO_DEADLINE, deadline = null, perMonth = null, months = 3)
        assertEquals("4,000 ETB saved", GoalCopy.amountLine(r))
        assertEquals("Kash sizes this once it has a full month of your spending", GoalCopy.nextStep(r, "GREGORIAN"))
        assertNull(GoalCopy.paceLabel(r))
    }

    @Test fun safetyNetWithAFinishEstimate() {
        val r = result(type = GoalType.OPEN_ENDED, target = 13_000.0, saved = 4_000.0, pace = Pace.NO_DEADLINE, deadline = null, perMonth = null, projected = june2027)
        assertEquals("9,000 ETB to go · about June 2027 at this pace", GoalCopy.nextStep(r, "GREGORIAN"))
    }

    @Test fun historyNames() {
        val manual = GoalProgress.Movement(500.0, 0L, GoalProgress.Source.MANUAL, note = "Cash from Ekub")
        val inn = GoalProgress.Movement(500.0, 0L, GoalProgress.Source.ACCOUNT, transactionId = 1)
        val out = GoalProgress.Movement(-200.0, 0L, GoalProgress.Source.ACCOUNT, transactionId = 2)
        assertEquals("Cash from Ekub", GoalCopy.movementName(manual, "CBE 2"))
        assertEquals("Added by you", GoalCopy.movementName(manual.copy(note = null), "CBE 2"))
        assertEquals("Into CBE 2", GoalCopy.movementName(inn, "CBE 2"))
        assertEquals("Out of CBE 2", GoalCopy.movementName(out, "CBE 2"))
    }
}
