package com.financeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.export.TransactionCsv
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashDivider
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSheet
import com.financeapp.ui.kash.components.KashSwitch
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.SettingsViewModel
import com.financeapp.viewmodel.SmsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Two letters for the avatar: "Meron Alemu" → MA, "meron@x.com" → ME. */
private fun initials(name: String, email: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> email.substringBefore('@').take(2)
    }.uppercase(Locale.US).ifBlank { "K" }
}

/** Sub-page frame: back + title-xs, optional big title, then the sheet. */
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    headline: String? = null,
    heroExtra: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    KashSystemBars(darkIcons = false)
    KashScreen(
        heroGap = 22.dp,
        hero = {
            KashTopBar(title, onBack = onBack)
            if (headline != null) Text(headline, style = KashType.TitleL, color = KashColors.OnSlate)
            heroExtra?.invoke()
        },
        content = content,
    )
}

/** Settings (AppSettings). Reached from the gear on Home only. */
@Composable
fun SettingsHomeScreen(
    vm: SettingsViewModel,
    followedBanks: Int,
    onBack: () -> Unit,
    onEditAccount: () -> Unit,
    onPrivacy: () -> Unit,
    onNotifications: () -> Unit,
    onChangePin: () -> Unit,
    onBanks: () -> Unit,
    onExport: () -> Unit,
    onSupport: () -> Unit,
) {
    val name by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState()
    val notifications by vm.notificationsEnabled.collectAsState()
    val cal by vm.calendarSystem.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    KashSystemBars(darkIcons = false)
    KashScreen(
        heroGap = 22.dp,
        hero = {
            KashTopBar("Settings", onBack = onBack)
            Row(
                Modifier.fillMaxWidth().kashClickable(onClickLabel = "Edit your account", onClick = onEditAccount),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(56.dp).clip(KashShapes.Control).background(KashColors.Sand),
                    contentAlignment = Alignment.Center,
                ) { Text(initials(name, email), style = KashType.TitleS, color = KashColors.SlateInk) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        name.ifBlank { "Your account" }, style = KashType.TitleS, color = KashColors.OnSlate,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (email.isNotBlank()) Text(email, style = KashType.Label, color = KashColors.OnSlateMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Edit", style = KashType.Label, color = KashColors.OnSlateMuted)
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KashListGroup {
                KashListRow("Privacy and data", icon = KashIcon.Eye, onClick = onPrivacy)
                KashListRow("Notifications", icon = KashIcon.Sms, value = if (notifications) "On" else "Off", onClick = onNotifications)
                KashListRow("Change PIN", icon = KashIcon.Lock, onClick = onChangePin)
                KashListRow("Banks Kash follows", icon = KashIcon.Card, value = followedBanks.toString(), onClick = onBanks)
                KashListRow("Export transactions", icon = KashIcon.Export, onClick = onExport)
                KashListRow("Email support", icon = KashIcon.External, showDivider = false, onClick = onSupport)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KashShapes.Card)
                    .background(KashColors.SurfaceRaised)
                    .border(1.dp, KashColors.Line, KashShapes.Card)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KashIconView(KashIcon.Calendar, size = 20.dp, tint = KashColors.Ink)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Calendar", style = KashType.Body, color = KashColors.Ink)
                    Text("Changes every date in the app", style = KashType.Caption, color = KashColors.InkMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KashChip("GC", cal != "ETHIOPIAN", { if (cal == "ETHIOPIAN") vm.toggleCalendarSystem() })
                    KashChip("EC", cal == "ETHIOPIAN", { if (cal != "ETHIOPIAN") vm.toggleCalendarSystem() })
                }
            }
            Spacer(Modifier.height(18.dp))
            KashButton("Log out", { vm.signOut() }, Modifier.fillMaxWidth(), KashButtonVariant.Secondary)
            DeleteAccountButton { confirmDelete = true }
        }
    }
    if (confirmDelete) DeleteAccountDialog(onDismiss = { confirmDelete = false })
}

@Composable
private fun DeleteAccountButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).kashClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("Delete account", style = KashType.Body, color = KashColors.MoneyOut) }
}

/**
 * AppDeleteAccount / AppDeleteBusy. On success SupabaseSyncer clears the
 * session, which sends AppNavigation to the sign-in screen.
 */
@Composable
fun DeleteAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    KashDialog(
        title = "Delete your account?",
        description = "This permanently removes your transactions, categories and settings from Kash's servers and clears them from this phone.",
        onDismiss = { if (!deleting) onDismiss() },
        dismissible = !deleting,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(KashShapes.Card)
                .background(KashColors.SurfaceRaised)
                .border(1.dp, KashColors.Line, KashShapes.Card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KashIconView(KashIcon.Sms, size = 18.dp, tint = KashColors.Ink)
                Text("Your bank SMS stay on the phone", style = KashType.Body, color = KashColors.Ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KashIconView(KashIcon.Lock, size = 18.dp, tint = KashColors.Ink)
                Text("This can't be undone", style = KashType.Body, color = KashColors.Ink)
            }
        }
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KashButton(
                if (deleting) "Deleting your data…" else "Delete permanently",
                {
                    deleting = true
                    scope.launch {
                        val result = com.financeapp.remote.SupabaseSyncer(context).deleteAccountAndData()
                        deleting = false
                        if (result.isSuccess) {
                            onDismiss()
                            Toast.makeText(context, "Your account was deleted", Toast.LENGTH_LONG).show()
                        } else {
                            val offline = generateSequence(result.exceptionOrNull()) { it.cause }.any { it is java.io.IOException }
                            Toast.makeText(
                                context,
                                if (offline) "No internet connection. Nothing was deleted — try again when you're online."
                                else "Couldn't delete your account. Nothing was deleted — please try again or contact Support.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                KashButtonVariant.Danger,
                busy = deleting,
            )
            if (deleting) {
                Text(
                    "Keep Kash open until this finishes.", style = KashType.Caption, color = KashColors.InkMuted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                KashButton("Keep my account", onDismiss, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
            }
        }
    }
}

/** Your account (AppEditAccount). */
@Composable
fun EditAccountPage(vm: SettingsViewModel, onBack: () -> Unit) {
    val name by vm.userName.collectAsState()
    val email by vm.supabaseEmail.collectAsState()
    var draft by rememberSaveable { mutableStateOf(name) }
    SettingsPage(
        "Your account", onBack,
        heroExtra = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(80.dp).clip(KashShapes.Card).background(KashColors.Sand),
                    contentAlignment = Alignment.Center,
                ) { Text(initials(draft, email), style = KashType.TitleM, color = KashColors.SlateInk) }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().imePadding().padding(top = 8.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KashTextField(draft, { draft = it.take(60) }, "Name", placeholder = "Your name")
                KashTextField(email, {}, "Email", readOnly = true, hint = "Your sign-in email can't be changed here.")
            }
            KashButton(
                "Save changes",
                { vm.updateUserName(draft.trim()); onBack() },
                Modifier.fillMaxWidth().padding(pad),
                enabled = draft.trim() != name.trim() && draft.isNotBlank(),
            )
        }
    }
}

/** Privacy and data (AppPrivacy). */
@Composable
fun PrivacyPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SettingsPage("Privacy and data", onBack, headline = "How Kash handles\nyour money data") { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad)) {
            listOf(
                Triple(KashIcon.Sms, "Only bank messages", "Kash reads SMS from banks and mobile money. Personal chats are never opened."),
                Triple(KashIcon.History, "Synced to your account", "Transactions are copied to your Kash account so they survive a new phone and show on the web."),
                Triple(KashIcon.Eye, "Who can see it", "You. The Kash team can also access synced data to run and fix the service. It is never sold or shared."),
                Triple(KashIcon.Lock, "On this phone", "A copy stays on your phone, so Kash works without internet."),
            ).forEachIndexed { i, (icon, title, text) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(KashShapes.Field).background(KashColors.Well), contentAlignment = Alignment.Center) {
                        KashIconView(icon, size = 20.dp, tint = KashColors.Ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = KashType.BodyStrong, color = KashColors.Ink)
                        Text(text, style = KashType.Caption, color = KashColors.InkMuted)
                    }
                }
                if (i < 3) KashDivider()
            }
            Spacer(Modifier.height(28.dp))
            KashButton(
                "Read the privacy policy",
                {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url))))
                    }
                },
                Modifier.fillMaxWidth(), KashButtonVariant.Secondary, icon = KashIcon.External,
            )
            Spacer(Modifier.height(8.dp))
            DeleteAccountButton { confirmDelete = true }
        }
    }
    if (confirmDelete) DeleteAccountDialog(onDismiss = { confirmDelete = false })
}

/** Notifications (AppNotifications): one switch. */
@Composable
fun NotificationsPage(vm: SettingsViewModel, onBack: () -> Unit) {
    val enabled by vm.notificationsEnabled.collectAsState()
    SettingsPage("Notifications", onBack, headline = "When Kash\ntaps your shoulder") { pad ->
        Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KashShapes.Card)
                    .background(KashColors.SurfaceRaised)
                    .border(1.dp, KashColors.Line, KashShapes.Card)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Kash notifications", style = KashType.BodyStrong, color = KashColors.Ink)
                    Text(
                        "A nudge when new transactions need a category, and a note when syncing is waiting for internet",
                        style = KashType.Caption, color = KashColors.InkMuted,
                    )
                }
                KashSwitch(enabled, vm::setNotificationsEnabled, "Kash notifications")
            }
            Text(
                "You can also turn Kash notifications off in your phone's settings.",
                style = KashType.Caption, color = KashColors.InkMuted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Banks Kash follows: the same switches as Choose sources. */
@Composable
fun BanksFollowedPage(sources: List<SmsSource>, onToggle: (String, Boolean) -> Unit, onBack: () -> Unit) {
    SettingsPage("Banks Kash follows", onBack) { pad ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (sources.isEmpty()) {
                Text("No banks yet. A bank shows up here as soon as Kash reads one of its SMS.", style = KashType.BodyL, color = KashColors.InkMuted)
            } else {
                KashListGroup {
                    sources.forEachIndexed { i, src ->
                        val bank = kashBank(src.bankName)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            KashBankBadge(src.bankName)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(bank.fullName, style = KashType.BodyStrong, color = KashColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(src.last4?.let { "•••• $it" }, "${src.count} found").joinToString(" · "),
                                    style = KashType.Caption.num(), color = KashColors.InkMuted,
                                )
                            }
                            KashSwitch(src.enabled, { onToggle(src.sender, it) }, "Follow ${bank.fullName}")
                        }
                        if (i < sources.lastIndex) KashDivider()
                    }
                }
                Text(
                    "Kash stops reading new SMS from a bank you switch off. Turn it back on any time.",
                    style = KashType.Caption, color = KashColors.InkMuted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private enum class ExportPeriod(val label: String) { MONTH("This month"), THREE("Last 3 months"), YEAR("This year"), ALL("All time") }

/**
 * Export transactions (AppExport): period and account, then the system save
 * dialog. Writes the same CSV as before, now named kash_transactions_….
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(all: List<TransactionEntity>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cal = LocalCalendarSystem.current
    var period by remember { mutableStateOf(ExportPeriod.THREE) }
    var account by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val banks = remember(all) { all.map { it.bankName }.distinct() }
    val rows = remember(all, period, account, cal) {
        val now = System.currentTimeMillis()
        val from = when (period) {
            ExportPeriod.MONTH -> KashDates.monthBounds(now, cal).first
            ExportPeriod.THREE -> {
                var m = KashDates.monthBounds(now, cal).first
                repeat(2) { m = KashDates.monthBounds(m - 1, cal).first }
                m
            }
            ExportPeriod.YEAR -> KashDates.yearBounds(now, cal).first
            ExportPeriod.ALL -> 0L
        }
        all.filter { it.dateTime >= from && (account == null || it.bankName == account) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        val snapshot = rows
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                    w.write("﻿")
                    TransactionCsv.writeTo(w, snapshot)
                } ?: error("Unable to write to selected destination")
                snapshot.size
            }
            withContext(Dispatchers.Main) {
                exporting = false
                result.onSuccess {
                    Toast.makeText(context, "Exported $it transactions", Toast.LENGTH_LONG).show()
                    onClose()
                }.onFailure { ex ->
                    scope.launch(Dispatchers.IO) { ErrorLogRepository.fromContext(context).log("CSV export failed", ex) }
                    Toast.makeText(context, "CSV export failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    KashSheet("Export transactions", onDismiss = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Period", style = KashType.Label, color = KashColors.InkMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportPeriod.entries.forEach { p -> KashChip(p.label, p == period, { period = p }) }
            }
        }
        if (banks.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Accounts", style = KashType.Label, color = KashColors.InkMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KashChip("All", account == null, { account = null })
                    banks.forEach { b -> KashChip(kashBank(b).shortName, account == b, { account = b }) }
                }
            }
        }
        Text(
            "A CSV file that opens in Excel or Google Sheets. You choose where to save it next.",
            style = KashType.Caption, color = KashColors.InkMuted,
        )
        KashButton(
            when {
                exporting -> "Exporting…"
                rows.size == 1 -> "Export 1 transaction"
                else -> "Export ${rows.size} transactions"
            },
            {
                val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                launcher.launch("kash_transactions_$ts.csv")
            },
            Modifier.fillMaxWidth(),
            icon = KashIcon.Export,
            busy = exporting,
            enabled = rows.isNotEmpty(),
        )
    }
}
