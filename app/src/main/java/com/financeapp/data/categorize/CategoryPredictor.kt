package com.financeapp.data.categorize

import com.financeapp.data.db.CategoryRuleDao
import com.financeapp.parsing.TransactionType
import kotlin.math.ln

/**
 * Predicts a category for a parsed transaction. Three sources, in priority:
 *  1. User-learned rule (i_category_rules / category_rules in Room)
 *  2. Seed regex pattern (SeedCategories)
 *  3. TransactionType fallback (CREDIT → Income, FEE → Fees)
 *
 * Confidence is 0..1 — fed to the UI to color the suggestion chip (mint =
 * high, amber = needs review).
 */
class CategoryPredictor(
    private val categoryRuleDao: CategoryRuleDao
) {

    enum class Source { USER_RULE, SEED, TYPE_FALLBACK, NONE }

    data class Prediction(
        val category: String?,
        val confidence: Double,
        val source: Source
    ) {
        companion object {
            val NONE = Prediction(null, 0.0, Source.NONE)
        }
    }

    /**
     * @param merchantKey    pre-normalized via MerchantKey.normalize
     * @param counterpartyId stable identifier (Telebirr phone / A/C No.) if known
     * @param type           TransactionType enum name from the parser
     */
    suspend fun predict(
        merchantKey: String?,
        counterpartyId: String?,
        type: String?
    ): Prediction {
        // 1a. Identifier match — highest confidence. Survives spelling drift
        //     (Telebirr "Melesew" vs CBE "Melisew") because both messages
        //     carrying the same phone match the same rule.
        if (!counterpartyId.isNullOrBlank()) {
            val byId = categoryRuleDao.findByIdentifier(counterpartyId)
            if (byId != null) {
                return Prediction(
                    category = byId.category,
                    confidence = confidenceFromMatchCount(byId.matchCount),
                    source = Source.USER_RULE
                )
            }
        }

        // 1b. Name-based lookup — prefix-tolerant so a rule for "BESELAM MELISEW"
        //     also catches incoming "BESELAM MELISEW BAYU".
        if (merchantKey != null) {
            val rule = categoryRuleDao.findByKeyOrPrefix(merchantKey)
            if (rule != null) {
                return Prediction(
                    category = rule.category,
                    confidence = confidenceFromMatchCount(rule.matchCount),
                    source = Source.USER_RULE
                )
            }
        }

        // 2. Seed pattern — high confidence but not user-confirmed.
        if (merchantKey != null) {
            val seedCat = SeedCategories.match(merchantKey)
            if (seedCat != null) {
                return Prediction(seedCat, SEED_CONFIDENCE, Source.SEED)
            }
        }

        // 3. Type fallback — coarse but useful (CREDIT → Income, etc.).
        val typeCat = typeFallback(type)
        if (typeCat != null) {
            return Prediction(typeCat, TYPE_CONFIDENCE, Source.TYPE_FALLBACK)
        }

        return Prediction.NONE
    }

    /**
     * Log-scaled confidence so the first confirmation already feels strong
     * and additional confirmations rapidly approach the ceiling.
     *   1 match  → 0.70
     *   5 matches → 0.80
     *  20 matches → 0.88
     *  50 matches → 0.95
     * 100 matches → 0.99 (cap)
     */
    private fun confidenceFromMatchCount(count: Int): Double {
        val raw = 0.65 + 0.075 * ln((count + 1).toDouble())
        return raw.coerceIn(0.0, 0.99)
    }

    private fun typeFallback(type: String?): String? = when (type) {
        TransactionType.CREDIT.name -> "Income"
        // We intentionally do NOT auto-tag TRANSFER_OUT/PAYMENT as "Transfer"
        // here — the user often sends money to friends (which they call
        // "Loan") or pays rent via transfer. The transfer flag-banner in
        // the queue handles the "actually a transfer" case explicitly.
        // Standalone fees usually come as enriched rows on the same SMS,
        // not their own TransactionType — no fallback needed.
        else -> null
    }

    companion object {
        const val SEED_CONFIDENCE = 0.80
        const val TYPE_CONFIDENCE = 0.55
        /** Below this, the queue renders the amber low-confidence banner. */
        const val LOW_CONFIDENCE_THRESHOLD = 0.50
    }
}
