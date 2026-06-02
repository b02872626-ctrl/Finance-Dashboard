package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Learned merchant_key -> category mapping. One row per merchant, written
 * either when the user confirms a suggestion or picks a different category
 * in the Daily Review queue.
 *
 * [matchCount] increments every time the user re-confirms the same mapping
 * — the categorizer turns count into a 0..1 confidence via a log scale.
 *
 * Mirrors public.i_category_rules (unique on user_id, merchant_key). Since
 * the local DB only stores one user's data, [merchantKey] is the primary key.
 */
@Entity(
    tableName = "category_rules",
    indices = [androidx.room.Index(value = ["identifier"])]
)
data class CategoryRuleEntity(
    @PrimaryKey val merchantKey: String,
    val category: String,
    val matchCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** False until the rule is mirrored to Supabase. */
    val isSynced: Boolean = false,
    /**
     * Stable counterparty identifier (Telebirr phone or bank A/C No.). When
     * present, predictor matches against this BEFORE the name-based merchant
     * key — survives "Beselam Melesew" vs "Beselam Melisew" spelling drift.
     * Null for rules created from name-only SMS (most bank-side messages).
     */
    val identifier: String? = null
)
