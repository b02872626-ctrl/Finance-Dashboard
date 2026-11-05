package com.financeapp

import com.financeapp.remote.CrashReporting
import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CrashReportingTest {

    @Test
    fun masksAmountsAccountsAndPhones() {
        val out = CrashReporting.scrubText(
            "Account 1000123456789 debited with ETB 12,500.00, call 0911223344"
        )
        assertFalse(out.contains(Regex("\\d")))
        assertEquals("Account # debited with ETB #, call #", out)
    }

    @Test
    fun masksQuotedSmsFragments() {
        assertEquals(
            "For input string: \"…\"",
            CrashReporting.scrubText("For input string: \"Birr 1,250.00 from ABEBE\"")
        )
    }

    @Test
    fun masksMaskedAccountNumbers() {
        assertEquals("Acct #", CrashReporting.scrubText("Acct 1*****6789"))
    }

    @Test
    fun keepsMessagesWithoutNumbers() {
        assertEquals(
            "Room cannot verify the data integrity",
            CrashReporting.scrubText("Room cannot verify the data integrity")
        )
    }

    @Test
    fun scrubDropsBreadcrumbsUserDetailsAndNumbers() {
        val event = SentryEvent().apply {
            addBreadcrumb(Breadcrumb("SMS from CBE: ETB 500.00"))
            setExtra("sms", "Dear customer your account 1000123 was credited")
            user = User().apply {
                id = "install-abc"
                email = "someone@example.com"
                username = "Abebe"
                ipAddress = "196.188.1.1"
            }
            message = Message().apply { formatted = "Failed on balance 9,999.10" }
            exceptions = listOf(SentryException().apply {
                type = "NumberFormatException"
                value = "For input string: \"1,234.00\""
            })
        }

        val out = CrashReporting.scrub(event)

        assertNull(out.breadcrumbs)
        assertNull(out.getExtra("sms"))
        assertEquals("install-abc", out.user?.id)
        assertNull(out.user?.email)
        assertNull(out.user?.username)
        assertNull(out.user?.ipAddress)
        assertEquals("Failed on balance #", out.message?.formatted)
        assertEquals("For input string: \"…\"", out.exceptions?.single()?.value)
    }
}
