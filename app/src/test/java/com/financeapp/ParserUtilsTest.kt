package com.financeapp

import com.financeapp.parsing.ParserUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUtilsTest {

    @Test
    fun `extractFirstUrl skips google forms links`() {
        val body = """
            Please review this survey first https://forms.gle/abc123
            Your receipt is here https://transactioninfo.ethiotelecom.et/receipt/DD18GI0O82
        """.trimIndent()

        assertEquals(
            "https://transactioninfo.ethiotelecom.et/receipt/DD18GI0O82",
            ParserUtils.extractFirstUrl(body)
        )
    }

    @Test
    fun `extractUrls excludes google forms links entirely`() {
        val body = """
            Share feedback: https://docs.google.com/forms/d/e/example/viewform
            Receipt: https://apps.cbe.com.et:100/?id=FT26090Y2Q8F82338166
        """.trimIndent()

        val urls = ParserUtils.extractUrls(body)

        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("apps.cbe.com.et"))
    }

    @Test
    fun `extractFirstUrl returns null when only google forms link exists`() {
        val body = "Google form = https://forms.gle/abc123"

        assertNull(ParserUtils.extractFirstUrl(body))
    }
}
