package com.financeapp.remote

data class RawSmsSyncPayload(
    val localId: Long,
    val senderName: String,
    val bankName: String,
    val rawMessage: String
)
