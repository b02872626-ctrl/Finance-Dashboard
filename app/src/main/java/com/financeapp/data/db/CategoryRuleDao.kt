package com.financeapp.data.db

import androidx.room.*
import com.financeapp.data.model.CategoryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<CategoryRuleEntity>>

    /** Snapshot for the parser-time predictor (no Flow). */
    @Query("SELECT * FROM category_rules")
    suspend fun snapshot(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE merchantKey = :key LIMIT 1")
    suspend fun getByKey(key: String): CategoryRuleEntity?

    /**
     * Looks up a rule by stable counterparty identifier (Telebirr phone, A/C No.).
     * Bridges name-spelling drift: a rule tagged with phone "2519****2219" matches
     * incoming SMS whether the bank wrote the name as "Beselam Melesew" or
     * "Beselam Melisew". Higher-priority than the name-based lookup.
     *
     * Ordering: highest matchCount wins when multiple rules share an identifier
     * (rare but possible if the user re-tags a merchant under a new category).
     */
    @Query("""
        SELECT * FROM category_rules
         WHERE identifier IS NOT NULL AND identifier = :identifier
         ORDER BY matchCount DESC
         LIMIT 1
    """)
    suspend fun findByIdentifier(identifier: String): CategoryRuleEntity?

    /**
     * Looks up a rule whose merchantKey either equals [key] OR is a word-prefix of [key]
     * (i.e. rule key + ' ' is a prefix of the incoming key).
     *
     * Example: incoming "BESELAM MELISEW BAYU" matches both:
     *   - exact rule "BESELAM MELISEW BAYU"
     *   - shorter rule "BESELAM MELISEW" (because "BESELAM MELISEW BAYU" starts with
     *     "BESELAM MELISEW ")
     *
     * The trailing-space guard prevents partial-token false matches: rule "BESELAM"
     * would NOT match "BESELAMITE" — the LIKE pattern "BESELAM %" requires a space.
     *
     * Ordering: longest rule key wins (most specific), then highest match count.
     */
    @Query("""
        SELECT * FROM category_rules
         WHERE merchantKey = :key
            OR :key LIKE merchantKey || ' %'
         ORDER BY LENGTH(merchantKey) DESC, matchCount DESC
         LIMIT 1
    """)
    suspend fun findByKeyOrPrefix(key: String): CategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("""
        UPDATE category_rules
           SET matchCount = matchCount + 1,
               updatedAt  = :now,
               isSynced   = 0
         WHERE merchantKey = :key
    """)
    suspend fun bumpMatchCount(key: String, now: Long = System.currentTimeMillis()): Int

    @Query("""
        UPDATE category_rules
           SET category   = :category,
               matchCount = matchCount + 1,
               updatedAt  = :now,
               isSynced   = 0
         WHERE merchantKey = :key
    """)
    suspend fun updateRule(
        key: String,
        category: String,
        now: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT * FROM category_rules WHERE isSynced = 0")
    suspend fun getUnsynced(): List<CategoryRuleEntity>

    @Query("UPDATE category_rules SET isSynced = 1 WHERE merchantKey IN (:keys)")
    suspend fun markSynced(keys: List<String>)

    @Query("DELETE FROM category_rules WHERE merchantKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM category_rules")
    suspend fun clear()
}
