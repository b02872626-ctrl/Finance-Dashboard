package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * CBE card withdrawals at an ATM (seen from 2026):
 *   "Dear NAME, an ATM withdrawal of ETB1500.00 was made from your account
 *    1********9408. If you authorized this transaction, no further action is
 *    required. … Service charge of ETB 5.25 and VAT(15%) of ETB0.79 and
 *    Disaster Recovery(5%) of 0.26 with total of ETB1506.30 .Your current
 *    balance is ETB903.56. Thanks for Banking with CBE. https://mbreciept…"
 */
class CbeAtmWithdrawalParser : BankParser {

    private val amountRe   = Regex("""ATM\s+withdrawal\s+of\s+ETB\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val accountRe  = Regex("""from\s+your\s+account\s+(\S+?)\.?\s""", RegexOption.IGNORE_CASE)
    private val chargeRe   = Regex("""Service\s+charge\s+of\s+ETB\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val vatRe      = Regex("""VAT\s*\(15%\)\s+of\s+ETB\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val drfRe      = Regex("""Disaster\s+Recovery\s*\(5%\)\s+of\s+(?:ETB\s*)?([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val totalRe    = Regex("""with\s+total\s+of\s+ETB\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""current\s+balance\s+is\s+ETB\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) && amountRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Commercial Bank of Ethiopia",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = "CBE ATM",
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(chargeRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = ParserUtils.parseAmount(drfRe.find(body)?.groupValues?.get(1)),
                totalCharged  = ParserUtils.parseAmount(totalRe.find(body)?.groupValues?.get(1)),
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
