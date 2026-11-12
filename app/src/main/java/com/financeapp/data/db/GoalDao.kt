package com.financeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.financeapp.data.model.GoalContributionEntity
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
}
