package com.financeapp.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.SettingsRepository
import com.financeapp.data.repository.TransactionRepository
import com.financeapp.remote.SyncNotificationHelper
import com.financeapp.ui.screens.AccountSecurityScreen
import com.financeapp.ui.screens.AccountSettingsScreen
import com.financeapp.ui.screens.AccountsScreen
import com.financeapp.ui.screens.AnalyticsScreen
import com.financeapp.ui.screens.BankAnalyticsScreen
import com.financeapp.ui.screens.CategorizeTransactionsScreen
import com.financeapp.ui.screens.CategoryDetailScreen
import com.financeapp.ui.screens.DashboardScreen
import com.financeapp.ui.screens.EditAccountScreen
import com.financeapp.ui.screens.NotificationPreferencesScreen
import com.financeapp.ui.screens.OnboardingScreen
import com.financeapp.ui.screens.SettingsScreen
import com.financeapp.ui.screens.SupabaseAuthScreen
import com.financeapp.ui.screens.TransactionDetailScreen
import com.financeapp.ui.screens.TransactionsScreen
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.AnalyticsViewModelFactory
import com.financeapp.viewmodel.BankAnalyticsViewModel
import com.financeapp.viewmodel.BankAnalyticsViewModelFactory
import com.financeapp.viewmodel.DashboardViewModel
import com.financeapp.viewmodel.DashboardViewModelFactory
import com.financeapp.viewmodel.SettingsViewModel
import com.financeapp.viewmodel.SettingsViewModelFactory
import com.financeapp.viewmodel.TransactionsViewModel
import com.financeapp.viewmodel.TransactionsViewModelFactory

sealed class Screen(val route: String, val label: String, @DrawableRes val iconRes: Int = 0) {
    object Dashboard : Screen("dashboard", "Home", R.drawable.ic_home_dark)
    object Accounts : Screen("accounts", "Accounts", R.drawable.ic_book_dark)
    object Transactions : Screen("transactions", "History", R.drawable.ic_history_dark)
    object Categorize : Screen(SyncNotificationHelper.CATEGORY_ROUTE, "Categorize")
    object Analytics : Screen("analytics", "Analytics", R.drawable.ic_anaytics_dark)
    object Profile : Screen("profile", "Profile", R.drawable.ic_profile_dark)
    object AccountSettings : Screen("account_settings", "Account", R.drawable.ic_profile_dark)
}

private val TABS = listOf(
    Screen.Dashboard,
    Screen.Accounts,
    Screen.Transactions,
    Screen.Analytics
)

@Composable
fun AppNavigation(
    repo: TransactionRepository,
    settingsRepo: SettingsRepository,
    hasSmsAccess: Boolean,
    requestSmsAccess: () -> Unit,
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {}
) {
    val setVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo, repo))
    val userName by setVm.userName.collectAsState()
    val isSupabaseSignedIn by settingsRepo.isSupabaseSignedInFlow()
        .collectAsState(initial = settingsRepo.getSupabaseSession() != null)
    val onboardingComplete by settingsRepo.onboardingCompleteFlow()
        .collectAsState(initial = settingsRepo.isOnboardingComplete())
    var smsRecoveryRequested by rememberSaveable { mutableStateOf(false) }

    val nameLoaded = true

    if (!nameLoaded) {
        Box(Modifier.fillMaxSize().background(AppBackground))
        return
    }

    if (!isSupabaseSignedIn) {
        SupabaseAuthScreen(
            onBack = {},
            showBackButton = false,
            onboardingMode = true
        )
        return
    }

    if (!onboardingComplete) {
        val transactions by repo.getAllTransactions().collectAsState(initial = emptyList())
        OnboardingScreen(
            hasSmsAccess = hasSmsAccess,
            transactions = transactions,
            onRequestSmsAccess = requestSmsAccess,
            onFinish = { settingsRepo.setOnboardingComplete(true) }
        )
        return
    }

    LaunchedEffect(onboardingComplete, hasSmsAccess) {
        if (!onboardingComplete) return@LaunchedEffect
        if (hasSmsAccess) {
            smsRecoveryRequested = false
        } else if (!smsRecoveryRequested) {
            smsRecoveryRequested = true
            requestSmsAccess()
        }
    }

    MainNavigation(
        repo = repo,
        userName = userName,
        settingsRepo = settingsRepo,
        pendingRoute = pendingRoute,
        onPendingRouteHandled = onPendingRouteHandled
    )
}

@Composable
private fun MainNavigation(
    repo: TransactionRepository,
    userName: String,
    settingsRepo: SettingsRepository,
    pendingRoute: String?,
    onPendingRouteHandled: () -> Unit
) {
    val navController = rememberNavController()
    val setVm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepo, repo))
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route
    val showBottomBar = currentRoute?.let { route ->
        TABS.any { it.route == route } ||
            route.startsWith(Screen.Transactions.route) ||
            route == Screen.Categorize.route
    } ?: true

    val dashFac = remember { DashboardViewModelFactory(repo) }
    val txFac = remember { TransactionsViewModelFactory(repo) }
    val anlFac = remember { AnalyticsViewModelFactory(repo) }

    val dashVm: DashboardViewModel = viewModel(factory = dashFac)
    val txVm: TransactionsViewModel = viewModel(factory = txFac)
    val anlVm: AnalyticsViewModel = viewModel(factory = anlFac)
    val categories by setVm.categories.collectAsState()
    val todayUncategorized by txVm.todayUncategorizedTransactions.collectAsState()
    val bulkSuggestion by txVm.bulkCategorySuggestion.collectAsState()

    LaunchedEffect(pendingRoute) {
        if (!pendingRoute.isNullOrBlank()) {
            navController.navigate(pendingRoute) {
                launchSingleTop = true
            }
            onPendingRouteHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    vm = dashVm,
                    userName = userName,
                    onTxClick = { id -> navController.navigate("detail/$id") },
                    onAccountsClick = {
                        navController.navigate(Screen.Accounts.route) {
                            launchSingleTop = true
                        }
                    },
                    onHistoryClick = {
                        txVm.clearFilters()
                        navController.navigate(Screen.Transactions.route)
                    },
                    onQuickFilterClick = { type ->
                        txVm.clearFilters()
                        if (type != null) {
                            txVm.setType(type)
                        }
                        navController.navigate(Screen.Transactions.route)
                    },
                    onSettingsClick = { navController.navigate(Screen.Profile.route) }
                )
            }
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    vm = dashVm,
                    onAccountClick = { bankName -> navController.navigate("bank_analytics/$bankName") }
                )
            }
            composable(
                route = "bank_analytics/{bankName}",
                arguments = listOf(navArgument("bankName") { type = NavType.StringType })
            ) { backStackEntry ->
                val bankName = backStackEntry.arguments?.getString("bankName") ?: return@composable
                val vm: BankAnalyticsViewModel = viewModel(
                    factory = BankAnalyticsViewModelFactory(repo, bankName)
                )
                BankAnalyticsScreen(vm = vm, onBack = { navController.popBackStack() })
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
                TransactionsScreen(
                    vm = txVm,
                    onTxClick = { id -> navController.navigate("detail/$id") }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(
                    vm = anlVm,
                    onCounterpartyClick = { categoryOrSentinel ->
                        if (categoryOrSentinel == "__uncategorized__") {
                            navController.navigate(Screen.Categorize.route) {
                                launchSingleTop = true
                            }
                        } else {
                            val encoded = android.net.Uri.encode(categoryOrSentinel)
                            navController.navigate("category_detail/$encoded")
                        }
                    }
                )
            }
            composable(
                route = "category_detail/{category}",
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { backStack ->
                val category = backStack.arguments?.getString("category") ?: return@composable
                CategoryDetailScreen(
                    vm = anlVm,
                    category = category,
                    onBack = { navController.popBackStack() },
                    onAddTransaction = { navController.navigate(Screen.Categorize.route) }
                )
            }
            composable(
                route = "detail/{txId}",
                arguments = listOf(navArgument("txId") { type = NavType.LongType })
            ) { backStack ->
                val txId = backStack.arguments?.getLong("txId") ?: return@composable
                var tx by remember { mutableStateOf<TransactionEntity?>(null) }
                LaunchedEffect(txId) { tx = repo.getById(txId) }
                tx?.let { transaction ->
                    TransactionDetailScreen(
                        tx = transaction,
                        categories = categories,
                        onCategorySelected = {
                            txVm.updateCategory(txId, it)
                            tx = transaction.copy(category = it)
                        },
                        onAddCategory = { setVm.addCustomCategory(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Screen.Categorize.route) {
                CategorizeTransactionsScreen(
                    userName = userName,
                    transactions = todayUncategorized,
                    categories = categories,
                    bulkSuggestion = bulkSuggestion,
                    onBack = { navController.popBackStack() },
                    onTxClick = { id -> navController.navigate("detail/$id") },
                    onCategorySelected = { id, category ->
                        txVm.updateCategory(id, category, allowBulkSuggestion = true)
                    },
                    onAddCategory = { setVm.addCustomCategory(it) },
                    onApplyBulkSuggestion = { txVm.applyBulkCategorySuggestion() },
                    onDismissBulkSuggestion = { txVm.dismissBulkCategorySuggestion() }
                )
            }
            composable(Screen.Profile.route) {
                SettingsScreen(
                    vm = setVm,
                    onBack = { navController.popBackStack() },
                    onEditAccount = { navController.navigate("profile_edit") },
                    onAccountSecurity = { navController.navigate("profile_security") },
                    onNotificationPreferences = { navController.navigate("profile_notifications") },
                    onConnectedBanks = {
                        navController.navigate(Screen.Accounts.route) { launchSingleTop = true }
                    },
                    onSupport = { /* TODO: open help center */ }
                )
            }
            composable("profile_edit") {
                EditAccountScreen(vm = setVm, onBack = { navController.popBackStack() })
            }
            composable("profile_security") {
                AccountSecurityScreen(vm = setVm, onBack = { navController.popBackStack() })
            }
            composable("profile_notifications") {
                NotificationPreferencesScreen(vm = setVm, onBack = { navController.popBackStack() })
            }
            composable(Screen.AccountSettings.route) {
                AccountSettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        if (showBottomBar) {
            FloatingBottomNav(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (screen == Screen.Transactions) txVm.clearFilters()
                    if (screen == Screen.Analytics) anlVm.setSearch("")
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingBottomNav(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    // Floating card with 4 evenly-spaced tab icons.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF231E1A))
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEach { screen ->
                val selected = currentRoute == screen.route ||
                    (screen == Screen.Transactions && currentRoute?.startsWith("transactions") == true)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onNavigate(screen) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = screen.iconRes),
                        contentDescription = screen.label,
                        tint = if (selected) AppBlue else Color(0xFFB7B3AC),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
