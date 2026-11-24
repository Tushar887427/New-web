package com.soloadmin.hub.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soloadmin.hub.data.preferences.AppSettings
import com.soloadmin.hub.data.preferences.StartScreen
import com.soloadmin.hub.di.AppContainer
import com.soloadmin.hub.ui.SoloViewModelFactory
import com.soloadmin.hub.ui.components.LocalSnackbar
import com.soloadmin.hub.ui.screens.attendance.AttendanceScreen
import com.soloadmin.hub.ui.screens.colleagues.ColleaguesScreen
import com.soloadmin.hub.ui.screens.expenses.ExpensesScreen
import com.soloadmin.hub.ui.screens.finance.FinanceScreen
import com.soloadmin.hub.ui.screens.home.HomeScreen
import com.soloadmin.hub.ui.screens.keydates.KeyDatesScreen
import com.soloadmin.hub.ui.screens.ledger.LedgerScreen
import com.soloadmin.hub.ui.screens.officialduty.OfficialDutyScreen
import com.soloadmin.hub.ui.screens.profile.ProfileScreen
import com.soloadmin.hub.ui.screens.settings.AboutScreen
import com.soloadmin.hub.ui.screens.settings.BackupScreen
import com.soloadmin.hub.ui.screens.settings.SettingsScreen
import com.soloadmin.hub.ui.screens.tasks.TasksScreen
import kotlinx.coroutines.launch

/**
 * Root of the app: a five-tab bottom bar (Home, Ledger, Expenses, Finance, Settings) over a
 * Navigation-Compose graph. Detail screens are pushed but keep the bar, because every one of them
 * is a task the user returns from quickly.
 */
@Composable
fun SoloAdminApp(
    settings: AppSettings,
    container: AppContainer,
    launchRoute: String?,
    importUri: android.net.Uri?,
    onImportConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val factory = remember(container) { SoloViewModelFactory(container) }
    val showSnackbar: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = tabForRoute(currentRoute)

    // Honour the "start screen" preference and notification deep links exactly once.
    var startHandled by remember { mutableStateOf(false) }
    LaunchedEffect(launchRoute, settings.startScreen) {
        if (startHandled) return@LaunchedEffect
        startHandled = true
        val target = launchRoute ?: when (settings.startScreen) {
            StartScreen.HOME -> Routes.HOME
            StartScreen.LEDGER -> Routes.LEDGER
            StartScreen.EXPENSES -> Routes.EXPENSES
            StartScreen.FINANCE -> Routes.FINANCE
        }
        if (target != Routes.HOME) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides showSnackbar) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                SoloBottomBar(
                    selected = currentTab,
                    onSelect = { tab -> navController.navigateToTab(tab) }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(180)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(120)) }
                ) {
                    composable(Routes.HOME) {
                        val vm: com.soloadmin.hub.ui.screens.home.HomeViewModel = viewModel(factory = factory)
                        val leaveVm: com.soloadmin.hub.ui.screens.attendance.AttendanceViewModel = viewModel(factory = factory)
                        HomeScreen(
                            viewModel = vm,
                            leaveViewModel = leaveVm,
                            settings = settings,
                            onNavigate = { navController.navigate(it) },
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.LEDGER) {
                        LedgerScreen(
                            container = container,
                            onNavigate = { navController.navigate(it) },
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.EXPENSES) {
                        val vm: com.soloadmin.hub.ui.screens.expenses.ExpensesViewModel = viewModel(factory = factory)
                        ExpensesScreen(vm, onSnackbar = showSnackbar)
                    }
                    composable(Routes.FINANCE) {
                        val vm: com.soloadmin.hub.ui.screens.finance.FinanceViewModel = viewModel(factory = factory)
                        FinanceScreen(vm, onSnackbar = showSnackbar, onOpenReports = { navController.navigate(Routes.SETTINGS) })
                    }
                    composable(Routes.SETTINGS) {
                        val vm: com.soloadmin.hub.ui.screens.settings.SettingsViewModel = viewModel(factory = factory)
                        SettingsScreen(
                            viewModel = vm,
                            onNavigate = { navController.navigate(it) },
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.ATTENDANCE) {
                        val vm: com.soloadmin.hub.ui.screens.attendance.AttendanceViewModel = viewModel(factory = factory)
                        AttendanceScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenDuty = { navController.navigate(Routes.OFFICIAL_DUTY) },
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.OFFICIAL_DUTY) {
                        val vm: com.soloadmin.hub.ui.screens.officialduty.OfficialDutyViewModel = viewModel(factory = factory)
                        OfficialDutyScreen(vm, onBack = { navController.popBackStack() }, onSnackbar = showSnackbar)
                    }
                    composable(Routes.TASKS) {
                        val vm: com.soloadmin.hub.ui.screens.tasks.TasksViewModel = viewModel(factory = factory)
                        TasksScreen(vm, onBack = { navController.popBackStack() })
                    }
                    composable(Routes.KEY_DATES) {
                        val vm: com.soloadmin.hub.ui.screens.keydates.KeyDatesViewModel = viewModel(factory = factory)
                        KeyDatesScreen(vm, onBack = { navController.popBackStack() }, onSnackbar = showSnackbar)
                    }
                    composable(Routes.COLLEAGUES) {
                        val vm: com.soloadmin.hub.ui.screens.colleagues.ColleaguesViewModel = viewModel(factory = factory)
                        ColleaguesScreen(vm, onBack = { navController.popBackStack() }, onSnackbar = showSnackbar)
                    }
                    composable(Routes.PROFILE) {
                        val vm: com.soloadmin.hub.ui.screens.profile.ProfileViewModel = viewModel(factory = factory)
                        ProfileScreen(
                            vm,
                            onBack = { navController.popBackStack() },
                            onOpenColleagues = { navController.navigate(Routes.COLLEAGUES) },
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.BACKUP) {
                        val vm: com.soloadmin.hub.ui.screens.settings.SettingsViewModel = viewModel(factory = factory)
                        BackupScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            importUri = importUri,
                            onImportConsumed = onImportConsumed,
                            onSnackbar = showSnackbar
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

        }
    }
}

private fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Fade transition helper used by screens that swap content in place. */
@Composable
fun <T> SubtleCrossfade(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "subtle",
        modifier = modifier
    ) { state -> content(state) }
}

