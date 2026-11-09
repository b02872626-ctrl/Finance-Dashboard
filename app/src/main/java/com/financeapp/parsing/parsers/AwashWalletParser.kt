package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Awash Bank app / AwashPay wordings (2025–2026) the older Awash parsers miss:
 *  - "You have sent ETB 1,400 To (0134…500) - NAME by Transaction ID: 2601…
 *     charge- 1.00 VAT- 0.15 Date 2026-01-12 22:18:07 . Your Available
 *     Balance is 257.77. Download the receipt by link https://awashpay…"
 *  - "Telebirr Transfer of 30.00 ETB to NAME  - 2519… from 0132…500/BANK,
 *     Reason- hi, Charge 5.00 VAT: 0.75 EDRRF 0.25 ETB. Your Balance is
 *     ETB 498.06 . Receipt Link: https://awashpay…"
 *  - "Dear NAME, you have received 50.00 from Awash Bank-NAME.Thank You!"
 */
class AwashWalletParser : BankParser {

    private val sentRe     = Regex("""You\s+have\s+sent\s+ETB\s*([\d,]+(?:\.\d+)?)\s+To\s+\(([^)]+)\)\s*-\s*(.+?)\s+by\s+Transaction\s+ID:\s*(\S+)""", RegexOption.IGNORE_CASE)
    private val telebirrRe = Regex("""Telebirr\s+Transfer\s+of\s+([\d,]+(?:\.\d+)?)\s*ETB\s+to\s+(.+?)\s+-\s+(\d+)\s+from\s+([^/\s]+)/BANK""", RegexOption.IGNORE_CASE)
    private val receivedRe = Regex("""you\s+have\s+received\s+([\d,]+(?:\.\d+)?)\s+from\s+Awash\s+Bank-(.+?)\s*\.\s*Thank""", RegexOption.IGNORE_CASE)
    private val chargeRe   = Regex("""charge-?\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val vatRe      = Regex("""VAT[-:]\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val drrfRe     = Regex("""EDRRF\s+([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""Balance\s+is\s+(?:ETB\s*)?([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Awash", ignoreCase = true) &&
            (sentRe.containsMatchIn(body) || telebirrRe.containsMatchIn(body) || receivedRe.containsMatchIn(body))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val sent = sentRe.find(body)
            val telebirr = telebirrRe.find(body)
            val received = receivedRe.find(body)
            val (type, amountRaw, counterparty) = when {
                sent != null     -> Triple(TransactionType.TRANSFER_OUT, sent.groupValues[1], sent.groupValues[3])
                telebirr != null -> Triple(TransactionType.TRANSFER_OUT, telebirr.groupValues[1], telebirr.groupValues[2])
                received != null -> Triple(TransactionType.CREDIT, received.groupValues[1], received.groupValues[2])
                else -> return null
            }
            val amount = ParserUtils.parseAmount(amountRaw) ?: return null
            val isOut = type == TransactionType.TRANSFER_OUT
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Awash Bank",
                type          = type.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty.trim().replace(Regex("""\s+"""), " "),
                accountNumber = telebirr?.groupValues?.get(4),
                refNumber     = sent?.groupValues?.get(4),
                dateTime      = sms.timestamp,
                serviceCharge = if (isOut) ParserUtils.parseAmount(chargeRe.find(body)?.groupValues?.get(1)) else null,
                vat           = if (isOut) ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)) else null,
                disasterFund  = if (isOut) ParserUtils.parseAmount(drrfRe.find(body)?.groupValues?.get(1)) else null,
                totalCharged  = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
