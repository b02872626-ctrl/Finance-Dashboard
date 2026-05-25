package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr wallet-side credit SMS (companion to Mela loan disbursement, or external top-up).
 * Example: "Dear Nahusenay
 *           Your telebirr account has been credited with ETB 500.00.
 *           Your current telebirr E-Money Account balance is ETB 500.44."
 *
 * Distinct from TelebirrCreditParser which handles "You have received ETB ..." (bank-to-wallet / P2P).
 */
class TelebirrWalletCreditParser : BankParser {

    private val amountRe  = Regex("""telebirr\s+account\s+has\s+been\s+credited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""current\s+telebirr\s+E-Money\s+Account\s+balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("telebirr account has been credited with ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = "Telebirr wallet top-up",
                accountNumber = null,
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
