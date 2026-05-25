package com.financeapp.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine

/**
 * Reads all historical SMS from the device inbox.
 * Delegates sender filtering to SmsParserEngine — single source of truth.
 * Adding a new bank to SmsParserEngine.FINANCIAL_SENDERS is the only change needed.
 */
object SmsReader {

    // Read from the full SMS provider instead of just inbox so we don't miss
    // provider-specific folders that still represent incoming financial messages.
    private val SMS_URI: Uri = Uri.parse("content://sms")

    /**
     * Reads all SMS from the inbox and returns those from financial senders.
     * Safe to call on a background coroutine (IO dispatcher).
     * Never throws — returns empty list on any error.
     */
    fun readFinancialSms(context: Context): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                SMS_URI,
                arrayOf("address", "body", "date"),
                null, null,
                "date ASC"
            ) ?: return messages

            val addrIdx = cursor.getColumnIndexOrThrow("address")
            val bodyIdx = cursor.getColumnIndexOrThrow("body")
            val dateIdx = cursor.getColumnIndexOrThrow("date")

            while (cursor.moveToNext()) {
                try {
                    val sender = cursor.getString(addrIdx)?.trim() ?: continue
                    val body   = cursor.getString(bodyIdx)?.trim() ?: continue
                    val date   = cursor.getLong(dateIdx)

                    // Delegate entirely to SmsParserEngine — single source of truth
                    if (SmsParserEngine.isFinancialSms(sender)) {
                        messages.add(SmsMessage(sender = sender, body = body, timestamp = date))
                    }
                } catch (e: Exception) {
                    // Skip malformed rows silently
                }
            }
        } catch (e: Exception) {
            // Return whatever we collected so far
        } finally {
            cursor?.close()
        }
        return messages
    }
}
