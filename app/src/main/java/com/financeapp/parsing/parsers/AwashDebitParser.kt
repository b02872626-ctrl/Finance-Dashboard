package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Awash Bank Debit SMS.
 * Example: "Dear Customer, your Account 01320xxxxxx6000 has been Debited with ETB 500.00 on
 *           2026-04-04 14:24:56 by ... Your balance now is ETB 83.59. Awash Bank."
 */
class AwashDebitParser : BankParser {

    private val accountRe  = Regex("""Account\s+(\S+)\s+has\s+been\s+Debited""", RegexOption.IGNORE_CASE)
    private val amountRe   = Regex("""Debited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val toRe       = Regex("""by\s+(.+?)(?:\s+via\s+|\.\s+Your)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""balance\s+now\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("Awash Bank", ignoreCase = true) &&
        body.contains("Debited with ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Awash Bank",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = toRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = accountRe.find(body)?.groupValues?.get(1)?.trim(),
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
