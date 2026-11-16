package com.financeapp

import com.financeapp.ui.screens.maskAccount
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Is this account yours?" shows the number masked like the design. Made-up numbers. */
class HomeConfirmAccountTest {
    @Test fun `long numbers keep the first digit and the last four`() {
        assertEquals("1•••••3333", maskAccount("1000123453333"))
        assertEquals("1•••••3333", maskAccount("1****3333"))
    }

    @Test fun `short numbers stay as they are`() {
        assertEquals("1234", maskAccount("1234"))
    }
}
