package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a parsed financial transaction.
 * refNumber is indexed for fast duplicate lookup.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["refNumber"]),
        Index(value = ["sender", "amount", "dateTime"]),
        Index(value = ["isSynced"])
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
    /** User-selected category for budgeting/categorization flows. */
    val category: String? = null,
    /** First receipt / transaction URL extracted from the SMS body, if present. */
    val receiptLink: String? = null,
    val rawBody: String,
    /** When this record was inserted into the DB */
    val createdAt: Long = System.currentTimeMillis(),
    /** False until the row is confirmed in Supabase. */
    val isSynced: Boolean = false
)
