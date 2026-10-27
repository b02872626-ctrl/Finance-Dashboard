package com.financeapp.ui.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.financeapp.ui.kash.components.KashAccountCard
import com.financeapp.ui.kash.components.KashAppIcon
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashDialogCard
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashEmptyState
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashHandle
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashKeyValue
import com.financeapp.ui.kash.components.KashKeyValueList
import com.financeapp.ui.kash.components.KashListGroup
import com.financeapp.ui.kash.components.KashListRow
import com.financeapp.ui.kash.components.KashNotice
import com.financeapp.ui.kash.components.KashPinPad
import com.financeapp.ui.kash.components.KashRadioRow
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSearchField
import com.financeapp.ui.kash.components.KashSheetBody
import com.financeapp.ui.kash.components.KashSkeletonRow
import com.financeapp.ui.kash.components.KashSnackbar
import com.financeapp.ui.kash.components.KashStepBar
import com.financeapp.ui.kash.components.KashSwitch
import com.financeapp.ui.kash.components.KashTab
import com.financeapp.ui.kash.components.KashTabBar
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.ui.kash.components.KashWeekChart
import org.junit.Rule
import org.junit.Test

/**
 * Renders the Kash components on the JVM so they can be compared with the
 * handoff PNGs. `./gradlew recordPaparazziDebug` writes the images to
 * app/src/test/snapshots/images; `verifyPaparazziDebug` checks them.
 */
class KashComponentsScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        // 390 × 844 dp at 3x, the handoff's phone size.
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1170, screenHeight = 2532, xdpi = 480, ydpi = 480),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val muted = KashType.Body.copy(fontWeight = FontWeight.Normal)

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot { KashTheme { content() } }

    @Test
    fun homeScreen() = shot {
        KashScreen(
            hero = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KashIconButton(KashIcon.Plus, "Add transaction", {}, onSlate = true)
                        KashIconButton(KashIcon.Settings, "Settings", {}, onSlate = true)
                    }
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Total balance", style = muted, color = KashColors.OnSlateMuted)
                        KashIconView(KashIcon.Eye, size = 18.dp, tint = KashColors.OnSlateMuted)
                    }
                    KashFigure(18245.0)
                }
                KashButton("Review", {}, Modifier.fillMaxWidth(), KashButtonVariant.Accent, badge = "4", compact = true)
                KashWeekChart(listOf(0.3f, 0.6f, 0.15f, 0.4f, 0.9f, 0f, 0f), today = 4, total = "−4,120")
            },
            tabBar = { KashTabBar(KashTab.Home, {}) },
        ) { pad ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Transactions", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f))
                Text("See all", style = KashType.Label, color = KashColors.InkMuted)
            }
            Column(Modifier.padding(pad)) {
                KashDayHeader("Today")
                KashTransactionRow("bekelu beyene", 160.0, KashDirection.In, account = "Telebirr", time = "12:36")
                KashTransactionRow("Daily Internet Pack 1GB", 100.0, KashDirection.Out, account = "Telebirr", time = "12:36", category = "Airtime")
                KashTransactionRow("CBE → Telebirr", 500.0, KashDirection.Transfer, account = "CBE", time = "9:02", category = "Own accounts")
            }
        }
    }

    @Test
    fun pinPad() = shot {
        Column(
            Modifier.background(KashColors.Slate).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(58.dp).clip(KashShapes.Control).background(KashColors.GhostFill),
                contentAlignment = Alignment.Center,
            ) { KashIconView(KashIcon.Lock, size = 22.dp, tint = KashColors.OnSlate) }
            Spacer(Modifier.size(24.dp))
            Text("Confirm your PIN", style = KashType.TitleM, color = KashColors.OnSlate)
            Text("Enter the same 4 digits again.", style = muted, color = KashColors.OnSlateMuted)
            Spacer(Modifier.size(28.dp))
            KashPinPad(filled = 4, onDigit = {}, onDelete = {}, message = "PINs didn't match. Start again.")
            Spacer(Modifier.size(28.dp))
            KashPinPad(filled = 0, onDigit = {}, onDelete = {}, countdown = "Try again in 0:24", disabled = true)
        }
    }

    @Test
    fun controls() = shot {
        Column(
            Modifier.background(KashColors.Surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KashButton("Allow SMS access", {}, Modifier.fillMaxWidth())
            KashButton("Open bank receipt", {}, Modifier.fillMaxWidth(), KashButtonVariant.Secondary, icon = KashIcon.External)
            KashButton("Creating your account…", {}, Modifier.fillMaxWidth(), busy = true)
            KashButton("Delete permanently", {}, Modifier.fillMaxWidth(), KashButtonVariant.Danger)
            KashButton("Save changes", {}, Modifier.fillMaxWidth(), enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KashButton("Skip", {}, variant = KashButtonVariant.Secondary)
                KashButton("Confirm as Own accounts", {}, Modifier.weight(1f))
            }
            KashButton("Not now", {}, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KashChip("Family", true, {})
                KashChip("Transfer", false, {})
                KashChip("More", false, {}, trailing = KashIcon.ChevronRight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KashChip("New category", false, {}, leading = KashIcon.Plus, dashed = true)
                KashSwitch(true, {}, "Track CBE")
                KashSwitch(false, {}, "Track BoA")
            }
            KashTextField("", {}, "Email", placeholder = "you@example.com")
            KashTextField("secret123", {}, "Password", password = true, error = "Wrong email or password.")
            KashSearchField("", {}, "Search this account")
            KashNotice("Looks like a move to your own CBE account. Own-account moves aren't counted as spending.", icon = KashIcon.Transfer)
            KashSnackbar("Confirmed bekelu beyene as Family", action = "Undo")
        }
    }

    @Test
    fun lists() = shot {
        Column(
            Modifier.background(KashColors.Surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KashKeyValueList(
                listOf(
                    KashKeyValue("To", "Ethio Telecom"),
                    KashKeyValue("Account", "Telebirr · •••• 3121", numeric = true),
                    KashKeyValue("When", "Today, 12:36"),
                    KashKeyValue("Balance after", "616.48", numeric = true),
                )
            )
            KashListGroup {
                KashListRow("Privacy and data", icon = KashIcon.Eye, onClick = {})
                KashListRow("Notifications", icon = KashIcon.Sms, value = "On", onClick = {})
                KashListRow("Change PIN", icon = KashIcon.Lock, onClick = {})
                KashListRow("Export transactions", icon = KashIcon.Export, showDivider = false, onClick = {})
            }
            Column {
                KashRadioRow("This week", false, {})
                KashRadioRow("This month", true, {})
                KashListRow("Pick dates…", icon = KashIcon.Calendar, showDivider = false, inset = 0.dp, onClick = {})
            }
            KashSkeletonRow()
            KashEmptyState(
                KashIcon.Search,
                "Nothing matches",
                "No money came in from \"Yod Abyssinia\" this month. Try Out, or a longer period.",
            ) {
                KashButton("Clear filters", {}, variant = KashButtonVariant.Secondary)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun slateParts() = shot {
        Column(
            Modifier.background(KashColors.Slate).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            KashTopBar("Review", onBack = {}, actions = { Text("3 of 6", style = KashType.Label, color = KashColors.OnSlateMuted) })
            KashStepBar(current = 3, total = 6, onSlate = true)
            KashTopBar("Commercial Bank", onBack = {}, centerTitle = true, leading = { KashBankBadge("Commercial Bank of Ethiopia", size = 32.dp) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KashChip("All", true, {}, onSlate = true)
                KashChip("In", false, {}, onSlate = true)
                KashChip("Out", false, {}, onSlate = true)
                KashChip("This month", false, {}, onSlate = true, trailing = KashIcon.ChevronDown)
            }
            KashSearchField("", {}, "Search name, category, reference", onSlate = true)
            KashAccountCard("Commercial Bank of Ethiopia", "4607", 12340.48, "From today's SMS, 12:36")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                KashBankBadge("Commercial Bank of Ethiopia")
                KashBankBadge("Telebirr")
                KashBankBadge("Bank of Abyssinia")
                KashBankBadge("Dashen Bank")
                KashAppIcon(size = 56.dp)
                KashAppIcon(size = 56.dp, bare = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KashIcon.entries.forEach { KashIconView(it, size = 24.dp, tint = KashColors.OnSlate) }
            }
        }
    }

    @Test
    fun overlays() = shot {
        Column(Modifier.background(KashColors.Backdrop).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KashDialogCard(
                "Delete your account?",
                "This permanently removes your transactions, categories and settings from Kash's servers and clears them from this phone.",
            ) {
                KashButton("Deleting your data…", {}, Modifier.fillMaxWidth(), KashButtonVariant.Danger, busy = true)
                KashButton("Keep my account", {}, Modifier.fillMaxWidth(), KashButtonVariant.Quiet, enabled = false)
            }
            Box(Modifier.clip(KashShapes.Sheet).background(KashColors.Surface)) {
                Column {
                    KashHandle(Modifier.padding(top = 10.dp))
                    KashSheetBody("Show transactions from", onClose = {}) {
                        Column {
                            KashRadioRow("This week", false, {})
                            KashRadioRow("This month", true, {})
                        }
                    }
                }
            }
        }
    }
}
