package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Fallback parser for SMS from known financial senders that couldn't be parsed,
 * or for completely unknown senders. Stores the raw body as UNKNOWN type.
 * This parser never crashes and always succeeds as a last resort.
 */
class UnknownBankParser : BankParser {
    // Matches any sender — used as absolute last resort
    override fun canParse(sender: String, body: String): Boolean = true

    override fun parse(sms: SmsMessage): TransactionEntity = TransactionEntity(
        sender        = sms.sender,
        bankName      = "Unknown (${sms.sender})",
        type          = TransactionType.UNKNOWN.name,
        amount        = 0.0,
        balance       = null,
        counterparty  = null,
        accountNumber = null,
        refNumber     = null,
        dateTime      = sms.timestamp,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
        currency      = "ETB",
        rawBody       = sms.body
    )
}
