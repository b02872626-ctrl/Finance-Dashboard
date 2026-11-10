package com.financeapp

import com.financeapp.ui.screens.notificationsRowValue
import org.junit.Assert.assertEquals
import org.junit.Test

/** NAH-15: Settings must say "Blocked" when the phone stops Kash notifications. */
class NotificationsRowValueTest {
    @Test fun `on when Kash and the phone both allow them`() = assertEquals("On", notificationsRowValue(true, true))
    @Test fun `blocked when the phone refuses them`() = assertEquals("Blocked", notificationsRowValue(true, false))
    @Test fun `off when Kash's own switch is off`() {
        assertEquals("Off", notificationsRowValue(false, true))
        assertEquals("Off", notificationsRowValue(false, false))
    }
}
