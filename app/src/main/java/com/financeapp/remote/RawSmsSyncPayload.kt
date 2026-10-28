package com.financeapp.remote

data class RawSmsSyncPayload(
    val localId: Long,
    val senderName: String,
    val bankName: String,
    val rawMessage: String,
    /** Epoch millis the SMS arrived on the device. */
    val receivedAt: Long,
    /** PARSED / UNPARSED / IGNORED — see supabase/admin_health_metrics.sql. */
    val parseStatus: String
)
