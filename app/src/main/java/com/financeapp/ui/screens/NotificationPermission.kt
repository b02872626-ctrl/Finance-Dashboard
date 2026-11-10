package com.financeapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton

/**
 * Whether Android lets Kash post notifications at all. False when the
 * Android 13+ permission was refused, or notifications were switched off in
 * the phone's settings. Onboarding asks for the permission together with
 * SMS; this is the way back for everyone who said no (NAH-15).
 */
fun systemNotificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** [systemNotificationsAllowed], re-checked each time the screen comes back (e.g. from the phone's settings). */
@Composable
fun rememberSystemNotificationsAllowed(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(systemNotificationsAllowed(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = systemNotificationsAllowed(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

/** Settings row value: Kash's own switch, unless the phone blocks everything. */
fun notificationsRowValue(kashSwitchOn: Boolean, systemAllowed: Boolean): String = when {
    !kashSwitchOn -> "Off"
    !systemAllowed -> "Blocked"
    else -> "On"
}

/** Opens the phone's notification settings for Kash (app details on Android 7 and older). */
fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Card on Settings → Notifications when the phone blocks Kash. The first tap
 * shows Android's own prompt (Android 13+, permission not yet given); if that
 * is refused, or Android no longer shows the prompt, the button opens the
 * phone's settings instead.
 */
@Composable
fun NotificationsBlockedCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var askedHere by rememberSaveable { mutableStateOf(false) }
    // Android's prompt pauses and resumes the screen, so rememberSystemNotificationsAllowed sees the answer.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val canPrompt = !askedHere &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    Column(
        modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Your phone is blocking Kash notifications", style = KashType.BodyStrong, color = KashColors.Ink)
            Text(
                "Money alerts, the evening digest and new-version notices won't show up until you allow them.",
                style = KashType.Caption, color = KashColors.InkMuted,
            )
        }
        KashButton(
            if (canPrompt) "Allow notifications" else "Open phone settings",
            {
                if (canPrompt && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askedHere = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            },
            Modifier.fillMaxWidth(),
            compact = true,
        )
    }
}
