package com.financeapp.remote

import com.financeapp.data.model.TransactionEntity
import java.math.BigDecimal
import java.security.MessageDigest

object SupabaseTransactionKey {

    fun from(transaction: TransactionEntity): Long {
        val fingerprint = buildString {
            append(normalize(transaction.sender))
            append('|')
            append(normalize(transaction.bankName))
            append('|')
            append(normalize(transaction.type))

            val ref = transaction.refNumber?.trim().orEmpty()
            if (ref.isNotEmpty()) {
                append("|ref|")
                append(normalize(ref))
            } else {
                append("|body|")
                append(normalizeAmount(transaction.amount))
                append('|')
                append(transaction.dateTime)
                append('|')
                append(normalize(transaction.counterparty))
                append('|')
                append(normalize(transaction.rawBody))
            }
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xffL)
        }
        val positiveValue = value and Long.MAX_VALUE
        return if (positiveValue == 0L) 1L else positiveValue
    }

    private fun normalize(value: String?): String =
        value
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()

    private fun normalizeAmount(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
