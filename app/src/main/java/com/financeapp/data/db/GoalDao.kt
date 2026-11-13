package com.financeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalDeletionEntity
import com.financeapp.data.model.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals ORDER BY priority ASC, createdAt ASC")
    fun allGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY contributedAt DESC")
    fun allContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoal(id: String): GoalEntity?

    @Query("SELECT * FROM goals")
    suspend fun snapshot(): List<GoalEntity>

    @Query("SELECT * FROM goal_contributions")
    suspend fun contributionsSnapshot(): List<GoalContributionEntity>

    @Query("SELECT COALESCE(MAX(priority), 0) FROM goals WHERE status = 'ACTIVE'")
    suspend fun maxActivePriority(): Int

    @Upsert
    suspend fun upsertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContribution(c: GoalContributionEntity)

    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteContribution(id: String)

    /** Its contributions go with it (foreign key, ON DELETE CASCADE). */
    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: String)

    @Query("DELETE FROM goals")
    suspend fun clear()

    // ---- Cloud sync (remote/GoalCloudSync.kt) ----

    @Query("SELECT * FROM goals WHERE isSynced = 0")
    suspend fun unsyncedGoals(): List<GoalEntity>

    @Query("SELECT * FROM goal_contributions WHERE isSynced = 0")
    suspend fun unsyncedContributions(): List<GoalContributionEntity>

    /** Only while the goal is as it was when read for upload; an edit made meanwhile stays unsynced. */
    @Query("UPDATE goals SET isSynced = 1 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markGoalSynced(id: String, updatedAt: Long)

    @Query("UPDATE goal_contributions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markContributionsSynced(ids: List<String>)

    @Upsert
    suspend fun upsertGoals(goals: List<GoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(contributions: List<GoalContributionEntity>)

    @Query("DELETE FROM goals WHERE id IN (:ids)")
    suspend fun deleteGoals(ids: List<String>)

    @Query("DELETE FROM goal_contributions WHERE id IN (:ids)")
    suspend fun deleteContributions(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: GoalDeletionEntity)

    @Query("SELECT * FROM goal_deletions")
    suspend fun pendingDeletions(): List<GoalDeletionEntity>

    @Query("DELETE FROM goal_deletions WHERE id IN (:ids)")
    suspend fun clearDeletions(ids: List<String>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM goals WHERE isSynced = 0) " +
            "+ (SELECT COUNT(*) FROM goal_contributions WHERE isSynced = 0) " +
            "+ (SELECT COUNT(*) FROM goal_deletions)"
    )
    suspend fun countPendingSync(): Int
}
