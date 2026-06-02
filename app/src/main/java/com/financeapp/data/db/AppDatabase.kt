package com.financeapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financeapp.data.model.CategoryRuleEntity
import com.financeapp.data.model.ErrorLogEntity
import com.financeapp.data.model.RawSmsEntity
import com.financeapp.data.model.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RawSmsEntity::class,
        ErrorLogEntity::class,
        CategoryRuleEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_app.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `error_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `message` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_error_logs_createdAt` ON `error_logs` (`createdAt`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_isSynced` ON `transactions` (`isSynced`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `transactions` SET `isSynced` = 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptLink` TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `raw_sms` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_sms_isSynced` ON `raw_sms` (`isSynced`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `category` TEXT")
            }
        }

        /**
         * Daily Review schema.
         *  - 4 new columns on `transactions`: predictedCategory,
         *    categoryConfidence, reviewStatus (NOT NULL DEFAULT 'PENDING'),
         *    merchantKey.
         *  - Backfill: rows that already had a `category` set are marked
         *    CONFIRMED so they don't reappear in the queue.
         *  - 2 new indices: one for "how many pending today" and one for
         *    per-merchant lookup.
         *  - New `category_rules` table for learned merchant -> category map.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- transactions: new columns -----------------------------
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `predictedCategory` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `categoryConfidence` REAL")
                db.execSQL(
                    "ALTER TABLE `transactions` " +
                    "ADD COLUMN `reviewStatus` TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `merchantKey` TEXT")

                // Backfill: rows that already have a category were "confirmed"
                // by the old categorize flow — skip them in the new queue.
                db.execSQL(
                    """
                    UPDATE `transactions`
                       SET `reviewStatus` = 'CONFIRMED'
                     WHERE `category` IS NOT NULL
                       AND TRIM(`category`) <> ''
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_reviewStatus_dateTime` " +
                    "ON `transactions` (`reviewStatus`, `dateTime`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_merchantKey` " +
                    "ON `transactions` (`merchantKey`)"
                )

                // ---- category_rules ----------------------------------------
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `category_rules` (
                        `merchantKey` TEXT NOT NULL,
                        `category`    TEXT NOT NULL,
                        `matchCount`  INTEGER NOT NULL DEFAULT 1,
                        `createdAt`   INTEGER NOT NULL,
                        `updatedAt`   INTEGER NOT NULL,
                        `isSynced`    INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`merchantKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Phase 1 of merchant-identity stabilization:
         *  - transactions.counterpartyId  — phone (Telebirr "2519****XXXX") or
         *                                   A/C No. (banks) when the SMS exposes it.
         *  - category_rules.identifier    — same identifier bound to the rule, so
         *                                   the predictor can match by stable ID
         *                                   even when name spelling drifts.
         *
         * Backfill: scan existing rows whose counterparty contains a Telebirr
         * phone (251XXXXX**** form, possibly inside parens) and lift it into
         * counterpartyId. This retroactively unifies the "BESELAM MELESEW" rows
         * with future Telebirr SMS for the same phone.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `counterpartyId` TEXT")
                db.execSQL("ALTER TABLE `category_rules` ADD COLUMN `identifier` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_category_rules_identifier` " +
                    "ON `category_rules` (`identifier`)"
                )

                // Backfill counterpartyId from existing counterparty strings.
                // Captures any "2519****NNNN"-style token (digits + asterisks,
                // any order, at least 9 chars after the 251 prefix).
                val cursor = db.query(
                    "SELECT id, counterparty FROM transactions " +
                    "WHERE counterparty IS NOT NULL AND counterparty LIKE '%251%'"
                )
                val phoneRe = Regex("""(251[\d*]{8,12})""")
                val updates = mutableListOf<Pair<Long, String>>()
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val cp = c.getString(1) ?: continue
                        val match = phoneRe.find(cp)?.groupValues?.get(1) ?: continue
                        updates += id to match
                    }
                }
                for ((id, phone) in updates) {
                    db.execSQL(
                        "UPDATE transactions SET counterpartyId = ? WHERE id = ?",
                        arrayOf<Any>(phone, id)
                    )
                }
            }
        }
    }
}
