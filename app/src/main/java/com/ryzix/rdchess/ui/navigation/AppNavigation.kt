package com.ryzix.rdchess.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    object Main : Screen("main")
    object ThemeSettings : Screen("theme_settings")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
    ) {
        composable(Screen.Main.route) {
            MainPagerScreen(onThemeSettings = { navController.navigate(Screen.ThemeSettings.route) })
        }
        composable(Screen.ThemeSettings.route) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(onThemeSettings: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope      = rememberCoroutineScope()
    val vm: GameViewModel = viewModel()

    // Fullscreen state — hides the bottom nav bar while on the Play page (index 1)
    var isFullscreen by remember { mutableStateOf(false) }

    // Reset fullscreen whenever the user navigates away from the Play page
    val onPlayPage = pagerState.currentPage == 1
    if (!onPlayPage && isFullscreen) isFullscreen = false

    // Mid-game save prompt — shown when user tries to leave the Play page with a game running
    var showSavePrompt  by remember { mutableStateOf(false) }
    var pendingPage     by remember { mutableIntStateOf(0) }

    val gameState    by vm.gameState.collectAsState()
    val isReviewMode by vm.isReviewMode.collectAsState()

    /** Navigate to [target], but gate behind a save-prompt if a live game is running. */
    fun navigateTo(target: Int) {
        val leavingPlay = pagerState.currentPage == 1 && target != 1
        val active = gameState.moves.isNotEmpty() && !gameState.isGameOver && !isReviewMode
        if (leavingPlay && active) {
            pendingPage = target
            showSavePrompt = true
        } else {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    val navBg     = Color(0xFF181818)
    val primary   = Color(0xFFFF2541)
    val muted     = Color(0xFF888888)
    val indicator = Color(0xFF1E0A0B)

    Scaffold(
        containerColor = Color(0xFF0D0D0D),
        bottomBar = {
            // Hide the bottom nav when fullscreen is active on the Play page
            if (!(isFullscreen && onPlayPage)) {
                NavigationBar(
                    containerColor = navBg,
                    tonalElevation = 0.dp,
                ) {
                    val items = listOf(
                        Triple("Home",     Icons.Rounded.Home,      0),
                        Triple("Play",     Icons.Rounded.PlayArrow, 1),
                        Triple("History",  Icons.Rounded.History,   2),
                        Triple("Settings", Icons.Rounded.Settings,  3),
                    )
                    items.forEach { (label, icon, idx) ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == idx,
                            onClick  = { navigateTo(idx) },
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
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            // Swipe disabled — only the bottom nav bar can switch tabs.
            // This is critical to stop accidental mid-game tab changes.
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    onPlayVsComputer = { navigateTo(1) },
                )
                1 -> GameScreen(
                    onBack = { navigateTo(0) },
                    vm = vm,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                )
                2 -> GameHistoryScreen(
                    vm = vm,
                    onGameLoaded = { game ->
                        vm.loadGameFromHistory(game)
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onGameContinued = { game ->
                        vm.continueGameFromHistory(game)
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onPgnLoaded = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                )
                3 -> SettingsScreen(
                    onBack = { navigateTo(0) },
                    onThemeSettings = onThemeSettings,
                )
            }
        }
    }

    // ── Mid-game save / discard prompt ────────────────────────────────────────
    if (showSavePrompt) {
        AlertDialog(
            onDismissRequest = { showSavePrompt = false },
            title = { Text("Save game?", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Do you want to save this game so you can continue or analyse it later?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.saveGameInProgress()
                        showSavePrompt = false
                        scope.launch { pagerState.animateScrollToPage(pendingPage) }
                    },
                ) {
                    Text("Save & Leave")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSavePrompt = false
                        scope.launch { pagerState.animateScrollToPage(pendingPage) }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Discard")
                }
            },
        )
    }
}
