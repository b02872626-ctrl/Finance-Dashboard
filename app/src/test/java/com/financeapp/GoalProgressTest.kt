package com.financeapp

import com.financeapp.data.goals.GoalProgress
import com.financeapp.data.goals.GoalRules
import com.financeapp.data.goals.GoalProgress.Pace
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Savings goals: auto-saves from SMS, saves by hand, one account per goal, and pace. Made-up numbers throughout. */
class GoalProgressTest {

    private val zone = TimeZone.getTimeZone("Africa/Addis_Ababa")
    private val cbe = "Commercial Bank of Ethiopia"
    private val savingsKey = "$cbe#4321"
    private var nextId = 0L

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private val start = at(2026, 1, 1, 0)
    private val now = at(2026, 4, 15)

    private fun row(
        type: TransactionType,
        amount: Double,
        when_: Long,
        account: String? = "1*****4321",
        bank: String = cbe,
        category: String? = null,
        counterparty: String? = null,
        hidden: Boolean = false,
        status: String = ReviewStatus.PENDING,
    ) = TransactionEntity(
        id = ++nextId, sender = "CBE", bankName = bank, type = type.name, amount = amount, balance = null,
        counterparty = counterparty, accountNumber = account, refNumber = null, dateTime = when_,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
        category = category, reviewStatus = status, hidden = hidden, rawBody = "",
    )

    private fun into(amount: Double, when_: Long) = row(TransactionType.CREDIT, amount, when_)
    private fun outOf(amount: Double, when_: Long) = row(TransactionType.DEBIT, amount, when_)

    private fun goal(
        id: String,
        type: String = GoalType.DATE_BOUND,
        target: Double? = 10_000.0,
        months: Int? = null,
        deadline: Long? = at(2026, 12, 31),
        priority: Int = 100,
        account: String? = savingsKey,
        created: Long = start,
        status: String = GoalStatus.ACTIVE,
        achievedAt: Long? = null,
    ) = GoalEntity(
        id = id, name = id, type = type, targetAmount = target, targetMonths = months, deadline = deadline,
        priority = priority, linkedAccountKey = account, createdAt = created, status = status, achievedAt = achievedAt,
    )

    private fun manual(goalId: String, amount: Double, when_: Long) =
        GoalContributionEntity(goalId = goalId, amount = amount, contributedAt = when_)

    private fun compute(
        goals: List<GoalEntity>,
        rows: List<TransactionEntity> = emptyList(),
        saves: List<GoalContributionEntity> = emptyList(),
        spend: Double = 0.0,
        at: Long = now,
    ) = GoalProgress.compute(goals, saves, rows, spend, at, zone)

    private fun GoalProgress.Summary.of(id: String) = results.single { it.goal.id == id }

    // ── Money from the linked account ───────────────────────────────────────

    @Test fun moneyIntoTheLinkedAccountCounts() {
        val s = compute(listOf(goal("laptop")), listOf(into(2_000.0, at(2026, 2, 3)), into(1_000.0, at(2026, 3, 3))))
        assertEquals(3_000.0, s.of("laptop").saved, 0.001)
        assertEquals(0.3, s.of("laptop").fraction, 0.001)
    }

    @Test fun moneyOutOfTheLinkedAccountTakesItBack() {
        val s = compute(listOf(goal("laptop")), listOf(into(2_000.0, at(2026, 2, 3)), outOf(500.0, at(2026, 2, 10))))
        assertEquals(1_500.0, s.of("laptop").saved, 0.001)
    }

    @Test fun aGoalNeverGoesBelowZeroFromTheAccount() {
        val s = compute(listOf(goal("laptop")), listOf(outOf(800.0, at(2026, 2, 1)), into(300.0, at(2026, 2, 2))))
        assertEquals(300.0, s.of("laptop").saved, 0.001)
    }

    @Test fun moneyBeforeTheGoalWasMadeDoesNotCount() {
        val g = goal("laptop", created = at(2026, 3, 1))
        val s = compute(listOf(g), listOf(into(5_000.0, at(2026, 2, 20)), into(1_000.0, at(2026, 3, 5))))
        assertEquals(1_000.0, s.of("laptop").saved, 0.001)
    }

    @Test fun otherAccountsAtTheSameBankDoNotCount() {
        val other = row(TransactionType.CREDIT, 4_000.0, at(2026, 2, 3), account = "1*****9999")
        val s = compute(listOf(goal("laptop")), listOf(other, into(1_000.0, at(2026, 2, 4))))
        assertEquals(1_000.0, s.of("laptop").saved, 0.001)
    }

    @Test fun aPlainBankKeyTakesEveryRowOfThatBank() {
        val tb = row(TransactionType.CREDIT, 700.0, at(2026, 2, 3), account = null, bank = "Telebirr")
        val s = compute(listOf(goal("trip", account = "Telebirr")), listOf(tb, into(1_000.0, at(2026, 2, 4))))
        assertEquals(700.0, s.of("trip").saved, 0.001)
    }

    @Test fun aMoveFromAnotherOwnAccountCountsAsSaved() {
        // Filed under "Own accounts" so it isn't income, but it is still a save.
        val move = row(TransactionType.CREDIT, 2_500.0, at(2026, 2, 3), category = Spending.OWN_ACCOUNTS)
        val s = compute(listOf(goal("laptop")), listOf(move))
        assertEquals(2_500.0, s.of("laptop").saved, 0.001)
    }

    @Test fun internalTransferReadsItsDirectionFromTheCounterparty() {
        val inn = row(TransactionType.INTERNAL_TRANSFER, 900.0, at(2026, 2, 3), counterparty = "from CBE 1000****1111")
        val out = row(TransactionType.INTERNAL_TRANSFER, 400.0, at(2026, 2, 4), counterparty = "to CBE 1000****1111")
        val s = compute(listOf(goal("laptop")), listOf(inn, out))
        assertEquals(500.0, s.of("laptop").saved, 0.001)
    }

    @Test fun hiddenRowsAndNotATransactionAreIgnored() {
        val rows = listOf(
            row(TransactionType.CREDIT, 1_000.0, at(2026, 2, 3), hidden = true),
            row(TransactionType.CREDIT, 2_000.0, at(2026, 2, 3), status = ReviewStatus.NOT_A_TXN),
            into(300.0, at(2026, 2, 5)),
        )
        assertEquals(300.0, compute(listOf(goal("laptop")), rows).of("laptop").saved, 0.001)
    }

    @Test fun futureRowsDoNotCount() {
        val s = compute(listOf(goal("laptop")), listOf(into(1_000.0, at(2026, 5, 1))))
        assertEquals(0.0, s.of("laptop").saved, 0.001)
    }

    // ── Saves by hand ───────────────────────────────────────────────────────

    @Test fun manualSavesAddToTheirGoalOnly() {
        val goals = listOf(goal("box", account = null), goal("other", account = null))
        val s = compute(goals, saves = listOf(manual("box", 600.0, at(2026, 2, 1)), manual("box", 400.0, at(2026, 3, 1))))
        assertEquals(1_000.0, s.of("box").saved, 0.001)
        assertEquals(0.0, s.of("other").saved, 0.001)
    }

    @Test fun manualAndAccountSavesAddUp() {
        val s = compute(
            listOf(goal("laptop")),
            rows = listOf(into(2_000.0, at(2026, 2, 3))),
            saves = listOf(manual("laptop", 500.0, at(2026, 2, 4))),
        )
        assertEquals(2_500.0, s.of("laptop").saved, 0.001)
        assertEquals(2, s.of("laptop").movements.size)
    }

    @Test fun anAccountWithdrawalDoesNotEatManualSaves() {
        val s = compute(
            listOf(goal("laptop")),
            rows = listOf(into(1_000.0, at(2026, 2, 3)), outOf(5_000.0, at(2026, 2, 10))),
            saves = listOf(manual("laptop", 700.0, at(2026, 2, 4))),
        )
        assertEquals(700.0, s.of("laptop").saved, 0.001)
    }

    // ── One account, one goal ──────────────────────────────────────────────

    @Test fun aGoalCanGoPastItsTarget() {
        val s = compute(listOf(goal("only", target = 1_000.0)), listOf(into(1_500.0, at(2026, 2, 3))))
        assertEquals(1_500.0, s.of("only").saved, 0.001)
        assertEquals(1.0, s.of("only").fraction, 0.001)
        assertEquals(Pace.REACHED, s.of("only").pace)
    }

    @Test fun anArchivedGoalTakesNoAccountMoney() {
        val s = compute(listOf(goal("old", status = GoalStatus.ARCHIVED)), listOf(into(2_000.0, at(2026, 2, 3))))
        assertEquals(0.0, s.of("old").saved, 0.001)
    }

    @Test fun aReachedGoalLetsTheAccountGoToTheNextGoal() {
        val goals = listOf(
            goal("done", target = 1_000.0, status = GoalStatus.ACHIEVED, achievedAt = at(2026, 2, 1)),
            goal("next", created = at(2026, 2, 2)),
        )
        val s = compute(goals, listOf(into(1_000.0, at(2026, 1, 20)), into(800.0, at(2026, 2, 5))))
        assertEquals(1_000.0, s.of("done").saved, 0.001)
        assertEquals(800.0, s.of("next").saved, 0.001)
    }

    @Test fun anAccountFeedsOnlyOneActiveGoal() {
        val laptop = goal("laptop")
        val goals = listOf(laptop, goal("box", account = null))
        assertEquals(laptop, GoalRules.goalUsing(goals, savingsKey))
        // Editing the goal that already has it is fine.
        assertNull(GoalRules.goalUsing(goals, savingsKey, exceptGoalId = "laptop"))
        // The same account written with a short masked tail still counts as taken.
        assertEquals(laptop, GoalRules.goalUsing(goals, "$cbe#21"))
        assertNull(GoalRules.goalUsing(goals, "$cbe#9999"))
    }

    @Test fun reachedAndArchivedGoalsLetGoOfTheirAccount() {
        val goals = listOf(
            goal("done", status = GoalStatus.ACHIEVED, achievedAt = at(2026, 2, 1)),
            goal("old", status = GoalStatus.ARCHIVED),
        )
        assertNull(GoalRules.goalUsing(goals, savingsKey))
    }

    // ── Pace for a goal with a date ────────────────────────────────────────

    @Test fun onTrackWhenSavedMatchesTheStraightLine() {
        // 1 Jan → 1 Jul, checked on 1 Apr: about half way.
        val g = goal("car", target = 6_000.0, deadline = at(2026, 7, 1, 0))
        val r = compute(listOf(g), listOf(into(3_000.0, at(2026, 3, 1))), at = at(2026, 4, 1, 0)).of("car")
        assertEquals(Pace.ON_TRACK, r.pace)
        assertEquals(3, r.monthsLeft)
        assertEquals(1_000.0, r.perMonthNeeded!!, 0.001)
    }

    @Test fun behindAndAheadOfTheLine() {
        val g = goal("car", target = 6_000.0, deadline = at(2026, 7, 1, 0))
        val behind = compute(listOf(g), listOf(into(1_000.0, at(2026, 3, 1))), at = at(2026, 4, 1, 0)).of("car")
        val ahead = compute(listOf(g), listOf(into(5_000.0, at(2026, 3, 1))), at = at(2026, 4, 1, 0)).of("car")
        assertEquals(Pace.BEHIND, behind.pace)
        assertEquals(Pace.AHEAD, ahead.pace)
    }

    @Test fun missedWhenTheDatePassedShort() {
        val g = goal("car", target = 6_000.0, deadline = at(2026, 3, 1))
        val r = compute(listOf(g), listOf(into(1_000.0, at(2026, 2, 1)))).of("car")
        assertEquals(Pace.MISSED, r.pace)
        assertEquals(5_000.0, r.remaining, 0.001)
    }

    @Test fun projectedFinishFollowsTheMonthlyAverage() {
        // 1,000 a month for Feb–Apr; 7,000 left → about 7 months from now.
        val rows = listOf(into(1_000.0, at(2026, 2, 3)), into(1_000.0, at(2026, 3, 3)), into(1_000.0, at(2026, 4, 3)))
        val r = compute(listOf(goal("laptop")), rows).of("laptop")
        assertEquals(1_000.0, r.averagePerMonth, 0.001)
        val months = (r.projectedFinishMs!! - now) / (30.44 * 86_400_000L)
        assertEquals(7.0, months, 0.05)
    }

    // ── Monthly goals ───────────────────────────────────────────────────────

    @Test fun recurringCountsThisMonthOnly() {
        val g = goal("monthly", type = GoalType.RECURRING, target = 2_000.0, deadline = null)
        val rows = listOf(into(2_000.0, at(2026, 3, 5)), into(500.0, at(2026, 4, 2)))
        val r = compute(listOf(g), rows).of("monthly")
        assertEquals(500.0, r.savedThisMonth, 0.001)
        assertEquals(0.25, r.fraction, 0.001)
        assertEquals(1_500.0, r.perMonthNeeded!!, 0.001)
        assertEquals(2_500.0, r.saved, 0.001)
    }

    @Test fun recurringReachedThisMonth() {
        val g = goal("monthly", type = GoalType.RECURRING, target = 1_000.0, deadline = null)
        val r = compute(listOf(g), listOf(into(1_500.0, at(2026, 4, 5)))).of("monthly")
        assertEquals(Pace.REACHED, r.pace)
        assertEquals(0.0, r.perMonthNeeded!!, 0.001)
    }

    @Test fun recurringStreakCountsFullMonthsBackFromLastMonth() {
        val g = goal("monthly", type = GoalType.RECURRING, target = 1_000.0, deadline = null)
        val rows = listOf(
            into(400.0, at(2026, 1, 5)),   // January short: the streak stops here
            into(1_000.0, at(2026, 2, 5)),
            into(1_200.0, at(2026, 3, 5)),
        )
        assertEquals(2, compute(listOf(g), rows).of("monthly").streakMonths)
    }

    // ── Safety-net goals (months of spending) ──────────────────────────────

    @Test fun safetyNetTargetIsMonthsTimesSpendingRoundedUp() {
        val g = goal("net", type = GoalType.OPEN_ENDED, target = null, months = 3, deadline = null)
        val r = compute(listOf(g), listOf(into(4_000.0, at(2026, 2, 1))), spend = 4_321.0).of("net")
        assertEquals(13_000.0, r.target!!, 0.001)
        assertEquals(Pace.NO_DEADLINE, r.pace)
    }

    @Test fun safetyNetWithNoSpendingHistoryHasNoTargetYet() {
        val g = goal("net", type = GoalType.OPEN_ENDED, target = null, months = 3, deadline = null)
        val r = compute(listOf(g), listOf(into(4_000.0, at(2026, 2, 1)))).of("net")
        assertNull(r.target)
        assertEquals(4_000.0, r.saved, 0.001)
        assertEquals(0.0, r.fraction, 0.001)
    }

    @Test fun averageMonthlySpendUsesTheLastThreeFullMonths() {
        val rows = listOf(
            row(TransactionType.DEBIT, 3_000.0, at(2026, 1, 10), account = null),
            row(TransactionType.DEBIT, 3_000.0, at(2026, 2, 10), account = null),
            row(TransactionType.DEBIT, 6_000.0, at(2026, 3, 10), account = null),
            // Not spending: own-account move, this month, and too old.
            row(TransactionType.DEBIT, 9_000.0, at(2026, 3, 11), account = null, category = Spending.OWN_ACCOUNTS),
            row(TransactionType.DEBIT, 9_000.0, at(2026, 4, 2), account = null),
            row(TransactionType.DEBIT, 9_000.0, at(2025, 12, 2), account = null),
        )
        assertEquals(4_000.0, GoalProgress.averageMonthlySpend(rows, now, zone), 0.001)
    }

    // ── Totals ──────────────────────────────────────────────────────────────

    @Test fun summaryCountsOnTrackActiveGoals() {
        val goals = listOf(
            goal("a", target = 1_000.0, priority = 1),
            goal("b", target = 100_000.0, deadline = at(2026, 5, 1), priority = 2, account = null),
        )
        val s = compute(goals, listOf(into(1_500.0, at(2026, 2, 3))))
        assertEquals(2, s.active)
        assertEquals(1, s.onTrack)
        assertEquals(1_500.0, s.totalSaved, 0.001)
    }

    // ── Numbers for the design: even pace, streaks, month done ─────────────

    @Test fun evenPaceIsTheStraightLineToday() {
        val g = goal("car", target = 6_000.0, deadline = at(2026, 7, 1, 0))
        val r = compute(listOf(g), listOf(into(3_000.0, at(2026, 3, 1))), at = at(2026, 4, 1, 0)).of("car")
        assertEquals(6_000.0 * 90 / 181, r.evenPaceNow!!, 1.0)
    }

    @Test fun noEvenPaceOnceReachedOrForASafetyNet() {
        val done = compute(listOf(goal("a", target = 1_000.0)), listOf(into(1_000.0, at(2026, 2, 1)))).of("a")
        assertNull(done.evenPaceNow)
        val net = goal("net", type = GoalType.OPEN_ENDED, target = null, months = 3, deadline = null)
        assertNull(compute(listOf(net), spend = 4_000.0).of("net").evenPaceNow)
    }

    @Test fun longestStreakAndMonthDone() {
        val g = goal("monthly", type = GoalType.RECURRING, target = 1_000.0, deadline = null)
        val rows = listOf(
            into(1_000.0, at(2026, 1, 5)),
            into(1_000.0, at(2026, 2, 5)),
            into(200.0, at(2026, 3, 5)),     // March short: run of 2 ends
            into(600.0, at(2026, 4, 3)),
            into(600.0, at(2026, 4, 9)),     // April done on the 9th
        )
        val r = compute(listOf(g), rows).of("monthly")
        assertEquals(2, r.longestStreak)
        assertEquals(0, r.streakMonths)
        assertEquals(at(2026, 4, 9), r.monthDoneAtMs)
    }

    @Test fun summaryAddsUpThisMonth() {
        val goals = listOf(goal("a"), goal("b", account = null), goal("old", account = null, status = GoalStatus.ARCHIVED))
        val s = compute(
            goals,
            rows = listOf(into(700.0, at(2026, 4, 2)), into(900.0, at(2026, 3, 2))),
            saves = listOf(manual("b", 300.0, at(2026, 4, 5)), manual("old", 999.0, at(2026, 4, 5))),
        )
        assertEquals(1_000.0, s.savedThisMonth, 0.001)
    }
}
