package com.financeapp.remote

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SupabaseAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val userId: String,
    val email: String?
)

sealed class SupabaseSignUpResult {
    data class SignedIn(val session: SupabaseAuthSession) : SupabaseSignUpResult()
    data object ConfirmationRequired : SupabaseSignUpResult()
}

class SupabaseAuthClient(private val config: SupabaseConfig) {

    fun signInWithPassword(email: String, password: String): SupabaseAuthSession {
        val resp = request(
            method = "POST",
            url = config.authBaseUrl + "token?grant_type=password",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        )
        return parseSession(resp)
    }

    fun signUp(email: String, password: String): SupabaseSignUpResult {
        val resp = request(
            method = "POST",
            url = config.authBaseUrl + "signup",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
        )

        val json = JSONObject(resp)
        return if (json.has("access_token")) {
            SupabaseSignUpResult.SignedIn(parseSession(json))
        } else {
            SupabaseSignUpResult.ConfirmationRequired
        }
    }

    fun refresh(refreshToken: String): SupabaseAuthSession {
        val resp = request(
            method = "POST",
            url = config.authBaseUrl + "token?grant_type=refresh_token",
            body = JSONObject().put("refresh_token", refreshToken).toString()
        )
        return parseSession(resp)
    }

    fun signInWithIdToken(idToken: String, provider: String = "google"): SupabaseAuthSession {
        val resp = request(
            method = "POST",
            url = config.authBaseUrl + "token?grant_type=id_token",
            body = JSONObject()
                .put("provider", provider)
                .put("id_token", idToken)
                .toString()
        )
        return parseSession(resp)
    }

    private fun parseSession(body: String): SupabaseAuthSession = parseSession(JSONObject(body))

    private fun parseSession(json: JSONObject): SupabaseAuthSession {
        val user = json.optJSONObject("user")
            ?: throw IllegalStateException("Supabase auth response missing user")
        val userId = user.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Supabase auth response missing user.id")
        val email = user.optString("email").takeIf { it.isNotBlank() }

        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 0L)

        if (access.isBlank() || refresh.isBlank() || expiresIn <= 0L) {
            throw IllegalStateException("Supabase auth response missing tokens")
        }

        return SupabaseAuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresInSeconds = expiresIn,
            userId = userId,
            email = email
        )
    }

    private fun request(method: String, url: String, body: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000

            setRequestProperty("apikey", config.anonKey)
            setRequestProperty("Authorization", "Bearer ${config.anonKey}")
            setRequestProperty("Accept", "application/json")

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            } ?: ""

            if (code !in 200..299) {
                throw RuntimeException("Supabase auth failed ($code): $text")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
