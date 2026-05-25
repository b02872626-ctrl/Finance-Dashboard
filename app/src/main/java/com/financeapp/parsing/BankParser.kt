package com.financeapp.parsing

import com.financeapp.data.model.TransactionEntity

/**
 * Contract every bank parser must implement.
 * canParse() must never throw.
 * parse() wraps all logic in try/catch and returns null on any failure.
 */
interface BankParser {
    fun canParse(sender: String, body: String): Boolean
    fun parse(sms: SmsMessage): TransactionEntity?
}
