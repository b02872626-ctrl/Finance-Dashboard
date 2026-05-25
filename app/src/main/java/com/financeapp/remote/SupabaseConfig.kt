package com.financeapp.remote

import com.financeapp.BuildConfig

data class SupabaseConfig(
    val projectUrl: String,
    val anonKey: String
) {
    val restBaseUrl: String = projectUrl.trimEnd('/') + "/rest/v1/"
    val authBaseUrl: String = projectUrl.trimEnd('/') + "/auth/v1/"
}

object SupabaseConfigProvider {
    fun fromBuildConfig(): SupabaseConfig? {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (url.isBlank() || key.isBlank()) return null
        return SupabaseConfig(projectUrl = url, anonKey = key)
    }
}
