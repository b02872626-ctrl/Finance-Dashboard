package com.financeapp.remote

import com.financeapp.data.model.TransactionEntity
import java.security.MessageDigest

/**
 * The transaction's id in i_transactions. It depends only on the SMS itself
 * (sender + exact text), never on what the parser made of it — so a newer
 * parser that reads the same SMS differently updates the same row instead
 * of adding a copy. The device already treats identical SMS text as one
 * transaction (raw-body dedup), so this matches.
 *
 * Manual entries carry their unique "manual-<uuid>" reference as well.
 *
 * Must stay identical to public.kash_txn_key() in
 * supabase/dedupe_transactions_by_sms.sql (the insert trigger and the
 * one-off merge use it): SHA-256 of the UTF-8 text, first 8 bytes as a
 * big-endian long, sign bit cleared, 0 → 1.
 */
object SupabaseTransactionKey {

    fun from(transaction: TransactionEntity): Long =
        of(transaction.sender, transaction.rawBody, transaction.refNumber)

    fun of(sender: String, rawBody: String, refNumber: String?): Long {
        val text = buildString {
            append(sender)
            append('\n')
            append(rawBody)
            if (sender == "manual") {
                append('\n')
                append(refNumber.orEmpty())
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xffL)
        }
        val positiveValue = value and Long.MAX_VALUE
        return if (positiveValue == 0L) 1L else positiveValue
    }
}
