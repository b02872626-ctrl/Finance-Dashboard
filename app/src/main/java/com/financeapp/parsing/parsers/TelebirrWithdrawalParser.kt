package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses Telebirr withdrawal (ATM cash-out) SMS.
 * Example:
 * "The request to withdraw ETB 200.00 from your telebirr account 2519... via secret code 149086
 *  on 2026-04-06 11:30:02 using Bank of Abyssinia ATM with transaction number DD66MGD9OM is
 *  successfully completed. The service fee (including 15% VAT) is ETB 1.15. Your current Account
 *  balance is ETB 1,965.81."
 */
class TelebirrWithdrawalParser : BankParser {

    private val amountRe  = Regex("""withdraw\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val acctRe    = Regex("""telebirr\s+account\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val dateRe    = Regex("""on\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val usingRe   = Regex("""using\s+(.+?)\s+with\s+transaction\s+number""", RegexOption.IGNORE_CASE)
    private val txnRe     = Regex("""transaction\s+number\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val feeRe     = Regex("""service\s+fee.*?\sETB\s*([\d,]+\.?\d*)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val balanceRe = Regex("""balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("request to withdraw", ignoreCase = true) &&
        body.contains("telebirr account", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            val dateMatch = dateRe.find(body)
            val rawDate = dateMatch?.groupValues?.get(1)
            val rawTime = dateMatch?.groupValues?.get(2)
            var ts = sms.timestamp
            if (rawDate != null && rawTime != null) {
                ts = runCatching {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .parse("$rawDate $rawTime")
                        ?.time
                }.getOrNull() ?: ts
            }

            TransactionEntity(
                sender = sms.sender,
                bankName = "Telebirr",
                type = TransactionType.DEBIT.name,
                amount = amount,
                balance = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty = usingRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = acctRe.find(body)?.groupValues?.get(1),
                refNumber = txnRe.find(body)?.groupValues?.get(1)?.trim(),
                dateTime = ts,
                serviceCharge = null,
                vat = null,
                disasterFund = null,
                totalCharged = ParserUtils.parseAmount(feeRe.find(body)?.groupValues?.get(1)),
                currency = "ETB",
                rawBody = body
            )
        } catch (e: Exception) { null }
    }
}
