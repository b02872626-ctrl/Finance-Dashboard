package com.financeapp.ui.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDayHeader
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashKeyValue
import com.financeapp.ui.kash.components.KashKeyValueList
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSnackbar
import com.financeapp.ui.kash.components.KashTab
import com.financeapp.ui.kash.components.KashTabBar
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import org.junit.Rule
import org.junit.Test

/**
 * HANDOFF rows 10 and 11: every screen at 360 dp with long names, and the
 * Ethiopian calendar. Renders the real components with worst-case content.
 */
class KashSmallPhoneScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2340, xdpi = 480, ydpi = 480),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val now = System.currentTimeMillis()

    private fun shot(cal: String = "GREGORIAN", content: @Composable () -> Unit) =
        paparazzi.snapshot { KashTheme { CompositionLocalProvider(LocalCalendarSystem provides cal) { content() } } }

    @Test
    fun historyLongNamesEthiopian() = shot("ETHIOPIAN") {
        KashScreen(
            tabBar = { KashTabBar(KashTab.History, {}) },
            heroGap = 16.dp,
            hero = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", style = KashType.TitleM, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                    KashIconButton(KashIcon.Calendar, "Pick dates", {}, onSlate = true)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KashChip("All", true, {}, onSlate = true)
                    KashChip("In", false, {}, onSlate = true)
                    KashChip("Out", false, {}, onSlate = true)
                    KashChip(KashDates.monthName(now, "ETHIOPIAN"), false, {}, onSlate = true, trailing = KashIcon.ChevronDown)
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad)) {
                KashDayHeader(KashDates.dayHeader(now, "ETHIOPIAN"), net = "−1,234,737.00")
                KashTransactionRow("Telecom package: Daily Internet Pack 1GB + 50 min, valid for 24 hours", 100.0, KashDirection.Out, account = "Telebirr", time = "12:36", category = "Coffee and refreshments")
                KashTransactionRow("bekelu beyene (2519****3121)", 160.0, KashDirection.In, account = "Telebirr", time = "12:36", category = "Family")
                KashTransactionRow("Commercial Bank of Ethiopia → CBE Birr", 1_250_000.0, KashDirection.Transfer, account = "CBE Birr", time = "9:02", category = "Own accounts")
                KashDayHeader(KashDates.dayHeader(now - 3 * 86_400_000L, "ETHIOPIAN"), net = "−1,850.00")
                KashTransactionRow("Ethiopian Electric Utility", 1250.0, KashDirection.Out, account = "CBE Birr", time = "18:10", category = "Bills")
            }
        }
    }

    @Test
    fun reviewLongCategory() = shot {
        KashScreen(
            heroGap = 18.dp,
            hero = { KashTopBar("Review", onBack = {}, actions = { Text("14 of 1,795", style = KashType.Label, color = KashColors.OnSlateMuted) }) },
        ) { pad ->
            Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KashFigure(1_234_567.89, style = KashType.FigureL, color = KashColors.MoneyOut, unitColor = KashColors.MoneyOut, sign = "−")
                KashKeyValueList(
                    listOf(
                        KashKeyValue("When", KashDates.full(now, "ETHIOPIAN"), numeric = true),
                        KashKeyValue("From", "Commercial Bank of Ethiopia · •••• 4607", numeric = true),
                    ),
                    card = false,
                )
                KashSnackbar("Confirmed bekelu beyene (2519****3121) as Coffee and refreshments", action = "Undo")
                // Long confirm labels stack (ReviewButtons rule).
                KashButton("Confirm as Coffee and refreshments", {}, Modifier.fillMaxWidth())
                KashButton("Skip", {}, Modifier.fillMaxWidth(), KashButtonVariant.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Money in", "Left over", "vs Mes").forEach { label ->
                        Column(
                            Modifier.weight(1f).background(KashColors.Slate).padding(12.dp),
                        ) {
                            Text(label, style = KashType.Micro, color = KashColors.OnSlateMuted, maxLines = 1)
                            Text("−125,300", style = KashType.FigureS, color = KashColors.OnSlate, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    // Google Play's prominent disclosure for SMS: all four facts, the policy
    // link and both buttons must fit at 360 dp.
    @Test
    fun allowSmsDisclosure() = shot { com.financeapp.ui.screens.AllowSmsScreen({}, {}, {}) }

    @Test
    fun allowSmsAgainAfterOnboarding() = shot { com.financeapp.ui.screens.AllowSmsScreen({}, {}, {}, step = 0) }
}
