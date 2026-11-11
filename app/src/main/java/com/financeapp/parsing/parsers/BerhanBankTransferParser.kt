package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Berhan Bank app transfers out to a Telebirr wallet (Aug 2026):
 *  "Dear Samuel, You have transferred ETB 800 from your account 101XXXXXXX771
 *   to TeleBirr number 251900000000. Reference No. is: VMAMSKKMQZC.
 *   08-Aug-2026 19:18:03 Through Android. Thank you for banking with us."
 * No balance in this SMS. The wallet may be the user's own; that is left to
 * review, like other bank → Telebirr transfers.
 */
class BerhanBankTransferParser : BankParser {

    private val transferRe = Regex(
        """transferred\s+ETB\s*([\d,]+(?:\.\d+)?)\s+from\s+your\s+account\s+(\S+)\s+to\s+TeleBirr\s+number\s+(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val refRe  = Regex("""Reference\s+No\.\s+is:?\s*([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val dateRe = Regex("""(\d{2}-[A-Za-z]{3}-\d{4}\s+\d{2}:\d{2}:\d{2})""")

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Berhan", ignoreCase = true) && transferRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val m = transferRe.find(body) ?: return null
            val ts = dateRe.find(body)?.let { d ->
                runCatching {
                    java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", java.util.Locale.US).parse(d.groupValues[1])?.time
                }.getOrNull()
            } ?: sms.timestamp
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Berhan Bank",
                type          = TransactionType.TRANSFER_OUT.name,
                amount        = ParserUtils.parseAmount(m.groupValues[1]) ?: return null,
                balance       = null,
                counterparty  = "Telebirr ${m.groupValues[3]}",
                accountNumber = m.groupValues[2].trimEnd('.'),
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = ts,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
