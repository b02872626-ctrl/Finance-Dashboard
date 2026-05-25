package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBE debit SMS (ATM, POS, system debit).
 * Example: "Dear Yohannes Tadesse your Account 1****8166 has been debited with ETB 1005.75
 *           including Service charge ETB0.00ETB0.00 and VAT(15%) .
 *           Your Current Balance is ETB 146479.08."
 */
class CbeDebitParser : BankParser {

    private val accountRe = Regex("""Account\s+(\S+)\s+has\s+been\s+debited""", RegexOption.IGNORE_CASE)
    private val amountRe  = Regex("""debited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // Modern format: "Service charge of ETB10 and VAT(15%) of ETB1.50 with a total of ETB3611"
    // Legacy format: "Service charge ETB0.00ETB0.00 and VAT(15%) ." (kept for backward compat)
    private val scRe       = Regex("""Service\s+charge\s+(?:of\s+)?ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatModern  = Regex("""VAT\(15%\)\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatLegacy  = Regex("""Service\s+charge\s+ETB[\d.,]*ETB([\d.,]*)""", RegexOption.IGNORE_CASE)
    private val totalRe    = Regex("""(?:with\s+a\s+)?total\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""(?:Current\s+)?Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe      = Regex("""[?&]id=([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
        body.contains("has been debited with ETB", ignoreCase = true)

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
                counterparty  = null,
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatModern.find(body)?.groupValues?.get(1))
                                ?: ParserUtils.parseAmount(vatLegacy.find(body)?.groupValues?.get(1)),
                disasterFund  = null,
                totalCharged  = ParserUtils.parseAmount(totalRe.find(body)?.groupValues?.get(1)),
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
