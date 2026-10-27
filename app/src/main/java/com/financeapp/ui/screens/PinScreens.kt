package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.security.PinManager
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.Haptic
import com.financeapp.ui.theme.pressScale
import com.financeapp.ui.theme.rememberHaptic
import com.financeapp.ui.theme.rememberTactileSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// =============================================================================
// 4-digit app PIN: setup, lock screen and change flow. All three share
// PinPad (title + 4 dots + numeric keypad). See security/PinManager.kt.
// =============================================================================

private val PinBg       = AppBackground
private val PinCard     = Color(0xFF231E1A)
private val PinHeadline = AppBlue
private val PinCoral    = CoralPrimary
private val PinMuted    = Color(0xFFB7B3AC)
private val PinFaint    = Color(0xFF7C7872)

/**
 * Title, subtitle, 4 dots and a 3×4 keypad. Calls [onComplete] once 4 digits
 * are entered and clears itself; [shakeKey] changes trigger a shake (wrong PIN).
 */
@Composable
private fun PinPad(
    title: String,
    subtitle: String,
    message: String?,
    messageIsError: Boolean,
    enabled: Boolean,
    shakeKey: Int,
    onComplete: (String) -> Unit,
    topBar: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {}
) {
    var entry by remember { mutableStateOf("") }
    val haptic = rememberHaptic()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == 0) return@LaunchedEffect
        for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
    }

    fun press(d: Char) {
        if (!enabled || entry.length >= PinManager.PIN_LENGTH) return
        haptic(Haptic.TAP)
        entry += d
        if (entry.length == PinManager.PIN_LENGTH) {
            val pin = entry
            entry = ""
            onComplete(pin)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PinBg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        topBar()
        Spacer(Modifier.weight(0.7f))
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(PinCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield),
                contentDescription = null,
                tint = PinCoral,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = PinHeadline, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = PinMuted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }
        ) {
            repeat(PinManager.PIN_LENGTH) { i ->
                val filled = i < entry.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) PinHeadline else Color.Transparent)
                        .border(2.dp, if (filled) PinHeadline else PinFaint, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message ?: " ",
            color = if (messageIsError) PinCoral else PinMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(36.dp)
        )
        Spacer(Modifier.weight(0.5f))

        val rows = listOf("123", "456", "789")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { d -> Key(label = d.toString(), enabled = enabled) { press(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Spacer(Modifier.size(76.dp))
                Key(label = "0", enabled = enabled) { press('0') }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled && entry.isNotEmpty()) {
                            haptic(Haptic.TAP); entry = entry.dropLast(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = if (entry.isNotEmpty()) PinHeadline else PinFaint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        footer()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    val ix = rememberTactileSource()
    Box(
        modifier = Modifier
            .size(76.dp)
            .pressScale(ix)
            .clip(CircleShape)
            .background(PinCard)
            .clickable(interactionSource = ix, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) PinHeadline else PinFaint, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Create a PIN: enter, then confirm. Used right after onboarding / on first
 * launch with no PIN, and as the second half of Change PIN.
 */
@Composable
fun PinSetupScreen(
    pinManager: PinManager,
    onDone: () -> Unit,
    title: String = "Create a PIN",
    onBack: (() -> Unit)? = null
) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    val haptic = rememberHaptic()

    PinPad(
        title = if (first == null) title else "Confirm your PIN",
        subtitle = if (first == null) "You'll enter this 4-digit PIN every time you open Kash."
                   else "Enter the same 4 digits again.",
        message = error,
        messageIsError = error != null,
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
                error = "PINs didn't match. Try again."
                shakeKey++
            }
        },
        topBar = { if (onBack != null) PinBackBar(onBack) }
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
    onForgot: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(pinManager.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmForgot by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()

    // Lockout countdown.
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(250)
        }
        if (lockedUntil > 0) { lockedUntil = 0; message = null }
    }
    val lockedOut = lockedUntil > now
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)

    PinPad(
        title = "Enter your PIN",
        subtitle = "Unlock Kash",
        message = if (lockedOut) "Too many wrong attempts. Try again in ${secondsLeft}s." else message,
        messageIsError = lockedOut || message != null,
        enabled = !lockedOut,
        shakeKey = shakeKey,
        onComplete = { pin ->
            when (val r = pinManager.verify(pin)) {
                PinManager.Result.Ok -> { haptic(Haptic.CONFIRM); onUnlocked() }
                is PinManager.Result.Wrong -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = if (r.attemptsLeft == 1) "Wrong PIN. 1 attempt left." else "Wrong PIN. ${r.attemptsLeft} attempts left."
                }
                is PinManager.Result.LockedOut -> {
                    haptic(Haptic.REJECT); shakeKey++
                    now = System.currentTimeMillis(); lockedUntil = r.untilMs
                }
            }
        },
        footer = {
            Text(
                "Forgot PIN?",
                color = PinMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { confirmForgot = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    )

    if (confirmForgot) {
        AlertDialog(
            onDismissRequest = { confirmForgot = false },
            containerColor = PinCard,
            title = { Text("Forgot your PIN?", color = PinHeadline, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "To set a new PIN, sign in to your Kash account again. " +
                        "Your transactions on this phone stay where they are.",
                    color = PinMuted, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmForgot = false; onForgot() }) {
                    Text("Sign in again", color = PinCoral, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgot = false }) { Text("Cancel", color = PinHeadline) }
            }
        )
    }
}

/** Settings → Change PIN: current PIN, then new PIN + confirm. */
@Composable
fun ChangePinScreen(
    pinManager: PinManager,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var verified by remember { mutableStateOf(!pinManager.hasPin()) }
    var message by remember { mutableStateOf<String?>(null) }
    var shakeKey by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(pinManager.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

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
            }
        )
        return
    }
    val lockedOut = lockedUntil > now
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    PinPad(
        title = "Enter your current PIN",
        subtitle = "Then choose a new one.",
        message = if (lockedOut) "Too many wrong attempts. Try again in ${secondsLeft}s." else message,
        messageIsError = lockedOut || message != null,
        enabled = !lockedOut,
        shakeKey = shakeKey,
        onComplete = { pin ->
            when (val r = pinManager.verify(pin)) {
                PinManager.Result.Ok -> scope.launch { verified = true }
                is PinManager.Result.Wrong -> {
                    haptic(Haptic.REJECT); shakeKey++
                    message = if (r.attemptsLeft == 1) "Wrong PIN. 1 attempt left." else "Wrong PIN. ${r.attemptsLeft} attempts left."
                }
                is PinManager.Result.LockedOut -> {
                    haptic(Haptic.REJECT); shakeKey++
                    now = System.currentTimeMillis(); lockedUntil = r.untilMs
                }
            }
        },
        topBar = { PinBackBar(onBack) }
    )
}

@Composable
private fun PinBackBar(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PinCard)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back_dark),
                contentDescription = "Back",
                tint = PinHeadline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
