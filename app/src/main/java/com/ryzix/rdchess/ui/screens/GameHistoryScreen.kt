package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryzix.rdchess.viewmodel.GameViewModel
import com.ryzix.rdchess.viewmodel.SavedGame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    vm: GameViewModel = viewModel(),
) {
    val history by vm.gameHistory.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Game History", fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear history",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    )
                    Text(
                        "No games played yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                    Text(
                        "Completed games will appear here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "${history.size} game${if (history.size == 1) "" else "s"} played",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(history) { idx, game ->
                    GameHistoryCard(index = idx + 1, game = game)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear history?", fontWeight = FontWeight.Bold) },
            text  = { Text("This will permanently remove all ${history.size} saved games.") },
            confirmButton = {
                Button(
                    onClick = { vm.clearHistory(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GameHistoryCard(index: Int, game: SavedGame) {
    val (resultLabel, resultColor) = when (game.result) {
        "1-0"     -> "White wins" to Color(0xFF4CAF50)
        "0-1"     -> "Black wins" to Color(0xFFF44336)
        "1/2-1/2" -> "Draw"       to Color(0xFFFF9800)
        else      -> game.result  to MaterialTheme.colorScheme.onSurface
    }

    val dateStr = remember(game.timestamp) {
        if (game.timestamp == 0L) "—"
        else SimpleDateFormat("dd MMM yyyy  HH:mm", Locale.getDefault()).format(Date(game.timestamp))
    }

    val timeLabel = when {
        game.startTimeSecs < 120  -> "${game.startTimeSecs}s"
        game.startTimeSecs < 3600 -> "${game.startTimeSecs / 60} min"
        else                      -> "${game.startTimeSecs / 3600}h"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Trophy icon coloured by result
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = resultColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resultLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = resultColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${game.moveCount} moves  ·  $timeLabel  ·  $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // Game number badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}
