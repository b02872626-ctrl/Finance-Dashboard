package com.financeapp.export

import com.financeapp.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionCsv {

    private val header = listOf(
        "id",
        "dateTimeMillis",
        "dateTimeLocal",
        "sender",
        "bankName",
        "type",
        "category",
        "amount",
        "currency",
        "balance",
        "counterparty",
        "accountNumber",
        "refNumber",
        "serviceCharge",
        "vat",
        "disasterFund",
        "totalCharged",
        "rawBody",
        "createdAtMillis"
    )

    fun build(transactions: List<TransactionEntity>): String =
        buildString { writeTo(this, transactions) }

    fun writeTo(out: Appendable, transactions: List<TransactionEntity>) {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        out.append(header.joinToString(",")).append("\n")
        for (tx in transactions) {
            val row = listOf(
                tx.id.toString(),
                tx.dateTime.toString(),
                df.format(Date(tx.dateTime)),
                tx.sender,
                tx.bankName,
                tx.type,
                tx.category.orEmpty(),
                tx.amount.toString(),
                tx.currency,
                tx.balance?.toString().orEmpty(),
                tx.counterparty.orEmpty(),
                tx.accountNumber.orEmpty(),
                tx.refNumber.orEmpty(),
                tx.serviceCharge?.toString().orEmpty(),
                tx.vat?.toString().orEmpty(),
                tx.disasterFund?.toString().orEmpty(),
                tx.totalCharged?.toString().orEmpty(),
                tx.rawBody,
                tx.createdAt.toString()
            )
            out.append(row.joinToString(",") { csvEscape(it) }).append("\n")
        }
    }

    private fun csvEscape(raw: String): String {
        val needsQuotes = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return raw
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
