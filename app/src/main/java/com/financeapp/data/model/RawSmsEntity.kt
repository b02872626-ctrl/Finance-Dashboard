package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores every raw SMS we've seen, keyed by body MD5 hash.
 * The UNIQUE index on bodyHash prevents re-inserting the same SMS
 * (the primary deduplication gate before even running the parser).
 */
@Entity(
    tableName = "raw_sms",
    indices = [
        Index(value = ["bodyHash"], unique = true),
        Index(value = ["isSynced"])
    ]
)
data class RawSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    /** MD5 of the trimmed SMS body */
    val bodyHash: String,
    /** Epoch millis when the SMS arrived on the device */
    val receivedAt: Long,
    /** False until this raw SMS is confirmed in Supabase. */
    val isSynced: Boolean = false
)
