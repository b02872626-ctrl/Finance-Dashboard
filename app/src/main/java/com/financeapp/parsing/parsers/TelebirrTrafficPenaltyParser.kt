package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr traffic penalty payments.
 * Example: "Dear yohannes You have paid  for traffic penalty  ETB 500.00 to
 *           515016 - Addis Ababa Traffic Management Authority; with Payment reference
 *           number VT301912 on 18/03/2026 17:01:14. The service fee is  ETB 2.50 and
 *           15% VAT on the service fee is ETB 0.37. Your transaction number is DCI4WLTE3U
 *           Your telebirr account balance is  ETB 3,798.28."
 */
class TelebirrTrafficPenaltyParser : BankParser {

    private val amountRe    = Regex("""ETB\s*([\d,]+\.?\d*)\s+to\s+(.+?);""", RegexOption.IGNORE_CASE)
    private val dateRe      = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val refRe       = Regex("""Payment\s+reference\s+number\s+(\w+)""", RegexOption.IGNORE_CASE)
    private val txnRe       = Regex("""transaction\s+number\s+is\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val scRe        = Regex("""service\s+fee\s+is\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatRe       = Regex("""VAT.*?is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""balance\s+is\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("paid", ignoreCase = true) &&
        body.contains("traffic penalty", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amountMatch = amountRe.find(body)
            val amount = ParserUtils.parseAmount(amountMatch?.groupValues?.get(1)) ?: return null
            val counterparty = amountMatch?.groupValues?.get(2)?.trim()
            
            val dateMatch = dateRe.find(body)
            val txn = txnRe.find(body)?.groupValues?.get(1)?.trim()
            val ref = refRe.find(body)?.groupValues?.get(1)?.trim()

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.PAYMENT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty ?: "Traffic Management Authority",
                accountNumber = null,
                refNumber     = txn, // Primary telebirr txn ID
                dateTime      = ParserUtils.parseDateTime(dateMatch?.groupValues?.get(1), dateMatch?.groupValues?.get(2))
                                ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = null,
                totalCharged  = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
