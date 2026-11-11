package com.financeapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.financeapp.notifications.UpdateInstaller
import com.financeapp.ui.kash.components.KashDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.financeapp.notifications.UpdateChecker
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.util.KashDates

/**
 * Home card: "Kash 1.0.25 is ready" with the release notes, Update
 * (downloads and installs inside Kash) and Later (hides it until a newer
 * build). Shown only for tester installs with a newer build on the site.
 */
@Composable
fun UpdateCard(latest: UpdateChecker.Latest, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).clip(KashShapes.Control).background(KashColors.Sand),
                contentAlignment = Alignment.Center,
            ) { KashIconView(KashIcon.Export, size = 18.dp, tint = KashColors.SlateInk) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Kash ${latest.versionName} is ready", style = KashType.BodyStrong, color = KashColors.Ink)
                Text("Installs over this one. Your data stays.", style = KashType.Caption, color = KashColors.InkMuted)
            }
        }
        ReleaseNotes(latest.notes.take(3))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UpdateButton(latest, Modifier.weight(1f))
            KashButton("Later", onLater, Modifier.weight(1f), KashButtonVariant.Quiet, compact = true)
        }
        UpdateStatusLine()
    }
}

/**
 * Pop-up when Kash opens and a newer tester build is out (NAH-35). "Later"
 * (or closing it) hides it for 3 days for that version; the Home card stays.
 */
@Composable
fun UpdatePopup(latest: UpdateChecker.Latest, onLater: () -> Unit) {
    val status by UpdateInstaller.status.collectAsState()
    val busy = status is UpdateInstaller.Status.Downloading
    KashDialog(
        title = "Kash ${latest.versionName} is ready",
        description = "Installs over this one. Your data stays.",
        onDismiss = onLater,
        dismissible = !busy,
    ) {
        ReleaseNotes(latest.notes.take(4))
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UpdateButton(latest, Modifier.fillMaxWidth())
            if (!busy) KashButton("Later", onLater, Modifier.fillMaxWidth(), KashButtonVariant.Quiet, compact = true)
            UpdateStatusLine()
        }
    }
}

/** Update / Downloading 45% / Allow installs from Kash / Install / Try again. */
@Composable
fun UpdateButton(latest: UpdateChecker.Latest, modifier: Modifier = Modifier, label: String = "Update") {
    val context = LocalContext.current
    val status by UpdateInstaller.status.collectAsState()
    val text = when (val s = status) {
        is UpdateInstaller.Status.Downloading -> s.progress?.let { "Downloading ${(it * 100).toInt()}%" } ?: "Downloading…"
        UpdateInstaller.Status.NeedsPermission -> "Allow installs from Kash"
        UpdateInstaller.Status.Ready -> "Install"
        is UpdateInstaller.Status.Failed -> "Try again"
        UpdateInstaller.Status.Idle -> label
    }
    val busy = status is UpdateInstaller.Status.Downloading
    KashButton(
        text, { UpdateInstaller.start(context, latest) }, modifier, KashButtonVariant.Primary,
        compact = true, busy = busy, enabled = !busy,
    )
}

/** One quiet line under the button saying what is happening. */
@Composable
private fun UpdateStatusLine() {
    val status by UpdateInstaller.status.collectAsState()
    val line = when (val s = status) {
        UpdateInstaller.Status.NeedsPermission -> "Android asks once: turn on \"Allow from this source\" for Kash, then come back."
        UpdateInstaller.Status.Ready -> "Android's installer is open. Tap Install there to finish."
        is UpdateInstaller.Status.Failed -> s.message
        else -> null
    } ?: return
    Text(line, style = KashType.Caption, color = KashColors.InkMuted)
}

@Composable
private fun ReleaseNotes(notes: List<String>) {
    if (notes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = KashType.Body, color = KashColors.InkMuted)
                Text(note, style = KashType.Body, color = KashColors.Ink)
            }
        }
    }
}

/** Opens the download page in the browser; a toast when no browser can. */
fun openDownloadPage(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Couldn't open the download page. Try again later.", Toast.LENGTH_SHORT).show() }
}

/** One line for the Settings row: "Up to date", "1.0.25 ready", "Google Play". */
fun updatesRowValue(s: UpdateChecker.State): String = when {
    s.fromPlay -> "Google Play"
    s.updateAvailable -> "${s.latest!!.versionName} ready"
    s.checkedAt != null -> "Up to date"
    else -> s.installedName
}

/** Body of Settings → Updates (the page frame lives in SettingsScreens). */
@Composable
fun UpdatesContent(s: UpdateChecker.State, cal: String, onCheckNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KashShapes.Card)
                .background(KashColors.SurfaceRaised)
                .border(1.dp, KashColors.Line, KashShapes.Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (title, line) = statusText(s, cal)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = KashType.BodyStrong, color = KashColors.Ink)
                Text(line, style = KashType.Caption, color = KashColors.InkMuted)
            }
            if (s.updateAvailable) {
                val latest = s.latest!!
                if (latest.notes.isNotEmpty()) {
                    Text("What's new", style = KashType.Label, color = KashColors.InkMuted)
                    ReleaseNotes(latest.notes)
                }
                UpdateButton(latest, Modifier.fillMaxWidth(), label = "Update to Kash ${latest.versionName}")
                UpdateStatusLine()
            }
        }
        if (!s.fromPlay) {
            KashButton(
                if (s.checking) "Checking…" else "Check now", onCheckNow, Modifier.fillMaxWidth(),
                KashButtonVariant.Secondary, busy = s.checking, enabled = !s.checking,
            )
        }
        Text(
            "Installed: Kash ${s.installedName} (${s.installedCode})",
            style = KashType.Caption, color = KashColors.InkFaint, modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun statusText(s: UpdateChecker.State, cal: String): Pair<String, String> {
    val checked = s.checkedAt?.let { "Checked ${KashDates.whenShort(it, cal)}, ${KashDates.time(it)}" }
    return when {
        s.fromPlay -> "Updates come from Google Play" to "Play installs new versions of Kash for you."
        s.updateAvailable -> "Kash ${s.latest!!.versionName} is ready" to "Installs over this one. Your data stays."
        s.lastCheckFailed && checked == null -> "Couldn't check yet" to "No connection to the Kash site. Try again when you're online."
        s.lastCheckFailed -> "You're up to date, as of the last check" to "Couldn't reach the Kash site just now. $checked."
        checked != null -> "You're up to date" to "$checked."
        else -> "Not checked yet" to "Kash checks for a new version when it opens and twice a day."
    }
}
