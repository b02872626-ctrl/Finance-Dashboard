package com.financeapp.ui.screens

/** What the sign-in sheet accepts. Pure; see AuthRulesTest. */
object AuthRules {
    /** A new password; Supabase's floor is 6, the approved copy says 8. */
    const val MIN_NEW_PASSWORD = 8
    /** Existing accounts may have shorter ones. */
    const val MIN_PASSWORD = 6

    fun validateEmail(email: String, looksLikeEmail: (String) -> Boolean): String? = when {
        email.isBlank() -> "Enter your email address."
        !looksLikeEmail(email.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun validate(signUp: Boolean, email: String, password: String, looksLikeEmail: (String) -> Boolean): String? {
        if (email.isBlank() || password.isBlank()) return "Email and password are required."
        if (!looksLikeEmail(email.trim())) return "Enter a valid email address."
        val min = if (signUp) MIN_NEW_PASSWORD else MIN_PASSWORD
        if (password.length < min) return "Password must be at least $min characters."
        return null
    }
}
