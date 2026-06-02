package com.financeapp

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
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.remote.SupabaseSyncScheduler
import com.financeapp.sms.SmsIngestService
import com.financeapp.sms.SmsIngestWorker
import com.financeapp.ui.navigation.AppNavigation
import com.financeapp.ui.theme.FinanceAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasSmsAccess by mutableStateOf(false)
    private var pendingRoute by mutableStateOf<String?>(null)

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
                hasSmsAccess = true
                SmsIngestService.start(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRoute = intent?.getStringExtra(EXTRA_DESTINATION_ROUTE)

        val db   = AppDatabase.getInstance(this)
        val repo = TransactionRepository(db)
        val settingsRepo = SettingsRepository(this)
        refreshPermissionState()

        setContent {
            FinanceAppTheme {
                AppNavigation(
                    repo = repo,
                    settingsRepo = settingsRepo,
                    hasSmsAccess = hasSmsAccess,
                    requestSmsAccess = ::checkAndRequestPermissions,
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null }
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
                SmsIngestWorker.enqueue(this)
            }
            settingsRepo.setLastIngestedVersion(currentVersion)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_DESTINATION_ROUTE)
    }

    override fun onStart() {
        super.onStart()
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
    }
}
