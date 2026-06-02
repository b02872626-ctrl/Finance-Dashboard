package com.financeapp

import com.financeapp.data.categorize.MerchantKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantKeyTest {

    @Test fun `same merchant with date noise collapses to one key`() {
        assertEquals(
            MerchantKey.normalize("KALDIS COFFEE BOLE on 06/04/26"),
            MerchantKey.normalize("Kaldis Coffee Bole")
        )
    }

    @Test fun `trailing BRANCH and BR are stripped`() {
        val k = MerchantKey.normalize("Awash Bank Megenagna Branch")
        assertEquals("AWASH BANK MEGENAGNA", k)

        val k2 = MerchantKey.normalize("CBE Bole Br.")
        assertEquals("CBE BOLE", k2)
    }

    @Test fun `SUPERMARKET suffix is collapsed`() {
        assertEquals(
            MerchantKey.normalize("Shola Super Market"),
            MerchantKey.normalize("Shola Supermarket")
        )
    }

    @Test fun `phone numbers and ETB amounts are stripped`() {
        val k = MerchantKey.normalize(
            "You paid 845.00 ETB to LUCY RESTAURANT on 06/04/26 from 0912345678"
        )
        assertEquals("YOU PAID LUCY RESTAURANT", k)
    }

    @Test fun `Telebirr reference codes are stripped`() {
        assertEquals(
            MerchantKey.normalize("ETHIOPIAN ELECTRIC POWER Ref: BP4421"),
            MerchantKey.normalize("Ethiopian Electric Power")
        )
    }

    @Test fun `different branches stay distinct`() {
        assertNotEquals(
            MerchantKey.normalize("Kaldis Coffee Bole"),
            MerchantKey.normalize("Kaldis Coffee Megenagna")
        )
    }

    @Test fun `empty or garbage input returns null`() {
        assertNull(MerchantKey.normalize(""))
        assertNull(MerchantKey.normalize("   "))
        assertNull(MerchantKey.normalize(null))
        assertNull(MerchantKey.normalize("12/04/26"))           // only a date
        assertNull(MerchantKey.normalize("845.00 ETB"))         // only money
        assertNull(MerchantKey.normalize("a"))                  // 1 alnum char
    }

    @Test fun `person names with apostrophes preserved`() {
        assertEquals(
            MerchantKey.normalize("Kaldi's Coffee"),
            MerchantKey.normalize("Kaldi's Coffee")
        )
        assertEquals("KALDI'S COFFEE", MerchantKey.normalize("Kaldi's Coffee"))
    }

    @Test fun `bracketed phone tail is stripped`() {
        val k = MerchantKey.normalize("NAHUSENAY GEBREAMLAK (2519****1234)")
        assertEquals("NAHUSENAY GEBREAMLAK", k)
    }

    @Test fun `POS unbranded merchants keep their masked id`() {
        // 6XX2218 carries merchant identity for unbranded POS — must NOT be
        // collapsed away. (It looks like a card-mask but is actually the
        // terminal id in BoA debit SMS.)
        val k = MerchantKey.normalize("POS 6XX2218 ADDIS")
        // The result keeps POS + the digit-id + ADDIS so two SMS from the
        // same terminal map to the same key.
        assertEquals("POS 6XX2218 ADDIS", k)
    }
}
