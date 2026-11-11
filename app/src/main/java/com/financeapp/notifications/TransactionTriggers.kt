package com.financeapp.notifications

import android.content.Context
import com.financeapp.R
import com.financeapp.data.accounts.AccountGrouping
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
        val name = firstName(context)
        val k = NotifyText::k

        // ── Low balance: per account, only when it drops below its floor.
        val last30 = dao.snapshotBetween(nowMs - 30 * DAY, nowMs + DAY)
        fresh.filter { it.balance != null }
            .groupBy { it.bankName to (AccountGrouping.accountSuffix(it.accountNumber) ?: "") }
            .forEach { (account, rows) ->
                val latest = rows.maxBy { it.dateTime }
                val balance = latest.balance ?: return@forEach
                val accountRows = last30.filter { sameAccount(it, account) }
                val spend30 = accountRows.filter { it.isSpending() }.sumOf { it.amount }
                val floor = NotificationRules.balanceFloor(prefs.balanceFloor, spend30)
                val previous = accountRows
                    .filter { it.balance != null && it.dateTime < latest.dateTime && it.id != latest.id }
                    .maxByOrNull { it.dateTime }?.balance
                if (!NotificationRules.isLowBalanceDrop(balance, previous, floor)) return@forEach
                val days = NotificationRules.daysLeft(balance, spend30)
                KashNotifier.submit(context, NotifyCandidate(
                    trigger = Trigger.LOW_BALANCE, tier = Tier.IMMEDIATE, group = NotifyGroup.ALERTS,
                    dedupKey = "${Trigger.LOW_BALANCE}:${account.first}:${account.second}:$today",
                    title = context.getString(R.string.notif_low_balance_title, hey(name), kashBank(latest.bankName).shortName),
                    text = when {
                        prefs.balanceFloor != null -> context.getString(
                            R.string.notif_low_balance_text_set, k(balance), k(floor))
                        days == null || days < 1 -> context.getString(R.string.notif_low_balance_text_today, k(balance))
                        else -> res.getQuantityString(R.plurals.notif_low_balance_text_days, days, k(balance), days)
                    },
                    route = accountRoute(latest.bankName, latest.accountNumber),
                ), nowMs)
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
                    title = if (name != null) context.getString(R.string.notif_income_title, name, k(tx.amount))
                    else context.getString(R.string.notif_income_title_noname, k(tx.amount)),
                    text = if (from != null) context.getString(R.string.notif_income_text_from, from, k(monthIn))
                    else context.getString(R.string.notif_income_text, k(monthIn)),
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
                title = context.getString(R.string.notif_fee_spike_title, kashBank(tx.bankName).shortName, k(fees)),
                text = context.getString(R.string.notif_fee_spike_text, (fees / tx.amount * 100).toInt(), k(tx.amount)),
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
                title = context.getString(R.string.notif_fee_day_title, k(dayFees)),
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

    /** The user's first name (Settings, or the sign-in account), or null. */
    fun firstName(context: Context): String? = NotifyText.firstName(SettingsRepository(context).getUserName())

    /** "hey Dawi," / "hey," */
    fun hey(name: String?): String = if (name != null) "hey $name," else "hey,"

    private fun sameAccount(tx: TransactionEntity, account: Pair<String, String>): Boolean {
        if (tx.bankName != account.first) return false
        val suffix = AccountGrouping.accountSuffix(tx.accountNumber) ?: return account.second.isEmpty()
        return account.second.isNotEmpty() && AccountGrouping.sameSuffix(suffix, account.second)
    }

    /** "Abebe Girma Tadesse" → "Abebe G."; long merchant names are cut. */
    fun shortName(name: String): String {
        val parts = name.trim().split(Regex("\\s+"))
        val person = parts.size in 2..4 && parts.all { p -> p.all { it.isLetter() || it == '.' } }
        return if (person) "${parts[0].lowercase().replaceFirstChar { it.uppercase() }} ${parts[1].first().uppercaseChar()}."
        else name.trim().take(24)
    }

    const val ROUTE_REVIEW = "categorize_today"
    const val ROUTE_ANALYTICS = "analytics"
    /** That account's history (Home has no Accounts tab since NAH-28). */
    fun accountRoute(bankName: String, accountNumber: String?): String =
        "bank_analytics/" + android.net.Uri.encode(bankName) +
            (com.financeapp.data.accounts.AccountGrouping.accountSuffix(accountNumber)?.let { "?account=$it" } ?: "")
}
