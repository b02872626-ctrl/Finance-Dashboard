package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Bank of Abyssinia (BOA) Credit SMS.
 * Example: "Dear YONAS, your account 1*26 was credited with ETB 4,000.00 by 
 *           A/R - P2P incoming settlement accou. Available Balance:  ETB 6,388.83. 
 *           Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT25337T5RC510104..."
 *
 * 2023–2024 wording, same parser:
 *  "Dear ABEBE, your account 9*****24 has been credited with ETB 350.00 on
 *   27/04/2024 by BDR9S9CK0F for a transfer made by A/R TELE BIRR through Branch
 *   Transfer with transfer description Telebirr to BoA Account-20240427165357.
 *   The available balance in the account is ETB 510.49. ..."
 * The sender is after "transfer made by"; a cash deposit leaves it blank and
 * names the depositor in the description.
 */
class BoaCreditParser : BankParser {

    private val accountRe = Regex("""account\s+(\S+)\s+(?:was|has\s+been)\s+credited""", RegexOption.IGNORE_CASE)
    private val amountRe  = Regex("""credited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val fromRe    = Regex("""by\s+(.*?)\.\s+Available""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Available\s+Balance(?::|\s+in\s+the\s+account\s+is)\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val madeByRe  = Regex("""transfer\s+made\s+by\s*(.*?)\s*through\b""", RegexOption.IGNORE_CASE)
    private val descRe    = Regex("""transfer\s+description\s+(.*?)\.\s+The\s+available""", RegexOption.IGNORE_CASE)
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
                counterparty  = counterparty(body),
                accountNumber = accountRe.find(body)?.groupValues?.get(1)?.trim(),
                refNumber     = refRe.find(body)?.groupValues?.get(1)?.trim(),
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }

    private fun counterparty(body: String): String? {
        val madeBy = madeByRe.find(body) ?: return fromRe.find(body)?.groupValues?.get(1)?.trim()
        val name = madeBy.groupValues[1].trim()
        return when {
            name.contains("TELE BIRR", ignoreCase = true) -> "Telebirr"
            name.startsWith("EthSwi", ignoreCase = true) -> "Other bank (EthSwitch)"
            name.isNotEmpty() -> name
            else -> {
                // Cash deposit: "transfer description Cash Deposit-BIRUK TESFAYE HAILE"
                val desc = descRe.find(body)?.groupValues?.get(1)?.trim().orEmpty()
                desc.substringAfter('-', "").trim().ifEmpty { "Cash deposit" }
            }
        }
    }
}
