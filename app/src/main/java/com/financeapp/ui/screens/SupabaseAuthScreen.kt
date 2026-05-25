package com.financeapp.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.financeapp.data.db.AppDatabase
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.SupabaseAuthClient
import com.financeapp.remote.SupabaseConfigProvider
import com.financeapp.remote.SupabaseSession
import com.financeapp.remote.SupabaseSignUpResult
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.AppSurface
import com.financeapp.ui.theme.AppSurfaceSecondary
import com.financeapp.ui.theme.BorderLight
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.PureWhite
import com.financeapp.ui.theme.TextMuted
import com.financeapp.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.financeapp.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseAuthScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onboardingMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }
    val errorRepo = remember { ErrorLogRepository.fromContext(context) }
    val transactionRepo = remember { TransactionRepository(AppDatabase.getInstance(context.applicationContext)) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    val existingSession = remember { settingsRepo.getSupabaseSession() }
    var isSignedIn by remember { mutableStateOf(existingSession != null) }
    var signedInEmail by remember { mutableStateOf(existingSession?.email ?: "") }

    val credentialManager = remember { CredentialManager.create(context) }

    fun validate(): String? {
        if (email.isBlank() || password.isBlank()) return "Email and password are required."
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) return "Enter a valid email address."
        if (password.length < 6) return "Password must be at least 6 characters."
        return null
    }

    fun updateSignedInState() {
        val session = settingsRepo.getSupabaseSession()
        isSignedIn = session != null
        signedInEmail = session?.email ?: ""
    }

    fun handleGoogleSignIn() {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank() || webClientId == "YOUR_GOOGLE_SERVER_CLIENT_ID_HERE") {
            error = "Google Web Client ID is not configured in local.properties"
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        isLoading = true
        Toast.makeText(context, "Starting Google Sign-In...", Toast.LENGTH_SHORT).show()
        
        scope.launch {
            try {
                val activity = findActivity(context)
                if (activity == null) {
                    error = "Could not find a valid Activity to launch Sign-In"
                    isLoading = false
                    return@launch
                }
                
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    
                    val cfg = SupabaseConfigProvider.fromBuildConfig()
                    if (cfg == null) {
                        error = "Supabase is not configured."
                        isLoading = false
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        val authResult = runCatching {
                            val auth = SupabaseAuthClient(cfg)
                            val session = auth.signInWithIdToken(idToken)
                            val saved = com.financeapp.remote.SupabaseSession(
                                accessToken = session.accessToken,
                                refreshToken = session.refreshToken,
                                userId = session.userId,
                                email = session.email,
                                expiresAtMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                            )
                            settingsRepo.setSupabaseSession(saved)
                            transactionRepo.countPendingCloudSyncItems() > 0
                        }

                        withContext(Dispatchers.Main) {
                            isLoading = false
                            if (authResult.isSuccess) {
                                updateSignedInState()
                                if (authResult.getOrNull() == true) {
                                    SupabaseSyncScheduler.enqueueIfPending(context)
                                }
                                Toast.makeText(context, "Signed in with Google", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                val ex = authResult.exceptionOrNull()
                                error = ex?.message ?: "Google sign-in failed on server"
                                if (ex != null) {
                                    scope.launch(Dispatchers.IO) {
                                        errorRepo.log("Google sign-in server error", ex)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    isLoading = false
                    error = "Unexpected credential type"
                }
            } catch (e: Exception) {
                isLoading = false
                val msg = when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled by user"
                    is androidx.credentials.exceptions.NoCredentialException -> 
                        "No Google accounts found. Please sign in to Google in your Android device settings, or check your SHA-1 configuration."
                    else -> "Google Error: ${e.javaClass.simpleName} - ${e.message}"
                }
                error = msg
                scope.launch(Dispatchers.IO) {
                    errorRepo.log("Google sign-in failed: $msg", e)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (onboardingMode) "Step 1 of 3" else "Cloud Sign-In",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = CharcoalText,
                    navigationIconContentColor = CharcoalText
                )
            )
        },
        containerColor = AppBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppBackground)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, BorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(AppSurfaceSecondary, RoundedCornerShape(11.dp))
                                .border(1.dp, BorderLight, RoundedCornerShape(11.dp))
                                .padding(horizontal = 11.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (onboardingMode) "1. Sign up or sign in".uppercase() else "Supabase access".uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Text(
                            text = if (isSignedIn) "You’re signed in" else if (onboardingMode) "Create your cloud space" else "Sign in to sync",
                            style = MaterialTheme.typography.displaySmall,
                            color = CharcoalText
                        )

                        Text(
                            text = if (isSignedIn) {
                                if (signedInEmail.isBlank()) "Your sync session is active." else "Signed in as $signedInEmail"
                            } else {
                                if (onboardingMode) {
                                    "Use email and password or Google. This secures your sync before we ask for SMS access."
                                } else {
                                    "Use your Supabase Auth email and password. The app only writes with your authenticated session."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        if (isSignedIn) {
                            Button(
                                onClick = {
                                    settingsRepo.clearSupabaseSession()
                                    updateSignedInState()
                                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = PureWhite)
                            ) {
                                Text("Sign out", style = MaterialTheme.typography.labelLarge)
                            }
                            return@Card
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; error = null },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppBlue.copy(alpha = 0.18f),
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = AppSurfaceSecondary,
                                unfocusedContainerColor = AppSurfaceSecondary,
                                cursorColor = AppBlue,
                                focusedLabelColor = AppBlue,
                                focusedTextColor = CharcoalText,
                                unfocusedTextColor = CharcoalText
                            ),
                            shape = RoundedCornerShape(13.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = TextMuted
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppBlue.copy(alpha = 0.18f),
                                unfocusedBorderColor = BorderLight,
                                focusedContainerColor = AppSurfaceSecondary,
                                unfocusedContainerColor = AppSurfaceSecondary,
                                cursorColor = AppBlue,
                                focusedLabelColor = AppBlue,
                                focusedTextColor = CharcoalText,
                                unfocusedTextColor = CharcoalText
                            ),
                            shape = RoundedCornerShape(13.dp)
                        )

                        Text(
                            text = error ?: if (onboardingMode) {
                                "You can create a new account here too. Next comes SMS access, then a quick finance wrapped."
                            } else {
                                "Email/password auth is required before cloud sync starts."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error == null) TextSecondary else MaterialTheme.colorScheme.error
                        )

                        Button(
                            enabled = !isLoading,
                            onClick = {
                                val validationError = validate()
                                if (validationError != null) {
                                    error = validationError
                                    return@Button
                                }

                                val cfg = SupabaseConfigProvider.fromBuildConfig()
                                if (cfg == null) {
                                    error = "Supabase is not configured."
                                    return@Button
                                }

                                isLoading = true
                                scope.launch(Dispatchers.IO) {
                                    val result = runCatching {
                                        val auth = SupabaseAuthClient(cfg)
                                        val session = auth.signInWithPassword(email.trim(), password)
                                        val saved = SupabaseSession(
                                            accessToken = session.accessToken,
                                            refreshToken = session.refreshToken,
                                            userId = session.userId,
                                            email = session.email,
                                            expiresAtMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                                        )
                                        settingsRepo.setSupabaseSession(saved)
                                        transactionRepo.countPendingCloudSyncItems() > 0
                                    }

                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        if (result.isSuccess) {
                                            updateSignedInState()
                                            if (result.getOrNull() == true) {
                                                SupabaseSyncScheduler.enqueueIfPending(context)
                                            }
                                            Toast.makeText(context, "Signed in", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        } else {
                                            val ex = result.exceptionOrNull()
                                            error = ex?.message ?: "Sign-in failed"
                                            if (ex != null) {
                                                scope.launch(Dispatchers.IO) {
                                                    errorRepo.log("Supabase sign-in failed", ex)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(35.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppBlue, contentColor = PureWhite)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = PureWhite
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Signing in", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Text("Sign in", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        OutlinedButton(
                            enabled = !isLoading,
                            onClick = { handleGoogleSignIn() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(35.dp),
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(1.dp, BorderLight),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CharcoalText)
                        ) {
                            Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Need an account?", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                enabled = !isLoading,
                                onClick = {
                                    val validationError = validate()
                                    if (validationError != null) {
                                        error = validationError
                                        return@TextButton
                                    }

                                    val cfg = SupabaseConfigProvider.fromBuildConfig()
                                    if (cfg == null) {
                                        error = "Supabase is not configured."
                                        return@TextButton
                                    }

                                    isLoading = true
                                    scope.launch(Dispatchers.IO) {
                                        val result = runCatching {
                                            val auth = SupabaseAuthClient(cfg)
                                            var shouldSync = false
                                            when (val signUp = auth.signUp(email.trim(), password)) {
                                                is SupabaseSignUpResult.SignedIn -> {
                                                    val session = signUp.session
                                                    val saved = SupabaseSession(
                                                        accessToken = session.accessToken,
                                                        refreshToken = session.refreshToken,
                                                        userId = session.userId,
                                                        email = session.email,
                                                        expiresAtMs = System.currentTimeMillis() + session.expiresInSeconds * 1000L
                                                    )
                                                    settingsRepo.setSupabaseSession(saved)
                                                    shouldSync = transactionRepo.countPendingCloudSyncItems() > 0
                                                }
                                                SupabaseSignUpResult.ConfirmationRequired -> Unit
                                            }
                                            shouldSync
                                        }

                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            if (result.isSuccess) {
                                                val sessionNow = settingsRepo.getSupabaseSession()
                                                if (sessionNow != null) {
                                                    updateSignedInState()
                                                    if (result.getOrNull() == true) {
                                                        SupabaseSyncScheduler.enqueueIfPending(context)
                                                    }
                                                    Toast.makeText(context, "Account created and signed in", Toast.LENGTH_LONG).show()
                                                    onBack()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Account created. Check your email to confirm, then sign in.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                val ex = result.exceptionOrNull()
                                                error = ex?.message ?: "Sign-up failed"
                                                if (ex != null) {
                                                    scope.launch(Dispatchers.IO) {
                                                        errorRepo.log("Supabase sign-up failed", ex)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text("Create one", style = MaterialTheme.typography.labelLarge, color = AppBlue)
                            }
                        }
                    }
                }
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
