package com.financeapp

import com.financeapp.data.accounts.AccountOwnership
import com.financeapp.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A phone that gets bank SMS for two other people's accounts besides its owner's (made-up names and numbers). */
class AccountOwnershipTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private var id = 0L
    private fun tx(body: String, ago: Long = 1) = TransactionEntity(
        id = ++id, sender = "CBE", bankName = "Commercial Bank of Ethiopia", type = "DEBIT", amount = 10.0,
        balance = 100.0, counterparty = null, accountNumber = null, refNumber = null, dateTime = now - ago * day,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null, rawBody = body,
    )

    private val mine = List(5) { tx("Dear Abebe your Account 1********1111 has been debited with ETB 10.") }
    private val hana = List(3) { tx("Dear Hana your Account 1********2222 has been debited with ETB 500.00.", ago = 432) }
    private val tsion = List(2) { tx("Dear Tsion your Account 1********3333 has been debited for Shop with ETB 700.", ago = 341) }

    @Test fun readsTheGreeting() {
        assertEquals("hana", AccountOwnership.greetingName("Dear Hana your Account 1****2222 has been debited"))
        assertEquals("abebe", AccountOwnership.greetingName("Dear Abebe, You have transfered ETB 300.00"))
        assertNull(AccountOwnership.greetingName("Dear Customer, your account 1****1 was credited"))
        assertNull(AccountOwnership.greetingName("Your account 1****1 was credited"))
    }

    @Test fun otherPeoplesAccountsAreSpotted() {
        val owners = AccountOwnership.ownerNames(mine + hana + tsion, userName = "Abebe Kebede")
        assertNull(AccountOwnership.otherHolder(mine, owners))
        assertEquals("hana", AccountOwnership.otherHolder(hana, owners))
        assertEquals("tsion", AccountOwnership.otherHolder(tsion, owners))
    }

    @Test fun withoutANameTheMostGreetedNameIsTheOwner() {
        val owners = AccountOwnership.ownerNames(mine + hana + tsion, userName = "")
        assertEquals(setOf("abebe"), owners)
        assertEquals("hana", AccountOwnership.otherHolder(hana, owners))
    }

    @Test fun shortFormsOfTheOwnersNameMatch() {
        val abe = List(2) { tx("Dear Abe your Account 1****4444 has been credited") }
        assertNull(AccountOwnership.otherHolder(abe, setOf("abebech")))
        assertFalse(AccountOwnership.samePerson("hana", "samuel"))
    }

    @Test fun quietForThreeMonthsIsStale() {
        assertTrue(AccountOwnership.isStale(hana, now))
        assertFalse(AccountOwnership.isStale(mine, now))
    }
}
