package com.financeapp.ui.screens

import android.app.Activity
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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

    val isBusy get() = busy != AuthBusy.None

    fun clearMessages() { error = null; info = null }

    private fun validate(): String? {
        if (email.isBlank() || password.isBlank()) return "Email and password are required."
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) return "Enter a valid email address."
        if (password.length < 6) return "Password must be at least 6 characters."
        return null
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
        return transactionRepo.countPendingCloudSyncItems() > 0
    }

    private fun finish(shouldSync: Boolean) {
        if (shouldSync) scope.launch(Dispatchers.IO) { SupabaseSyncScheduler.enqueueIfPending(context) }
        onSignedIn()
    }

    fun signIn() {
        clearMessages()
        validate()?.let { error = it; return }
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
        validate()?.let { error = it; return }
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
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()
            )
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
                        "No Google account found on this phone. Add one in Settings → Accounts, or sign in with email."
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

/**
 * Placeholder for Google's official "Continue with Google" button: white,
 * #747775 border, Roboto Medium. The G mark is a dashed stand-in until the
 * official asset is supplied.
 */
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean, busy: Boolean) {
    Row(
        Modifier
            .kashClickable(enabled = enabled, onClick = onClick)
            .fillMaxWidth()
            .height(52.dp)
            .clip(KashShapes.Control)
            .background(Color.White)
            .border(1.dp, Color(0xFF747775), KashShapes.Control),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) {
            KashSpinner(size = 18.dp, color = Color(0xFF1F1F1F))
        } else {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF9A9A9A), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("G", fontSize = 9.sp, color = Color(0xFF6F6F6F)) }
        }
        Text(
            "Continue with Google",
            style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            color = Color(0xFF1F1F1F),
            maxLines = 1,
        )
    }
}

/** Google button, "or with email" divider, email + password with messages under them. */
@Composable
private fun AuthFields(state: AuthFormState, onSubmit: () -> Unit) {
    GoogleButton(onClick = state::google, enabled = !state.isBusy || state.busy == AuthBusy.Google, busy = state.busy == AuthBusy.Google)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(KashColors.LineStrong))
        Text("or with email", style = KashType.Caption, color = KashColors.InkMuted)
        Box(Modifier.weight(1f).height(1.dp).background(KashColors.LineStrong))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            placeholder = "At least 6 characters",
            password = true,
            enabled = !state.isBusy,
            error = state.error,
            hint = state.info,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )
    }
}

/** Onboarding step 3 of 3 (AppSignIn). [signInFirst] starts in sign-in mode. */
@Composable
fun OnboardingSignInStep(
    signInFirst: Boolean,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val state = rememberAuthFormState(onSignedIn)
    var signInMode by remember { mutableStateOf(signInFirst) }
    OnboardingPage(
        step = 3,
        onBack = onBack,
        bottom = {
            if (signInMode) {
                KashButton(
                    if (state.busy == AuthBusy.SignIn) "Signing in…" else "Sign in", state::signIn, Modifier.fillMaxWidth(),
                    busy = state.busy == AuthBusy.SignIn, enabled = !state.isBusy || state.busy == AuthBusy.SignIn,
                )
                KashButton(
                    "Create a new account", { signInMode = false; state.clearMessages() }, Modifier.fillMaxWidth(),
                    KashButtonVariant.Quiet, enabled = !state.isBusy,
                )
            } else {
                KashButton(
                    if (state.busy == AuthBusy.SignUp) "Creating your account…" else "Create account", state::signUp, Modifier.fillMaxWidth(),
                    busy = state.busy == AuthBusy.SignUp, enabled = !state.isBusy || state.busy == AuthBusy.SignUp,
                )
                KashButton(
                    "I have an account — sign in", { signInMode = true; state.clearMessages() }, Modifier.fillMaxWidth(),
                    KashButtonVariant.Quiet, enabled = !state.isBusy,
                )
            }
        },
    ) {
        Column {
            Text("Keep a copy\nin the cloud", style = KashType.TitleL, color = KashColors.Ink)
            Text(
                "Your history survives a new phone and shows on the web. It syncs whenever you are online.",
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 14.dp),
            )
        }
        AuthFields(state, onSubmit = { if (signInMode) state.signIn() else state.signUp() })
    }
}

/**
 * AppSignInAgain: shown when onboarding is done but there is no session —
 * the session expired, the user logged out, or chose "Forgot PIN?".
 */
@Composable
fun SignInAgainScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.applicationContext) }
    val expired = remember { settings.isSessionExpired() }
    val state = rememberAuthFormState(onSignedIn)
    var signUpMode by remember { mutableStateOf(false) }
    KashSystemBars(darkIcons = true)
    Column(
        Modifier
            .fillMaxSize()
            .background(KashColors.Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = KashSpace.InsetOnboarding, end = KashSpace.InsetOnboarding, top = KashSpace.HeaderTop, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
            Text("kash", style = KashType.Wordmark, color = KashColors.Ink)
        }
        Column {
            Text(if (signUpMode) "Create an account" else "Sign in again", style = KashType.TitleL, color = KashColors.Ink)
            Text(
                if (expired) "You were signed out because your session expired. Your transactions on this phone are safe."
                else "Sign in to your Kash account. Your transactions on this phone are safe.",
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 12.dp),
            )
        }
        AuthFields(state, onSubmit = { if (signUpMode) state.signUp() else state.signIn() })
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (signUpMode) {
                KashButton(
                    if (state.busy == AuthBusy.SignUp) "Creating your account…" else "Create account", state::signUp, Modifier.fillMaxWidth(),
                    busy = state.busy == AuthBusy.SignUp, enabled = !state.isBusy || state.busy == AuthBusy.SignUp,
                )
                KashButton("I have an account — sign in", { signUpMode = false; state.clearMessages() }, Modifier.fillMaxWidth(), KashButtonVariant.Quiet, enabled = !state.isBusy)
            } else {
                KashButton(
                    if (state.busy == AuthBusy.SignIn) "Signing in…" else "Sign in", state::signIn, Modifier.fillMaxWidth(),
                    busy = state.busy == AuthBusy.SignIn, enabled = !state.isBusy || state.busy == AuthBusy.SignIn,
                )
                KashButton("Create a new account", { signUpMode = true; state.clearMessages() }, Modifier.fillMaxWidth(), KashButtonVariant.Quiet, enabled = !state.isBusy)
            }
        }
    }
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
    KashSystemBars(darkIcons = true)
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
            com.financeapp.ui.kash.components.KashStepBar(current = step, total = 3, modifier = Modifier.weight(1f), label = "Step $step of 3")
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
