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

    @Test fun rowsShowAmountTargetAndAQuietStatus() {
        val r = result(saved = 34_600.0, fraction = 0.577)
        assertEquals("34,600", GoalCopy.rowAmount(r))
        assertEquals("of 60,000 ETB", GoalCopy.rowOf(r))
        assertEquals("58%" to GoalCopy.Tone.NEUTRAL, GoalCopy.rowStatus(r))
        assertEquals("Behind" to GoalCopy.Tone.BAD, GoalCopy.rowStatus(result(pace = Pace.BEHIND, fraction = 0.17)))
        val monthly = result(type = GoalType.RECURRING, target = 5_000.0, thisMonth = 2_000.0, deadline = null, fraction = 0.4)
        assertEquals("2,000", GoalCopy.rowAmount(monthly))
        assertEquals("of 5,000 this month", GoalCopy.rowOf(monthly))
    }

    @Test fun statusIsColouredTextWithTheApprovedWords() {
        val cal = "GREGORIAN"
        assertEquals("On track" to GoalCopy.Tone.GOOD, GoalCopy.status(result(), cal))
        assertEquals("Behind" to GoalCopy.Tone.BAD, GoalCopy.status(result(pace = Pace.BEHIND), cal))
        assertEquals("Date passed" to GoalCopy.Tone.BAD, GoalCopy.status(result(pace = Pace.MISSED), cal))
        assertEquals("Reached" to GoalCopy.Tone.GOOD, GoalCopy.status(result(pace = Pace.REACHED), cal))
        assertEquals("Just started" to GoalCopy.Tone.NEUTRAL, GoalCopy.status(result(saved = 0.0), cal))
        assertEquals("Waiting for a full month" to GoalCopy.Tone.NEUTRAL, GoalCopy.status(result(type = GoalType.OPEN_ENDED, target = null), cal))
        assertEquals(
            "On track · 3 months in a row" to GoalCopy.Tone.GOOD,
            GoalCopy.status(result(type = GoalType.RECURRING, target = 5_000.0, streak = 3, deadline = null), cal),
        )
    }

    @Test fun heroSaysOneStatusOrHowManyAreOnTrack() {
        assertEquals("On track", GoalCopy.heroSummary(listOf(result()), 1, "GREGORIAN"))
        assertEquals("3 of 4 on track", GoalCopy.heroSummary(List(4) { result() }, 3, "GREGORIAN"))
        assertEquals("All goals reached", GoalCopy.heroSummary(emptyList(), 0, "GREGORIAN"))
    }

    @Test fun theSentenceGivesThePacePerDayWeekOrMonth() {
        val r = result(perMonth = 3_044.0)
        assertTrue(GoalCopy.statusSentence(r, "GREGORIAN").startsWith("3,044 ETB a month gets you there by June 2027"))
        assertEquals("100 ETB a day", GoalCopy.perUnit(3_044.0, GoalCopy.PaceUnit.DAY))
        assertEquals("700 ETB a week", GoalCopy.perUnit(3_044.0, GoalCopy.PaceUnit.WEEK))
        assertTrue(GoalCopy.statusSentence(r, "GREGORIAN", GoalCopy.PaceUnit.DAY).startsWith("100 ETB a day gets you there"))
        assertEquals("A new month starts at 0 on the 1st.", GoalCopy.statusSentence(result(type = GoalType.RECURRING, deadline = null), "GREGORIAN"))
        assertTrue(GoalCopy.statusSentence(result(pace = Pace.MISSED), "GREGORIAN").endsWith("has passed. Pick a new date to keep going."))
    }

    @Test fun theFooterLeadsToHowItIsCountedOrANewDate() {
        assertEquals(GoalCopy.FooterAction.INFO, GoalCopy.footer(result(), "CBE 2").action)
        assertEquals("Counts money into CBE 2", GoalCopy.footer(result(), "CBE 2").text)
        assertEquals("Counts saves you add", GoalCopy.footer(result(), null).text)
        assertEquals(GoalCopy.FooterAction.SET_DATE, GoalCopy.footer(result(pace = Pace.MISSED), "CBE 2").action)
        assertEquals("CBE 2 is free for another goal", GoalCopy.footer(result(pace = Pace.REACHED), "CBE 2").text)
    }

    @Test fun reachedAndMonthDoneSayHowEarly() {
        val r = result(pace = Pace.REACHED, saved = 60_000.0, achievedAt = june2027 - 53 * day)
        assertEquals("60,000 ETB for Laptop, 53 days early.", GoalCopy.reachedLine(r))
        assertEquals("4 months in a row, your longest yet", GoalCopy.runLine(result(type = GoalType.RECURRING, streak = 3, longest = 3)))
        assertEquals("4 months in a row", GoalCopy.runLine(result(type = GoalType.RECURRING, streak = 3, longest = 6)))
    }

    @Test fun historyRowsNameTheAccountOrTheNote() {
        val sms = GoalProgress.Movement(2_500.0, june2027, GoalProgress.Source.ACCOUNT, transactionId = 1)
        val hand = GoalProgress.Movement(1_000.0, june2027, GoalProgress.Source.MANUAL, contributionId = "c", note = "Cash from Ekub")
        assertEquals("Into CBE 2", GoalCopy.movementName(sms, "CBE 2"))
        assertEquals("Cash from Ekub", GoalCopy.movementName(hand, "CBE 2"))
        assertTrue(GoalCopy.movementSub(hand, "CBE 2").startsWith("Added by you · "))
        assertEquals("+2,500.00", GoalCopy.dayNet(2_500.0))
        assertEquals("Nothing yet. Money into CBE 1 shows up here.", GoalCopy.emptyHistory("CBE 1"))
    }
}
