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
 * Home card: "Kash 1.0.25 is ready" with the release notes, Update (opens
 * the download page) and Later (hides it until a newer build). Shown only
 * for tester installs with a newer build on the site.
 */
@Composable
fun UpdateCard(latest: UpdateChecker.Latest, onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
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
            KashButton("Update", onUpdate, Modifier.weight(1f), KashButtonVariant.Primary, compact = true)
            KashButton("Later", onLater, Modifier.weight(1f), KashButtonVariant.Quiet, compact = true)
        }
    }
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
fun UpdatesContent(s: UpdateChecker.State, cal: String, onCheckNow: () -> Unit, onDownload: (String) -> Unit) {
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
                KashButton("Download Kash ${latest.versionName}", { onDownload(latest.url) }, Modifier.fillMaxWidth(), compact = true)
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
