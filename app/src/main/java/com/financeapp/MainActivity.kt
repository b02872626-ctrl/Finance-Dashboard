package com.financeapp

import com.financeapp.security.PinManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.financeapp.data.db.AppDatabase
import com.financeapp.notifications.AppForeground
import com.financeapp.notifications.NotificationPrefs
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.remote.AppSessionTracker
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.sms.SmsIngestService
import com.financeapp.sms.SmsIngestWorker
import com.financeapp.ui.navigation.AppNavigation
import com.financeapp.ui.kash.KashTheme
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasSmsAccess by mutableStateOf(false)
    /** True once Android refused READ_SMS (incl. "Don't ask again"). */
    private var smsDenied by mutableStateOf(false)
    private var pendingRoute by mutableStateOf<String?>(null)

    // App PIN: locked on every cold start and after > RELOCK_AFTER_MS in the
    // background. AppNavigation only shows the lock screen when a PIN exists.
    private var isLocked by mutableStateOf(true)
    private var backgroundedAt = 0L

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.READ_SMS] == true) {
                com.financeapp.remote.Telemetry.track(this, com.financeapp.remote.Telemetry.SMS_PERMISSION_GRANTED)
                hasSmsAccess = true
                smsDenied = false
                SmsIngestService.start(this)
            } else if (granted.containsKey(Manifest.permission.READ_SMS)) {
                smsDenied = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars; each screen picks its
        // bar icon colours (KashSystemBars).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Rotation / theme change recreates the activity — don't re-lock for that.
        isLocked = savedInstanceState?.getBoolean(STATE_LOCKED, true) ?: true
        pendingRoute = intent?.getStringExtra(EXTRA_DESTINATION_ROUTE)

        val db   = AppDatabase.getInstance(this)
        val settingsRepo = SettingsRepository(this)
        // Repo gets settings so it can push category picks onto the
        // recents list for the picker sheet's "Recent" row.
        val repo = TransactionRepository(db, settings = settingsRepo)
        refreshPermissionState()

        setContent {
            KashTheme {
                AppNavigation(
                    repo = repo,
                    settingsRepo = settingsRepo,
                    hasSmsAccess = hasSmsAccess,
                    requestSmsAccess = ::checkAndRequestPermissions,
                    smsDenied = smsDenied,
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null },
                    isLocked = isLocked,
                    onUnlocked = { isLocked = false }
                )
            }
        }

        lifecycleScope.launch {
            SupabaseSyncScheduler.bootstrap(this@MainActivity)
        }
        if (hasSmsAccess) {
            SmsIngestService.start(this)
        }

        // Auto-rescan the inbox when the user has just upgraded to a newer
        // app version. The new build may contain parser improvements (e.g.
        // beta3's CBE "successfully transferred" support) that can now parse
        // historical SMS rows beta2 silently rejected. Without this, the
        // user has to manually pull-to-refresh to backfill — and many never
        // realize they need to.
        val currentVersion = BuildConfig.VERSION_CODE
        if (settingsRepo.getLastIngestedVersion() < currentVersion) {
            if (hasSmsAccess) {
                // Rescan: old SMS replayed, so no transaction alerts from it.
                SmsIngestWorker.enqueue(this, rescan = true)
            }
            settingsRepo.setLastIngestedVersion(currentVersion)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_DESTINATION_ROUTE)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, isLocked)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAt = System.currentTimeMillis()
            AppSessionTracker.onBackground(this)
        }
        AppForeground.isForeground = false
        NotificationPrefs(this).lastAppOpenMs = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        AppForeground.isForeground = true
        NotificationPrefs(this).lastAppOpenMs = System.currentTimeMillis()
        AppSessionTracker.onForeground(this)
        if (backgroundedAt > 0L &&
            System.currentTimeMillis() - backgroundedAt > PinManager.RELOCK_AFTER_MS
        ) {
            isLocked = true
        }
        backgroundedAt = 0L
        refreshPermissionState()
        if (hasSmsAccess) {
            SmsIngestService.start(this)
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            SmsIngestService.start(this)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshPermissionState() {
        hasSmsAccess = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_DESTINATION_ROUTE = "destination_route"
        private const val STATE_LOCKED = "pin_locked"
    }
}
