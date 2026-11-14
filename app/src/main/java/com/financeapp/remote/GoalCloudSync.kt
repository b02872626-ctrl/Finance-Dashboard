package com.financeapp.remote

import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Goals go to Supabase the way transactions do: the phone is the source of
 * truth, `i_goals` / `i_goal_contributions` are a copy that survives a
 * reinstall and that the web dashboard can read and edit.
 *
 * A sync pass (SupabaseSyncer.syncGoals) pulls first, then pushes:
 *
 *  1. **Pull.** The whole cloud copy is read (a user has a handful of
 *     goals) and merged by [plan]:
 *      - a cloud row the phone doesn't have is added (reinstall, second
 *        phone, made on the web);
 *      - a synced row that differs from the cloud takes the cloud's
 *        version: only the web could have changed it, since a local edit
 *        makes the row unsynced;
 *      - a synced row missing from the cloud was deleted on the web, so it
 *        goes from the phone too;
 *      - an unsynced row is never touched: it is waiting to be uploaded;
 *      - a cloud row the phone has a pending delete for is left out, so
 *        it is not brought back.
 *     No clock comparison: a wrong phone clock can't lose an edit.
 *  2. **Push.** Deletes remembered in `goal_deletions`, then goals and
 *     saves with `isSynced = 0`.
 *
 * Pure: no database, no network, no default clock. Unit-tested in
 * GoalCloudSyncTest.
 */
object GoalCloudSync {
    private const val DAY = 86_400_000L

    /** A row of public.i_goals. Timestamps in epoch ms; the deadline is a plain date ("2026-06-30"). */
    data class CloudGoal(
        val id: String,
        val name: String,
        val emoji: String?,
        val type: String,
        val targetAmount: Double?,
        val targetMonths: Int?,
        val deadline: String?,
        val priority: Int,
        val note: String?,
        val status: String,
        val linkedAccountKey: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val achievedAt: Long?,
    )

    /** A row of public.i_goal_contributions. */
    data class CloudContribution(
        val id: String,
        val goalId: String,
        val amount: Double,
        val contributedAt: Long,
        val note: String?,
        val createdAt: Long,
    )

    /** What the pull changes on the phone, applied in one transaction (GoalRepository.applyPlan). */
    data class Plan(
        val goalsToSave: List<GoalEntity> = emptyList(),
        val goalIdsToDelete: List<String> = emptyList(),
        val contributionsToAdd: List<GoalContributionEntity> = emptyList(),
        val contributionIdsToDelete: List<String> = emptyList(),
    ) {
        val size: Int
            get() = goalsToSave.size + goalIdsToDelete.size + contributionsToAdd.size + contributionIdsToDelete.size
        val isEmpty: Boolean get() = size == 0
    }

    fun plan(
        localGoals: List<GoalEntity>,
        localContributions: List<GoalContributionEntity>,
        cloudGoals: List<CloudGoal>,
        cloudContributions: List<CloudContribution>,
        pendingDeletionIds: Set<String> = emptySet(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Plan {
        val cloudG = cloudGoals.filter { it.id !in pendingDeletionIds }.associateBy { it.id }
        val localG = localGoals.associateBy { it.id }

        val goalsToSave = mutableListOf<GoalEntity>()
        for (c in cloudG.values) {
            val local = localG[c.id]
            val fromCloud = toLocal(c, zone)
            when {
                local == null -> goalsToSave += fromCloud
                !local.isSynced -> Unit
                !sameGoal(local, fromCloud, zone) -> goalsToSave += fromCloud
            }
        }
        val goalIdsToDelete = localGoals.filter { it.isSynced && it.id !in cloudG }.map { it.id }
        val gone = goalIdsToDelete.toSet()
        val goalsAfter = (localG.keys - gone) + goalsToSave.map { it.id }

        val cloudC = cloudContributions.filter { it.id !in pendingDeletionIds }.associateBy { it.id }
        val localC = localContributions.associateBy { it.id }
        val contributionsToAdd = cloudC.values
            .filter { it.id !in localC && it.goalId in goalsAfter }
            .map { toLocal(it) }
        // Saves of a goal that is going are removed with it (cascade).
        val contributionIdsToDelete = localContributions
            .filter { it.isSynced && it.id !in cloudC && it.goalId !in gone }
            .map { it.id }

        return Plan(goalsToSave, goalIdsToDelete, contributionsToAdd, contributionIdsToDelete)
    }

    /** The fields that sync. Amounts within half a cent count as equal (numeric(18,2) in the cloud). */
    fun sameGoal(a: GoalEntity, b: GoalEntity, zone: TimeZone = TimeZone.getDefault()): Boolean =
        a.id == b.id &&
            a.name == b.name &&
            blankToNull(a.emoji) == blankToNull(b.emoji) &&
            a.type == b.type &&
            sameAmount(a.targetAmount, b.targetAmount) &&
            a.targetMonths == b.targetMonths &&
            a.deadline?.let { deadlineToDate(it, zone) } == b.deadline?.let { deadlineToDate(it, zone) } &&
            a.priority == b.priority &&
            blankToNull(a.note) == blankToNull(b.note) &&
            a.status == b.status &&
            blankToNull(a.linkedAccountKey) == blankToNull(b.linkedAccountKey) &&
            a.createdAt == b.createdAt &&
            a.achievedAt == b.achievedAt

    fun toCloud(g: GoalEntity, zone: TimeZone = TimeZone.getDefault()) = CloudGoal(
        id = g.id,
        name = g.name,
        emoji = blankToNull(g.emoji),
        type = g.type,
        targetAmount = g.targetAmount,
        targetMonths = g.targetMonths,
        deadline = g.deadline?.let { deadlineToDate(it, zone) },
        priority = g.priority,
        note = blankToNull(g.note),
        status = g.status,
        linkedAccountKey = blankToNull(g.linkedAccountKey),
        createdAt = g.createdAt,
        updatedAt = g.updatedAt,
        achievedAt = g.achievedAt,
    )

    /** A cloud row as it is stored on the phone: already synced. */
    fun toLocal(c: CloudGoal, zone: TimeZone = TimeZone.getDefault()) = GoalEntity(
        id = c.id,
        name = c.name,
        emoji = blankToNull(c.emoji),
        type = c.type,
        targetAmount = c.targetAmount,
        targetMonths = c.targetMonths,
        deadline = c.deadline?.let { dateToDeadline(it, zone) },
        priority = c.priority,
        note = blankToNull(c.note),
        status = c.status,
        linkedAccountKey = blankToNull(c.linkedAccountKey),
        createdAt = c.createdAt,
        updatedAt = c.updatedAt,
        achievedAt = c.achievedAt,
        isSynced = true,
    )

    fun toCloud(c: GoalContributionEntity) = CloudContribution(
        id = c.id,
        goalId = c.goalId,
        amount = c.amount,
        contributedAt = c.contributedAt,
        note = blankToNull(c.note),
        createdAt = c.createdAt,
    )

    fun toLocal(c: CloudContribution) = GoalContributionEntity(
        id = c.id,
        goalId = c.goalId,
        amount = c.amount,
        contributedAt = c.contributedAt,
        note = blankToNull(c.note),
        createdAt = c.createdAt,
        isSynced = true,
    )

    /** The deadline (end of a day on the phone) as the cloud's date, "2026-06-30". */
    fun deadlineToDate(ms: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        )
    }

    /** The cloud's date as the phone's deadline: the end of that day (how GoalForm stores it). Null when unreadable. */
    fun dateToDeadline(date: String, zone: TimeZone = TimeZone.getDefault()): Long? {
        val m = DATE.matchEntire(date.trim()) ?: return null
        val c = Calendar.getInstance(zone).apply {
            clear()
            set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt())
        }
        return c.timeInMillis + DAY - 1
    }

    /**
     * A Postgres timestamptz as PostgREST sends it ("2026-11-13T07:58:21.123456+00:00",
     * also "…Z" or "…+03:00", any number of fraction digits) to epoch ms.
     */
    fun parseIsoMs(s: String?): Long? {
        val m = ISO.matchEntire(s?.trim() ?: return null) ?: return null
        val g = m.groupValues
        val fraction = g[7]
        val millis = if (fraction.isEmpty()) 0 else (fraction + "00").take(3).toInt()
        val offset = g[8]
        val offsetMs = if (offset.isEmpty() || offset == "Z") 0L else {
            val sign = if (offset[0] == '-') -1L else 1L
            val digits = offset.drop(1).replace(":", "")
            val hours = digits.take(2).toLong()
            val minutes = if (digits.length >= 4) digits.substring(2, 4).toLong() else 0L
            sign * (hours * 60 + minutes) * 60_000L
        }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            set(Calendar.MILLISECOND, millis)
        }
        return utc.timeInMillis - offsetMs
    }

    private fun sameAmount(a: Double?, b: Double?): Boolean =
        if (a == null || b == null) a == b else abs(a - b) < 0.005

    private fun blankToNull(s: String?): String? = s?.takeIf { it.isNotBlank() }

    private val DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val ISO = Regex(
        """^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}(?::?\d{2})?)?$"""
    )
}
