package com.financeapp.data.repository

import com.financeapp.data.db.AppDatabase
import com.financeapp.data.db.CounterpartyAggregate
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.RawSmsEntity
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import com.financeapp.remote.RawSmsSyncPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository — single source of truth for all financial data.
 */
class TransactionRepository(db: AppDatabase) {

    private val txnDao = db.transactionDao()
    private val rawDao = db.rawSmsDao()

    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        txnDao.getAllTransactions()

    fun getRecentTransactions(limit: Int = 10, disabledSenders: Set<String> = emptySet()): Flow<List<TransactionEntity>> =
        txnDao.getRecentTransactions(limit).map { list ->
            if (disabledSenders.isEmpty()) list else list.filter { it.sender !in disabledSenders }
        }

    fun getFiltered(
        sender: String? = null,
        type: String? = null,
        fromMs: Long = 0L,
        toMs: Long = Long.MAX_VALUE,
        disabledSenders: Set<String> = emptySet()
    ): Flow<List<TransactionEntity>> = txnDao.getFiltered(sender, type, fromMs, toMs).map { list ->
        if (disabledSenders.isEmpty()) list else list.filter { it.sender !in disabledSenders }
    }

    fun getMonthlyAggregates(fromMs: Long, disabledSenders: Set<String> = emptySet()): Flow<List<MonthlyAggregate>> =
        txnDao.getMonthlyAggregates(fromMs).map { list ->
            if (disabledSenders.isEmpty()) list else list
        }

    suspend fun getLatestBySender(sender: String): TransactionEntity? =
        txnDao.getLatestBySender(sender)

    suspend fun getAllSenders(): List<String> = txnDao.getAllSenders()

    suspend fun getById(id: Long): TransactionEntity? = txnDao.getById(id)

    fun getTodayUncategorizedTransactions(nowMs: Long = System.currentTimeMillis()): Flow<List<TransactionEntity>> {
        val (fromMs, toMs) = todayBounds(nowMs)
        return txnDao.getUncategorizedBetween(fromMs, toMs)
    }

    suspend fun countTodayUncategorizedTransactions(nowMs: Long = System.currentTimeMillis()): Int {
        val (fromMs, toMs) = todayBounds(nowMs)
        return txnDao.countUncategorizedBetween(fromMs, toMs)
    }

    suspend fun updateCategory(id: Long, category: String?) {
        txnDao.updateCategory(
            id = id,
            category = category?.let(TransactionCategoryCatalog::normalize)?.ifBlank { null }
        )
    }

    suspend fun getOutgoingCounterpartyAggregate(counterparty: String): CounterpartyAggregate =
        txnDao.getOutgoingCounterpartyAggregate(counterparty)

    suspend fun updateCategoryForCounterparty(counterparty: String, category: String): Int =
        txnDao.updateCategoryForCounterparty(
            counterparty = counterparty,
            category = TransactionCategoryCatalog.normalize(category)
        )

    suspend fun clearAllData() {
        txnDao.clear()
        rawDao.clear()
    }

    suspend fun getUnsyncedTransactions(): List<TransactionEntity> =
        txnDao.getUnsyncedTransactions()

    suspend fun getUnsyncedRawMessages(): List<RawSmsSyncPayload> =
        rawDao.getUnsyncedRawSms().map { raw ->
            val bankName = SmsParserEngine.inferBankName(
                SmsMessage(
                    sender = raw.sender,
                    body = raw.body,
                    timestamp = raw.receivedAt
                )
            ).trim()

            RawSmsSyncPayload(
                localId = raw.id,
                senderName = raw.sender.trim(),
                bankName = if (bankName.isBlank()) raw.sender.trim() else bankName,
                rawMessage = raw.body
            )
        }

    suspend fun countUnsyncedTransactions(): Int =
        txnDao.countUnsynced()

    suspend fun countUnsyncedRawMessages(): Int =
        rawDao.countUnsynced()

    suspend fun countPendingCloudSyncItems(): Int =
        countUnsyncedTransactions() + countUnsyncedRawMessages()

    suspend fun markTransactionsSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        txnDao.markSynced(ids)
    }

    suspend fun markRawMessagesSynced(ids: List<Long>) {
        if (ids.isEmpty()) return
        rawDao.markSynced(ids)
    }

    data class IngestDetailedResult(
        val result: IngestResult,
        val inserted: TransactionEntity? = null
    )

    suspend fun ingestDetailed(sms: SmsMessage): IngestDetailedResult {
        if (!SmsParserEngine.isFinancialSms(sms.sender)) return IngestDetailedResult(IngestResult.IGNORED)

        val hash = ParserUtils.md5(sms.body.trim())
        val rawId = rawDao.insert(
            RawSmsEntity(
                sender = sms.sender,
                body = sms.body,
                bodyHash = hash,
                receivedAt = sms.timestamp
            )
        )

        if (rawId == -1L) {
            val existingTx = txnDao.findByRawHash(hash)
            if (existingTx != null && existingTx.type != TransactionType.UNKNOWN.name) {
                return IngestDetailedResult(IngestResult.DUPLICATE_BODY)
            }
        }

        val entity = SmsParserEngine.parse(sms) ?: return IngestDetailedResult(IngestResult.IGNORED)
        if (entity.type == TransactionType.UNKNOWN.name) return IngestDetailedResult(IngestResult.IGNORED)

        if (!entity.refNumber.isNullOrBlank()) {
            val existing = txnDao.findByRefNumber(entity.refNumber)
            if (existing != null) return IngestDetailedResult(IngestResult.DUPLICATE_REF)
        }

        val nearDup = txnDao.findNearDuplicate(entity.sender, entity.amount, entity.dateTime)
        if (nearDup != null) return IngestDetailedResult(IngestResult.DUPLICATE_NEAR)

        val insertedId = txnDao.insert(entity)
        return if (insertedId > 0) {
            IngestDetailedResult(IngestResult.INSERTED, inserted = entity.copy(id = insertedId))
        } else {
            IngestDetailedResult(IngestResult.DUPLICATE_BODY)
        }
    }

    suspend fun ingestAll(messages: List<SmsMessage>): Int {
        var count = 0
        for (sms in messages) {
            if (ingestDetailed(sms).result == IngestResult.INSERTED) count++
        }
        return count
    }

    suspend fun ingest(sms: SmsMessage): IngestResult = ingestDetailed(sms).result

    suspend fun ingestAllReturningInserted(messages: List<SmsMessage>): List<TransactionEntity> {
        val inserted = mutableListOf<TransactionEntity>()
        for (sms in messages) {
            val res = ingestDetailed(sms)
            res.inserted?.let(inserted::add)
        }
        return inserted
    }

    private fun todayBounds(nowMs: Long): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return start to (calendar.timeInMillis - 1)
    }
}

enum class IngestResult {
    INSERTED,
    DUPLICATE_BODY,
    DUPLICATE_REF,
    DUPLICATE_NEAR,
    IGNORED
}
