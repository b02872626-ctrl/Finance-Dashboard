package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Awash Bank wordings the older parsers miss:
 *  - "You have transferred to COMMERCIAL BANK OF ETHIOPIA Amount 400 ETB To
 *     1000469454607 (NAHUSENAY G/AMLAK TEKA). VAT: 0.36 Your Balance is ETB 120.85."
 *  - "You have transferred to other bank ETB 300 To 74701531 (GEBREAMLAK TEKA MEZGEBE)
 *     In Bank of Abyssinia with charge of 1.80 VAT: 0.27 EDRRF 0.09 ETB."
 *  - "Your Account 01320******500 has been credited with 9,500.00 ETB on … by
 *     [NAME via] IPS from Commercial Bank of Ethiopia"
 */
class AwashMoreParser : BankParser {

    private val toBankRe  = Regex("""transferred\s+to\s+(?:other\s+bank\s+ETB\s*([\d,]+\.?\d*)|.+?\s+Amount\s+([\d,]+\.?\d*)\s*ETB)\s+To\s+(\S+)\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val creditRe  = Regex("""Account\s+(\S+)\s+has\s+been\s+credited\s+with\s+([\d,]+\.?\d*)\s*ETB""", RegexOption.IGNORE_CASE)
    private val byRe      = Regex("""by\s+(?:(.+?)\s+via\s+)?IPS\s+from\s+(.+?)(?:\s+For\s+any|\.|$)""", RegexOption.IGNORE_CASE)
    private val chargeRe  = Regex("""charge\s+of\s+([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatRe     = Regex("""VAT:\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val drrfRe    = Regex("""EDRRF\s+([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Awash", ignoreCase = true) &&
            (toBankRe.containsMatchIn(body) || creditRe.containsMatchIn(body))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val out = toBankRe.find(body)
            val credit = creditRe.find(body)
            val by = byRe.find(body)
            val amount = ParserUtils.parseAmount(
                out?.groupValues?.get(1)?.ifBlank { out.groupValues[2] } ?: credit?.groupValues?.get(2)
            ) ?: return null
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Awash Bank",
                type          = (if (out != null) TransactionType.TRANSFER_OUT else TransactionType.CREDIT).name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = out?.groupValues?.get(4)?.trim()
                    ?: by?.groupValues?.get(1)?.ifBlank { null }?.trim()
                    ?: by?.groupValues?.get(2)?.trim(),
                accountNumber = if (out != null) null else credit?.groupValues?.get(1),
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(chargeRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = ParserUtils.parseAmount(drrfRe.find(body)?.groupValues?.get(1)),
                totalCharged  = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
