package com.financeapp.data.repository

import androidx.room.withTransaction
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.goals.GoalRules
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus
import kotlinx.coroutines.flow.Flow

/** Why a goal couldn't be saved: its account already feeds [usedBy]. */
class AccountInUseException(val usedBy: GoalEntity) :
    IllegalStateException("Account already feeds the goal \"${usedBy.name}\"")

/**
 * Savings goals on the phone (offline first). Not synced to Supabase yet:
 * i_goals has no column for the linked account, see docs/PROGRESS.md.
 */
class GoalRepository(private val db: AppDatabase) {
    private val dao = db.goalDao()

    fun goals(): Flow<List<GoalEntity>> = dao.allGoals()
    fun contributions(): Flow<List<GoalContributionEntity>> = dao.allContributions()

    suspend fun get(id: String): GoalEntity? = dao.getGoal(id)

    /** A new goal goes to the end of the list. Throws [AccountInUseException]. */
    suspend fun create(goal: GoalEntity): GoalEntity = db.withTransaction {
        checkAccountFree(goal)
        val g = goal.copy(priority = dao.maxActivePriority() + 1)
        dao.upsertGoal(g)
        g
    }

    /** Throws [AccountInUseException] when the new account feeds another goal. */
    suspend fun update(goal: GoalEntity) = db.withTransaction {
        checkAccountFree(goal)
        dao.upsertGoal(goal.copy(updatedAt = System.currentTimeMillis(), isSynced = false))
    }

    private suspend fun checkAccountFree(goal: GoalEntity) {
        if (goal.status != GoalStatus.ACTIVE) return
        val key = goal.linkedAccountKey ?: return
        GoalRules.goalUsing(dao.snapshot(), key, exceptGoalId = goal.id)?.let { throw AccountInUseException(it) }
    }

    suspend fun logSave(goalId: String, amount: Double, note: String?, at: Long = System.currentTimeMillis()) {
        require(amount > 0.0) { "A save must be more than 0" }
        dao.insertContribution(
            GoalContributionEntity(goalId = goalId, amount = amount, contributedAt = at, note = note?.trim()?.ifBlank { null })
        )
    }

    suspend fun deleteSave(id: String) = dao.deleteContribution(id)

    suspend fun markAchieved(goal: GoalEntity, at: Long = System.currentTimeMillis()) =
        update(goal.copy(status = GoalStatus.ACHIEVED, achievedAt = at))

    suspend fun archive(goal: GoalEntity) = update(goal.copy(status = GoalStatus.ARCHIVED))

    suspend fun delete(id: String) = dao.deleteGoal(id)
}
