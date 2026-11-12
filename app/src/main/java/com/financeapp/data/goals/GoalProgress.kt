package com.financeapp.data.goals

import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.data.model.GoalType
import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isSpending
import com.financeapp.parsing.TransactionType
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * How far each savings goal has got, and whether it is on pace.
 *
 * Money reaches a goal two ways:
 *
 *  1. **Automatically, from SMS.** A goal can be linked to one of the user's
 *     accounts (their savings account). Every money-in on that account after
 *     the goal was made counts as saved; every money-out takes it back.
 *     This is how a move from CBE to a CBE savings account, or a salary
 *     landing straight in it, shows up without the user typing anything.
 *  2. **By hand.** A save the user logs (cash in a box, an account Kash
 *     can't read). It goes to that one goal.
 *
 * One account feeds one goal at a time (GoalRules checks this when a goal
 * is made or relinked). Money out of the account can take the goal down
 * to zero but never eats into saves logged by hand. Once a goal is
 * reached, money after that no longer counts for it, so the account is
 * free for the next goal.
 *
 * Pure: no database and no clock. Pass `now` and the time zone.
 */
object GoalProgress {

    /** Either side of the expected pace, as a share of the target, still "on track". */
    const val PACE_TOLERANCE = 0.05

    /** Average month length, for "months left". */
    private const val MONTH_MS = 30.44 * 86_400_000L

    enum class Source { ACCOUNT, MANUAL }

    /** One movement of money into (or out of) a goal, for the goal's history. */
    data class Movement(
        val amount: Double,
        val atMs: Long,
        val source: Source,
        /** The SMS row it came from (ACCOUNT). */
        val transactionId: Long? = null,
        /** The logged save (MANUAL). */
        val contributionId: String? = null,
        val note: String? = null,
    )

    enum class Pace {
        /** Target reached (this month, for a monthly goal). */
        REACHED,
        AHEAD,
        ON_TRACK,
        BEHIND,
        /** The date has passed and the target wasn't reached. */
        MISSED,
        /** No date to keep pace with (a safety-net goal, or no target yet). */
        NO_DEADLINE,
    }

    data class Result(
        val goal: GoalEntity,
        /** Everything saved toward the goal so far. */
        val saved: Double,
        /** What it is aiming for (per month for RECURRING). Null when it can't be known yet. */
        val target: Double?,
        /** 0..1 of [target] reached ([savedThisMonth] for RECURRING). */
        val fraction: Double,
        val pace: Pace,
        /** Left to save: in total, or this month for RECURRING. */
        val remaining: Double,
        /** To save each month from now to make the date (DATE_BOUND), or what's left this month (RECURRING). */
        val perMonthNeeded: Double?,
        /** Whole months to the deadline, rounded up (DATE_BOUND). */
        val monthsLeft: Int?,
        val savedThisMonth: Double,
        /** Average saved a month over the last 3 months. */
        val averagePerMonth: Double,
        /** When the goal would be reached at [averagePerMonth]; null if never or already reached. */
        val projectedFinishMs: Long?,
        /** RECURRING: months in a row, before this one, that hit the amount. */
        val streakMonths: Int,
        /**
         * Where an even pace would be today, in birr: a straight line from the
         * goal's start to its date (DATE_BOUND), or through the month
         * (RECURRING). The mark on the progress bar. Null otherwise or once reached.
         */
        val evenPaceNow: Double? = null,
        /** RECURRING: the longest run of months that reached the amount, this month included. */
        val longestStreak: Int = 0,
        /** RECURRING: when this month's saves first reached the amount; null if not yet. */
        val monthDoneAtMs: Long? = null,
        /** Newest first. */
        val movements: List<Movement>,
    ) {
        val reached: Boolean get() = pace == Pace.REACHED
    }

    data class Summary(val results: List<Result>) {
        val totalSaved: Double get() = results.filter { it.goal.status != GoalStatus.ARCHIVED }.sumOf { it.saved }
        /** Net saved this month across goals that aren't archived ("+21,500 ETB this month"). */
        val savedThisMonth: Double get() = results.filter { it.goal.status != GoalStatus.ARCHIVED }.sumOf { it.savedThisMonth }
        val onTrack: Int get() = results.count { it.goal.status == GoalStatus.ACTIVE && it.pace in GOOD }
        val active: Int get() = results.count { it.goal.status == GoalStatus.ACTIVE }
    }

    private val GOOD = setOf(Pace.REACHED, Pace.AHEAD, Pace.ON_TRACK, Pace.NO_DEADLINE)

    // ── Entry point ─────────────────────────────────────────────────────────

    fun compute(
        goals: List<GoalEntity>,
        contributions: List<GoalContributionEntity>,
        transactions: List<TransactionEntity>,
        averageMonthlySpend: Double,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): Summary {
        val ordered = goals.sortedWith(compareBy<GoalEntity> { it.priority }.thenBy { it.createdAt })
        val byGoal = contributions.filter { it.amount > 0 }.groupBy { it.goalId }
        val usable = transactions.filter { it.dateTime <= now && !it.hidden && it.reviewStatus != ReviewStatus.NOT_A_TXN }
        val results = ordered.map { g ->
            val moves = manualMoves(byGoal[g.id].orEmpty()) + accountMoves(g, usable)
            result(g, targetOf(g, averageMonthlySpend), moves, now, zone)
        }
        return Summary(results)
    }

    private fun manualMoves(saves: List<GoalContributionEntity>) = saves.map {
        Movement(it.amount, it.contributedAt, Source.MANUAL, contributionId = it.id, note = it.note)
    }

    /**
     * Money in and out of the goal's account while the goal was taking it:
     * from [GoalEntity.createdAt] to [GoalEntity.achievedAt]. Archived goals
     * take none. A money-out is cut so the account part never drops below 0.
     */
    private fun accountMoves(g: GoalEntity, rows: List<TransactionEntity>): List<Movement> {
        val key = g.linkedAccountKey ?: return emptyList()
        if (g.status == GoalStatus.ARCHIVED) return emptyList()
        val end = g.achievedAt ?: Long.MAX_VALUE
        val out = mutableListOf<Movement>()
        var balance = 0.0
        rows.filter { it.dateTime in g.createdAt..end && rowOnAccount(it, key) }
            .sortedWith(compareBy<TransactionEntity> { it.dateTime }.thenBy { it.id })
            .forEach { t ->
                val flow = flowOf(t)
                val amount = if (flow < 0) -min(-flow, balance) else flow
                if (amount == 0.0) return@forEach
                balance += amount
                out += Movement(amount, t.dateTime, Source.ACCOUNT, transactionId = t.id)
            }
        return out
    }

    // ── One goal ────────────────────────────────────────────────────────────

    private fun result(
        g: GoalEntity,
        target: Double?,
        moves: List<Movement>,
        now: Long,
        zone: TimeZone,
    ): Result {
        val saved = max(0.0, moves.sumOf { it.amount })
        val thisMonth = moves.filter { sameMonth(it.atMs, now, zone) }.sumOf { it.amount }
        val average = averagePerMonth(moves, now, zone)

        val recurring = g.type == GoalType.RECURRING
        val reachedAmount = if (recurring) thisMonth else saved
        val remaining = if (target == null) 0.0 else max(0.0, target - reachedAmount)
        val fraction = if (target == null || target <= 0.0) 0.0 else (reachedAmount / target).coerceIn(0.0, 1.0)

        var monthsLeft: Int? = null
        var evenPace: Double? = null
        // A monthly goal always says what's left this month (0 once it's reached).
        var perMonth: Double? = if (recurring && target != null) remaining else null
        val pace: Pace = when {
            target != null && target > 0.0 && remaining <= 0.005 -> Pace.REACHED
            g.status == GoalStatus.ACHIEVED -> Pace.REACHED
            target == null -> Pace.NO_DEADLINE
            recurring -> {
                val (day, days) = dayOfMonth(now, zone)
                evenPace = target * day / days
                paceAgainst(thisMonth, target * day / days, target)
            }
            g.type == GoalType.DATE_BOUND && g.deadline != null -> {
                if (now > g.deadline) {
                    monthsLeft = 0
                    perMonth = remaining
                    Pace.MISSED
                } else {
                    monthsLeft = max(1, ceil((g.deadline - now) / MONTH_MS).toInt())
                    perMonth = remaining / monthsLeft
                    val span = (g.deadline - g.createdAt).coerceAtLeast(1L)
                    val elapsed = ((now - g.createdAt).toDouble() / span).coerceIn(0.0, 1.0)
                    evenPace = target * elapsed
                    paceAgainst(saved, target * elapsed, target)
                }
            }
            else -> Pace.NO_DEADLINE
        }

        val projected = if (pace == Pace.REACHED || recurring || target == null || average <= 0.0) null
        else now + (remaining / average * MONTH_MS).toLong()

        return Result(
            goal = g,
            saved = saved,
            target = target,
            fraction = fraction,
            pace = pace,
            remaining = remaining,
            perMonthNeeded = perMonth,
            monthsLeft = monthsLeft,
            savedThisMonth = thisMonth,
            averagePerMonth = average,
            projectedFinishMs = projected,
            streakMonths = if (recurring && target != null) streak(g, target, moves, now, zone) else 0,
            movements = moves.sortedByDescending { it.atMs },
            evenPaceNow = if (pace == Pace.REACHED) null else evenPace,
            longestStreak = if (recurring && target != null) longestStreak(g, target, moves, now, zone) else 0,
            monthDoneAtMs = if (recurring && target != null) monthDoneAt(target, moves, now, zone) else null,
        )
    }

    private fun paceAgainst(saved: Double, expected: Double, target: Double): Pace {
        val slack = target * PACE_TOLERANCE
        return when {
            saved >= expected + slack -> Pace.AHEAD
            saved < expected - slack -> Pace.BEHIND
            else -> Pace.ON_TRACK
        }
    }

    /** RECURRING: months in a row before this one that reached [target], counting back. */
    private fun streak(g: GoalEntity, target: Double, moves: List<Movement>, now: Long, zone: TimeZone): Int {
        var count = 0
        val cal = Calendar.getInstance(zone).apply { timeInMillis = startOfMonth(now, zone) }
        while (true) {
            cal.add(Calendar.MONTH, -1)
            val monthStart = cal.timeInMillis
            // Stop at the month before the goal existed.
            if (startOfMonth(g.createdAt, zone) > monthStart) break
            val inMonth = moves.filter { sameMonth(it.atMs, monthStart, zone) }.sumOf { it.amount }
            if (inMonth + 0.005 < target) break
            count++
        }
        return count
    }

    /** RECURRING: the longest run of months (from the goal's first month to this one) that reached [target]. */
    private fun longestStreak(g: GoalEntity, target: Double, moves: List<Movement>, now: Long, zone: TimeZone): Int {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = startOfMonth(g.createdAt, zone) }
        val last = startOfMonth(now, zone)
        var best = 0
        var run = 0
        while (cal.timeInMillis <= last) {
            val monthStart = cal.timeInMillis
            val inMonth = moves.filter { sameMonth(it.atMs, monthStart, zone) }.sumOf { it.amount }
            run = if (inMonth + 0.005 >= target) run + 1 else 0
            best = max(best, run)
            cal.add(Calendar.MONTH, 1)
        }
        return best
    }

    /** RECURRING: the moment this month's running total first reached [target]. */
    private fun monthDoneAt(target: Double, moves: List<Movement>, now: Long, zone: TimeZone): Long? {
        var total = 0.0
        moves.filter { sameMonth(it.atMs, now, zone) }.sortedBy { it.atMs }.forEach {
            total += it.amount
            if (total + 0.005 >= target) return it.atMs
        }
        return null
    }

    /** Average net saved a month over the last 3 months (this one included), counting only months the goal existed. */
    private fun averagePerMonth(moves: List<Movement>, now: Long, zone: TimeZone): Double {
        if (moves.isEmpty()) return 0.0
        val first = moves.minOf { it.atMs }
        val cal = Calendar.getInstance(zone).apply { timeInMillis = startOfMonth(now, zone) }
        cal.add(Calendar.MONTH, -2)
        val from = max(cal.timeInMillis, startOfMonth(first, zone))
        val months = monthsBetween(from, now, zone) + 1
        return moves.filter { it.atMs >= from }.sumOf { it.amount } / months
    }

    // ── Targets and spending ────────────────────────────────────────────────

    /** What a goal aims for. OPEN_ENDED: months × average spending, rounded up to 100 ETB. */
    fun targetOf(g: GoalEntity, averageMonthlySpend: Double): Double? = when (g.type) {
        GoalType.OPEN_ENDED -> {
            val months = g.targetMonths ?: 0
            if (months <= 0 || averageMonthlySpend <= 0.0) null
            else ceil(months * averageMonthlySpend / 100.0) * 100.0
        }
        else -> g.targetAmount?.takeIf { it > 0.0 }
    }

    /**
     * Average spending a month over the last 3 full months that have any
     * rows (Spending rules: own-account moves don't count). 0 with no history.
     */
    fun averageMonthlySpend(rows: List<TransactionEntity>, now: Long, zone: TimeZone = TimeZone.getDefault()): Double {
        val thisMonth = startOfMonth(now, zone)
        val cal = Calendar.getInstance(zone).apply { timeInMillis = thisMonth; add(Calendar.MONTH, -3) }
        val from = cal.timeInMillis
        val usable = rows.filter { !it.hidden && it.reviewStatus != ReviewStatus.NOT_A_TXN && it.dateTime in from until thisMonth }
        val months = usable.map { startOfMonth(it.dateTime, zone) }.toSet().size
        if (months == 0) return 0.0
        return usable.filter { it.isSpending() }.sumOf { it.amount } / months
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    /**
     * What a row does to the account it is on: + money in, − money out.
     * Own-account moves count here (they are exactly how money reaches a
     * savings account); a parser-typed INTERNAL_TRANSFER says its direction
     * in the counterparty ("to …" / "from …").
     */
    fun flowOf(t: TransactionEntity): Double {
        if (t.amount <= 0.0) return 0.0
        return when (t.type) {
            TransactionType.CREDIT.name -> t.amount
            in Spending.EXPENSE_TYPES -> -t.amount
            TransactionType.INTERNAL_TRANSFER.name -> {
                val cp = t.counterparty?.trimStart().orEmpty()
                when {
                    cp.startsWith("from ", ignoreCase = true) -> t.amount
                    cp.startsWith("to ", ignoreCase = true) -> -t.amount
                    else -> 0.0
                }
            }
            else -> 0.0
        }
    }

    /**
     * True when [t] is on the account [key] names. A "bank#1234" key needs
     * the row's account ending to match; a plain bank key (the user's only
     * account there) takes every row of that bank.
     */
    fun rowOnAccount(t: TransactionEntity, key: String): Boolean {
        val bank = t.bankName.ifBlank { t.sender }
        val parsed = AccountGrouping.parseAccountKey(key)
            ?: return bank == key
        val (keyBank, suffix) = parsed
        if (bank != keyBank) return false
        val rowSuffix = AccountGrouping.accountSuffix(t.accountNumber) ?: return false
        return AccountGrouping.sameSuffix(rowSuffix, suffix)
    }

    // ── Calendar ────────────────────────────────────────────────────────────

    fun startOfMonth(ms: Long, zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        timeInMillis = ms
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun sameMonth(a: Long, b: Long, zone: TimeZone) = startOfMonth(a, zone) == startOfMonth(b, zone)

    private fun monthsBetween(from: Long, to: Long, zone: TimeZone): Int {
        val a = Calendar.getInstance(zone).apply { timeInMillis = from }
        val b = Calendar.getInstance(zone).apply { timeInMillis = to }
        return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 + (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
    }

    /** (day, days in month) for [ms]. */
    private fun dayOfMonth(ms: Long, zone: TimeZone): Pair<Int, Int> {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return c.get(Calendar.DAY_OF_MONTH) to c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
