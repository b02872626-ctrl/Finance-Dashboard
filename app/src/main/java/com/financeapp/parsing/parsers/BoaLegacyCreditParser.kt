package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Bank of Abyssinia credits, 2021–2023 wording (no space before "with"):
 *  "A/C No. 1*****38 has been credited By DANIEL LAKEW HULUKAwith ETB 14,000.00 on
 *   29/05/2023 @MEKANISSA MAZORIA BR. Info: Internet Credit-DANIEL LAKEW HULUKA.
 *   Avail. bal: ETB 14,024.65."
 * Cash deposits have no name after "By"; the Info line names the deposit.
 */
class BoaLegacyCreditParser : BankParser {

    private val creditRe  = Regex("""A/C\s+No\.\s*(\S+)\s+has\s+been\s+credited\s+By\s*(.*?)\s*with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val infoRe    = Regex("""Info:\s*([^-.]+)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Avail\.\s*bal:\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        (sender.equals("BOA", ignoreCase = true) || sender.contains("Abyssinia", ignoreCase = true)) &&
            creditRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val m = creditRe.find(body) ?: return null
            val by = m.groupValues[2].trim()
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Bank of Abyssinia",
                type          = TransactionType.CREDIT.name,
                amount        = ParserUtils.parseAmount(m.groupValues[3]) ?: return null,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = by.ifBlank { infoRe.find(body)?.groupValues?.get(1)?.trim() ?: "Deposit" },
                accountNumber = m.groupValues[1],
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
