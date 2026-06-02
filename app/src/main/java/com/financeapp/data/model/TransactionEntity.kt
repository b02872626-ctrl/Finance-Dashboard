package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a parsed financial transaction.
 * refNumber is indexed for fast duplicate lookup.
 */
/**
 * Review-status state machine for the Daily Review queue.
 *  - PENDING     : parser emitted a row, user hasn't touched it
 *  - CONFIRMED   : user accepted the predicted category (or it had a category
 *                  before Daily Review existed — backfilled in MIGRATION_7_8)
 *  - CHANGED     : user picked a different category than the prediction
 *  - SKIPPED     : user swiped left — ask again tomorrow
 *  - NOT_A_TXN   : user marked it as not a real transaction
 *
 * Mirrors the check constraint in supabase/add_daily_review.sql.
 */
object ReviewStatus {
    const val PENDING   = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val CHANGED   = "CHANGED"
    const val SKIPPED   = "SKIPPED"
    const val NOT_A_TXN = "NOT_A_TXN"
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["refNumber"]),
        Index(value = ["sender", "amount", "dateTime"]),
        Index(value = ["isSynced"]),
        // Daily Review: how many PENDING txns today? + per-merchant lookup.
        Index(value = ["reviewStatus", "dateTime"]),
        Index(value = ["merchantKey"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val bankName: String,
    /** TransactionType.name — stored as string for Room compatibility */
    val type: String,
    val amount: Double,
    val balance: Double?,
    val counterparty: String?,
    val accountNumber: String?,
    /** Ref/transaction number from SMS — used for deduplication */
    val refNumber: String?,
    /** Epoch millis of the transaction as parsed from SMS body */
    val dateTime: Long,
    val serviceCharge: Double?,
    val vat: Double?,
    val disasterFund: Double?,
    val totalCharged: Double?,
    val currency: String = "ETB",
    /**
     * User-confirmed category. Set when the user confirms or changes a
     * suggestion. Null until the txn has been reviewed.
     */
    val category: String? = null,
    /**
     * What the categorizer suggested at parse time. Independent of [category]
     * so we can compare "did the user accept our guess?" later.
     */
    val predictedCategory: String? = null,
    /** 0.0 – 1.0. Null when the categorizer had no match. */
    val categoryConfidence: Double? = null,
    /** See [ReviewStatus]. Defaults to PENDING for every fresh parse. */
    val reviewStatus: String = ReviewStatus.PENDING,
    /**
     * Normalized merchant identifier for rule lookup + "apply to similar".
     * Computed by MerchantKey.normalize(counterparty). Null when counterparty
     * is missing (e.g. some BoA balance updates).
     */
    val merchantKey: String? = null,
    /**
     * Stable identifier for the counterparty across name-spelling drift —
     * Telebirr phone like "2519****2219" or a bank A/C No. when the SMS
     * includes one. Persists across CBE/Telebirr transliteration variants
     * ("Beselam Melesew" vs "Beselam Melisew") and across longer/shorter
     * name renderings. Null when the SMS doesn't expose an identifier.
     */
    val counterpartyId: String? = null,
    /** First receipt / transaction URL extracted from the SMS body, if present. */
    val receiptLink: String? = null,
    val rawBody: String,
    /** When this record was inserted into the DB */
    val createdAt: Long = System.currentTimeMillis(),
    /** False until the row is confirmed in Supabase. */
    val isSynced: Boolean = false
)
