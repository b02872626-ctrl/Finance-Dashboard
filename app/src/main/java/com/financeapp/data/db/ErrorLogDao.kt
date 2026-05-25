package com.financeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeapp.data.model.ErrorLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorLogDao {

    @Query("SELECT * FROM error_logs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ErrorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ErrorLogEntity): Long

    @Query("DELETE FROM error_logs")
    suspend fun clear()
}

