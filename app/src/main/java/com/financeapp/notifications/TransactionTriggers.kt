package com.financeapp.notifications

import android.content.Context
import com.financeapp.R
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.security.PinManager
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.util.KashDates

/**
 * Tier 1 triggers, run on the rows an SMS scan just inserted: low balance,
 * income landed and fee spikes. Never on history — the first-run scan, an
 * upgrade rescan, or any SMS older than a day — so nobody gets last year's
 * alerts.
 */
object TransactionTriggers {
    private const val DAY = 24 * 60 * 60 * 1000L

    suspend fun onInserted(
        context: Context,
        inserted: List<TransactionEntity>,
        batchIsHistorical: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val fresh = inserted.filterNot { NotificationRules.isHistorical(it.dateTime, nowMs, batchIsHistorical) }
        if (fresh.isEmpty()) return
        val dao = AppDatabase.getInstance(context).transactionDao()
        val prefs = NotificationPrefs(context)
        val res = context.resources
        val today = KashNotifier.dayKey(nowMs)

        // ── Balance below floor: once per account per day, on the newest row.
        val last30 = dao.snapshotBetween(nowMs - 30 * DAY, nowMs + DAY)
        val floor = NotificationRules.balanceFloor(prefs.balanceFloor, last30.filter { it.isSpending() }.sumOf { it.amount })
        if (floor > 0) {
            fresh.filter { it.balance != null }
                .groupBy { it.bankName to (it.accountNumber ?: "") }
                .forEach { (account, rows) ->
                    val latest = rows.maxBy { it.dateTime }
                    val balance = latest.balance ?: return@forEach
                    if (balance >= floor) return@forEach
                    KashNotifier.submit(context, NotifyCandidate(
                        trigger = Trigger.LOW_BALANCE, tier = Tier.IMMEDIATE, group = NotifyGroup.ALERTS,
                        dedupKey = "${Trigger.LOW_BALANCE}:${account.first}:${account.second}:$today",
                        title = context.getString(R.string.notif_low_balance_title, kashBank(latest.bankName).shortName),
                        text = context.getString(R.string.notif_low_balance_text, KashMoney.whole(balance), KashMoney.whole(floor)),
                        route = ROUTE_ACCOUNTS,
                    ), nowMs)
                }
        }

        // ── Income landed.
        val credits = fresh.filter { it.isIncome() }
        if (credits.isNotEmpty()) {
            val history = dao.snapshotBetween(nowMs - 120 * DAY, nowMs + DAY).filter { it.isIncome() }
            val (monthStart, monthEnd) = KashDates.monthBounds(nowMs, SettingsRepository(context).getCalendarSystem())
            for (tx in credits) {
                if (!NotificationRules.isIncomeLanded(tx, history)) continue
                val monthIn = history.filter { it.dateTime in monthStart until monthEnd }.sumOf { it.amount }
                val from = tx.counterparty?.takeIf { it.isNotBlank() }?.let(::shortName)
                KashNotifier.submit(context, NotifyCandidate(
                    trigger = Trigger.INCOME, tier = Tier.IMMEDIATE, group = NotifyGroup.ALERTS,
                    dedupKey = "${Trigger.INCOME}:${tx.id}",
                    title = context.getString(R.string.notif_income_title, KashMoney.whole(tx.amount)),
                    text = if (from != null) context.getString(R.string.notif_income_text_from, from, KashMoney.whole(monthIn))
                    else context.getString(R.string.notif_income_text, KashMoney.whole(monthIn)),
                    route = ROUTE_ANALYTICS,
                ), nowMs)
            }
        }

        // ── Fees: one transaction over X%, or the day's total over Y.
        for (tx in fresh) {
            if (!NotificationRules.isFeeSpike(tx, prefs.feePercent)) continue
            val fees = NotificationRules.feesOf(tx)
            KashNotifier.submit(context, NotifyCandidate(
                trigger = Trigger.FEE_SPIKE, tier = Tier.IMMEDIATE, group = NotifyGroup.ALERTS,
                dedupKey = "${Trigger.FEE_SPIKE}:${tx.id}",
                title = context.getString(R.string.notif_fee_spike_title, KashMoney.amount(fees)),
                text = context.getString(
                    R.string.notif_fee_spike_text,
                    (fees / tx.amount * 100).toInt(), KashMoney.whole(tx.amount), kashBank(tx.bankName).shortName,
                ),
                route = null,
            ), nowMs)
        }
        val todayRows = dao.snapshotBetween(KashDates.startOfDay(nowMs), nowMs + DAY)
        val withFees = todayRows.filter { NotificationRules.feesOf(it) > 0 }
        val dayFees = withFees.sumOf { NotificationRules.feesOf(it) }
        if (dayFees > prefs.feeDailyTotal && fresh.any { NotificationRules.feesOf(it) > 0 }) {
            KashNotifier.submit(context, NotifyCandidate(
                trigger = Trigger.FEE_DAY, tier = Tier.IMMEDIATE, group = NotifyGroup.ALERTS,
                dedupKey = "${Trigger.FEE_DAY}:$today",
                title = context.getString(R.string.notif_fee_day_title, KashMoney.amount(dayFees)),
                text = res.getQuantityString(R.plurals.notif_fee_day_text, withFees.size, withFees.size),
            ), nowMs)
        }
    }

    /** Security: the PIN lockout. Always allowed, no quiet hours, no switch. */
    suspend fun onPinLockout(context: Context, untilMs: Long) {
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.SECURITY_LOCKOUT, tier = Tier.IMMEDIATE, group = NotifyGroup.SECURITY,
            dedupKey = "${Trigger.SECURITY_LOCKOUT}:$untilMs",
            title = context.getString(R.string.notif_lockout_title),
            text = context.getString(R.string.notif_lockout_text, PinManager.MAX_ATTEMPTS),
            route = null,
        ))
    }

    /** "Abebe Girma Tadesse" → "Abebe G."; long merchant names are cut. */
    fun shortName(name: String): String {
        val parts = name.trim().split(Regex("\\s+"))
        val person = parts.size in 2..4 && parts.all { p -> p.all { it.isLetter() || it == '.' } }
        return if (person) "${parts[0].lowercase().replaceFirstChar { it.uppercase() }} ${parts[1].first().uppercaseChar()}."
        else name.trim().take(24)
    }

    const val ROUTE_REVIEW = com.financeapp.remote.SyncNotificationHelper.CATEGORY_ROUTE
    const val ROUTE_ANALYTICS = "analytics"
    const val ROUTE_ACCOUNTS = "accounts"
}
