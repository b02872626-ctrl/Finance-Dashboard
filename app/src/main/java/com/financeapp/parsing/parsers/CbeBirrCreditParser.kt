package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBEBirr wallet credit SMS.
 *
 * Example:
 *   "Dear nahusenay, your CBE Birr account has been credited with 200.00Br.
 *    on 24/05/26 14:14,Txn ID DEO31DWV8EJ. Your balance is 204.60Br. Thank you!"
 */
class CbeBirrCreditParser : BankParser {

    private val amountRe  = Regex("""credited\s+with\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""(?:Your\s+)?balance\s+is\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    private val refRe     = Regex("""Txn\s+ID\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBEBirr", ignoreCase = true) &&
        body.contains("has been credited with", ignoreCase = true) &&
        body.contains("Br.", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "CBE Birr",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = null,
                accountNumber = null,
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = sms.timestamp,
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
