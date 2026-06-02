package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Bank of Abyssinia (BOA) Credit SMS.
 * Example: "Dear YOHANNES, your account 1*26 was credited with ETB 4,000.00 by 
 *           A/R - P2P incoming settlement accou. Available Balance:  ETB 6,388.83. 
 *           Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT25337T5RC510104..."
 */
class BoaCreditParser : BankParser {

    private val accountRe = Regex("""account\s+(\S+)\s+was\s+credited""", RegexOption.IGNORE_CASE)
    private val amountRe  = Regex("""credited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val fromRe    = Regex("""by\s+(.*?)\.\s+Available""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Available\s+Balance:\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe     = Regex("""[?&]trx=([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        // BOA SMS arrive from either short-code "BOA" or display "Abyssinia"
        (sender.equals("BOA", ignoreCase = true) ||
         sender.equals("Abyssinia", ignoreCase = true)) &&
        body.contains("credited with", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Bank of Abyssinia",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = fromRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = accountRe.find(body)?.groupValues?.get(1)?.trim(),
                refNumber     = refRe.find(body)?.groupValues?.get(1)?.trim(),
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
