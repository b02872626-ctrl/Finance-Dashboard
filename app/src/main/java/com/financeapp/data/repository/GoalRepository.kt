package com.financeapp.data.repository

import androidx.room.withTransaction
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.goals.GoalRules
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalDeletionEntity
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import com.financeapp.remote.GoalCloudSync
import kotlinx.coroutines.flow.Flow

/** Why a goal couldn't be saved: its account already feeds [usedBy]. */
class AccountInUseException(val usedBy: GoalEntity) :
    IllegalStateException("Account already feeds the goal \"${usedBy.name}\"")

/**
 * Savings goals on the phone (offline first). Every change is stored
 * unsynced and [onChanged] asks for a sync pass, which copies it to
 * i_goals / i_goal_contributions (see remote/GoalCloudSync.kt). A delete is
 * remembered in goal_deletions until the cloud row is gone too.
 */
class GoalRepository(
    private val db: AppDatabase,
    private val onChanged: () -> Unit = {},
) {
    private val dao = db.goalDao()

    fun goals(): Flow<List<GoalEntity>> = dao.allGoals()
    fun contributions(): Flow<List<GoalContributionEntity>> = dao.allContributions()

    suspend fun get(id: String): GoalEntity? = dao.getGoal(id)

    /** A new goal goes to the end of the list. Throws [AccountInUseException]. */
    suspend fun create(goal: GoalEntity): GoalEntity {
        val g = db.withTransaction {
            checkAccountFree(goal)
            goal.copy(priority = dao.maxActivePriority() + 1, isSynced = false).also { dao.upsertGoal(it) }
        }
        onChanged()
        return g
    }

    /** Throws [AccountInUseException] when the new account feeds another goal. */
    suspend fun update(goal: GoalEntity) {
        db.withTransaction {
            checkAccountFree(goal)
            dao.upsertGoal(goal.copy(updatedAt = System.currentTimeMillis(), isSynced = false))
        }
        onChanged()
    }

    private suspend fun checkAccountFree(goal: GoalEntity) {
        if (goal.status != GoalStatus.ACTIVE) return
        val key = goal.linkedAccountKey ?: return
        GoalRules.goalUsing(dao.snapshot(), key, exceptGoalId = goal.id)?.let { throw AccountInUseException(it) }
    }

    suspend fun logSave(goalId: String, amount: Double, note: String?, at: Long = System.currentTimeMillis()) {
        require(amount > 0.0) { "A save must be more than 0" }
        dao.insertContribution(
            GoalContributionEntity(
                goalId = goalId,
                amount = amount,
                contributedAt = at,
                note = note?.trim()?.ifBlank { null },
                isSynced = false,
            )
        )
        onChanged()
    }

    suspend fun deleteSave(id: String) {
        db.withTransaction {
            dao.deleteContribution(id)
            dao.insertDeletion(GoalDeletionEntity(id, GoalDeletionEntity.KIND_CONTRIBUTION))
        }
        onChanged()
    }

    suspend fun markAchieved(goal: GoalEntity, at: Long = System.currentTimeMillis()) =
        update(goal.copy(status = GoalStatus.ACHIEVED, achievedAt = at))

    suspend fun archive(goal: GoalEntity) = update(goal.copy(status = GoalStatus.ARCHIVED))

    /** Its saves go with it, here (cascade) and in the cloud (cascade there too). */
    suspend fun delete(id: String) {
        db.withTransaction {
            dao.deleteGoal(id)
            dao.insertDeletion(GoalDeletionEntity(id, GoalDeletionEntity.KIND_GOAL))
        }
        onChanged()
    }

    // ---- Cloud sync (SupabaseSyncer.syncGoals) ----

    suspend fun countPendingSync(): Int = dao.countPendingSync()
    suspend fun snapshot(): List<GoalEntity> = dao.snapshot()
    suspend fun contributionsSnapshot(): List<GoalContributionEntity> = dao.contributionsSnapshot()
    suspend fun unsyncedGoals(): List<GoalEntity> = dao.unsyncedGoals()
    suspend fun unsyncedContributions(): List<GoalContributionEntity> = dao.unsyncedContributions()
    suspend fun pendingDeletions(): List<GoalDeletionEntity> = dao.pendingDeletions()

    /** After a successful upload. A goal edited meanwhile stays unsynced. */
    suspend fun markGoalsUploaded(goals: List<GoalEntity>) = db.withTransaction {
        goals.forEach { dao.markGoalSynced(it.id, it.updatedAt) }
    }

    suspend fun markContributionsUploaded(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markContributionsSynced(ids)
    }

    suspend fun clearDeletions(ids: List<String>) {
        if (ids.isNotEmpty()) dao.clearDeletions(ids)
    }

    /** The cloud's side of the merge, in one transaction. Returns how many rows changed. */
    suspend fun applyPlan(plan: GoalCloudSync.Plan): Int {
        if (plan.isEmpty) return 0
        db.withTransaction {
            if (plan.goalIdsToDelete.isNotEmpty()) dao.deleteGoals(plan.goalIdsToDelete)
            if (plan.goalsToSave.isNotEmpty()) dao.upsertGoals(plan.goalsToSave)
            if (plan.contributionIdsToDelete.isNotEmpty()) dao.deleteContributions(plan.contributionIdsToDelete)
            if (plan.contributionsToAdd.isNotEmpty()) dao.insertContributions(plan.contributionsToAdd)
        }
        return plan.size
    }
}
