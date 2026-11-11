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

    // ── NAH-35: in-app download and install ──

    private val sha = "a".repeat(64)

    private fun json(apk: String?, sha256: String?) = """{"versionCode": 49, "versionName": "1.0.29",
        "url": "https://kash-140.pages.dev/download"""" +
        (apk?.let { """, "apk": "$it"""" } ?: "") + (sha256?.let { """, "sha256": "$it"""" } ?: "") + "}"

    @Test
    fun readsTheApkLinkAndChecksum() {
        val l = UpdateChecker.parse(json("https://kash-140.pages.dev/apk/Kash-1.0.29.apk", sha))!!
        assertEquals("https://kash-140.pages.dev/apk/Kash-1.0.29.apk", l.apkUrl)
        assertEquals(sha, l.sha256)
        assertTrue(l.installsInApp)
    }

    @Test
    fun onlyKashsOwnApkWithAValidChecksumInstallsInApp() {
        // Another host, a path trick, or a bad checksum: fall back to the download page.
        assertFalse(UpdateChecker.parse(json("https://evil.example/apk/Kash.apk", sha))!!.installsInApp)
        assertFalse(UpdateChecker.parse(json("https://kash-140.pages.dev/apk/../x.apk", sha))!!.installsInApp)
        assertFalse(UpdateChecker.parse(json("https://kash-140.pages.dev/apk/Kash-1.0.29.apk", "1234"))!!.installsInApp)
        // Older version.json without the fields still parses.
        val old = UpdateChecker.parse(json(null, null))!!
        assertFalse(old.installsInApp)
        assertEquals(49, old.versionCode)
    }

    @Test
    fun laterHidesThePopupForThreeDaysForThatVersionOnly() {
        val v29 = Latest(49, "1.0.29", UpdateChecker.DOWNLOAD_URL)
        val now = 1_000_000_000L
        val snoozed = UpdateChecker.State(installedCode = 48, latest = v29, popupSnoozedCode = 49,
            popupSnoozedUntil = now + UpdateChecker.POPUP_SNOOZE_MS)
        assertNull(snoozed.popupVersion(now))
        assertEquals(v29, snoozed.popupVersion(now + UpdateChecker.POPUP_SNOOZE_MS))
        // A newer build than the one snoozed shows right away.
        val v30 = Latest(50, "1.0.30", UpdateChecker.DOWNLOAD_URL)
        assertEquals(v30, snoozed.copy(latest = v30).popupVersion(now))
        // Never for Play installs or when up to date.
        assertNull(UpdateChecker.State(installedCode = 48, latest = v29, fromPlay = true).popupVersion(now))
        assertNull(UpdateChecker.State(installedCode = 49, latest = v29).popupVersion(now))
    }

    @Test
    fun checksumOfADownload() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            UpdateInstaller.sha256Of("hello".byteInputStream()),
        )
    }
}
