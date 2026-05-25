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

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
