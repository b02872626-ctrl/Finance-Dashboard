package com.financeapp.remote

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.ParserUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SupabaseRestClient(
    private val config: SupabaseConfig,
    private val accessToken: String
) {

    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun upsertUser(userId: String) {
        val body = JSONArray()
            .put(JSONObject().put("id", userId))
            .toString()
        request(
            method = "POST",
            path = "i_users?on_conflict=id",
            body = body,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    /** Upserts the shared bank catalog by bank_name. */
    fun upsertBanks(bankNames: Set<String>) {
        if (bankNames.isEmpty()) return

        val arr = JSONArray()
        for (name in bankNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()) {
            arr.put(
                JSONObject()
                    .put("bank_name", name)
            )
        }

        if (arr.length() == 0) return

        request(
            method = "POST",
            path = "i_banks?on_conflict=bank_name",
            body = arr.toString(),
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    fun upsertRawMessages(userId: String, rawMessages: List<RawSmsSyncPayload>): Int {
        val uniqueMessages = rawMessages.distinctBy { "${it.senderName}\u0000${it.rawMessage}" }
        if (uniqueMessages.isEmpty()) return 0

        var total = 0
        for (batch in uniqueMessages.chunked(BATCH_SIZE)) {
            val arr = JSONArray()
            for (raw in batch) {
                arr.put(
                    JSONObject()
                        .put("user_id", userId)
                        .put("bank_name", raw.bankName.trim())
                        .put("sender_name", raw.senderName.trim())
                        .put("raw_message", raw.rawMessage)
                )
            }

            request(
                method = "POST",
                path = "i_raw_data?on_conflict=user_id,sender_name,raw_message",
                body = arr.toString(),
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            total += batch.size
        }

        return total
    }

    /**
     * Upserts transactions using (user_id, id) as the conflict key.
     * Returns how many were attempted.
     */
    fun upsertTransactions(userId: String, transactions: List<TransactionEntity>): Int {
        val uniqueTransactions = transactions.distinctBy(SupabaseTransactionKey::from)
        if (uniqueTransactions.isEmpty()) return 0

        var total = 0
        for (batch in uniqueTransactions.chunked(BATCH_SIZE)) {
            val arr = JSONArray()
            for (tx in batch) {
                val obj = JSONObject()
                obj.put("id", SupabaseTransactionKey.from(tx))
                obj.put("user_id", userId)
                obj.put("bank_name", tx.bankName.trim())
                obj.put("occurred_at", isoUtc.format(Date(tx.dateTime)))
                obj.put("sender", tx.sender)
                obj.put("type", tx.type)
                obj.put("category", tx.category ?: JSONObject.NULL)
                // Daily Review fields — include so re-uploads don't clobber
                // cloud-side state that was set by another client (e.g. web).
                obj.put("predicted_category",   tx.predictedCategory ?: JSONObject.NULL)
                obj.put("category_confidence",  tx.categoryConfidence ?: JSONObject.NULL)
                obj.put("review_status",        tx.reviewStatus)
                obj.put("merchant_key",         tx.merchantKey ?: JSONObject.NULL)
                obj.put("amount", tx.amount)
                obj.put("balance", tx.balance ?: JSONObject.NULL)
                obj.put("counterparty", tx.counterparty ?: JSONObject.NULL)
                obj.put("ref_num", tx.refNumber ?: JSONObject.NULL)
                obj.put("total_charged", tx.totalCharged ?: JSONObject.NULL)
                obj.put("recipt_link", tx.receiptLink ?: ParserUtils.extractFirstUrl(tx.rawBody) ?: JSONObject.NULL)
                obj.put("raw_body", tx.rawBody)
                arr.put(obj)
            }

            request(
                method = "POST",
                path = "i_transactions?on_conflict=user_id,id",
                body = arr.toString(),
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            total += batch.size
        }

        return total
    }

    /**
     * Snapshot of a remote i_transactions row, used by the pull-sync.
     * Only carries the fields that can be edited from another client
     * (the web app) — we don't try to reconcile raw_body / amount / etc.
     */
    data class RemoteTxnState(
        val id: Long,
        val category: String?,
        val predictedCategory: String?,
        val categoryConfidence: Double?,
        val reviewStatus: String?,
        val merchantKey: String?
    )

    /**
     * Pull the user's recently-touched transactions so we can adopt
     * categorizations the web app made. Filters server-side to rows that
     * are not still PENDING — those have nothing for us to learn from.
     *
     * @param userId      the signed-in user (RLS will also enforce this)
     * @param sinceIsoUtc only rows whose [occurred_at] is at-or-after this
     *                    timestamp. Pass null to pull everything (used on
     *                    first sync), otherwise pass e.g. "the last 90d ago".
     */
    fun fetchTransactionStates(userId: String, sinceIsoUtc: String?): List<RemoteTxnState> {
        // PostgREST: select specific cols, filter by user_id, only non-PENDING
        val filters = buildString {
            append("user_id=eq."); append(userId)
            append("&review_status=neq.PENDING")
            if (sinceIsoUtc != null) {
                append("&occurred_at=gte.")
                append(java.net.URLEncoder.encode(sinceIsoUtc, "UTF-8"))
            }
            // Sensible bound — the user's history might be huge but the
            // recent slice is what matters for sync.
            append("&order=occurred_at.desc&limit=5000")
        }
        val cols = "select=id,category,predicted_category,category_confidence,review_status,merchant_key"
        val body = request(
            method = "GET",
            path = "i_transactions?$cols&$filters"
        )
        val arr = JSONArray(body)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            RemoteTxnState(
                id                 = o.optLong("id"),
                category           = o.optStringOrNull("category"),
                predictedCategory  = o.optStringOrNull("predicted_category"),
                categoryConfidence = if (o.isNull("category_confidence")) null else o.getDouble("category_confidence"),
                reviewStatus       = o.optStringOrNull("review_status"),
                merchantKey        = o.optStringOrNull("merchant_key")
            )
        }
    }

    /** Defensive optString that returns real `null` (not the literal "null"). */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        prefer: String? = null
    ): String {
        val url = URL(config.restBaseUrl + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000

            setRequestProperty("apikey", config.anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""

            if (code !in 200..299) {
                throw RuntimeException("Supabase request failed ($code): $text")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
