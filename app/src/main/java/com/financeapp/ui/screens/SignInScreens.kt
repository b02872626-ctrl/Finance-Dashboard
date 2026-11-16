package com.financeapp.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashStepBar
import android.content.Context
import android.content.ContextWrapper
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.financeapp.BuildConfig
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.SupabaseAuthClient
import com.financeapp.remote.SupabaseConfigProvider
import com.financeapp.remote.SupabaseSession
import com.financeapp.remote.SupabaseSignUpResult
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashSpinner
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.kashClickable
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =============================================================================
// Sign in (AppSignIn, AppSignInBusy, AppSignInAgain). The auth calls and every
// message are the ones the old SupabaseAuthScreen used; only the look changed.
// =============================================================================

enum class AuthBusy { None, SignIn, SignUp, Google }

/** Form state + the three auth actions. [onSignedIn] runs once a session is saved. */
@Stable
class AuthFormState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit,
) {
    private val settingsRepo = SettingsRepository(context.applicationContext)
    private val errorRepo = ErrorLogRepository.fromContext(context)
    private val transactionRepo = TransactionRepository(AppDatabase.getInstance(context.applicationContext))
    private val credentialManager = CredentialManager.create(context)

    var email by mutableStateOf(settingsRepo.getSupabaseSession()?.email ?: "")
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    /** Neutral messages (e.g. "check your email to confirm"). */
    var info by mutableStateOf<String?>(null)
    var busy by mutableStateOf(AuthBusy.None)
    /** The reset email is on its way. */
    var resetBusy by mutableStateOf(false)

    val isBusy get() = busy != AuthBusy.None

    fun clearMessages() { error = null; info = null }

    private fun validate(signUp: Boolean): String? =
        AuthRules.validate(signUp, email, password) { Patterns.EMAIL_ADDRESS.matcher(it).matches() }

    /** Forgot password: emails a reset link, then [onSent]. */
    fun sendReset(onSent: () -> Unit) {
        clearMessages()
        AuthRules.validateEmail(email) { Patterns.EMAIL_ADDRESS.matcher(it).matches() }?.let { error = it; return }
        val cfg = SupabaseConfigProvider.fromBuildConfig()
            ?: run { error = "Sign-in isn't available in this version of the app."; return }
        resetBusy = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching { SupabaseAuthClient(cfg).recover(email.trim()) }
            withContext(Dispatchers.Main) {
                resetBusy = false
                result.onSuccess { onSent() }.onFailure { ex ->
                    error = friendlyAuthError(ex, "Couldn't send the link. Please try again.")
                    scope.launch(Dispatchers.IO) { errorRepo.log("Supabase password reset failed", ex) }
                }
            }
        }
    }

    private suspend fun save(session: com.financeapp.remote.SupabaseAuthSession): Boolean {
        settingsRepo.setSupabaseSession(
            SupabaseSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                userId = session.userId,
                email = session.email,
                expiresAtMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L,
            )
        )
        // The name in Settings (used for notifications and own-account
        // matching) comes from the sign-in account unless the user set one.
        session.fullName?.let { if (settingsRepo.getUserName().isBlank()) settingsRepo.setUserName(it) }
        return transactionRepo.countPendingCloudSyncItems() > 0
    }

    private fun finish(shouldSync: Boolean) {
        if (shouldSync) scope.launch(Dispatchers.IO) { SupabaseSyncScheduler.enqueueIfPending(context) }
        onSignedIn()
    }

    fun signIn() {
        clearMessages()
        validate(signUp = false)?.let { error = it; return }
        val cfg = SupabaseConfigProvider.fromBuildConfig()
            ?: run { error = "Sign-in isn't available in this version of the app."; return }
        busy = AuthBusy.SignIn
        scope.launch(Dispatchers.IO) {
            val result = runCatching { save(SupabaseAuthClient(cfg).signInWithPassword(email.trim(), password)) }
            withContext(Dispatchers.Main) {
                busy = AuthBusy.None
                result.onSuccess { finish(it) }.onFailure { ex ->
                    error = friendlyAuthError(ex, "Sign-in failed. Please try again.")
                    scope.launch(Dispatchers.IO) { errorRepo.log("Supabase sign-in failed", ex) }
                }
            }
        }
    }

    fun signUp() {
        clearMessages()
        validate(signUp = true)?.let { error = it; return }
        val cfg = SupabaseConfigProvider.fromBuildConfig()
            ?: run { error = "Sign-in isn't available in this version of the app."; return }
        busy = AuthBusy.SignUp
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                when (val signUp = SupabaseAuthClient(cfg).signUp(email.trim(), password)) {
                    is SupabaseSignUpResult.SignedIn -> save(signUp.session)
                    SupabaseSignUpResult.ConfirmationRequired -> null
                }
            }
            withContext(Dispatchers.Main) {
                busy = AuthBusy.None
                result.onSuccess { shouldSync ->
                    if (shouldSync != null) finish(shouldSync)
                    else info = "Account created. Check your email to confirm, then sign in."
                }.onFailure { ex ->
                    error = friendlyAuthError(ex, "Sign-up failed. Please try again.")
                    scope.launch(Dispatchers.IO) { errorRepo.log("Supabase sign-up failed", ex) }
                }
            }
        }
    }

    fun google() {
        clearMessages()
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank() || webClientId == "YOUR_GOOGLE_SERVER_CLIENT_ID_HERE") {
            error = "Google sign-in isn't available right now. Please use email instead."
            return
        }
        // The button flow (full Google account picker), not the one-tap sheet:
        // on some Samsung phones the sheet answers "no matching credentials"
        // even with Google accounts on the phone and the OAuth client set up.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        busy = AuthBusy.Google
        scope.launch {
            try {
                val activity = findActivity(context)
                    ?: run { error = "Couldn't open Google sign-in. Please try again."; busy = AuthBusy.None; return@launch }
                val credential = credentialManager.getCredential(activity, request).credential
                if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    busy = AuthBusy.None
                    error = "Google sign-in failed. Please try again."
                    return@launch
                }
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val cfg = SupabaseConfigProvider.fromBuildConfig()
                    ?: run { error = "Sign-in isn't available in this version of the app."; busy = AuthBusy.None; return@launch }
                val result = withContext(Dispatchers.IO) {
                    runCatching { save(SupabaseAuthClient(cfg).signInWithIdToken(idToken)) }
                }
                busy = AuthBusy.None
                result.onSuccess { finish(it) }.onFailure { ex ->
                    error = friendlyAuthError(ex, "Google sign-in failed. Please try again.")
                    launch(Dispatchers.IO) { errorRepo.log("Google sign-in server error", ex) }
                }
            } catch (e: Exception) {
                busy = AuthBusy.None
                error = when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled"
                    is androidx.credentials.exceptions.NoCredentialException ->
                        "Google sign-in isn't working on this phone right now. Please sign in with email."
                    else -> friendlyAuthError(e, "Google sign-in failed. Please try again.")
                }
                launch(Dispatchers.IO) { errorRepo.log("Google sign-in failed: ${e.javaClass.simpleName}", e) }
            }
        }
    }
}

@Composable
fun rememberAuthFormState(onSignedIn: () -> Unit): AuthFormState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { AuthFormState(context, scope, onSignedIn) }
}

/** Which account the sheet's tabs mean. */
enum class AuthMode(val label: String) { New("New to Kash"), Existing("I have an account") }

private enum class AuthPage { Form, Forgot, Sent }

/**
 * The approved sign-in (canvas section 1, "Sign in"): slate top with the
 * [header] (step bar or wordmark) and the heading; a sheet with the New to
 * Kash / I have an account tabs, email, password with an eye, the main
 * button, then Google. Forgot password is its own page behind the sheet.
 */
@Composable
fun AuthScreen(
    state: AuthFormState,
    startMode: AuthMode,
    header: @Composable () -> Unit,
    body: String = "Your financial history survives a new phone and shows on the web.",
) {
    var mode by remember { mutableStateOf(startMode) }
    var page by remember { mutableStateOf(AuthPage.Form) }
    var reveal by remember { mutableStateOf(false) }
    when (page) {
        AuthPage.Forgot -> {
            ForgotPasswordScreen(state, onBack = { page = AuthPage.Form }, onSent = { page = AuthPage.Sent })
            return
        }
        AuthPage.Sent -> {
            ResetSentScreen(
                email = state.email.trim(),
                onBack = { page = AuthPage.Forgot },
                onBackToSignIn = { page = AuthPage.Form },
                onResend = { state.sendReset {} },
            )
            return
        }
        AuthPage.Form -> Unit
    }
    val existing = mode == AuthMode.Existing
    val submit = { if (existing) state.signIn() else state.signUp() }
    val mainBusy = state.busy == AuthBusy.SignIn || state.busy == AuthBusy.SignUp
    KashSystemBars(darkIcons = false)
    KashScreen(
        heroGap = 22.dp,
        heroBottom = 26.dp,
        expandable = false,
        hero = {
            header()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (existing) "Welcome back" else "Never lose your history", style = KashType.TitleL, color = KashColors.OnSlate)
                Text(body, style = KashType.Body, color = KashColors.OnSlateMuted, modifier = Modifier.widthIn(max = 270.dp))
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(pad),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AuthTabs(mode, enabled = !state.isBusy) { mode = it; state.clearMessages() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KashTextField(
                    value = state.email,
                    onValueChange = { state.email = it; state.clearMessages() },
                    label = "Email",
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                    enabled = !state.isBusy,
                )
                KashTextField(
                    value = state.password,
                    onValueChange = { state.password = it; state.clearMessages() },
                    label = "Password",
                    placeholder = if (existing) "Your password" else "At least ${AuthRules.MIN_NEW_PASSWORD} characters",
                    password = true,
                    reveal = reveal,
                    enabled = !state.isBusy,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailing = { RevealToggle(reveal) { reveal = !reveal } },
                )
                if (existing) {
                    Text(
                        "Forgot password?", style = KashType.Label, color = KashColors.InkMuted,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(KashShapes.Chip)
                            .kashClickable(role = Role.Button) { state.clearMessages(); page = AuthPage.Forgot }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                // Every message on its own line: a Google failure never paints the password field red.
                state.error?.let { Text(it, style = KashType.Caption, color = KashColors.MoneyOut) }
                state.info?.let { Text(it, style = KashType.Caption, color = KashColors.InkMuted) }
            }
            KashButton(
                when {
                    state.busy == AuthBusy.SignIn -> "Signing in…"
                    state.busy == AuthBusy.SignUp -> "Creating your account…"
                    existing -> "Sign in"
                    else -> "Create account"
                },
                submit, Modifier.fillMaxWidth(),
                busy = mainBusy, enabled = !state.isBusy || mainBusy,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(KashColors.LineStrong))
                Text("or continue with", style = KashType.Caption, color = KashColors.InkMuted)
                Box(Modifier.weight(1f).height(1.dp).background(KashColors.LineStrong))
            }
            GoogleButton(onClick = state::google, enabled = !state.isBusy || state.busy == AuthBusy.Google, busy = state.busy == AuthBusy.Google)
            Text(
                "Your bank messages and transactions are synced. Never your personal chats.",
                style = KashType.Caption, color = KashColors.InkMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

/** New to Kash | I have an account, on the well. */
@Composable
private fun AuthTabs(mode: AuthMode, enabled: Boolean, onSelect: (AuthMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KashColors.Well)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AuthMode.entries.forEach { m ->
            val on = m == mode
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) KashColors.SurfaceRaised else Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = { onSelect(m) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label,
                    style = KashType.Label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) KashColors.Ink else KashColors.InkMuted,
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** The eye inside the password field. */
@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(KashShapes.Chip)
            .kashClickable(onClickLabel = if (revealed) "Hide password" else "Show password", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        KashIconView(if (revealed) KashIcon.EyeOff else KashIcon.Eye, size = 18.dp, tint = KashColors.InkMuted)
    }
}

/** Google's blue button with the G on a white tile at the left, as approved. */
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    Box(
        Modifier
            .kashClickable(enabled = enabled, onClickLabel = "Continue with Google", onClick = onClick)
            .fillMaxWidth()
            .height(52.dp)
            .clip(KashShapes.Control)
            .background(Color(0xFF1A73E8)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) KashSpinner(size = 18.dp, color = Color(0xFF1F1F1F)) else GoogleG(22.dp)
        }
        Text(
            "Continue with Google",
            style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = Color.White, maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The four-colour G. */
@Composable
private fun GoogleG(size: Dp) {
    val vector = remember {
        ImageVector.Builder(name = "GoogleG", defaultWidth = 48.dp, defaultHeight = 48.dp, viewportWidth = 48f, viewportHeight = 48f).apply {
            listOf(
                0xFFFFC107 to "M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3l5.7-5.7C34 7.1 29.3 5 24 5 13.5 5 5 13.5 5 24s8.5 19 19 19 19-8.5 19-19c0-1.3-.1-2.5-.4-3.5z",
                0xFFFF3D00 to "M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3l5.7-5.7C34 7.1 29.3 5 24 5c-7.3 0-13.6 4.1-16.7 9.7z",
                0xFF4CAF50 to "M24 43c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.4 34 26.8 35 24 35c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 38.6 16.2 43 24 43z",
                0xFF1976D2 to "M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C36.9 39.2 44 34 44 24c0-1.3-.1-2.5-.4-3.5z",
            ).forEach { (color, d) ->
                addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color(color)))
            }
        }.build()
    }
    Image(vector, contentDescription = null, modifier = Modifier.size(size))
}

/** Forgot password (new): the email, then "Send reset link". */
@Composable
private fun ForgotPasswordScreen(state: AuthFormState, onBack: () -> Unit, onSent: () -> Unit) {
    KashSystemBars(darkIcons = false)
    BackHandler(onBack = onBack)
    KashScreen(
        heroGap = 18.dp,
        expandable = false,
        hero = {
            Row { KashIconButton(KashIcon.Back, "Back", onBack, onSlate = true) }
            Column {
                Text("Reset your password", style = KashType.TitleM, color = KashColors.OnSlate)
                Text("We’ll email you a link to set a new one.", style = KashType.Body, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 8.dp))
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(pad).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            KashTextField(
                value = state.email,
                onValueChange = { state.email = it; state.clearMessages() },
                label = "Email",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                enabled = !state.resetBusy,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { state.sendReset(onSent) }),
            )
            state.error?.let { Text(it, style = KashType.Caption, color = KashColors.MoneyOut) }
            KashButton(
                if (state.resetBusy) "Sending…" else "Send reset link", { state.sendReset(onSent) }, Modifier.fillMaxWidth(),
                busy = state.resetBusy,
            )
            Text(
                "Signed in with Google? Reset it in your Google account.",
                style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = KashColors.InkMuted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Reset link sent (new). */
@Composable
private fun ResetSentScreen(email: String, onBack: () -> Unit, onBackToSignIn: () -> Unit, onResend: () -> Unit) {
    KashSystemBars(darkIcons = false)
    BackHandler(onBack = onBack)
    KashScreen(
        heroGap = 18.dp,
        expandable = false,
        hero = {
            Row { KashIconButton(KashIcon.Back, "Back", onBack, onSlate = true) }
            Column {
                Text("Link sent", style = KashType.TitleM, color = KashColors.OnSlate)
                Text("Open it on this phone to set a new password.", style = KashType.Body, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 8.dp))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(KashColors.Well),
                contentAlignment = Alignment.Center,
            ) { KashIconView(KashIcon.Sms, size = 22.dp, tint = KashColors.Ink) }
            Text("Check your email", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.padding(top = 10.dp))
            Text(
                "We sent a link to $email. It works for 30 minutes.",
                style = KashType.Caption.copy(fontWeight = FontWeight.Medium), color = KashColors.InkMuted,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).widthIn(max = 280.dp),
            )
            KashButton("Back to sign in", onBackToSignIn, Modifier.fillMaxWidth().padding(top = 26.dp))
            Text(
                "Didn’t get it? Send again", style = KashType.Label, color = KashColors.InkMuted,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(KashShapes.Chip)
                    .kashClickable(role = Role.Button, onClick = onResend)
                    .padding(vertical = 6.dp, horizontal = 8.dp),
            )
        }
    }
}

/** Onboarding step 3 of 3 (Sign in). [signInFirst] starts on "I have an account". */
@Composable
fun OnboardingSignInStep(
    signInFirst: Boolean,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val state = rememberAuthFormState(onSignedIn)
    AuthScreen(
        state = state,
        startMode = if (signInFirst) AuthMode.Existing else AuthMode.New,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KashIconButton(KashIcon.Back, "Back", onBack, onSlate = true)
                KashStepBar(current = 3, total = 3, modifier = Modifier.weight(1f), onSlate = true, label = "Step 3 of 3")
                Spacer(Modifier.size(44.dp))
            }
        },
    )
}

/**
 * Signed out after onboarding: the session expired, the user logged out, or
 * chose "Forgot PIN?". The same sheet, on "I have an account".
 */
@Composable
fun SignInAgainScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val expired = remember { settings.isSessionExpired() }
    val state = rememberAuthFormState(onSignedIn)
    AuthScreen(
        state = state,
        startMode = AuthMode.Existing,
        header = {
            Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
                Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate)
            }
        },
        body = if (expired) "You were signed out because your session expired. Your transactions on this phone are safe."
        else "Sign in to your Kash account. Your transactions on this phone are safe.",
    )
}

private fun findActivity(context: Context): Activity? {
    var cur = context
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

/**
 * Maps auth failures to copy a user can act on. The raw exception still goes
 * to the error log at each call site; it is never shown.
 */
internal fun friendlyAuthError(e: Throwable?, fallback: String): String {
    if (e == null) return fallback
    val causes = generateSequence(e) { it.cause }.toList()
    if (causes.any {
            it is java.net.UnknownHostException ||
                it is java.net.SocketTimeoutException ||
                it is java.net.ConnectException ||
                it is javax.net.ssl.SSLException
        }) {
        return "No internet connection. Check your connection and try again."
    }
    val msg = causes.joinToString(" ") { it.message.orEmpty() }.lowercase()
    return when {
        "invalid login credentials" in msg -> "Wrong email or password."
        "user already registered" in msg || "already been registered" in msg ->
            "An account with this email already exists. Try signing in instead."
        "email not confirmed" in msg -> "Please confirm your email first — check your inbox."
        "password should be" in msg || "weak_password" in msg -> "Password must be at least 6 characters."
        "invalid email" in msg || "unable to validate email" in msg -> "That email address doesn't look right."
        "(429)" in msg || "rate limit" in msg -> "Too many attempts. Wait a minute and try again."
        Regex("""failed \(5\d\d\)""").containsMatchIn(msg) -> "Our server is having trouble. Please try again in a moment."
        else -> fallback
    }
}

/**
 * Light onboarding page (AppAllowSMS…AppSignIn): back button + step bar,
 * scrollable [content] at the top, and [bottom] (cards and buttons) pinned
 * to the bottom of the screen.
 */
@Composable
fun OnboardingPage(
    step: Int,
    onBack: () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    KashSystemBars(darkIcons = !KashColors.isDark)
    Column(
        Modifier
            .fillMaxSize()
            .background(KashColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = KashSpace.InsetOnboarding, end = KashSpace.InsetOnboarding, top = KashSpace.HeaderTop, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            com.financeapp.ui.kash.components.KashIconButton(com.financeapp.ui.kash.KashIcon.Back, "Back", onBack)
            if (step > 0) {
                com.financeapp.ui.kash.components.KashStepBar(current = step, total = 3, modifier = Modifier.weight(1f), label = "Step $step of 3")
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.size(44.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = bottom)
    }
}
