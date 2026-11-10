package com.financeapp.notifications

import com.financeapp.notifications.UpdateChecker.Latest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val v20 = Latest(40, "1.0.20", UpdateChecker.DOWNLOAD_URL)

    @Test
    fun parsesTheSiteFile() {
        val json = java.io.File("../Web/version.json").takeIf { it.exists() }?.readText()
            ?: java.io.File("Web/version.json").readText()
        val latest = UpdateChecker.parse(json)!!
        assertTrue(latest.versionCode > 0)
        assertTrue(latest.url.startsWith("https://"))
    }

    @Test
    fun siteFileMatchesTheBuild() {
        val json = java.io.File("../Web/version.json").takeIf { it.exists() }?.readText()
            ?: java.io.File("Web/version.json").readText()
        val latest = UpdateChecker.parse(json)!!
        assertEquals(com.financeapp.BuildConfig.VERSION_CODE, latest.versionCode)
        assertEquals(com.financeapp.BuildConfig.VERSION_NAME, latest.versionName)
    }

    @Test
    fun rejectsBrokenFiles() {
        assertNull(UpdateChecker.parse("not json"))
        assertNull(UpdateChecker.parse("""{"versionName":"1.0.20"}"""))
        assertNull(UpdateChecker.parse("""{"versionCode":40,"versionName":""}"""))
        assertNull(UpdateChecker.parse("""{"versionCode":40,"versionName":"1.0.20","url":"http://evil"}"""))
    }

    @Test
    fun defaultsTheUrlToTheDownloadPage() {
        assertEquals(UpdateChecker.DOWNLOAD_URL, UpdateChecker.parse("""{"versionCode":40,"versionName":"1.0.20"}""")!!.url)
    }

    @Test
    fun notifiesOnceForANewerBuild() {
        assertTrue(UpdateChecker.shouldNotify(39, v20, 0, null))
        assertTrue(UpdateChecker.shouldNotify(39, v20, 39, "com.google.android.packageinstaller"))
        assertFalse("already told", UpdateChecker.shouldNotify(39, v20, 40, null))
    }

    @Test
    fun staysQuietWhenUpToDateOrFromPlay() {
        assertFalse(UpdateChecker.shouldNotify(40, v20, 0, null))
        assertFalse(UpdateChecker.shouldNotify(41, v20, 0, null))
        assertFalse(UpdateChecker.shouldNotify(39, null, 0, null))
        assertFalse(UpdateChecker.shouldNotify(39, v20, 0, "com.android.vending"))
    }

    @Test
    fun readsReleaseNotes() {
        val latest = UpdateChecker.parse(
            """{"versionCode":44,"versionName":"1.0.24","url":"https://kash-140.pages.dev/download",
               "notes":["BoA and Berhan transactions show up again","  ",""]}"""
        )!!
        assertEquals(listOf("BoA and Berhan transactions show up again"), latest.notes)
        assertEquals(emptyList<String>(), UpdateChecker.parse("""{"versionCode":40,"versionName":"1.0.20"}""")!!.notes)
    }

    @Test
    fun cardShowsForANewerTesterBuildUntilLater() {
        val s = UpdateChecker.State(installedCode = 39, installedName = "1.0.19", latest = v20)
        assertTrue(s.updateAvailable)
        assertEquals(v20, s.cardVersion)
        assertNull("Later hides it", s.copy(dismissedCode = 40).cardVersion)
        assertEquals("a newer build shows again", v20.copy(versionCode = 41), s.copy(dismissedCode = 40, latest = v20.copy(versionCode = 41)).cardVersion)
    }

    @Test
    fun noCardWhenUpToDateOrFromPlay() {
        assertNull(UpdateChecker.State(installedCode = 40, latest = v20).cardVersion)
        assertNull(UpdateChecker.State(installedCode = 39, latest = null).cardVersion)
        val play = UpdateChecker.State(installedCode = 39, latest = v20, fromPlay = true)
        assertFalse(play.updateAvailable)
        assertNull(play.cardVersion)
    }

    @Test
    fun settingsRowSaysWhereThingsStand() {
        assertEquals("1.0.20 ready", com.financeapp.ui.screens.updatesRowValue(UpdateChecker.State(installedCode = 39, latest = v20)))
        assertEquals("Up to date", com.financeapp.ui.screens.updatesRowValue(UpdateChecker.State(installedCode = 40, latest = v20, checkedAt = 1L)))
        assertEquals("Google Play", com.financeapp.ui.screens.updatesRowValue(UpdateChecker.State(fromPlay = true)))
        assertEquals("1.0.19", com.financeapp.ui.screens.updatesRowValue(UpdateChecker.State(installedName = "1.0.19")))
    }
}
