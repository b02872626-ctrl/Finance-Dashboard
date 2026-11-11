package com.financeapp.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates Kash without the browser (NAH-35): downloads the APK named in
 * version.json, checks its SHA-256 against the one published next to it,
 * then hands it to Android's own installer. The first time, Android asks
 * the user to allow installs from Kash; [onResume] picks up from there.
 *
 * Tester (sideloaded) installs only: Play installs never see an update
 * ([UpdateChecker.State.updateAvailable] is false for them).
 */
object UpdateInstaller {

    sealed interface Status {
        data object Idle : Status
        /** [progress] 0..1, or null while the size is unknown. */
        data class Downloading(val progress: Float?) : Status
        /** Android needs "Allow from this source" for Kash first. */
        data object NeedsPermission : Status
        /** Downloaded and checked; Android's installer was opened (tap again to reopen it). */
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private const val AUTHORITY_SUFFIX = ".updates"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var ready: File? = null

    /** Update button: download (or reuse a checked download), then install. */
    fun start(context: Context, latest: UpdateChecker.Latest) {
        val app = context.applicationContext
        val apkUrl = latest.apkUrl
        val sha = latest.sha256
        if (apkUrl == null || sha == null) {
            // Older version.json without a direct link: the download page, as before.
            com.financeapp.ui.screens.openDownloadPage(context, latest.url)
            return
        }
        if (job?.isActive == true) return
        val target = File(File(app.cacheDir, "updates"), "Kash-${latest.versionName}.apk")
        if (target.exists() && sha256Of(target) == sha) {
            ready = target
            install(app)
            return
        }
        job = scope.launch {
            _status.value = Status.Downloading(null)
            val result = runCatching { download(apkUrl, target, sha) }
            result.onSuccess { ok ->
                if (ok) { ready = target; install(app) }
                else _status.value = Status.Failed("The download didn't check out, so it wasn't installed. Try again.")
            }.onFailure {
                _status.value = Status.Failed("Couldn't download the update. Check your connection and try again.")
            }
        }
    }

    /** Back from Android's "Allow from this source" screen: carry on if it was allowed. */
    fun onResume(context: Context) {
        if (_status.value == Status.NeedsPermission && canInstall(context)) install(context.applicationContext)
    }

    private fun install(context: Context) {
        val file = ready ?: return
        if (!canInstall(context)) {
            _status.value = Status.NeedsPermission
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        _status.value = if (opened) Status.Ready else Status.Failed("Couldn't open the installer. Try again.")
    }

    private fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Streams to a .part file while hashing; keeps it only when the SHA-256 matches. */
    private fun download(url: String, target: File, expectedSha: String): Boolean {
        target.parentFile?.apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val part = File(target.path + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
        }
        try {
            if (conn.responseCode != 200) return false
            val total = conn.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastShown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total != null) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastShown) { lastShown = pct; _status.value = Status.Downloading(pct / 100f) }
                        }
                    }
                }
            }
            if (hex(digest.digest()) != expectedSha) { part.delete(); return false }
            return part.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    fun sha256Of(file: File): String = file.inputStream().use(::sha256Of)

    fun sha256Of(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
