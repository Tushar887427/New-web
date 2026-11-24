package com.soloadmin.hub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Every destination in the app. Detail screens are pushed on top of a tab. */
object Routes {
    const val HOME = "home"
    const val LEDGER = "ledger"
    const val EXPENSES = "expenses"
    const val FINANCE = "finance"
    const val SETTINGS = "settings"

    const val ATTENDANCE = "attendance"
    const val OFFICIAL_DUTY = "official_duty"
    const val KEY_DATES = "key_dates"
    const val TASKS = "tasks"
    const val COLLEAGUES = "colleagues"
    const val PROFILE = "profile"
    const val ABOUT = "about"
    const val BACKUP = "backup"
}

enum class TopLevelTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    LEDGER(Routes.LEDGER, "Ledger", Icons.Outlined.GridView, Icons.Outlined.GridView),
    EXPENSES(Routes.EXPENSES, "Expenses", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    FINANCE(Routes.FINANCE, "Finance", Icons.Rounded.Payments, Icons.Outlined.Payments),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings);

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
        fun forRoute(route: String?): TopLevelTab? = entries.firstOrNull { it.route == route }
    }
}

/** Which tab a pushed screen belongs to, so the bottom bar can stay visible and highlighted. */
fun tabForRoute(route: String?): TopLevelTab = when (route) {
    Routes.LEDGER, Routes.ATTENDANCE, Routes.OFFICIAL_DUTY, Routes.TASKS -> TopLevelTab.LEDGER
    Routes.EXPENSES -> TopLevelTab.EXPENSES
    Routes.FINANCE -> TopLevelTab.FINANCE
    Routes.SETTINGS, Routes.KEY_DATES, Routes.COLLEAGUES, Routes.PROFILE, Routes.ABOUT, Routes.BACKUP ->
        TopLevelTab.SETTINGS
    else -> TopLevelTab.HOME
}
