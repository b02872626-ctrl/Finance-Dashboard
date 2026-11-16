package com.financeapp

import com.financeapp.remote.ProductAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    // ─── Replay labels ─────────────────────────────────────────────────────
    // Replay masks every text. A few fixed labels (titles, buttons, filter
    // chips, Settings rows) are let through by Modifier.replayLabel(). These
    // tests keep that list short and away from money, names and SMS text.

    private val src = File("src/main/java/com/financeapp")

    private fun kotlinFiles() = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Files where Kash components put replayLabel() on their own label Text. */
    private val labelComponents = setOf("KashReplay.kt", "KashControls.kt", "KashLayout.kt", "KashLists.kt")

    /** Screens allowed to pass showInReplay = true. Add one only after checking each label is fixed text. */
    private val labelScreens = setOf(
        "AccountScreens.kt", "AddScreen.kt", "AnalyticsScreens.kt", "HistoryScreen.kt",
        "PinScreens.kt", "SettingsScreens.kt",
    )

    @Test
    fun onlyKashReplayTalksToPostHogMasking() {
        val offenders = kotlinFiles().filter { f ->
            f.name != "KashReplay.kt" && Regex("postHogUnmask|PostHogMaskModifier|ph-no-mask|ph-no-capture").containsMatchIn(f.readText())
        }
        assertTrue("unmask only through ui/kash/KashReplay.kt: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun replayLabelOnlyInsideApprovedComponents() {
        val offenders = kotlinFiles().filter { it.name !in labelComponents && it.readText().contains("replayLabel(") }
        assertTrue("screens opt in with showInReplay = true, never replayLabel(): ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun showInReplayOnlyOnApprovedScreens() {
        val offenders = kotlinFiles().filter { it.name !in labelScreens && it.readText().contains("showInReplay = true") }
        assertTrue("showInReplay = true outside the approved screens: ${offenders.map { it.name }}", offenders.isEmpty())
    }

    @Test
    fun noReplayLabelOnMoneyOrNames() {
        val risky = Regex("KashMoney|amount|balance|merchant|counterparty|category|bankName|accountNumber|state\\.label|email|name\\b", RegexOption.IGNORE_CASE)
        val quoted = Regex("\"[^\"$]*\"")
        val hits = kotlinFiles().flatMap { f ->
            f.readLines().withIndex()
                // Fixed words in quotes ("Email support") are fine; check the code around them.
                .filter { (_, line) -> line.contains("showInReplay = true") && risky.containsMatchIn(line.replace(quoted, "\"\"")) }
                .map { (i, line) -> "${f.name}:${i + 1}: ${line.trim()}" }
        }
        assertTrue("a replay label may show money or a name:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun moneyComponentsStayMasked() {
        val lists = File(src, "ui/kash/components/KashLists.kt").readText()
        val row = lists.substringAfter("fun KashTransactionRow(").substringBefore("\n@Composable")
        assertFalse("transaction rows must stay masked", row.contains("replayLabel"))
        val basics = File(src, "ui/kash/components/KashBasics.kt").readText()
        assertFalse("account cards and charts must stay masked", basics.contains("replayLabel"))
        val inputs = File(src, "ui/kash/components/KashInputs.kt").readText()
        assertFalse("text fields must stay masked", inputs.contains("replayLabel"))
        val tx = File(src, "ui/kash/KashTx.kt").readText()
        assertFalse("Kash transaction rows must stay masked", tx.contains("replayLabel") || tx.contains("showInReplay"))
    }
}
