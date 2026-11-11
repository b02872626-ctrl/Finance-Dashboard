package com.financeapp.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifyTextTest {
    @Test fun underAThousandStaysAsIs() {
        assertEquals("250", NotifyText.k(250.0))
        assertEquals("12.50", NotifyText.k(12.5))
        assertEquals("999", NotifyText.k(999.0))
        assertEquals("42.50", NotifyText.k(-42.50))
    }

    @Test fun thousandsBecomeK() {
        assertEquals("1k", NotifyText.k(1000.0))
        assertEquals("1.2k", NotifyText.k(1200.0))
        assertEquals("1.3k", NotifyText.k(1250.0))
        assertEquals("32k", NotifyText.k(32000.0))
        assertEquals("16.1k", NotifyText.k(16123.45))
        assertEquals("1m", NotifyText.k(999_960.0))
        assertEquals("1.2m", NotifyText.k(1_200_000.0))
    }

    @Test fun firstName() {
        assertEquals("Dawit", NotifyText.firstName("Dawit Haile Bekele"))
        assertEquals("Dawi", NotifyText.firstName("  Dawi "))
        assertNull(NotifyText.firstName(""))
        assertNull(NotifyText.firstName(null))
    }
}
