package com.financeapp.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financeapp.BuildConfig
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.SyncNotificationHelper
import com.financeapp.security.PinManager
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.components.KashTab
import com.financeapp.ui.kash.components.KashTabBar
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.ui.screens.AccountDetailScreen
import com.financeapp.ui.screens.AccountsTabScreen
import com.financeapp.ui.screens.AddScreen
import com.financeapp.ui.screens.AnalyticsTabScreen
import com.financeapp.ui.screens.BanksFollowedPage
import com.financeapp.ui.screens.CategoryScreen
import com.financeapp.ui.screens.ChangePinScreen
import com.financeapp.ui.screens.EditAccountPage
import com.financeapp.ui.screens.ExportSheet
import com.financeapp.ui.screens.HistoryScreen
import com.financeapp.ui.screens.HomeScreen
import com.financeapp.ui.screens.NotificationsPage
import com.financeapp.ui.screens.OnboardingFlow
import com.financeapp.ui.screens.PinLockScreen
import com.financeapp.ui.screens.PinSetupScreen
import com.financeapp.ui.screens.PrivacyPage
import com.financeapp.ui.screens.ReviewScreen
import com.financeapp.ui.screens.SettingsHomeScreen
import com.financeapp.ui.screens.SignInAgainScreen
import com.financeapp.ui.screens.TransactionScreen
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.AddTransactionViewModel
import com.financeapp.viewmodel.AddTransactionViewModelFactory
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.AnalyticsViewModelFactory
import com.financeapp.viewmodel.BankAnalyticsViewModel
import com.financeapp.viewmodel.BankAnalyticsViewModelFactory
import com.financeapp.viewmodel.DashboardViewModel
import com.financeapp.viewmodel.DashboardViewModelFactory
import com.financeapp.viewmodel.OnboardingViewModel
import com.financeapp.viewmodel.OnboardingViewModelFactory
import com.financeapp.viewmodel.ReviewQueueViewModel
import com.financeapp.viewmodel.ReviewQueueViewModelFactory
import com.financeapp.viewmodel.ReviewScope
import com.financeapp.viewmodel.SettingsViewModel
import com.financeapp.viewmodel.SettingsViewModelFactory
import com.financeapp.viewmodel.TransactionsViewModel
import com.financeapp.viewmodel.TransactionsViewModelFactory

/** Route names. Notifications deep-link into [Categorize] (now Review). */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Accounts : Screen("accounts")
    object Transactions : Screen("transactions")
    object Categorize : Screen(SyncNotificationHelper.CATEGORY_ROUTE)
    object DailyReview : Screen("daily_review")
    object Analytics : Screen("analytics")
    object Profile : Screen("profile")
    object AddTransaction : Screen("add_transaction")
}

@Composable
fun AppNavigation(
    repo: TransactionRepository,
    settingsRepo: SettingsRepository,
    hasSmsAccess: Boolean,
    requestSmsAccess: () -> Unit,
    smsDenied: Boolean = false,
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
    isLocked: Boolean = false,
    onUnlocked: () -> Unit = {}
) {
    val setVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo, repo))
    val userName by setVm.userName.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val pinManager = remember { PinManager(appContext) }
    val isSupabaseSignedIn by settingsRepo.isSupabaseSignedInFlow()
        .collectAsState(initial = settingsRepo.getSupabaseSession() != null)
    val onboardingComplete by settingsRepo.onboardingCompleteFlow()
        .collectAsState(initial = settingsRepo.isOnboardingComplete())
    var smsRecoveryRequested by rememberSaveable { mutableStateOf(false) }
    val calendarSystem by setVm.calendarSystem.collectAsState()

    CompositionLocalProvider(LocalCalendarSystem provides calendarSystem) {
        // First launch: Welcome → SMS → sources → sign in → summary (the auth
        // step now comes after the SMS steps). The PIN gate below follows.
        if (!onboardingComplete) {
            OnboardingFlow(
                repo = repo,
                settingsRepo = settingsRepo,
                hasSmsAccess = hasSmsAccess,
                smsDenied = smsDenied,
                isSignedIn = isSupabaseSignedIn,
                onRequestSmsAccess = requestSmsAccess,
                onFinish = { settingsRepo.setOnboardingComplete(true) }
            )
            return@CompositionLocalProvider
        }

        // Signed out after onboarding: session expired, Log out or Forgot PIN.
        if (!isSupabaseSignedIn) {
            SignInAgainScreen(onSignedIn = {})
            return@CompositionLocalProvider
        }

        // App PIN. Re-read whenever sign-in state flips (log out / forgot PIN
        // clear it, so the next sign-in lands on "Create a PIN").
        var hasPin by remember(isSupabaseSignedIn) { mutableStateOf(pinManager.hasPin()) }
        if (!hasPin) {
            PinSetupScreen(pinManager = pinManager, onDone = { hasPin = true; onUnlocked() })
            return@CompositionLocalProvider
        }
        if (isLocked) {
            PinLockScreen(
                pinManager = pinManager,
                onUnlocked = onUnlocked,
                onForgot = {
                    setVm.signOut()      // clears PIN + session → Sign in again
                    onUnlocked()
                }
            )
            return@CompositionLocalProvider
        }

        LaunchedEffect(hasSmsAccess) {
            if (hasSmsAccess) {
                smsRecoveryRequested = false
            } else if (!smsRecoveryRequested) {
                smsRecoveryRequested = true
                requestSmsAccess()
            }
        }

        MainNavigation(
            repo = repo,
            hasSmsAccess = hasSmsAccess,
            userName = userName,
            settingsRepo = settingsRepo,
            setVm = setVm,
            pendingRoute = pendingRoute,
            onPendingRouteHandled = onPendingRouteHandled
        )
    }
}

@Composable
private fun MainNavigation(
    repo: TransactionRepository,
    hasSmsAccess: Boolean,
    userName: String,
    settingsRepo: SettingsRepository,
    setVm: SettingsViewModel,
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val reviewTodayFac = remember { ReviewQueueViewModelFactory(repo, ReviewScope.TODAY) }
    val reviewAllFac = remember { ReviewQueueViewModelFactory(repo, ReviewScope.ALL) }
    val dashVm: DashboardViewModel = viewModel(factory = remember { DashboardViewModelFactory(repo) })
    val txVm: TransactionsViewModel = viewModel(factory = remember { TransactionsViewModelFactory(repo) })
    val anlVm: AnalyticsViewModel = viewModel(factory = remember { AnalyticsViewModelFactory(repo) })
    val sourcesVm: OnboardingViewModel = viewModel(factory = remember { OnboardingViewModelFactory(repo, settingsRepo) })
    val categories by setVm.categories.collectAsState()
    // Real recent picks for the picker's "Recent" row, pushed by the repo
    // every time the user confirms a category.
    val recentCategories by remember(settingsRepo) { settingsRepo.recentCategoriesFlow() }
        .collectAsState(initial = settingsRepo.getRecentCategories())

    LaunchedEffect(pendingRoute) {
        if (!pendingRoute.isNullOrBlank()) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onPendingRouteHandled()
        }
    }

    // The tab bar for the four tab screens (icons only, dot under the active one).
    val tabBar: (KashTab) -> (@Composable () -> Unit) = { active ->
        {
            KashTabBar(active, onSelect = { tab ->
                val target = when (tab) {
                    KashTab.Home -> Screen.Dashboard
                    KashTab.Accounts -> Screen.Accounts
                    KashTab.History -> Screen.Transactions
                    KashTab.Analytics -> Screen.Analytics
                }
                if (target == Screen.Transactions) txVm.clearFilters()
                navController.navigate(target.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                    launchSingleTop = true
                    restoreState = false
                }
            })
        }
    }

    @Composable
    fun ReviewRoute(vm: ReviewQueueViewModel) {
        ReviewScreen(
            vm = vm,
            userName = userName,
            customCategories = categories,
            recentCategories = recentCategories,
            onAddCustomCategory = { setVm.addCustomCategory(it) },
            onClose = { navController.popBackStack() },
            onReviewOlder = {
                navController.navigate("${Screen.DailyReview.route}?scope=all") { popUpTo(Screen.Dashboard.route) }
            },
        )
    }

    fun openTx(id: Long) = navController.navigate("detail/$id")
    fun openAccount(bankName: String) = navController.navigate("bank_analytics/${Uri.encode(bankName)}")
    fun back() { navController.popBackStack() }

    Box(Modifier.fillMaxSize().background(KashColors.Slate)) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize(),
            // No slide animations between screens: routes hard-cut.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Dashboard.route) {
                HomeScreen(
                    vm = dashVm,
                    tabBar = tabBar(KashTab.Home),
                    onTxClick = ::openTx,
                    onSeeAll = {
                        txVm.clearFilters()
                        navController.navigate(Screen.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onSettings = { navController.navigate(Screen.Profile.route) },
                    onAdd = { navController.navigate(Screen.AddTransaction.route) },
                    onReviewToday = { navController.navigate(Screen.DailyReview.route) { launchSingleTop = true } },
                    onReviewOlder = { navController.navigate("${Screen.DailyReview.route}?scope=all") { launchSingleTop = true } },
                )
            }
            composable(Screen.AddTransaction.route) {
                val addVm: AddTransactionViewModel = viewModel(factory = AddTransactionViewModelFactory(repo))
                val dash by dashVm.state.collectAsState()
                AddScreen(
                    vm = addVm,
                    banks = dash.accountBalances.map { kashBank(it.bankName).shortName },
                    customCategories = categories,
                    recentCategories = recentCategories,
                    onAddCategory = { setVm.addCustomCategory(it) },
                    onSaved = ::back,
                    onClose = ::back,
                )
            }
            composable(Screen.Accounts.route) {
                AccountsTabScreen(
                    vm = dashVm,
                    hasSmsAccess = hasSmsAccess,
                    tabBar = tabBar(KashTab.Accounts),
                    onOpenAccount = ::openAccount,
                    onAdd = { navController.navigate(Screen.AddTransaction.route) },
                    onTxClick = ::openTx,
                )
            }
            composable(
                route = "bank_analytics/{bankName}",
                arguments = listOf(navArgument("bankName") { type = NavType.StringType })
            ) { backStackEntry ->
                val bankName = backStackEntry.arguments?.getString("bankName")?.let(Uri::decode) ?: return@composable
                val vm: BankAnalyticsViewModel = viewModel(key = "bank_$bankName", factory = BankAnalyticsViewModelFactory(repo, bankName))
                AccountDetailScreen(vm = vm, onBack = ::back, onTxClick = ::openTx)
            }
            composable(
                route = "${Screen.Transactions.route}?search={search}",
                arguments = listOf(navArgument("search") { type = NavType.StringType; nullable = true })
            ) { backStack ->
                val search = backStack.arguments?.getString("search")
                LaunchedEffect(search) {
                    if (!search.isNullOrBlank()) {
                        txVm.clearFilters()
                        txVm.setSearch(search)
                    }
                }
                HistoryScreen(vm = txVm, tabBar = tabBar(KashTab.History), onTxClick = ::openTx)
            }
            composable(Screen.Analytics.route) {
                AnalyticsTabScreen(
                    vm = anlVm,
                    tabBar = tabBar(KashTab.Analytics),
                    onCategory = { navController.navigate("category_detail/${Uri.encode(it)}") },
                    onReview = { navController.navigate("${Screen.DailyReview.route}?scope=all") { launchSingleTop = true } },
                    onAccount = ::openAccount,
                )
            }
            composable(
                route = "category_detail/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { backStack ->
                val raw = backStack.arguments?.getString("category") ?: return@composable
                // Navigation does not decode StringType args; "Drinks%20and%20fun" stays encoded otherwise.
                CategoryScreen(vm = anlVm, category = Uri.decode(raw), onBack = ::back, onTxClick = ::openTx)
            }
            composable(
                route = "detail/{txId}",
                arguments = listOf(navArgument("txId") { type = NavType.LongType })
            ) { backStack ->
                val txId = backStack.arguments?.getLong("txId") ?: return@composable
                var tx by remember { mutableStateOf<TransactionEntity?>(null) }
                LaunchedEffect(txId) { tx = repo.getById(txId) }
                tx?.let { transaction ->
                    TransactionScreen(
                        tx = transaction,
                        customCategories = categories,
                        recentCategories = recentCategories,
                        onCategorySelected = {
                            txVm.updateCategory(txId, it)
                            tx = transaction.copy(category = it)
                        },
                        onAddCategory = { setVm.addCustomCategory(it) },
                        onBack = ::back
                    )
                }
            }
            // "Categorize today" (the notification) merged into Review.
            composable(Screen.Categorize.route) {
                ReviewRoute(viewModel(key = "review_queue_today", factory = reviewTodayFac))
            }
            composable(
                route = "${Screen.DailyReview.route}?scope={scope}",
                arguments = listOf(navArgument("scope") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { backStack ->
                val scopeArg = backStack.arguments?.getString("scope")
                ReviewRoute(
                    viewModel(
                        key = "review_queue_${scopeArg ?: "today"}",
                        factory = if (scopeArg == "all") reviewAllFac else reviewTodayFac
                    )
                )
            }
            composable(Screen.DailyReview.route) {
                ReviewRoute(viewModel(key = "review_queue_today", factory = reviewTodayFac))
            }
            composable(Screen.Profile.route) {
                val sources by sourcesVm.sources.collectAsState()
                var exportOpen by rememberSaveable { mutableStateOf(false) }
                SettingsHomeScreen(
                    vm = setVm,
                    followedBanks = sources.count { it.enabled },
                    onBack = ::back,
                    onEditAccount = { navController.navigate("profile_edit") },
                    onPrivacy = { navController.navigate("profile_security") },
                    onNotifications = { navController.navigate("profile_notifications") },
                    onChangePin = { navController.navigate("profile_pin") },
                    onBanks = { navController.navigate("profile_banks") },
                    onExport = { exportOpen = true },
                    onSupport = { openSupportEmail(context) }
                )
                if (exportOpen) {
                    val all by remember { repo.getAllTransactions() }.collectAsState(initial = emptyList())
                    ExportSheet(all = all, onClose = { exportOpen = false })
                }
            }
            composable("profile_edit") { EditAccountPage(vm = setVm, onBack = ::back) }
            composable("profile_security") { PrivacyPage(onBack = ::back) }
            composable("profile_notifications") { NotificationsPage(vm = setVm, onBack = ::back) }
            composable("profile_banks") {
                val sources by sourcesVm.sources.collectAsState()
                BanksFollowedPage(sources = sources, onToggle = sourcesVm::setSourceEnabled, onBack = ::back)
            }
            composable("profile_pin") {
                val pm = remember { PinManager(context) }
                ChangePinScreen(pinManager = pm, onDone = ::back, onBack = ::back)
            }
        }
    }
}

/** Settings → Email support: pre-filled email with version + device for triage. */
private fun openSupportEmail(context: Context) {
    val address = context.getString(R.string.support_email)
    val body = "\n\n---\nApp: ${BuildConfig.VERSION_NAME}" +
        "\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" +
        "\nAndroid: ${android.os.Build.VERSION.RELEASE}"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        putExtra(Intent.EXTRA_SUBJECT, "Kash support")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "No email app found. Write to us at $address", Toast.LENGTH_LONG).show()
    }
}
