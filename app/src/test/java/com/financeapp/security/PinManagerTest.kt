package com.financeapp.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinManagerTest {

    @Test
    fun isValidPin_acceptsOnlyFourDigits() {
        assertTrue(PinManager.isValidPin("0000"))
        assertTrue(PinManager.isValidPin("4821"))
        assertFalse(PinManager.isValidPin("123"))
        assertFalse(PinManager.isValidPin("12345"))
        assertFalse(PinManager.isValidPin("12a4"))
        assertFalse(PinManager.isValidPin(""))
    }

    @Test
    fun hash_isDeterministicPerSalt_andSaltSensitive() {
        val salt = ByteArray(16) { it.toByte() }
        val other = ByteArray(16) { (it + 1).toByte() }
        val h = PinManager.hash("1234", salt)
        assertEquals(32, h.size)
        assertArrayEquals(h, PinManager.hash("1234", salt))
        assertFalse(h.contentEquals(PinManager.hash("1235", salt)))
        assertFalse(h.contentEquals(PinManager.hash("1234", other)))
    }
}
