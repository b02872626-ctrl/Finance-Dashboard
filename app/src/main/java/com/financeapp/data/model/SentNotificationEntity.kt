package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every notification Kash has decided to show, keyed by [dedupKey] —
 * `trigger:subject` (a transaction id, an account + day, a period). The
 * UNIQUE index is what stops a retried worker or an upgrade rescan, which
 * replays old SMS, from firing the same notification twice.
 *
 * [sentAt] is null while the item waits for the evening digest (quiet
 * hours, a full daily cap, or a Tier 2 trigger); [digestKey] records which
 * digest finally carried it.
 */
@Entity(
    tableName = "sent_notifications",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["tier", "sentAt"]),
    ]
)
data class SentNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupKey: String,
    /** See notifications.Trigger. */
    val triggerType: String,
    val tier: Int,
    val title: String,
    val text: String,
    /** App route the notification opens, or null for Home. */
    val route: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    /** SYSTEM, IN_APP or DIGEST. Null while queued. */
    val shownAs: String? = null,
    val digestKey: String? = null,
)
