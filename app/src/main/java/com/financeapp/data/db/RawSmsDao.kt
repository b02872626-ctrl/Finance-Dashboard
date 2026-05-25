package com.financeapp.data.db

import androidx.room.*
import com.financeapp.data.model.RawSmsEntity

@Dao
interface RawSmsDao {

    /**
     * Insert raw SMS. Returns -1 if body hash already exists (IGNORE strategy).
     * This is the first deduplication gate — if -1, skip parsing entirely.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sms: RawSmsEntity): Long

    @Query("SELECT bodyHash FROM raw_sms WHERE bodyHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): String?

    @Query("SELECT COUNT(*) FROM raw_sms")
    suspend fun count(): Int

    @Query("SELECT * FROM raw_sms WHERE isSynced = 0 ORDER BY receivedAt ASC")
    suspend fun getUnsyncedRawSms(): List<RawSmsEntity>

    @Query("SELECT COUNT(*) FROM raw_sms WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("UPDATE raw_sms SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM raw_sms")
    suspend fun clear()
}
