package com.mobilecontrol.app.ui.start

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobilecontrol.app.R
import com.mobilecontrol.app.ui.dashboards.DashboardListScreen
import com.mobilecontrol.app.ui.navigation.Routes
import com.mobilecontrol.app.ui.notifications.NotificationsScreen
import com.mobilecontrol.app.ui.objects.ObjectBrowserScreen
import com.mobilecontrol.app.ui.settings.SettingsScreen

private data class StartTab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    StartTab(Routes.DASHBOARDS, R.string.start_tab_dashboards, Icons.Filled.Dashboard),
    StartTab(Routes.OBJECTS, R.string.start_tab_objects, Icons.Filled.List),
    StartTab(Routes.NOTIFICATIONS, R.string.start_tab_notifications, Icons.Filled.Notifications),
    StartTab(Routes.SETTINGS, R.string.start_tab_settings, Icons.Filled.Settings),
)

@Composable
fun StartScreen(
    onOpenDashboard: (String) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by tabNavController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = Routes.DASHBOARDS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARDS) { DashboardListScreen(onOpenDashboard = onOpenDashboard) }
            composable(Routes.OBJECTS) { ObjectBrowserScreen() }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onLoggedOut = onLoggedOut) }
        }
    }
}
