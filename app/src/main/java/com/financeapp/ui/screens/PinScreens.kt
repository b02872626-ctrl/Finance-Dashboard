package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeapp.security.PinManager
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashPinCountdown
import com.financeapp.ui.kash.components.KashPinDots
import com.financeapp.ui.kash.components.KashPinKeys
import com.financeapp.ui.kash.components.KashPinMessage
import com.financeapp.ui.kash.components.KashSpinner
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.Haptic
import com.financeapp.ui.kash.rememberHaptic
import kotlinx.coroutines.delay

// =============================================================================
// 4-digit app PIN: create, unlock and change (AppPinCreate, AppPinConfirm,
// AppUnlock, AppUnlockLocked, AppForgotPin, AppChangePin). All slate.
// Logic lives in security/PinManager.kt.
// =============================================================================

/**
 * Slate PIN page: a 44dp header row, the lock tile + title + subtitle + dots
 * + message centred in the free space, and the keypad at the bottom.
 * Collects digits and calls [onComplete] with all four, then clears.
 */
@Composable
private fun PinPage(
    title: String,
    subtitle: String,
    message: String?,
    countdown: String?,
    enabled: Boolean,
    shakeKey: Int,
    onComplete: (String) -> Unit,
    header: @Composable () -> Unit = { Box(Modifier.height(KashSize.IconButton)) },
    footer: (@Composable () -> Unit)? = null,
) {
    KashSystemBars(darkIcons = false)
    var entry by remember { mutableStateOf("") }
    val haptic = rememberHaptic()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(KashColors.Slate)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = KashSpace.InsetOnboarding, end = KashSpace.InsetOnboarding, top = KashSpace.HeaderTop,
                bottom = if (footer != null) 12.dp else 24.dp),
    ) {
        header()
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(KashShapes.Control)
                    .background(KashColors.GhostFill)
                    .border(1.dp, KashColors.GhostLine, KashShapes.Control),
                contentAlignment = Alignment.Center,
            ) { KashIconView(KashIcon.Lock, size = 22.dp, tint = KashColors.OnSlate) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title, style = KashType.TitleM, color = KashColors.OnSlate, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    subtitle, style = KashType.BodyL, color = KashColors.OnSlateMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                KashPinDots(entry.length, offset = shake.value.dp)
                when {
                    countdown != null -> KashPinCountdown(countdown)
                    message != null -> KashPinMessage(message)
                }
            }
        }
        KashPinKeys(
            disabled = !enabled,
            onDigit = { d ->
                if (entry.length < PinManager.PIN_LENGTH) {
                    haptic(Haptic.TAP)
                    entry += d
                    if (entry.length == PinManager.PIN_LENGTH) {
                        val pin = entry
                        entry = ""
                        onComplete(pin)
                    }
                }
            },
            onDelete = { if (entry.isNotEmpty()) { haptic(Haptic.TAP); entry = entry.dropLast(1) } },
        )
        if (footer != null) Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { footer() }
    }
}

private fun wrongPinMessage(attemptsLeft: Int) =
    if (attemptsLeft == 1) "Wrong PIN. 1 attempt left." else "Wrong PIN. $attemptsLeft attempts left."

private fun countdownText(secondsLeft: Long) = "Try again in %d:%02d".format(secondsLeft / 60, secondsLeft % 60)

/**
 * Create a PIN: enter, then confirm. Used right after onboarding / on first
 * launch with no PIN, and as the second half of Change PIN.
 */
@Composable
fun PinSetupScreen(
    pinManager: PinManager,
    onDone: () -> Unit,
    title: String = "Create a PIN",
    onBack: (() -> Unit)? = null,
) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    val haptic = rememberHaptic()

    PinPage(
        title = if (first == null) title else "Confirm your PIN",
        subtitle = if (first == null) "You'll enter these 4 digits\nevery time you open Kash."
        else "Enter the same 4 digits again.",
        message = error,
        countdown = null,
        enabled = true,
        shakeKey = shakeKey,
        onComplete = { pin ->
            val firstPin = first
            if (firstPin == null) {
                first = pin; error = null
            } else if (pin == firstPin) {
                haptic(Haptic.CONFIRM)
                pinManager.setPin(pin)
                onDone()
            } else {
                haptic(Haptic.REJECT)
                first = null
                error = "PINs didn't match. Start again."
                shakeKey++
            }
        },
        header = {
            if (onBack != null) KashTopBar("Change PIN", onBack = onBack)
            else Box(Modifier.height(KashSize.IconButton))
        },
    )
}

/**
 * Shown on app open and after more than 30 s in the background.
 * [onForgot] signs the user out (after confirmation); they set a new PIN
 * after signing back in.
 */
@Composable
fun PinLockScreen(
    pinManager: PinManager,
    onUnlocked: () -> Unit,
    onForgot: () -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(pinManager.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmForgot by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()

    if (unlocking) {
        UnlockLoading()
        LaunchedEffect(Unit) { delay(UNLOCK_LOADING_MS); onUnlocked() }
        return
    }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(250)
        }
        if (lockedUntil > 0) { lockedUntil = 0; message = null }
    }
    val lockedOut = lockedUntil > now
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)

    PinPage(
        title = if (lockedOut) "Too many tries" else "Welcome back",
        subtitle = if (lockedOut) "For your safety, wait a moment\nbefore trying again." else "Enter your PIN to unlock Kash.",
        message = message,
        countdown = if (lockedOut) countdownText(secondsLeft) else null,
        enabled = !lockedOut,
        shakeKey = shakeKey,
        onComplete = { pin ->
            when (val r = pinManager.verify(pin)) {
                PinManager.Result.Ok -> { haptic(Haptic.CONFIRM); unlocking = true }
                is PinManager.Result.Wrong -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = wrongPinMessage(r.attemptsLeft)
                }
                is PinManager.Result.LockedOut -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = null
                    now = System.currentTimeMillis(); lockedUntil = r.untilMs
                }
            }
        },
        header = {
            Box(Modifier.fillMaxWidth().height(KashSize.IconButton), contentAlignment = Alignment.Center) {
                Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate)
            }
        },
        footer = {
            KashButton("Forgot PIN?", { confirmForgot = true }, Modifier.fillMaxWidth(), KashButtonVariant.QuietOnSlate)
        },
    )

    if (confirmForgot) {
        ForgotPinDialog(
            onSignInAgain = { confirmForgot = false; onForgot() },
            onCancel = { confirmForgot = false },
        )
    }
}

private const val UNLOCK_LOADING_MS = 900L

/** Brief slate hold after a correct PIN: wordmark + spinner, then the app. */
@Composable
private fun UnlockLoading() {
    KashSystemBars(darkIcons = false)
    Column(
        Modifier
            .fillMaxSize()
            .background(KashColors.Slate)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate)
        KashSpinner(size = 24.dp, color = KashColors.OnSlate, label = "Opening Kash")
        Text("Opening your accounts…", style = KashType.Body, color = KashColors.OnSlateMuted)
    }
}

/** AppForgotPin. */
@Composable
private fun ForgotPinDialog(onSignInAgain: () -> Unit, onCancel: () -> Unit) {
    KashDialog(
        title = "Forgot your PIN?",
        description = "Sign in to your Kash account again, then choose a new PIN.",
        onDismiss = onCancel,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(KashShapes.Card)
                .background(KashColors.SurfaceRaised)
                .border(1.dp, KashColors.Line, KashShapes.Card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KashIconView(KashIcon.Lock, size = 20.dp, tint = KashColors.Ink)
            Text("Your transactions on this phone stay where they are.", style = KashType.Body, color = KashColors.Ink)
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KashButton("Sign in again", onSignInAgain, Modifier.fillMaxWidth())
            KashButton("Cancel", onCancel, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        }
    }
}

/** Settings → Change PIN: current PIN, then new PIN + confirm. */
@Composable
fun ChangePinScreen(
    pinManager: PinManager,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var verified by remember { mutableStateOf(!pinManager.hasPin()) }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(pinManager.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = rememberHaptic()
    val context = LocalContext.current

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(250) }
        if (lockedUntil > 0) { lockedUntil = 0; message = null }
    }

    if (verified) {
        PinSetupScreen(
            pinManager = pinManager,
            title = "Choose a new PIN",
            onBack = onBack,
            onDone = {
                android.widget.Toast.makeText(context, "PIN changed", android.widget.Toast.LENGTH_SHORT).show()
                onDone()
            },
        )
        return
    }
    val lockedOut = lockedUntil > now
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    PinPage(
        title = if (lockedOut) "Too many tries" else "Enter your current PIN",
        subtitle = if (lockedOut) "For your safety, wait a moment\nbefore trying again." else "Then you'll choose a new one.",
        message = message,
        countdown = if (lockedOut) countdownText(secondsLeft) else null,
        enabled = !lockedOut,
        shakeKey = shakeKey,
        onComplete = { pin ->
            when (val r = pinManager.verify(pin)) {
                PinManager.Result.Ok -> verified = true
                is PinManager.Result.Wrong -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = wrongPinMessage(r.attemptsLeft)
                }
                is PinManager.Result.LockedOut -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = null
                    now = System.currentTimeMillis(); lockedUntil = r.untilMs
                }
            }
        },
        header = { KashTopBar("Change PIN", onBack = onBack) },
    )
}
