package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryzix.rdchess.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeSettings: () -> Unit,
    vm: GameViewModel = viewModel(),
) {
    val prefs by vm.prefs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = false,
                    onClick = onBack,
                    icon = { Icon(Icons.Rounded.SportsEsports, contentDescription = null) },
                    label = { Text("Play") },
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                SettingsSectionHeader("Appearance")
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.Palette,
                    title = "Theme & Board",
                    subtitle = "Dark theme, Green board, Staunty pieces",
                    onClick = onThemeSettings,
                    trailing = { Icon(Icons.Rounded.ChevronRight, null) },
                )
            }

            item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                SettingsSectionHeader("Chess Engine")
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.Memory,
                    title = "Engine",
                    subtitle = "Stockfish 10",
                    onClick = {},
                    trailing = {
                        Text(
                            "Stockfish 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    },
                )
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.BarChart,
                    title = "Level",
                    subtitle = "Level ${prefs.levelIndex + 1} of 12",
                    onClick = {},
                    trailing = {
                        Text(
                            "${prefs.levelIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    InlineEngineSettings(
                        prefs = prefs,
                        onLevelChange = { vm.saveLevel(it) },
                        onSearchTimeChange = { vm.saveSearchTime(it) },
                        onMultiPvChange = { vm.saveMultiPv(it) },
                        onThreadsChange = { vm.saveThreads(it) },
                    )
                }
            }

            item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }

            item {
                SettingsSectionHeader("About")
            }
            item {
                SettingsTile(
                    icon = Icons.Rounded.Info,
                    title = "A1 Chess",
                    subtitle = "Version 1.0 — Native Kotlin Edition",
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}
