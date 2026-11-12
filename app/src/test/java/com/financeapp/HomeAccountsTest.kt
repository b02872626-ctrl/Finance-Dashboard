package com.financeapp

import com.financeapp.viewmodel.showsOnHome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which accounts get a card on Home. */
class HomeAccountsTest {
    @Test fun cashNeverShows() {
        assertFalse(showsOnHome("Cash", listOf("manual"), emptySet()))
        assertFalse(showsOnHome(" cash ", listOf("manual"), emptySet()))
    }

    @Test fun aBankSwitchedOffLeavesHome() {
        assertFalse(showsOnHome("Bank of Abyssinia", listOf("BOA", "BOA"), setOf("BOA")))
    }

    @Test fun aBankStillFollowedStays() {
        assertTrue(showsOnHome("Commercial Bank of Ethiopia", listOf("CBE"), setOf("BOA")))
        // Hand-entered rows on a followed bank don't hide it.
        assertTrue(showsOnHome("Commercial Bank of Ethiopia", listOf("CBE", "manual"), setOf("BOA")))
    }
}
