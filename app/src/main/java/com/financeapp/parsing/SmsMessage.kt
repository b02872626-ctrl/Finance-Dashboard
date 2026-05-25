package com.financeapp.parsing

/**
 * Represents a raw SMS message as received from the device.
 */
data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long  // epoch millis when SMS was received
)
