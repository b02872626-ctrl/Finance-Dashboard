package com.financeapp.data.db

import androidx.room.*
import com.financeapp.data.model.TransactionEntity

data class MonthlyAggregate(
    val month: String,
    val totalIncome: Double,
    val totalExpense: Double
)

data class CounterpartyAggregate(
    val transactionCount: Int,
    val totalAmount: Double
)

/** Row projection for the merchant-key backfill — id + raw counterparty. */
data class MerchantKeyBackfillRow(
    val id: Long,
    val counterparty: String?
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC")
    fun getAllTransactions(): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT DISTINCT sender FROM transactions")
    suspend fun getAllSenders(): List<String>

    @Query("SELECT * FROM transactions WHERE sender = :sender ORDER BY dateTime DESC LIMIT 1")
    suspend fun getLatestBySender(sender: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE refNumber = :ref LIMIT 1")
    suspend fun findByRefNumber(ref: String): TransactionEntity?

    /** Find a transaction that was parsed from a specific raw SMS body hash */
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN raw_sms r ON r.sender = t.sender AND r.body = t.rawBody
        WHERE r.bodyHash = :hash
        LIMIT 1
    """)
    suspend fun findByRawHash(hash: String): TransactionEntity?

    /** Near-duplicate check: same sender+amount within ±5 seconds */
    @Query("""
        SELECT * FROM transactions
        WHERE sender = :sender
          AND ABS(amount - :amount) < 0.01
          AND ABS(dateTime - :dateTime) < 5000
        LIMIT 1
    """)
    suspend fun findNearDuplicate(sender: String, amount: Double, dateTime: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE (:sender IS NULL OR sender = :sender)
          AND (:type IS NULL OR type = :type)
          AND dateTime >= :fromMs AND dateTime <= :toMs
        ORDER BY dateTime DESC
    """)
    fun getFiltered(
        sender: String?,
        type: String?,
        fromMs: Long,
        toMs: Long
    ): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE dateTime >= :fromMs
          AND dateTime <= :toMs
          AND (category IS NULL OR TRIM(category) = '')
        ORDER BY dateTime DESC
    """)
    fun getUncategorizedBetween(
        fromMs: Long,
        toMs: Long
    ): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE dateTime >= :fromMs
          AND dateTime <= :toMs
          AND (category IS NULL OR TRIM(category) = '')
    """)
    suspend fun countUncategorizedBetween(fromMs: Long, toMs: Long): Int

    @Query("""
        SELECT
            COUNT(*) AS transactionCount,
            COALESCE(SUM(amount), 0.0) AS totalAmount
        FROM transactions
        WHERE counterparty IS NOT NULL
          AND LOWER(TRIM(counterparty)) = LOWER(TRIM(:counterparty))
          AND type IN ('DEBIT', 'TRANSFER_OUT', 'PAYMENT')
    """)
    suspend fun getOutgoingCounterpartyAggregate(counterparty: String): CounterpartyAggregate

    @Query("""
        SELECT
            strftime('%Y-%m', datetime(dateTime/1000, 'unixepoch')) AS month,
            SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0.0 END) AS totalIncome,
            SUM(CASE WHEN type IN ('DEBIT','TRANSFER_OUT','PAYMENT') THEN amount ELSE 0.0 END) AS totalExpense
        FROM transactions
        WHERE dateTime >= :fromMs
        GROUP BY month
        ORDER BY month ASC
    """)
    fun getMonthlyAggregates(fromMs: Long): kotlinx.coroutines.flow.Flow<List<MonthlyAggregate>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity): Long

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedTransactions(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("UPDATE transactions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE transactions SET category = :category, isSynced = 0 WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String?)

    @Query("""
        UPDATE transactions
        SET category = :category,
            isSynced = 0
        WHERE counterparty IS NOT NULL
          AND LOWER(TRIM(counterparty)) = LOWER(TRIM(:counterparty))
          AND type IN ('DEBIT', 'TRANSFER_OUT', 'PAYMENT')
    """)
    suspend fun updateCategoryForCounterparty(counterparty: String, category: String): Int

    // =========================================================================
    // Daily Review queue queries.
    // =========================================================================

    /** Today's pending queue, ordered chronologically for the swipe stream. */
    @Query("""
        SELECT * FROM transactions
         WHERE dateTime >= :fromMs AND dateTime <= :toMs
           AND reviewStatus = 'PENDING'
         ORDER BY dateTime ASC
    """)
    fun getPendingBetween(
        fromMs: Long,
        toMs: Long
    ): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    /**
     * ALL pending rows across history, newest-first. Used by the "review
     * older transactions" path so the user can chip away at the backlog.
     */
    @Query("""
        SELECT * FROM transactions
         WHERE reviewStatus = 'PENDING'
         ORDER BY dateTime DESC
    """)
    fun getAllPendingReview(): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    /** Count of PENDING rows older than today — drives the Home "older" link. */
    @Query("""
        SELECT COUNT(*) FROM transactions
         WHERE reviewStatus = 'PENDING'
           AND dateTime < :startOfTodayMs
    """)
    fun countOlderPendingReview(
        startOfTodayMs: Long
    ): kotlinx.coroutines.flow.Flow<Int>

    /** Snapshot version for one-shot reads. */
    @Query("""
        SELECT * FROM transactions
         WHERE dateTime >= :fromMs AND dateTime <= :toMs
           AND reviewStatus = 'PENDING'
         ORDER BY dateTime ASC
    """)
    suspend fun snapshotPendingBetween(fromMs: Long, toMs: Long): List<TransactionEntity>

    @Query("""
        SELECT COUNT(*) FROM transactions
         WHERE dateTime >= :fromMs AND dateTime <= :toMs
           AND reviewStatus = :status
    """)
    fun countByStatusBetween(
        status: String,
        fromMs: Long,
        toMs: Long
    ): kotlinx.coroutines.flow.Flow<Int>

    /** Last SMS we ingested (any status), used by the "empty" home card. */
    @Query("SELECT MAX(dateTime) FROM transactions")
    suspend fun latestTransactionMs(): Long?

    @Query("""
        UPDATE transactions
           SET reviewStatus = :status,
               isSynced     = 0
         WHERE id = :id
    """)
    suspend fun updateReviewStatus(id: Long, status: String)

    /**
     * Confirm or change a single txn's category. [status] should be either
     * CONFIRMED (user accepted prediction) or CHANGED (user picked different).
     */
    @Query("""
        UPDATE transactions
           SET category      = :category,
               reviewStatus  = :status,
               isSynced      = 0
         WHERE id = :id
    """)
    suspend fun applyReviewedCategory(id: Long, category: String, status: String)

    /**
     * "Apply to similar" backfill — re-categorize every txn whose
     * merchantKey matches. Caller decides whether status should be CHANGED
     * (forward-only) or CONFIRMED (first-time backfill: rule didn't exist
     * yet so historical rows are getting their first real category).
     */
    @Query("""
        UPDATE transactions
           SET category      = :category,
               reviewStatus  = :status,
               isSynced      = 0
         WHERE merchantKey = :merchantKey
    """)
    suspend fun applyCategoryToMerchant(
        merchantKey: String,
        category: String,
        status: String
    ): Int

    /** Backfill only the rows that haven't been touched yet (first-time apply). */
    @Query("""
        UPDATE transactions
           SET category      = :category,
               reviewStatus  = 'CONFIRMED',
               isSynced      = 0
         WHERE merchantKey   = :merchantKey
           AND reviewStatus  = 'PENDING'
           AND (category IS NULL OR TRIM(category) = '')
    """)
    suspend fun backfillPendingForMerchant(merchantKey: String, category: String): Int

    /**
     * Aggressive overwrite: applies [category] to EVERY row with the
     * given [merchantKey], regardless of its current reviewStatus or
     * existing category. Used by the "Apply to all transactions for this
     * name" popup so the user can re-categorize historical rows too.
     */
    @Query("""
        UPDATE transactions
           SET category      = :category,
               reviewStatus  = 'CHANGED',
               isSynced      = 0
         WHERE merchantKey   = :merchantKey
    """)
    suspend fun applyCategoryToAllForMerchant(merchantKey: String, category: String): Int

    /** Count of ALL transactions (any status) sharing a merchant_key. */
    @Query("SELECT COUNT(*) FROM transactions WHERE merchantKey = :merchantKey")
    suspend fun countTransactionsByMerchantKey(merchantKey: String): Int

    /**
     * Pre-migration rows are missing merchantKey. Return them so we can
     * compute the key from counterparty + write it back. Counterparty is
     * the only stable text we have to build the key from.
     */
    @Query("""
        SELECT id, counterparty FROM transactions
         WHERE merchantKey IS NULL
           AND counterparty IS NOT NULL
           AND TRIM(counterparty) <> ''
        LIMIT :limit
    """)
    suspend fun getRowsMissingMerchantKey(limit: Int = 5000): List<MerchantKeyBackfillRow>

    @Query("UPDATE transactions SET merchantKey = :key WHERE id = :id")
    suspend fun setMerchantKey(id: Long, key: String)

    /** Set/refresh the predicted category + confidence — used after parse. */
    @Query("""
        UPDATE transactions
           SET predictedCategory  = :predicted,
               categoryConfidence = :confidence,
               merchantKey        = :merchantKey,
               isSynced           = 0
         WHERE id = :id
    """)
    suspend fun updatePrediction(
        id: Long,
        predicted: String?,
        confidence: Double?,
        merchantKey: String?
    )

    /**
     * Pull-sync helper: only rows that don't have pending local edits.
     * Web→Mobile sync must not overwrite changes the user just made on
     * mobile but hasn't yet uploaded.
     */
    @Query("SELECT * FROM transactions WHERE isSynced = 1")
    suspend fun getSyncedTransactions(): List<TransactionEntity>

    /**
     * Apply a remote category/review-status update to a local row. Only
     * touches the sync-relevant fields and leaves isSynced = 1 (since the
     * cloud is now authoritative for this state).
     */
    @Query("""
        UPDATE transactions
           SET category           = :category,
               reviewStatus       = :reviewStatus,
               predictedCategory  = COALESCE(:predictedCategory,  predictedCategory),
               categoryConfidence = COALESCE(:categoryConfidence, categoryConfidence),
               merchantKey        = COALESCE(:merchantKey,        merchantKey),
               isSynced           = 1
         WHERE id = :id
    """)
    suspend fun applyRemoteState(
        id: Long,
        category: String?,
        reviewStatus: String,
        predictedCategory: String?,
        categoryConfidence: Double?,
        merchantKey: String?
    ): Int

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
