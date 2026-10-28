package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Bank of Abyssinia card purchases. BoA sends one SMS per card payment —
 * a hold — and no later debit SMS (checked against the i_raw_data export):
 *  "ETB 215.00 is Locked From Your Account Number 1******26 Due to POS
 *   TRANSACTION at AASTU CONSUMER COOPERATIVE PLC. Your Available Balance
 *   is ETB 6173.83"
 * USD holds are left alone (the app only handles birr).
 */
class BoaPosHoldParser : BankParser {

    private val holdRe    = Regex("""ETB\s*([\d,]+\.?\d*)\s+is\s+Locked\s+From\s+Your\s+Account\s+Number\s+(\S+)\s+Due\s+to\s+POS\s+TRANSACTION\s+at\s+(.+?)\.\s+Your""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Available\s+Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        (sender.equals("BOA", ignoreCase = true) || sender.equals("Abyssinia", ignoreCase = true)) && holdRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val m = holdRe.find(body) ?: return null
            TransactionEntity(
                sender = sms.sender, bankName = "Bank of Abyssinia", type = TransactionType.PAYMENT.name,
                amount = ParserUtils.parseAmount(m.groupValues[1]) ?: return null,
                balance = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty = m.groupValues[3].trim(), accountNumber = m.groupValues[2],
                refNumber = null, dateTime = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency = "ETB", rawBody = body,
            )
        } catch (e: Exception) { null }
    }
}
