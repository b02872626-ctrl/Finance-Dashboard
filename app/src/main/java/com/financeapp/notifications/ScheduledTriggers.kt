package com.financeapp.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.financeapp.R
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isSpending
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.util.KashDates
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Tier 2 (evening digest), Tier 3 (scheduled) and Tier 4 (health) triggers.
 * [DigestWorker] runs [atDigestTime] once a day; [PermissionHealthWorker]
 * runs [checkSmsAccess] a few times a day.
 */
object ScheduledTriggers {
    private const val DAY = 24 * 60 * 60 * 1000L

    suspend fun atDigestTime(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val db = AppDatabase.getInstance(context)
        val prefs = NotificationPrefs(context)
        val cal = SettingsRepository(context).getCalendarSystem()
        val today = KashNotifier.dayKey(nowMs)
        val dayStart = KashDates.startOfDay(nowMs)
        // Insights need a working pipeline: no summaries while SMS access is off.
        val pipelineOk = hasSmsAccess(context)
        // First run: start the review clock now, not at 1970.
        if (prefs.lastReviewSessionMs == 0L) prefs.lastReviewSessionMs = nowMs

        // ── Tier 2: Daily Review ready, headlined by the least-sure item.
        val pendingToday = db.transactionDao().snapshotPendingBetween(dayStart, dayStart + DAY)
        if (pendingToday.isNotEmpty()) {
            val unsure = pendingToday.minBy { it.categoryConfidence ?: 0.0 }
            val res = context.resources
            val title = if (unsure.predictedCategory != null && unsure.counterparty != null) context.getString(
                R.string.notif_review_question,
                KashMoney.whole(unsure.amount), TransactionTriggers.shortName(unsure.counterparty), unsure.predictedCategory,
            ) else res.getQuantityString(R.plurals.notif_review_title, pendingToday.size, pendingToday.size)
            KashNotifier.submit(context, NotifyCandidate(
                trigger = Trigger.REVIEW_READY, tier = Tier.DIGEST, group = NotifyGroup.REVIEW,
                dedupKey = "${Trigger.REVIEW_READY}:$today",
                title = title,
                text = res.getQuantityString(R.plurals.notif_review_text, pendingToday.size, pendingToday.size),
                route = TransactionTriggers.ROUTE_REVIEW,
            ), nowMs)
        }

        // ── Tier 2: messages from banks Kash can't read yet.
        for (c in db.rawSmsDao().capturedOnlySince(dayStart)) {
            val bank = kashBank(c.sender).shortName
            KashNotifier.submit(context, NotifyCandidate(
                trigger = Trigger.CAPTURE_ONLY, tier = Tier.DIGEST, group = NotifyGroup.HEALTH,
                dedupKey = "${Trigger.CAPTURE_ONLY}:${c.sender}:$today",
                title = context.resources.getQuantityString(R.plurals.notif_capture_title, c.count, c.count, bank),
                text = context.getString(R.string.notif_capture_text, bank),
            ), nowMs)
        }

        if (pipelineOk) {
            if (isSunday(nowMs)) weekly(context, nowMs, today)
            if (isLastDayOfMonth(nowMs, cal)) monthEnd(context, nowMs, cal)
        }
        backlog(context, prefs, nowMs)
        reengage(context, prefs, nowMs)

        KashNotifier.sendDigest(context, nowMs)
    }

    // ── Tier 3 ───────────────────────────────────────────────────────────

    private suspend fun weekly(context: Context, nowMs: Long, today: String) {
        val dao = AppDatabase.getInstance(context).transactionDao()
        val start = KashDates.weekStart(nowMs)
        val week = dao.snapshotBetween(start, nowMs + 1).filter { it.isSpending() }
        if (week.isEmpty()) return
        val prev = dao.snapshotBetween(start - 7 * DAY, start).filter { it.isSpending() }
        val spent = week.sumOf { it.amount }
        val top = byCategory(context, week).take(3).joinToString(", ") { (cat, sum) -> "$cat ${KashMoney.whole(sum)}" }
        val prevSpent = prev.sumOf { it.amount }
        val change = if (prevSpent <= 0) context.getString(R.string.notif_weekly_first) else {
            val pct = ((spent - prevSpent) / prevSpent * 100).roundToInt()
            when {
                pct >= 5 -> context.getString(R.string.notif_weekly_more, pct)
                pct <= -5 -> context.getString(R.string.notif_weekly_less, -pct)
                else -> context.getString(R.string.notif_weekly_same)
            }
        }
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.WEEKLY, tier = Tier.SCHEDULED, group = NotifyGroup.SUMMARIES,
            dedupKey = "${Trigger.WEEKLY}:$today",
            title = context.getString(R.string.notif_weekly_title, KashMoney.whole(spent)),
            text = context.getString(R.string.notif_weekly_text, top, change),
            route = TransactionTriggers.ROUTE_ANALYTICS,
        ), nowMs)
    }

    private suspend fun monthEnd(context: Context, nowMs: Long, cal: String) {
        val (start, end) = KashDates.monthBounds(nowMs, cal)
        val rows = AppDatabase.getInstance(context).transactionDao().snapshotBetween(start, end)
        val spent = rows.filter { it.isSpending() }.sumOf { it.amount }
        val income = rows.filter { it.isIncome() }.sumOf { it.amount }
        if (spent <= 0 && income <= 0) return
        val (bigCat, bigSum) = byCategory(context, rows.filter { it.isSpending() }).firstOrNull()
            ?: (context.getString(R.string.notif_uncategorized) to 0.0)
        val text = when {
            income <= 0 -> context.getString(R.string.notif_month_text_no_income, KashMoney.whole(spent), bigCat, KashMoney.whole(bigSum))
            spent > income -> context.getString(
                R.string.notif_month_text_overspent,
                KashMoney.whole(income), KashMoney.whole(spent), KashMoney.whole(spent - income), bigCat, KashMoney.whole(bigSum),
            )
            else -> context.getString(
                R.string.notif_month_text,
                KashMoney.whole(income), KashMoney.whole(spent), ((income - spent) / income * 100).roundToInt(), bigCat, KashMoney.whole(bigSum),
            )
        }
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.MONTH_END, tier = Tier.SCHEDULED, group = NotifyGroup.SUMMARIES,
            dedupKey = "${Trigger.MONTH_END}:$cal:${KashNotifier.dayKey(start)}",
            title = context.getString(R.string.notif_month_title, KashDates.monthYear(nowMs, cal)),
            text = text,
            route = TransactionTriggers.ROUTE_ANALYTICS,
        ), nowMs)
    }

    private suspend fun backlog(context: Context, prefs: NotificationPrefs, nowMs: Long) {
        val pending = AppDatabase.getInstance(context).transactionDao().countAllPending()
        if (!NotificationRules.backlogDue(pending, prefs.lastReviewSessionMs, nowMs)) return
        // At most once per 3 days: the key is the 3-day bucket.
        val bucket = nowMs / NotificationRules.BACKLOG_IDLE_MS
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.BACKLOG, tier = Tier.SCHEDULED, group = NotifyGroup.REVIEW,
            dedupKey = "${Trigger.BACKLOG}:$bucket",
            title = context.resources.getQuantityString(R.plurals.notif_backlog_title, pending, pending),
            text = context.getString(R.string.notif_backlog_text),
            route = TransactionTriggers.ROUTE_REVIEW,
        ), nowMs)
    }

    private suspend fun reengage(context: Context, prefs: NotificationPrefs, nowMs: Long) {
        val lastOpen = prefs.lastAppOpenMs
        if (lastOpen == 0L) return
        val since = AppDatabase.getInstance(context).transactionDao().snapshotCreatedSince(lastOpen)
            .filterNot { NotificationRules.isHistorical(it.dateTime, it.createdAt, false) }
        if (!NotificationRules.reengageDue(lastOpen, since.size, nowMs)) return
        val spend = since.filter { it.isSpending() }
        val biggest = spend.maxByOrNull { it.amount }
        val res = context.resources
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.REENGAGE, tier = Tier.SCHEDULED, group = NotifyGroup.SUMMARIES,
            // Once per absence: keyed by the last open.
            dedupKey = "${Trigger.REENGAGE}:$lastOpen",
            title = if (spend.isNotEmpty()) context.getString(R.string.notif_reengage_title, KashMoney.whole(spend.sumOf { it.amount }))
            else res.getQuantityString(R.plurals.notif_reengage_title_count, since.size, since.size),
            text = if (biggest?.counterparty != null) context.getString(
                R.string.notif_reengage_text, KashMoney.whole(biggest.amount), TransactionTriggers.shortName(biggest.counterparty),
            ) else context.getString(R.string.notif_reengage_text_plain),
        ), nowMs)
    }

    // ── Tier 4 ───────────────────────────────────────────────────────────

    /** SMS access lost: in-app first (Home asks again); a notification after 24 h. */
    suspend fun checkSmsAccess(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = NotificationPrefs(context)
        if (hasSmsAccess(context)) { prefs.smsLostSinceMs = 0L; return }
        if (!SettingsRepository(context).isOnboardingComplete()) return
        if (prefs.smsLostSinceMs == 0L) { prefs.smsLostSinceMs = nowMs; return }
        if (!NotificationRules.smsLostDue(prefs.smsLostSinceMs, nowMs)) return
        KashNotifier.submit(context, NotifyCandidate(
            trigger = Trigger.SMS_LOST, tier = Tier.HEALTH, group = NotifyGroup.HEALTH,
            dedupKey = "${Trigger.SMS_LOST}:${prefs.smsLostSinceMs}",
            title = context.getString(R.string.notif_sms_lost_title),
            text = context.getString(R.string.notif_sms_lost_text),
        ), nowMs)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun byCategory(context: Context, rows: List<TransactionEntity>): List<Pair<String, Double>> {
        val none = context.getString(R.string.notif_uncategorized)
        return rows.groupBy { it.category ?: it.predictedCategory ?: none }
            .map { (cat, list) -> cat to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
    }

    private fun hasSmsAccess(context: Context): Boolean =
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun isSunday(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

    /** Last day of the month in the user's calendar — Pagume's 5th or 6th day included. */
    fun isLastDayOfMonth(ms: Long, cal: String): Boolean =
        KashDates.monthBounds(ms, cal).second - KashDates.startOfDay(ms) <= DAY + 60 * 60 * 1000L
}
