package com.financeapp

import com.financeapp.ui.screens.AuthRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRulesTest {
    private val email = { s: String -> "@" in s && "." in s }

    @Test
    fun `a new account needs 8 characters, an existing one 6`() {
        assertEquals("Password must be at least 8 characters.", AuthRules.validate(true, "a@b.co", "1234567", email))
        assertNull(AuthRules.validate(true, "a@b.co", "12345678", email))
        assertNull(AuthRules.validate(false, "a@b.co", "123456", email))
        assertEquals("Password must be at least 6 characters.", AuthRules.validate(false, "a@b.co", "12345", email))
    }

    @Test
    fun `email and password are both needed and the email must look like one`() {
        assertEquals("Email and password are required.", AuthRules.validate(false, "", "123456", email))
        assertEquals("Enter a valid email address.", AuthRules.validate(false, "nope", "123456", email))
        assertEquals("Enter your email address.", AuthRules.validateEmail("  ", email))
        assertNull(AuthRules.validateEmail("a@b.co", email))
    }
}
