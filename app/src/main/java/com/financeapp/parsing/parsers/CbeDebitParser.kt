package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBE debit SMS (ATM, POS, system debit). THREE format families:
 *
 *  Legacy:  "Dear Yohannes Tadesse your Account 1****8166 has been debited
 *            with ETB 1005.75 including Service charge ETB0.00ETB0.00 and
 *            VAT(15%) . Your Current Balance is ETB 146479.08."
 *
 *  Modern:  "Dear Nahusenay your Account 1*********4607 has been debited
 *            with ETB100.00. Service charge of ETB 10.00 and VAT(15%) of
 *            ETB1.50 and Disaster Fund (5%) of ETB0.50 with a total of
 *            ETB 112.00. Your Current Balance is ETB 270.45."
 *
 *  2026+:   "Dear Yabsira Endegena Andarge A debit transaction of ETB 15.0.
 *            has occurred on your account 1********9408. Service charge of
 *            ETB 0.00 and VAT(15%) of 0.0 and Disaster Recovery(5%) of 0.00
 *            with total of ETB15.00. Your current balance is ETB7.58."
 *           (Different verb: "debit transaction has occurred" vs "has been
 *            debited". Also uses "Disaster Recovery" not "Disaster Fund".)
 */
class CbeDebitParser : BankParser {

    // Both verbs: legacy "Account X has been debited", 2026 "on your account X"
    private val accountRe = Regex(
        """(?:Account|on\s+your\s+account)\s+(\S+)""",
        RegexOption.IGNORE_CASE
    )
    // Two amount phrasings:
    //  - "debited with ETB 100.00"
    //  - "A debit transaction of ETB 15.0"
    private val amountRe  = Regex(
        """(?:debited\s+with|debit\s+transaction\s+of)\s+ETB\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val scRe       = Regex("""Service\s+charge\s+(?:of\s+)?ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatModern  = Regex("""VAT\(15%\)\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatLegacy  = Regex("""Service\s+charge\s+ETB[\d.,]*ETB([\d.,]*)""", RegexOption.IGNORE_CASE)
    // 2026 variant drops "ETB" before the value: "VAT(15%) of 0.0"
    private val vat2026    = Regex("""VAT\(15%\)\s+of\s+([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // OLD/MODERN: "Disaster Fund (5%) of ETB 0.03"
    // 2026:       "Disaster Recovery(5%) of 0.03"
    private val dfRe       = Regex(
        """Disaster\s+(?:Fund|Recovery)\s*\(5%\)\s+of\s+(?:ETB)?\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val totalRe    = Regex("""(?:with\s+a\s+)?total\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""(?:Current\s+)?Balance\s+is\s+ETB\s*(-?[\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe      = Regex("""[?&]id=([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
        (body.contains("has been debited with ETB", ignoreCase = true) ||
         body.contains("debit transaction of ETB",  ignoreCase = true))

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
                // Strip trailing punctuation — the 2026 "on your account X."
                // variant leaves a period at the end of the captured group.
                accountNumber = accountRe.find(body)?.groupValues?.get(1)?.trimEnd('.', ',', ';'),
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatModern.find(body)?.groupValues?.get(1))
                                ?: ParserUtils.parseAmount(vatLegacy.find(body)?.groupValues?.get(1))
                                ?: ParserUtils.parseAmount(vat2026.find(body)?.groupValues?.get(1)),
                disasterFund  = ParserUtils.parseAmount(dfRe.find(body)?.groupValues?.get(1)),
                totalCharged  = ParserUtils.parseAmount(totalRe.find(body)?.groupValues?.get(1)),
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
