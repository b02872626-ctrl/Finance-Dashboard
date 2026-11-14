package com.financeapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financeapp.data.model.CategoryRuleEntity
import com.financeapp.data.model.ErrorLogEntity
import com.financeapp.data.model.GoalContributionEntity
import com.financeapp.data.model.GoalDeletionEntity
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.RawSmsEntity
import com.financeapp.data.model.SentNotificationEntity
import com.financeapp.data.model.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        RawSmsEntity::class,
        ErrorLogEntity::class,
        CategoryRuleEntity::class,
        SentNotificationEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        GoalDeletionEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun sentNotificationDao(): SentNotificationDao
    abstract fun goalDao(): GoalDao

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
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .addMigrations(MIGRATION_11_12)
                    .addMigrations(MIGRATION_12_13)
                    .addMigrations(MIGRATION_13_14)
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
        /**
         * Testers filed self-transfers under hand-made categories ("internal",
         * "Internal transfer") that the totals didn't understand. Move them to
         * the built-in Spending.OWN_ACCOUNTS so they drop out of spending, and
         * mark the rows unsynced so the web dashboard gets the new category.
         * No schema change.
         */
        /** Notification log for dedup, caps and the evening digest. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sent_notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dedupKey` TEXT NOT NULL,
                        `triggerType` TEXT NOT NULL,
                        `tier` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `route` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `sentAt` INTEGER,
                        `shownAs` TEXT,
                        `digestKey` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sent_notifications_dedupKey` ON `sent_notifications` (`dedupKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sent_notifications_tier_sentAt` ON `sent_notifications` (`tier`, `sentAt`)")
            }
        }

        /** Hidden accounts: rows of an account the user hid drop out of every read. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Goals section: savings goals and the saves logged by hand. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goals` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `emoji` TEXT,
                        `type` TEXT NOT NULL,
                        `targetAmount` REAL,
                        `targetMonths` INTEGER,
                        `deadline` INTEGER,
                        `priority` INTEGER NOT NULL,
                        `note` TEXT,
                        `status` TEXT NOT NULL,
                        `linkedAccountKey` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `achievedAt` INTEGER,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_status_priority` ON `goals` (`status`, `priority`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_contributions` (
                        `id` TEXT NOT NULL,
                        `goalId` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `contributedAt` INTEGER NOT NULL,
                        `note` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `isSynced` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId_contributedAt` " +
                        "ON `goal_contributions` (`goalId`, `contributedAt`)"
                )
            }
        }

        /** Goals sync: deletes made on the phone wait here for the cloud (GoalDeletionEntity). */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_deletions` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val legacy = com.financeapp.data.model.Spending.LEGACY_OWN_ACCOUNT_NAMES
                    .joinToString(",") { "'" + it + "'" }
                val own = com.financeapp.data.model.Spending.OWN_ACCOUNTS
                db.execSQL(
                    "UPDATE transactions SET category = ?, isSynced = 0 " +
                        "WHERE LOWER(TRIM(category)) IN ($legacy)",
                    arrayOf(own)
                )
                db.execSQL(
                    "UPDATE category_rules SET category = ?, isSynced = 0 " +
                        "WHERE LOWER(TRIM(category)) IN ($legacy)",
                    arrayOf(own)
                )
            }
        }

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
