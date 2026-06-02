package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Bank of Abyssinia (BOA) Debit SMS.
 * Example: "Dear Yohannes, your account 1*26 was debited with ETB 1,005.41.
 *           Available Balance: ETB 5,191.03.
 *           Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT26089P6G1X03526..."
 */
class BoaDebitParser : BankParser {

    private val accountRe = Regex("""account\s+(\S+)\s+was\s+debited""", RegexOption.IGNORE_CASE)
    private val amountRe  = Regex("""debited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Available\s+Balance:\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe     = Regex("""[?&]trx=([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        (sender.equals("BOA", ignoreCase = true) ||
         sender.equals("Abyssinia", ignoreCase = true)) &&
        body.contains("debited with", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Bank of Abyssinia",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = null,
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
