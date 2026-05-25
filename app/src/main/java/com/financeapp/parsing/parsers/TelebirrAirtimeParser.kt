package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr airtime top-up payments.
 * Example: "Dear yohannes 
 *           You have recharged ETB 5.00 airtime for 954998471 on 13/03/2026 10:17:14.
 *           Your transaction number is DCD5PW1A9X. Your current  balance is  ETB 4,933.15."
 */
class TelebirrAirtimeParser : BankParser {

    private val amountRe    = Regex("""recharged\s+ETB\s*([\d,]+\.?\d*)\s+airtime\s+for\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val dateRe      = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val txnRe       = Regex("""transaction\s+number\s+is\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""balance\s+is\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("recharged", ignoreCase = true) &&
        body.contains("airtime", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amountMatch = amountRe.find(body)
            val amount = ParserUtils.parseAmount(amountMatch?.groupValues?.get(1)) ?: return null
            val phone = amountMatch?.groupValues?.get(2)?.trim()
            
            val dateMatch = dateRe.find(body)
            val txn = txnRe.find(body)?.groupValues?.get(1)?.trim()

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.PAYMENT.name, // Airtime is a payment
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = "Airtime ($phone)",
                accountNumber = null,
                refNumber     = txn,
                dateTime      = ParserUtils.parseDateTime(dateMatch?.groupValues?.get(1), dateMatch?.groupValues?.get(2))
                                ?: sms.timestamp,
                serviceCharge = null,
                vat           = null,
                disasterFund  = null,
                totalCharged  = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
