package com.financeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeapp.data.model.SentNotificationEntity

@Dao
interface SentNotificationDao {

    /** Returns -1 when [n]'s dedupKey was already used — the notification is a repeat. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: SentNotificationEntity): Long

    @Query("UPDATE sent_notifications SET sentAt = :sentAt, shownAs = :shownAs WHERE id = :id")
    suspend fun markShown(id: Long, sentAt: Long, shownAs: String)

    /** Shown (not queued) notifications of [tier] since [fromMs], for the frequency caps. */
    @Query("SELECT COUNT(*) FROM sent_notifications WHERE tier = :tier AND sentAt >= :fromMs AND shownAs != 'DIGEST' AND triggerType != :exceptTrigger")
    suspend fun countShownSince(tier: Int, fromMs: Long, exceptTrigger: String = ""): Int

    /** Everything waiting for the next digest, oldest first. */
    @Query("SELECT * FROM sent_notifications WHERE sentAt IS NULL ORDER BY createdAt ASC")
    suspend fun queued(): List<SentNotificationEntity>

    @Query("UPDATE sent_notifications SET sentAt = :sentAt, shownAs = 'DIGEST', digestKey = :digestKey WHERE id IN (:ids)")
    suspend fun markDigested(ids: List<Long>, sentAt: Long, digestKey: String)

    /** Drop the log after 120 days; dedup keys older than that can't recur. */
    @Query("DELETE FROM sent_notifications WHERE createdAt < :beforeMs")
    suspend fun prune(beforeMs: Long)

    @Query("DELETE FROM sent_notifications")
    suspend fun clear()
}
