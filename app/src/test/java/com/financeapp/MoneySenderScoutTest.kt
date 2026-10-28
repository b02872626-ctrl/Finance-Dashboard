package com.financeapp

import com.financeapp.sms.MoneySenderScout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneySenderScoutTest {

    @Test fun `phone numbers are never business senders`() {
        assertFalse(MoneySenderScout.isBusinessSender("+251911223344"))
        assertFalse(MoneySenderScout.isBusinessSender("0911223344"))
        assertFalse(MoneySenderScout.isBusinessSender("127"))
        assertTrue(MoneySenderScout.isBusinessSender("AhaduBank"))
        assertTrue(MoneySenderScout.isBusinessSender("M-PESA"))
    }

    @Test fun `bank-shaped SMS look like money`() {
        assertTrue(MoneySenderScout.looksLikeMoney("Dear customer, your account has been credited with ETB 1,500.00. Balance ETB 2,000.00"))
        assertTrue(MoneySenderScout.looksLikeMoney("You have paid 250 Birr to Shoa Supermarket"))
        assertTrue(MoneySenderScout.looksLikeMoney("ሂሳብዎ 500 ብር ገቢ ተደርጓል"))
    }

    @Test fun `promos and codes do not`() {
        assertFalse(MoneySenderScout.looksLikeMoney("Your verification code is 482913"))
        assertFalse(MoneySenderScout.looksLikeMoney("Win up to 10,000 Birr in our holiday lottery!"))
        assertFalse(MoneySenderScout.looksLikeMoney("Happy new year from all of us"))
    }
}
