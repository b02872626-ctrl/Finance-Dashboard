package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBE outgoing transfer SMS. CBE ships TWO formats:
 *
 *  OLD format (single-r "transfered", legacy):
 *    "Dear Yohannes, You have transfered ETB 600.00 to Fetlework Ansa on
 *     03/04/2026 at 22:46:55 from your account 1*****8166. Your account has
 *     been debited with a S.charge of ETB 0.50 and VAT(15%) of ETB0.08 and
 *     Disaster Fund (5%) of ETB0.03, with a total of ETB 600.61. Your
 *     Current Balance is ETB 78,471.86."
 *
 *  NEW format (double-r "successfully transferred", introduced ~mid-2026):
 *    "Dear  Nahusenay G/amlak Teka You have successfully transferred ETB
 *     250.61 from account 1********4607 to account 1********9258 (Firehiwot
 *     Asmelash Belay). Service charge of ETB 0.50 and VAT(15%) of ETB0.08
 *     and Disaster Recovery(5%) of 0.03 with total of ETB250.61 .Your
 *     current balance is ETB-243.21."
 *
 * Differences in the new format:
 *  - "from account X to account Y (NAME)" instead of "transfered to NAME
 *    from your account X"
 *  - "Disaster Recovery" instead of "Disaster Fund"
 *  - "Service charge of ETB" instead of "S.charge of ETB"
 *  - sometimes no space between ETB and amount (`ETB250.61`)
 *  - no "on DATE at TIME" — uses SMS arrival time as the dateTime
 */
class CbeTransferParser : BankParser {

    // Matches both "transfered" (CBE typo) and "transferred"
    private val amountRe   = Regex("""transfer(?:r)?ed\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    // OLD: "transfered ETB X to NAME on dd/MM/yyyy"
    private val toOldRe    = Regex("""transfer(?:r)?ed\s+ETB\s*[\d,.]+\s+to\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE)
    // NEW: "to account MASKED_ACCT (Counterparty Name)" — counterparty is in the parens
    private val toNewRe    = Regex("""to\s+account\s+\S+\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)

    private val dateRe     = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+at\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)

    // OLD: "from your account 1*****8166"  NEW: "from account 1*****4607"
    private val accountRe  = Regex("""from\s+(?:your\s+)?account\s+(\S+)""", RegexOption.IGNORE_CASE)

    // OLD: "S.charge of ETB 0.50"   NEW: "Service charge of ETB 0.50"
    private val scRe       = Regex("""(?:S\.?charge|Service\s+charge)\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatRe      = Regex("""(?:VAT\(15%\)\s+of\s+ETB|15%\s+VAT\s+of\s+ETB)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // OLD: "Disaster Fund (5%) of ETB0.03"   NEW: "Disaster Recovery(5%) of 0.03"
    private val dfRe       = Regex("""Disaster\s+(?:Fund|Recovery)\s*\(5%\)\s+of\s+(?:ETB)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // OLD: "with a total of ETB 600.61"   NEW: "with total of ETB250.61"
    private val totalRe    = Regex("""with\s+(?:a\s+)?total\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""(?:Current\s+)?Balance\s+is\s+ETB\s*(-?[\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe      = Regex("""[?&]id=([A-Z0-9]+)""")       // extract from URL if present

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
        body.contains("transfer", ignoreCase = true) &&
        // Accept both the OLD "from your account" and the NEW "from account" /
        // "to account" patterns. The "to account" check covers cases where the
        // SMS doesn't contain "from account" but does start with the new
        // bank-to-bank pattern.
        (body.contains("from your account", ignoreCase = true) ||
         body.contains("from account",      ignoreCase = true) ||
         body.contains("to account",        ignoreCase = true))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val dateMatch = dateRe.find(body)
            // Prefer the OLD format's "to NAME on DATE" match; if not found,
            // fall back to the NEW format's "to account X (NAME)".
            val counterparty = (toOldRe.find(body)?.groupValues?.get(1)
                                  ?: toNewRe.find(body)?.groupValues?.get(1))?.trim()
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Commercial Bank of Ethiopia",
                type          = TransactionType.TRANSFER_OUT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = ParserUtils.parseDateTime(dateMatch?.groupValues?.get(1), dateMatch?.groupValues?.get(2))
                                ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = ParserUtils.parseAmount(dfRe.find(body)?.groupValues?.get(1)),
                totalCharged  = ParserUtils.parseAmount(totalRe.find(body)?.groupValues?.get(1)),
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
