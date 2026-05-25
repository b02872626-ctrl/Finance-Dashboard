package com.financeapp.remote

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAtMs: Long
) {
    fun isExpired(bufferMs: Long = 60_000L): Boolean =
        System.currentTimeMillis() + bufferMs >= expiresAtMs
}

