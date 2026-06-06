package com.ryzix.rdchess.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ryzix.rdchess.ui.screens.GameHistoryScreen
import com.ryzix.rdchess.ui.screens.GameScreen
import com.ryzix.rdchess.ui.screens.HomeScreen
import com.ryzix.rdchess.ui.screens.SettingsScreen
import com.ryzix.rdchess.ui.screens.ThemeSettingsScreen
import com.ryzix.rdchess.viewmodel.GameViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Main         : Screen("main")
    object Game         : Screen("game")
    object ThemeSettings: Screen("theme_settings")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val vm: GameViewModel = viewModel()
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainPagerScreen(
                vm              = vm,
                onPlayPressed   = { navController.navigate(Screen.Game.route) },
                onThemeSettings = { navController.navigate(Screen.ThemeSettings.route) },
            )
        }
        composable(Screen.Game.route) {
            GameScreen(
                onBack = { navController.popBackStack() },
                vm     = vm,
            )
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(
    vm: GameViewModel,
    onPlayPressed: () -> Unit,
    onThemeSettings: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope      = rememberCoroutineScope()

    fun goTo(idx: Int) = scope.launch { pagerState.animateScrollToPage(idx) }

    val navBg     = Color(0xFF181818)
    val primary   = Color(0xFFFF2541)
    val muted     = Color(0xFF888888)
    val indicator = Color(0xFF1E0A0B)

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        bottomBar = {
            NavigationBar(containerColor = navBg, tonalElevation = 0.dp) {
                listOf(
                    Triple("Home",     Icons.Rounded.Home,    0),
                    Triple("History",  Icons.Rounded.History, 1),
                    Triple("Settings", Icons.Rounded.Settings, 2),
                ).forEach { (label, icon, idx) ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == idx,
                        onClick  = { goTo(idx) },
                        icon     = { Icon(icon, contentDescription = label) },
                        label    = { Text(label) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = primary,
                            selectedTextColor   = primary,
                            indicatorColor      = indicator,
                            unselectedIconColor = muted,
                            unselectedTextColor = muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state          = pagerState,
            userScrollEnabled = false,
            modifier       = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    vm        = vm,
                    onPlay    = onPlayPressed,
                    onReview  = { game -> vm.loadGameFromHistory(game);    onPlayPressed() },
                    onContinue= { game -> vm.continueGameFromHistory(game);onPlayPressed() },
                )
                1 -> GameHistoryScreen(
                    vm             = vm,
                    onGameLoaded   = { game -> vm.loadGameFromHistory(game);    onPlayPressed() },
                    onGameContinued= { game -> vm.continueGameFromHistory(game);onPlayPressed() },
                    onPgnLoaded    = { onPlayPressed() },
                )
                2 -> SettingsScreen(
                    onBack         = { goTo(0) },
                    onThemeSettings= onThemeSettings,
                )
            }
        }
    }
}
