package com.financeapp

import com.financeapp.remote.ProductAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAnalyticsTest {

    @Test
    fun errorTextLosesAmountsAndQuotedSms() {
        val out = ProductAnalytics.sanitize(mapOf(
            "error" to "NumberFormatException: For input string: \"12,500.00\"",
            "summary" to "Parse failed for 1000123456789"
        ))
        assertEquals("NumberFormatException: For input string: \"…\"", out["error"])
        assertEquals("Parse failed for #", out["summary"])
    }

    @Test
    fun countsAndFlagsAreKeptAndNullsDropped() {
        val out = ProductAnalytics.sanitize(mapOf(
            "source" to "worker", "messages" to 240, "first_run" to true, "scope" to null
        ))
        assertEquals(mapOf("source" to "worker", "messages" to 240, "first_run" to true), out)
        assertFalse(out.containsKey("scope"))
    }

    @Test
    fun sessionReplayHidesAllTextAndImagesAndLogs() {
        val c = ProductAnalytics.replayConfig()
        assertTrue("text and text fields must be masked", c.maskAllTextInputs)
        assertTrue("images must be masked", c.maskAllImages)
        assertFalse("logcat must not be sent", c.captureLogcat)
    }
}
