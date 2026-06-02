package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBEBirr ATM withdrawal SMS.
 *
 * Example:
 *   "Dear nahusenay, you have withdrawn 200.00Br. from CBE ATM on 24/05/26 14:21,
 *    Txn ID DEO11DWVQAX. You have paid 0.15Br. for 5% Disaster Risk Response Fund
 *    & 15% Tax and Services Charge 0.70Br.. Your CBE Birr account balance is 3.75Br."
 *
 * - amount = the withdrawn amount (DEBIT)
 * - disasterFund = the 5% Disaster Risk Response Fund (0.15 in the example)
 * - vat = the 15% Tax + Services Charge (0.70 in the example)
 * - totalCharged = amount + disasterFund + vat (sum the user actually lost)
 * - counterparty = "CBE ATM" so the user can group ATM withdrawals together
 */
class CbeBirrWithdrawalParser : BankParser {

    private val amountRe   = Regex("""withdrawn\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""CBE\s*Birr\s+account\s+balance\s+is\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    private val refRe      = Regex("""Txn\s+ID\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val disasterRe = Regex("""paid\s+([\d,]+\.?\d*)\s*Br[.\s]*for\s+5%\s+Disaster""", RegexOption.IGNORE_CASE)
    private val vatRe      = Regex("""15%\s+Tax\s+and\s+Services\s+Charge\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBEBirr", ignoreCase = true) &&
        body.contains("withdrawn", ignoreCase = true) &&
        body.contains("CBE ATM", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount   = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val disaster = ParserUtils.parseAmount(disasterRe.find(body)?.groupValues?.get(1))
            val vat      = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1))
            val total    = amount + (disaster ?: 0.0) + (vat ?: 0.0)

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "CBE Birr",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = "CBE ATM",
                accountNumber = null,
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = sms.timestamp,
                serviceCharge = null,
                vat           = vat,
                disasterFund  = disaster,
                totalCharged  = total.takeIf { it > amount },
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
