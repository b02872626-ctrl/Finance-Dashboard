package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Telebirr moves of the user's own money — not spending or income:
 *  - Paying back Ethiotel Credit. The purchase made on credit already has
 *    its own "You have paid ETB …" SMS, so the repayment must not count
 *    again: "You have successfully paid a credit amount of ETB 59.54 on
 *    2026-05-21 08:58:52. Your current outstanding credit amount ETB 0.00"
 *  - Wallet to savings: "You have successfully deposited ETB 50.00 to your Saving
 *    Account" / "… from your telebirr account to your Sinq saving account"
 *  - Savings to wallet: "You have successfully Withdraw ETB 50.00 from your
 *    saving account on 16/03/2024 16:05:10. Your transaction number is
 *    BCG9ESNJGT. … e-money account balance is ETB 76.38."
 */
class TelebirrOwnMovesParser : BankParser {

    private val creditRepaidRe = Regex("""paid\s+a\s+credit\s+amount\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val savingsRe      = Regex("""withdraw\s+ETB\s*([\d,]+\.?\d*)\s+from\s+your\s+(?:sinq\s+)?saving\s+account""", RegexOption.IGNORE_CASE)
    private val depositRe      = Regex("""deposited\s+ETB\s*([\d,]+\.?\d*)\s+(?:from\s+your\s+telebirr\s+account\s+)?to\s+your\s+(?:sinq\s+)?saving\s+account""", RegexOption.IGNORE_CASE)
    private val isoDateRe      = Regex("""on\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""")
    private val slashDateRe    = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""")
    private val txnRe          = Regex("""transaction\s+number\s+is\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val walletRe       = Regex("""(?:e-money|telebirr)\s+account\s+balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" && (creditRepaidRe.containsMatchIn(body) || savingsRe.containsMatchIn(body) || depositRe.containsMatchIn(body))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val repaid = creditRepaidRe.find(body)
            val deposit = depositRe.find(body)
            val amount = ParserUtils.parseAmount((repaid ?: deposit ?: savingsRe.find(body))?.groupValues?.get(1)) ?: return null
            val slash = slashDateRe.find(body)
            TransactionEntity(
                sender = sms.sender, bankName = "Telebirr", type = TransactionType.INTERNAL_TRANSFER.name,
                amount = amount,
                balance = ParserUtils.parseAmount(walletRe.find(body)?.groupValues?.get(1)),
                counterparty = when {
                    repaid != null -> "Ethiotel Credit repayment"
                    deposit != null -> "To telebirr savings"
                    else -> "From telebirr savings"
                },
                accountNumber = null,
                refNumber = txnRe.find(body)?.groupValues?.get(1),
                dateTime = ParserUtils.parseIsoDateTime(isoDateRe.find(body)?.groupValues?.get(1))
                    ?: ParserUtils.parseDateTime(slash?.groupValues?.get(1), slash?.groupValues?.get(2))
                    ?: sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency = "ETB", rawBody = body,
            )
        } catch (e: Exception) { null }
    }
}
