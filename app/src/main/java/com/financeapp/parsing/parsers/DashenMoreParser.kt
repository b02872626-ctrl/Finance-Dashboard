package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Dashen Bank wordings the credit/debit parsers don't cover (from the
 * i_raw_data export, Oct 2026):
 *  - "you have withdrawn ETB 12,000.00 from your account '0145******011'.
 *     Your current balance is ETB 287,900.00."                       → DEBIT
 *  - "KULU NETWORK has transferred ETB 300,000.00 to your account
 *     '0145******011' on 02/02/2024. Your current balance is …"     → CREDIT
 *    (also "… as check payment to your account …")
 *  - "FANUEL ANTENEH has debited ETB 24,000.00 as check payment from your
 *     account '0145******011' on 13/02/2024."                         → DEBIT
 *  - "ETB 5,000.00 has been debited from your account 5014********011 and
 *     credited to the Telebirr account +251921118602 … A service fee of
 *     ETB 5 and VAT of ETB 0.25 and DRRF fee of ETB 0.75 …"     → TRANSFER_OUT
 */
class DashenMoreParser : BankParser {

    private val withdrawnRe = Regex("""you\s+have\s+withdrawn\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val inRe        = Regex("""Dear\s+Customer,\s*(.+?)\s+has\s+transferred\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val chequeOutRe = Regex("""Dear\s+Customer,\s*(.+?)\s+has\s+debited\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val toTeleRe    = Regex("""ETB\s*([\d,]+\.?\d*)\s+has\s+been\s+debited\s+from\s+your\s+account\s+(\S+)\s+and\s+credited\s+to\s+the\s+Telebirr\s+account\s+(\+?\d+)""", RegexOption.IGNORE_CASE)
    private val accountRe   = Regex("""account\s+'([^']+)'""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""current\s+balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val feeRe       = Regex("""service\s+fee\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatRe       = Regex("""VAT\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val drrfRe      = Regex("""DRRF\s+fee\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val receiptRe   = Regex("""receipt/([A-Za-z0-9]+)""")

    private fun isDashen(sender: String) =
        sender.equals("DashenBank", ignoreCase = true) || sender.equals("Dashen", ignoreCase = true)

    override fun canParse(sender: String, body: String): Boolean =
        isDashen(sender) && (withdrawnRe.containsMatchIn(body) || inRe.containsMatchIn(body) ||
            chequeOutRe.containsMatchIn(body) || toTeleRe.containsMatchIn(body))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val balance = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1))
            val account = accountRe.find(body)?.groupValues?.get(1)?.trim()
            fun tx(type: TransactionType, amount: Double, cp: String?, acct: String? = account) = TransactionEntity(
                sender = sms.sender, bankName = "Dashen Bank", type = type.name, amount = amount, balance = balance,
                counterparty = cp, accountNumber = acct, refNumber = receiptRe.find(body)?.groupValues?.get(1),
                dateTime = sms.timestamp, serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency = "ETB", rawBody = body,
            )
            toTeleRe.find(body)?.let { m ->
                val amount = ParserUtils.parseAmount(m.groupValues[1]) ?: return null
                return tx(TransactionType.TRANSFER_OUT, amount, "Telebirr ${m.groupValues[3]}", m.groupValues[2].trimEnd('.'))
                    .copy(
                        serviceCharge = ParserUtils.parseAmount(feeRe.find(body)?.groupValues?.get(1)),
                        vat = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                        disasterFund = ParserUtils.parseAmount(drrfRe.find(body)?.groupValues?.get(1)),
                    )
            }
            withdrawnRe.find(body)?.let { m ->
                return tx(TransactionType.DEBIT, ParserUtils.parseAmount(m.groupValues[1]) ?: return null, "Cash withdrawal")
            }
            chequeOutRe.find(body)?.let { m ->
                return tx(TransactionType.DEBIT, ParserUtils.parseAmount(m.groupValues[2]) ?: return null, m.groupValues[1].trim().trim('"').trim())
            }
            inRe.find(body)?.let { m ->
                return tx(TransactionType.CREDIT, ParserUtils.parseAmount(m.groupValues[2]) ?: return null, m.groupValues[1].trim())
            }
            null
        } catch (e: Exception) { null }
    }
}
