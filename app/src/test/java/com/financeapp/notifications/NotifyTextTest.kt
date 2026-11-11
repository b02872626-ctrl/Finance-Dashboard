package com.financeapp.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifyTextTest {
    @Test fun underAThousandStaysAsIs() {
        assertEquals("250", NotifyText.k(250.0))
        assertEquals("12.50", NotifyText.k(12.5))
        assertEquals("999", NotifyText.k(999.0))
        assertEquals("34.83", NotifyText.k(-34.83))
    }

    @Test fun thousandsBecomeK() {
        assertEquals("1k", NotifyText.k(1000.0))
        assertEquals("1.2k", NotifyText.k(1200.0))
        assertEquals("1.3k", NotifyText.k(1250.0))
        assertEquals("32k", NotifyText.k(32000.0))
        assertEquals("16.1k", NotifyText.k(16083.24))
        assertEquals("1m", NotifyText.k(999_960.0))
        assertEquals("1.2m", NotifyText.k(1_200_000.0))
    }

    @Test fun firstName() {
        assertEquals("Natnael", NotifyText.firstName("Natnael Getinet Dagne"))
        assertEquals("Natty", NotifyText.firstName("  Natty "))
        assertNull(NotifyText.firstName(""))
        assertNull(NotifyText.firstName(null))
    }
}
