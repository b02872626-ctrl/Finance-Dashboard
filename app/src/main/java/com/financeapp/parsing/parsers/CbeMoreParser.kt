package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Short CBE messages:
 *  - App transfer with nothing after the name (no separate debit SMS in the
 *    data): "Dear Gebreamlak, You have transfered ETB 100.00 to Fiseha"
 *  - CBE Fast Loan repayments: "ETB 420.00 is paid on 07 Sep, 2026 for Loan Id
 *    26249. CBE Fast Loan." / "ETB 478.63 is debited via auto-collection on …"
 */
class CbeMoreParser : BankParser {

    private val shortTransferRe = Regex("""You\s+have\s+transfered\s+ETB\s*([\d,]+\.?\d*)\s+to\s+([^\n.]+?)\s*$""", RegexOption.IGNORE_CASE)
    private val loanRepayRe     = Regex("""ETB\s*([\d,]+\.?\d*)\s+is\s+(?:paid\s+on|debited\s+via\s+auto-collection)""", RegexOption.IGNORE_CASE)
    private val loanIdRe        = Regex("""Loan\s+Id\s+(\d+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
            (shortTransferRe.containsMatchIn(body.trim()) ||
                (body.contains("Fast Loan", ignoreCase = true) && loanRepayRe.containsMatchIn(body)))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val transfer = shortTransferRe.find(body.trim())
            val repay = if (transfer == null) loanRepayRe.find(body) else null
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Commercial Bank of Ethiopia",
                type          = (if (transfer != null) TransactionType.TRANSFER_OUT else TransactionType.DEBIT).name,
                amount        = ParserUtils.parseAmount((transfer ?: repay)?.groupValues?.get(1)) ?: return null,
                balance       = null,
                counterparty  = transfer?.groupValues?.get(2)?.trim() ?: "CBE Fast Loan repayment",
                accountNumber = null,
                refNumber     = loanIdRe.find(body)?.groupValues?.get(1)?.let { "LOAN-$it" },
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
