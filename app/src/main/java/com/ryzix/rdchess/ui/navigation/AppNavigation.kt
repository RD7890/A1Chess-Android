package com.ryzix.rdchess.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryzix.rdchess.ui.screens.GameScreen
import com.ryzix.rdchess.ui.screens.HomeScreen
import com.ryzix.rdchess.ui.screens.SettingsScreen
import com.ryzix.rdchess.ui.screens.ThemeSettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Game : Screen("game")
    object Settings : Screen("settings")
    object ThemeSettings : Screen("theme_settings")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onPlayVsComputer = { navController.navigate(Screen.Game.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Game.route) {
            GameScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onThemeSettings = { navController.navigate(Screen.ThemeSettings.route) },
            )
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
