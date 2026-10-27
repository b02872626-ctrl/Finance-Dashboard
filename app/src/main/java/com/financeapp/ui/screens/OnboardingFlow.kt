package com.financeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashBankBadge
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashDivider
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashSwitch
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.ui.kash.num
import com.financeapp.ui.kash.rememberReducedMotion
import com.financeapp.viewmodel.OnboardingSummary
import com.financeapp.viewmodel.OnboardingViewModel
import com.financeapp.viewmodel.OnboardingViewModelFactory
import com.financeapp.viewmodel.SmsSource
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

// =============================================================================
// First launch (HANDOFF row 1): Welcome → Allow SMS → (SMS denied) → Choose
// sources → Sign in → Your money at a glance. The PIN is created next, by the
// PIN gate in AppNavigation. Steps already done are skipped.
// =============================================================================

private enum class Step { Welcome, AllowSms, SmsDenied, Sources, SignIn, Summary }

/**
 * Drives the first-launch steps. [isSignedIn] and [hasSmsAccess] are live;
 * [smsDenied] flips true when Android refuses the permission (including
 * "Don't ask again"). [onFinish] marks onboarding complete.
 */
@Composable
fun OnboardingFlow(
    repo: TransactionRepository,
    settingsRepo: SettingsRepository,
    hasSmsAccess: Boolean,
    smsDenied: Boolean,
    isSignedIn: Boolean,
    onRequestSmsAccess: () -> Unit,
    onFinish: () -> Unit,
) {
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory(repo, settingsRepo))
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    var signInFirst by rememberSaveable { mutableStateOf(false) }
    var skippedSms by rememberSaveable { mutableStateOf(false) }
    var askedSms by rememberSaveable { mutableStateOf(false) }

    /** The next step that still needs doing, in the designed order. */
    fun next(): Step = when {
        !hasSmsAccess && !skippedSms -> Step.AllowSms
        hasSmsAccess && step != Step.Sources && step != Step.SignIn && step != Step.Summary -> Step.Sources
        !isSignedIn -> Step.SignIn
        else -> Step.Summary
    }

    // Permission granted (dialog or settings) → move on to the sources.
    LaunchedEffect(hasSmsAccess) {
        if (hasSmsAccess && (step == Step.AllowSms || step == Step.SmsDenied)) step = Step.Sources
    }
    LaunchedEffect(smsDenied) {
        if (smsDenied && askedSms && step == Step.AllowSms && !hasSmsAccess) step = Step.SmsDenied
    }

    fun back() {
        step = when (step) {
            Step.Welcome -> Step.Welcome
            Step.AllowSms -> Step.Welcome
            Step.SmsDenied -> Step.AllowSms
            Step.Sources -> if (signInFirst) Step.SignIn else Step.Welcome
            Step.SignIn -> when {
                signInFirst -> Step.Welcome
                hasSmsAccess -> Step.Sources
                skippedSms -> Step.SmsDenied
                else -> Step.AllowSms
            }
            Step.Summary -> if (hasSmsAccess) Step.Sources else Step.Welcome
        }
    }
    BackHandler(enabled = step != Step.Welcome) { back() }

    when (step) {
        Step.Welcome -> WelcomeScreen(
            onGetStarted = { signInFirst = false; step = if (hasSmsAccess) Step.Sources else Step.AllowSms },
            onHaveAccount = {
                signInFirst = true
                step = if (isSignedIn) next() else Step.SignIn
            },
        )
        Step.AllowSms -> AllowSmsScreen(
            onBack = ::back,
            onAllow = { askedSms = true; onRequestSmsAccess() },
            onNotNow = { step = Step.SmsDenied },
        )
        Step.SmsDenied -> SmsDeniedScreen(
            onBack = ::back,
            onByHand = { skippedSms = true; step = if (isSignedIn) Step.Summary else Step.SignIn },
        )
        Step.Sources -> {
            val sources by vm.sources.collectAsState()
            val total by vm.totalFound.collectAsState()
            SourcesScreen(
                sources = sources,
                totalFound = total,
                onBack = ::back,
                onToggle = vm::setSourceEnabled,
                onImport = { step = if (isSignedIn) Step.Summary else Step.SignIn },
            )
        }
        Step.SignIn -> OnboardingSignInStep(
            signInFirst = signInFirst,
            onBack = ::back,
            onSignedIn = {
                step = when {
                    signInFirst && hasSmsAccess -> Step.Sources
                    signInFirst && !skippedSms -> Step.AllowSms
                    else -> Step.Summary
                }
            },
        )
        Step.Summary -> {
            val summary by vm.summary.collectAsState()
            SummaryScreen(summary, onContinue = onFinish)
        }
    }
}

// ─── Welcome ─────────────────────────────────────────────────────────────

private data class WheelWord(val word: String, val icon: KashIcon, val tint: Color, val arrow: Boolean)

private val WHEEL = listOf(
    WheelWord("Spending", KashIcon.Out, KashColors.MoneyOut, true),
    WheelWord("Income", KashIcon.In, KashColors.MoneyIn, true),
    WheelWord("Transfers", KashIcon.Transfer, KashColors.InkMuted, true),
    WheelWord("Bills", KashIcon.Calendar, KashColors.Slate, false),
    WheelWord("Trends", KashIcon.Chart, KashColors.Slate, false),
)

/** AppWelcome: light top with the rolling words, slate panel rising from the bottom. */
@Composable
private fun WelcomeScreen(onGetStarted: () -> Unit, onHaveAccount: () -> Unit) {
    KashSystemBars(darkIcons = true, navDarkIcons = false)
    BoxWithConstraints(Modifier.fillMaxSize().background(KashColors.Surface)) {
        val panelTop = maxHeight * 0.51f
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = KashSpace.InsetOnboarding)
                .padding(top = 20.dp),
        ) {
            Text("kash", style = KashType.Wordmark, color = KashColors.Ink)
        }
        RollingWords(
            Modifier
                .padding(horizontal = KashSpace.InsetOnboarding)
                .offset(y = (panelTop - 168.dp) / 2 + 24.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = panelTop)
                .clip(KashShapes.Sheet)
                .background(KashColors.Slate)
                .navigationBarsPadding()
                .padding(start = KashSpace.InsetOnboarding, end = KashSpace.InsetOnboarding, top = 32.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Your bank SMS,\nalready sorted.", style = KashType.TitleL, color = KashColors.OnSlate)
            Text(
                "Kash turns CBE, Telebirr and other bank messages into a clear history. Nothing to type.",
                style = KashType.BodyL, color = KashColors.OnSlateMuted,
            )
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KashButton("Get started", onGetStarted, Modifier.fillMaxWidth(), KashButtonVariant.Accent)
                KashButton("I already have an account", onHaveAccount, Modifier.fillMaxWidth(), KashButtonVariant.QuietOnSlate)
            }
        }
    }
}

/**
 * Three 56dp rows; the middle word is full strength, the ones above and
 * below fade to 14%. Rolls one word every 2.4 s; still with reduced motion.
 */
@Composable
private fun RollingWords(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var index by remember { mutableStateOf(0) }
    val roll = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(2400)
            roll.animateTo(1f, tween(600, easing = CubicBezierEasing(.65f, 0f, .35f, 1f)))
            index = (index + 1) % WHEEL.size
            roll.snapTo(0f)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(0.dp))
            .clearAndSetSemantics { contentDescription = "Kash sorts your spending, income, transfers, bills and monthly trends" },
    ) {
        // Rows for index-1 … index+2; the column slides up by one row per roll.
        for (k in -1..2) {
            val w = WHEEL[(index + k + WHEEL.size) % WHEEL.size]
            val y = (k + 1 - roll.value) * 56f
            val distance = kotlin.math.abs(y - 56f) / 56f   // 0 at the middle row
            val alpha = (1f - distance * 0.86f).coerceIn(0.14f, 1f)
            Row(
                Modifier
                    .offset(y = y.dp)
                    .height(56.dp)
                    .alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(KashShapes.Field).background(KashColors.Well),
                    contentAlignment = Alignment.Center,
                ) { KashIconView(w.icon, size = 20.dp, tint = w.tint, strokeWidth = if (w.arrow) 2.2f else 2f) }
                Text(w.word, style = KashType.TitleL, color = KashColors.Ink)
            }
        }
    }
}

// ─── Allow SMS ───────────────────────────────────────────────────────────

@Composable
private fun FactRow(icon: KashIcon, title: String, text: String, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(KashShapes.Field).background(KashColors.Well), contentAlignment = Alignment.Center) {
                KashIconView(icon, size = 20.dp, tint = KashColors.Ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = KashType.BodyStrong, color = KashColors.Ink)
                Text(text, style = KashType.Caption, color = KashColors.InkMuted)
            }
        }
        if (divider) KashDivider()
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KashShapes.Card)
            .background(KashColors.SurfaceRaised)
            .border(1.dp, KashColors.Line, KashShapes.Card)
    ) { content() }
}

/** AppAllowSMS · step 1 of 3. */
@Composable
private fun AllowSmsScreen(onBack: () -> Unit, onAllow: () -> Unit, onNotNow: () -> Unit) {
    OnboardingPage(
        step = 1,
        onBack = onBack,
        bottom = {
            CardColumn {
                // The parsers cover CBE, CBE Birr, Telebirr, Awash, Dashen, BoA and Berhan.
                FactRow(KashIcon.Sms, "Bank messages only", "CBE, Telebirr, Awash, Dashen and 3 more", true)
                FactRow(KashIcon.History, "Your full SMS history, at once", "Then it keeps up, even offline", true)
                FactRow(KashIcon.Lock, "Personal chats stay private", "Nothing is sold or shared", false)
            }
            Spacer(Modifier.height(22.dp))
            KashButton("Allow SMS access", onAllow, Modifier.fillMaxWidth())
            KashButton("Not now", onNotNow, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        },
    ) {
        Column {
            Text("Let Kash read\nyour bank SMS", style = KashType.TitleL, color = KashColors.Ink)
            Text(
                "Android asks for permission next. Here is exactly what Kash does with it.",
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** AppSMSDenied: also covers "Don't ask again" — Open settings is the way back. */
@Composable
private fun SmsDeniedScreen(onBack: () -> Unit, onByHand: () -> Unit) {
    val context = LocalContext.current
    OnboardingPage(
        step = 1,
        onBack = onBack,
        bottom = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(KashShapes.Card)
                    .background(KashColors.SurfaceRaised)
                    .border(1.dp, KashColors.Line, KashShapes.Card)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("In settings, tap", style = KashType.Label, color = KashColors.InkMuted)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Permissions", "SMS", "Allow").forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(28.dp).clip(KashShapes.Chip).background(KashColors.Well),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", style = KashType.Label.num(), color = KashColors.Ink) }
                            Text(label, style = KashType.Body, color = KashColors.Ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            KashButton(
                "Open settings",
                {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                Modifier.fillMaxWidth(),
                icon = KashIcon.External,
            )
            KashButton("Add transactions by hand", onByHand, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        },
    ) {
        Column {
            Text("Kash can't see\nyour bank SMS yet", style = KashType.TitleL, color = KashColors.Ink)
            Text(
                "Without it, nothing imports by itself. You can turn it on in Android settings any time.",
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

// ─── Choose sources ──────────────────────────────────────────────────────

private fun count(n: Int) = NumberFormat.getIntegerInstance(Locale.US).format(n)

/** AppSources · step 2 of 3. Switches write the disabled-senders prefs. */
@Composable
private fun SourcesScreen(
    sources: List<SmsSource>,
    totalFound: Int,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onImport: () -> Unit,
) {
    val importing = sources.filter { it.enabled }.sumOf { it.count }
    OnboardingPage(
        step = 2,
        onBack = onBack,
        bottom = {
            if (sources.isNotEmpty()) {
                CardColumn {
                    sources.forEachIndexed { i, src ->
                        val bank = kashBank(src.bankName)
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                KashBankBadge(src.bankName)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(bank.fullName, style = KashType.BodyStrong, color = KashColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(src.last4?.let { "•••• $it" }, "${count(src.count)} found").joinToString(" · "),
                                        style = KashType.Caption.num(), color = KashColors.InkMuted,
                                    )
                                }
                                KashSwitch(src.enabled, { onToggle(src.sender, it) }, "Follow ${bank.fullName}")
                            }
                            if (i < sources.lastIndex) KashDivider()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Missing a bank? Kash only sees SMS already on this phone. New banks show up here as their messages arrive.",
                style = KashType.Caption, color = KashColors.InkMuted,
            )
            Spacer(Modifier.height(22.dp))
            KashButton(
                if (importing > 0) "Import ${count(importing)} transactions" else "Continue",
                onImport, Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column {
            Text("Found on this phone", style = KashType.Label, color = KashColors.InkMuted)
            Text(
                buildFoundLine(totalFound),
                style = KashType.FigureXl, color = KashColors.Ink, modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                if (sources.isEmpty()) "Kash is reading your SMS. Banks show up here as it finds them."
                else "Pick the accounts Kash should follow.",
                style = KashType.BodyL, color = KashColors.InkMuted, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun buildFoundLine(n: Int) = androidx.compose.ui.text.buildAnnotatedString {
    append(count(n))
    pushStyle(KashType.FigureUnit.toSpanStyle().copy(color = KashColors.InkMuted))
    append(if (n == 1) " transaction" else " transactions")
    pop()
}

// ─── Your money at a glance ──────────────────────────────────────────────

/** AppSummary. Every number is from the real transactions; rows without data hide. */
@Composable
private fun SummaryScreen(summary: OnboardingSummary, onContinue: () -> Unit) {
    KashSystemBars(darkIcons = false)
    KashScreen(
        heroGap = 22.dp,
        heroBottom = 28.dp,
        hero = {
            Text("Your money at a glance", style = KashType.TitleM, color = KashColors.OnSlate, modifier = Modifier.padding(top = 6.dp))
            if (summary.avgMonthlySpend != null) {
                Column {
                    Text("You spend about", style = KashType.Body, color = KashColors.OnSlateMuted)
                    KashFigure(
                        summary.avgMonthlySpend, showDecimals = false, currency = "ETB a month",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    if (summary.transactionCount == 0) "No bank SMS found yet. New ones show up on Home as they arrive."
                    else "Not enough spending yet to show a monthly figure.",
                    style = KashType.BodyL, color = KashColors.OnSlateMuted,
                )
            }
            if (summary.months.isNotEmpty()) {
                val max = summary.months.maxOf { it.spent }.coerceAtLeast(1.0)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .semantics { contentDescription = "Monthly spending, last six months; this month is highlighted" },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    summary.months.forEachIndexed { i, m ->
                        val current = i == summary.months.lastIndex
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((m.spent / max).toFloat().coerceIn(0.04f, 1f))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (current) KashColors.Sand else KashColors.ChartBar)
                                )
                            }
                            Text(
                                m.label, style = KashType.Micro, textAlign = TextAlign.Center,
                                color = if (current) KashColors.OnSlate else KashColors.OnSlateMuted,
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (summary.transactionCount > 0) SummaryRow(KashIcon.History, "Transactions found", count(summary.transactionCount), divider = summary.topPayee != null || summary.avgMonthlyIncome != null)
            summary.topPayee?.let { SummaryRow(KashIcon.Out, "Most money goes to", it, divider = summary.avgMonthlyIncome != null, arrow = true) }
            summary.avgMonthlyIncome?.let { SummaryRow(KashIcon.In, "Money in, a month", "${KashMoney.whole(it)} ETB", divider = false, arrow = true) }
        }
        KashButton("Continue", onContinue, Modifier.fillMaxWidth().padding(bottom = 12.dp).padding(pad))
    }
}

@Composable
private fun SummaryRow(icon: KashIcon, label: String, value: String, divider: Boolean, arrow: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(KashShapes.Field).background(KashColors.Well), contentAlignment = Alignment.Center) {
                KashIconView(icon, size = 20.dp, tint = KashColors.Ink, strokeWidth = if (arrow) 2.2f else 2f)
            }
            Text(label, style = KashType.Body.copy(fontWeight = FontWeight.Normal), color = KashColors.InkMuted, modifier = Modifier.weight(1f))
            Text(value, style = KashType.BodyStrong.num(), color = KashColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
        }
        if (divider) KashDivider()
    }
}
