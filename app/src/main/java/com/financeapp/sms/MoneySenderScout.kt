package com.financeapp.sms

import android.content.Context
import android.net.Uri
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.remote.Telemetry

/**
 * Finds banks Kash doesn't follow yet. Looks only at SMS from business
 * names (alphanumeric sender IDs — never phone numbers, so never personal
 * chats) that aren't on the sender list, and counts the ones that read like
 * money moving. Reports the sender name, a count and the latest date, never
 * any message text. At most once a week. Shown on the admin page; real
 * banks then get added to SmsParserEngine's sender list.
 */
object MoneySenderScout {

    private const val PREFS = "kash_money_senders"
    private const val KEY_REPORTED_AT = "reported_at"
    private const val EVERY_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_SENDERS = 50

    data class Seen(val sender: String, val count: Int, val latestAt: Long)

    /** A business sender ID: has a letter, so it isn't a phone number. */
    fun isBusinessSender(sender: String): Boolean {
        val s = sender.trim()
        return s.length in 2..20 && s.any { it.isLetter() } && !s.startsWith("+")
    }

    private val amountRe = Regex(
        """(?:\b(?:ETB|Birr|Br)\.?\s*[\d,]+(?:\.\d+)?)|(?:[\d,]+(?:\.\d+)?\s*(?:ETB|Birr|Br)\b)|(?:[\d,]+(?:\.\d+)?\s*ብር)""",
        RegexOption.IGNORE_CASE,
    )
    private val actionRe = Regex(
        """\b(credited|debited|deposited|withdrawn|withdrew|transferred|transfered|received|paid|sent|balance|purchase|payment)\b|ገቢ|ወጪ|ተላልፏል|ተቀብለዋል""",
        RegexOption.IGNORE_CASE,
    )

    /** An amount in birr plus a money verb: the shape of a bank SMS. */
    fun looksLikeMoney(body: String): Boolean = amountRe.containsMatchIn(body) && actionRe.containsMatchIn(body)

    /** Unknown business senders whose SMS read like money, most frequent first. */
    fun scan(context: Context): List<Seen> {
        val counts = HashMap<String, Seen>()
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://sms"), arrayOf("address", "body", "date"), null, null, null,
            )?.use { c ->
                val a = c.getColumnIndexOrThrow("address")
                val b = c.getColumnIndexOrThrow("body")
                val d = c.getColumnIndexOrThrow("date")
                while (c.moveToNext()) {
                    val sender = c.getString(a)?.trim() ?: continue
                    // Phone numbers (people) and known banks are skipped before
                    // the text is looked at.
                    if (!isBusinessSender(sender) || SmsParserEngine.isFinancialSms(sender)) continue
                    val body = c.getString(b) ?: continue
                    if (!looksLikeMoney(body)) continue
                    val date = c.getLong(d)
                    val prev = counts[sender]
                    counts[sender] = Seen(sender, (prev?.count ?: 0) + 1, maxOf(prev?.latestAt ?: 0L, date))
                }
            }
        }
        return counts.values.sortedByDescending { it.count }.take(MAX_SENDERS)
    }

    /** Scans and queues one event per sender, at most once a week. */
    fun reportWeekly(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong(KEY_REPORTED_AT, 0L) < EVERY_MS) return
        scan(context).forEach { seen ->
            Telemetry.track(
                context, Telemetry.MONEY_SENDER_SEEN,
                mapOf("sender" to seen.sender, "count" to seen.count, "latest_at" to seen.latestAt),
            )
        }
        p.edit().putLong(KEY_REPORTED_AT, System.currentTimeMillis()).apply()
    }
}
