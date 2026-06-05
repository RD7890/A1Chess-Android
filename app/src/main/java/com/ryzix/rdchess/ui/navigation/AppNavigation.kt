package com.ryzix.rdchess.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryzix.rdchess.ui.screens.GameScreen
import com.ryzix.rdchess.ui.screens.HomeScreen
import com.ryzix.rdchess.ui.screens.SettingsScreen
import com.ryzix.rdchess.ui.screens.ThemeSettingsScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object ThemeSettings : Screen("theme_settings")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        enterTransition = { slideInHorizontally(tween(280)) { it } },
        exitTransition = { slideOutHorizontally(tween(280)) { -it } },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it } },
        popExitTransition = { slideOutHorizontally(tween(280)) { it } },
    ) {
        composable(Screen.Main.route) {
            MainPagerScreen(onThemeSettings = { navController.navigate(Screen.ThemeSettings.route) })
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun MainPagerScreen(onThemeSettings: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val navBg = Color(0xFF181818)
    val navBorder = Color(0xFF3C3C3C)
    val primary = Color(0xFFFF2541)
    val muted = Color(0xFF888888)
    val indicator = Color(0xFF1E0A0B)

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        bottomBar = {
            NavigationBar(
                containerColor = navBg,
                tonalElevation = 0.dp,
            ) {
                val items = listOf(
                    Triple("Home", Icons.Rounded.Home, 0),
                    Triple("Play", Icons.Rounded.PlayArrow, 1),
                    Triple("Settings", Icons.Rounded.Settings, 2),
                )
                items.forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == idx,
                        onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = primary,
                            selectedTextColor = primary,
                            indicatorColor = indicator,
                            unselectedIconColor = muted,
                            unselectedTextColor = muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onPlayVsComputer = { scope.launch { pagerState.animateScrollToPage(1) } },
                )
                1 -> GameScreen(
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                )
                2 -> SettingsScreen(
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onThemeSettings = onThemeSettings,
                )
            }
        }
    }
}
