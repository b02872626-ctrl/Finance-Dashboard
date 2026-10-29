package com.financeapp

import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountGroupingTest {

    private val cbe = "Commercial Bank of Ethiopia"

    private fun tx(bank: String, account: String?, dateTime: Long, balance: Double? = null) =
        TransactionEntity(
            sender = "CBE",
            bankName = bank,
            type = "DEBIT",
            amount = 10.0,
            balance = balance,
            counterparty = null,
            accountNumber = account,
            refNumber = null,
            dateTime = dateTime,
            serviceCharge = null,
            vat = null,
            disasterFund = null,
            totalCharged = null,
            rawBody = ""
        )

    @Test
    fun `suffix ignores mask length`() {
        assertEquals("8166", AccountGrouping.accountSuffix("1*****8166"))
        assertEquals("8166", AccountGrouping.accountSuffix("1********8166."))
        assertNull(AccountGrouping.accountSuffix("1***"))
        assertNull(AccountGrouping.accountSuffix(null))
    }

    @Test
    fun `single account bank stays one group with every row`() {
        val groups = AccountGrouping.group(listOf(
            tx(cbe, "1*****6155", 1),
            tx(cbe, "1********6155", 2),
            tx(cbe, null, 3)
        ))
        assertEquals(1, groups.size)
        assertEquals(cbe, groups[0].label)
        assertEquals(3, groups[0].transactions.size)
        assertNull(groups[0].accountSuffix)
        assertEquals("1********6155", groups[0].accountNumber)
    }

    @Test
    fun `two accounts at one bank split and number by first seen`() {
        val groups = AccountGrouping.group(listOf(
            tx(cbe, "1********8506", 50),
            tx(cbe, "1********6155", 10),
            tx(cbe, "1********6155", 60),
            tx(cbe, null, 70),
            tx("Telebirr", null, 5)
        ))
        val cbeGroups = groups.filter { it.bankName == cbe }
        assertEquals(listOf("CBE 1", "CBE 2"), cbeGroups.map { it.label })
        assertEquals(listOf("6155", "8506"), cbeGroups.map { it.accountSuffix })
        assertEquals(listOf(2, 1), cbeGroups.map { it.transactions.size })
        assertEquals(listOf("$cbe#6155", "$cbe#8506"), cbeGroups.map { it.key })
        assertEquals("Telebirr", groups.single { it.bankName == "Telebirr" }.label)
    }
}
