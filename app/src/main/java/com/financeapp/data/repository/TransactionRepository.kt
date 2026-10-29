package com.financeapp.data.repository

import androidx.room.withTransaction
import com.financeapp.data.categorize.CategoryPredictor
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.db.CounterpartyAggregate
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.data.model.RawSmsEntity
import com.financeapp.data.model.ReviewStatus
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
 *
 * Optional [settings] is used only for side-effect side-channels —
 * specifically pushing onto the "recent categories" list whenever the
 * user actually picks one. Kept optional so legacy callers + tests
 * don't need to thread it through.
 */
class TransactionRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository? = null
) {

    private val txnDao = db.transactionDao()
    private val rawDao = db.rawSmsDao()
    private val ruleDao = db.categoryRuleDao()
    private val predictor = CategoryPredictor(ruleDao)

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

    // ─────────────────────────────────────────────────────────────────────
    // Daily Review queue
    // ─────────────────────────────────────────────────────────────────────

    fun getTodayPendingReview(nowMs: Long = System.currentTimeMillis()): Flow<List<TransactionEntity>> {
        val (fromMs, toMs) = todayBounds(nowMs)
        return txnDao.getPendingBetween(fromMs, toMs)
    }

    /** Every PENDING row in history, newest first — for the "older" queue. */
    fun getAllPendingReview(): Flow<List<TransactionEntity>> =
        txnDao.getAllPendingReview()

    /** PENDING rows from before today — used to surface the backlog count on Home. */
    fun countOlderPendingReview(nowMs: Long = System.currentTimeMillis()): Flow<Int> {
        val (fromMs, _) = todayBounds(nowMs)
        return txnDao.countOlderPendingReview(fromMs)
    }

    suspend fun snapshotTodayPendingReview(nowMs: Long = System.currentTimeMillis()): List<TransactionEntity> {
        val (fromMs, toMs) = todayBounds(nowMs)
        return txnDao.snapshotPendingBetween(fromMs, toMs)
    }

    fun countTodayByReviewStatus(status: String, nowMs: Long = System.currentTimeMillis()): Flow<Int> {
        val (fromMs, toMs) = todayBounds(nowMs)
        return txnDao.countByStatusBetween(status, fromMs, toMs)
    }

    suspend fun latestTransactionMs(): Long? = txnDao.latestTransactionMs()

    suspend fun countTransactions(): Int = txnDao.count()

    /**
     * Confirm / change a single review. Decides automatically whether the
     * result is CONFIRMED (user accepted prediction) or CHANGED (user
     * picked something different) based on whether [category] == the row's
     * predictedCategory.
     *
     * When [applyToSimilar] is true, the user has explicitly opted into
     * recategorizing **every** transaction with the same merchant_key —
     * past + present + future. We overwrite historical rows (including
     * previously-CONFIRMED ones) because the popup made the scope explicit.
     */
    suspend fun applyReviewedCategory(
        id: Long,
        category: String,
        applyToSimilar: Boolean
    ) {
        val canonical = TransactionCategoryCatalog.normalize(category)
        val tx = txnDao.getById(id) ?: return
        val status =
            if (tx.predictedCategory == canonical) ReviewStatus.CONFIRMED
            else ReviewStatus.CHANGED
        // Push to the user's recents list so the picker's "Recent" row
        // reflects actual usage instead of a hardcoded starter trio.
        settings?.pushRecentCategory(canonical)

        if (applyToSimilar && !tx.merchantKey.isNullOrBlank()) {
            // Aggressive overwrite — every row with this merchant_key now
            // takes the new category. Then upsert/refresh the rule so all
            // future SMS from this merchant auto-categorize the same way.
            txnDao.applyCategoryToAllForMerchant(tx.merchantKey, canonical)
            val identifier = tx.counterpartyId?.takeIf { it.isNotBlank() }
            val existingRule = ruleDao.getByKey(tx.merchantKey)
            if (existingRule == null) {
                // First rule for this merchant — bind it to the stable
                // identifier when we have one (Telebirr phone, A/C No.) so
                // future spelling variants still match.
                ruleDao.upsert(
                    com.financeapp.data.model.CategoryRuleEntity(
                        merchantKey = tx.merchantKey,
                        category = canonical,
                        identifier = identifier
                    )
                )
            } else if (existingRule.category == canonical) {
                ruleDao.bumpMatchCount(tx.merchantKey)
                // Late-bind identifier if the original rule was name-only and
                // we've since seen the same merchant via Telebirr (phone known).
                if (existingRule.identifier == null && identifier != null) {
                    ruleDao.upsert(existingRule.copy(identifier = identifier, isSynced = false))
                }
            } else {
                ruleDao.updateRule(tx.merchantKey, canonical)
                if (identifier != null) {
                    val refreshed = ruleDao.getByKey(tx.merchantKey)
                    if (refreshed != null && refreshed.identifier != identifier) {
                        ruleDao.upsert(refreshed.copy(identifier = identifier, isSynced = false))
                    }
                }
            }
        } else {
            // Single-row update — leave the rule and historical rows alone.
            txnDao.applyReviewedCategory(id, canonical, status)
        }
    }

    /** Count of ALL transactions sharing a merchant_key (any reviewStatus). */
    suspend fun countTransactionsByMerchantKey(merchantKey: String): Int =
        txnDao.countTransactionsByMerchantKey(merchantKey)

    /** Newest rows sharing a merchant_key, for the "Apply to all?" dialog. */
    suspend fun getByMerchantKey(merchantKey: String, limit: Int = 4): List<TransactionEntity> =
        txnDao.getByMerchantKey(merchantKey, limit)

    /**
     * One-shot backfill: pre-migration rows have merchantKey = null because
     * the column was added without recomputing keys from counterparty. This
     * walks rows missing a key and fills them in using MerchantKey.normalize.
     *
     * Wrapped in a single Room transaction so writes commit together —
     * 3,000+ individual UPDATEs become one batched flush, ~50× faster.
     * Returns the number of rows updated. Safe to call repeatedly — only
     * touches rows where merchantKey is still null.
     */
    suspend fun backfillMissingMerchantKeys(): Int {
        val rows = txnDao.getRowsMissingMerchantKey()
        if (rows.isEmpty()) return 0
        var updated = 0
        db.withTransaction {
            for (row in rows) {
                val key = com.financeapp.data.categorize.MerchantKey
                    .normalize(row.counterparty) ?: continue
                txnDao.setMerchantKey(row.id, key)
                updated++
            }
        }
        return updated
    }

    /**
     * Insert a transaction the user typed in by hand (cash spend, missed
     * SMS, transfer the bank didn't text about, etc.). Bypasses the Daily
     * Review queue — the user already knows what this is — and lands as
     * CONFIRMED so it sums into balances + analytics immediately.
     *
     * The row's [TransactionEntity.sender] is "manual" and the rawBody is
     * a synthetic single-line string for traceability. A pseudo-random
     * refNumber is generated so the dedup index sees a unique key and the
     * row can be synced upward like any parsed row.
     *
     * @param type         CREDIT / DEBIT / TRANSFER_OUT / etc. — TransactionType.name
     * @param amount       positive value; sign comes from [type]
     * @param counterparty who paid / who received — used to derive merchantKey
     * @param bankName     "Cash", "Wallet", or a real bank name
     * @param dateTimeMs   epoch millis the user picked
     * @param category     optional — when set, also writes/refreshes a rule
     * @param note         optional free-text appended to rawBody
     * @return the row's autogenerated id
     */
    suspend fun insertManual(
        type: String,
        amount: Double,
        counterparty: String,
        bankName: String,
        dateTimeMs: Long,
        category: String? = null,
        note: String? = null
    ): Long {
        val canonicalCat = category?.let(TransactionCategoryCatalog::normalize)?.ifBlank { null }
        val merchantKey = com.financeapp.data.categorize.MerchantKey
            .normalize(counterparty.trim())
        val raw = buildString {
            append("[MANUAL ENTRY] ")
            append(type).append(" • ")
            append("ETB ").append(amount).append(" • ")
            append(counterparty.trim())
            if (!note.isNullOrBlank()) {
                append(" • Note: ").append(note.trim())
            }
        }
        val entity = TransactionEntity(
            sender        = "manual",
            bankName      = bankName.trim().ifBlank { "Cash" },
            type          = type,
            amount        = amount,
            balance       = null,
            counterparty  = counterparty.trim(),
            accountNumber = null,
            refNumber     = "manual-${java.util.UUID.randomUUID()}",
            dateTime      = dateTimeMs,
            serviceCharge = null,
            vat           = null,
            disasterFund  = null,
            totalCharged  = null,
            currency      = "ETB",
            category      = canonicalCat,
            predictedCategory = canonicalCat,
            categoryConfidence = canonicalCat?.let { 1.0 },
            reviewStatus  = ReviewStatus.CONFIRMED,
            merchantKey   = merchantKey,
            counterpartyId = null,
            rawBody       = raw
        )
        val id = txnDao.insert(entity)
        // Push to recents whenever the user gives us a category on a
        // manual entry — same UX surface as a Daily Review pick.
        if (canonicalCat != null) settings?.pushRecentCategory(canonicalCat)

        // Teach the categorizer so future manual entries for the same
        // counterparty (or a parsed SMS arriving later for that name) auto-
        // categorize.
        if (id > 0 && canonicalCat != null && !merchantKey.isNullOrBlank()) {
            val existing = ruleDao.getByKey(merchantKey)
            if (existing == null) {
                ruleDao.upsert(
                    com.financeapp.data.model.CategoryRuleEntity(
                        merchantKey = merchantKey,
                        category = canonicalCat
                    )
                )
            } else if (existing.category == canonicalCat) {
                ruleDao.bumpMatchCount(merchantKey)
            }
        }
        return id
    }

    suspend fun skipReview(id: Long) {
        txnDao.updateReviewStatus(id, ReviewStatus.SKIPPED)
    }

    suspend fun markNotATransaction(id: Long) {
        txnDao.updateReviewStatus(id, ReviewStatus.NOT_A_TXN)
    }

    /** Reset a review row to PENDING — used by the Undo snackbar. */
    suspend fun undoReview(id: Long) {
        txnDao.updateReviewStatus(id, ReviewStatus.PENDING)
    }

    suspend fun clearAllData() {
        txnDao.clear()
        rawDao.clear()
        db.sentNotificationDao().clear()
    }

    suspend fun getUnsyncedTransactions(): List<TransactionEntity> =
        txnDao.getUnsyncedTransactions()

    /**
     * Bank SMS waiting to upload. Ones the parser ignores (one-time codes,
     * promos, notices) never leave the phone — they are marked synced here
     * instead. A new RULES_VERSION re-queues everything (resyncRawOnce), so
     * a message the new rules can read is uploaded then.
     */
    suspend fun getUnsyncedRawMessages(): List<RawSmsSyncPayload> {
        val (ignored, uploads) = rawDao.getUnsyncedRawSms()
            .map { it to rawParseStatus(SmsMessage(sender = it.sender, body = it.body, timestamp = it.receivedAt)) }
            .partition { (_, status) -> status == RAW_IGNORED }
        markRawMessagesSynced(ignored.map { (raw, _) -> raw.id })
        return uploads.map { (raw, parseStatus) ->
            val sms = SmsMessage(
                sender = raw.sender,
                body = raw.body,
                timestamp = raw.receivedAt
            )
            val bankName = SmsParserEngine.inferBankName(sms).trim()

            RawSmsSyncPayload(
                localId = raw.id,
                senderName = raw.sender.trim(),
                bankName = if (bankName.isBlank()) raw.sender.trim() else bankName,
                rawMessage = raw.body,
                receivedAt = raw.receivedAt,
                parseStatus = parseStatus
            )
        }
    }

    companion object {
        const val RAW_IGNORED = "IGNORED"

        /**
         * What the parser makes of a bank SMS today, for the admin parse-rate
         * view: IGNORED = ignore pattern (OTP, promo) or not money-shaped,
         * UNPARSED = no parser matched (a broken or new template, or a
         * capture-only bank), PARSED = a transaction.
         */
        fun rawParseStatus(sms: SmsMessage): String {
            val parsed = SmsParserEngine.parse(sms)
            return when {
                parsed == null                                -> RAW_IGNORED
                parsed.type == TransactionType.UNKNOWN.name   -> "UNPARSED"
                else                                          -> "PARSED"
            }
        }
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

    /** Pull-sync surface: only rows with no pending local edits. */
    suspend fun getSyncedTransactions(): List<TransactionEntity> =
        txnDao.getSyncedTransactions()

    /**
     * Adopt cloud-side category/review-status for a local row. Used by the
     * Web→Mobile pull-sync. Returns 1 if the row was updated, 0 otherwise.
     */
    suspend fun applyRemoteState(
        id: Long,
        category: String?,
        reviewStatus: String,
        predictedCategory: String?,
        categoryConfidence: Double?,
        merchantKey: String?
    ): Int = txnDao.applyRemoteState(
        id = id,
        category = category,
        reviewStatus = reviewStatus,
        predictedCategory = predictedCategory,
        categoryConfidence = categoryConfidence,
        merchantKey = merchantKey
    )

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
        // Banks the user switched off (Choose sources / Banks Kash follows)
        // are not read any more.
        if (settings?.getDisabledSenders()?.contains(sms.sender) == true) return IngestDetailedResult(IngestResult.IGNORED)

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

        val parsed = SmsParserEngine.parse(sms) ?: return IngestDetailedResult(IngestResult.IGNORED)
        if (parsed.type == TransactionType.UNKNOWN.name) return IngestDetailedResult(IngestResult.IGNORED)

        if (!parsed.refNumber.isNullOrBlank()) {
            val existing = txnDao.findByRefNumber(parsed.refNumber)
            if (existing != null) return IngestDetailedResult(IngestResult.DUPLICATE_REF)
        }

        val nearDup = txnDao.findNearDuplicate(parsed.sender, parsed.amount, parsed.dateTime)
        if (nearDup != null) return IngestDetailedResult(IngestResult.DUPLICATE_NEAR)

        // Daily Review: predict category at parse time so the queue can show
        // a suggestion immediately.
        //
        // Auto-confirm path: when the user has explicitly taught us "Legacy
        // Ata = Loan" via the Apply-to-all popup (i.e. a row in
        // i_category_rules with that merchant_key), the predictor returns
        // source = USER_RULE. There's no value in re-asking the user — they
        // already gave us the answer. We skip Daily Review entirely for
        // these rows and stamp them as CONFIRMED at insert time.
        //
        // Seed-matches (KALDIS → Coffee) and type-fallbacks (CREDIT → Income)
        // are NOT auto-confirmed — those are app-side guesses, not user
        // decisions, so they still surface for confirmation. A single swipe-
        // right on a seed-matched row upgrades it to a user rule, after
        // which future SMS from that merchant auto-confirm too.
        val prediction = predictor.predict(parsed.merchantKey, parsed.counterpartyId, parsed.type)
        val autoConfirm = prediction.source == CategoryPredictor.Source.USER_RULE
                          && prediction.category != null
        val entity = parsed.copy(
            predictedCategory  = prediction.category,
            categoryConfidence = prediction.confidence.takeIf { it > 0.0 },
            category           = if (autoConfirm) prediction.category else null,
            reviewStatus       = if (autoConfirm) ReviewStatus.CONFIRMED
                                 else ReviewStatus.PENDING
        )

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
