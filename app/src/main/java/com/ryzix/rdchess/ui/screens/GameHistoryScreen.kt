package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
    onGameLoaded: (SavedGame) -> Unit = {},
    onPgnLoaded: () -> Unit = {},
) {
    val history by vm.gameHistory.collectAsState()
    var showClearDialog    by remember { mutableStateOf(false) }
    var showPgnDialog      by remember { mutableStateOf(false) }
    var pgnInput           by remember { mutableStateOf("") }
    var pgnError           by remember { mutableStateOf(false) }
    var expandedGameIdx    by remember { mutableStateOf<Int?>(null) }

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
                    // Analyse PGN button
                    IconButton(onClick = { showPgnDialog = true; pgnInput = ""; pgnError = false }) {
                        Icon(
                            Icons.Rounded.Analytics,
                            contentDescription = "Analyse PGN",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                modifier = Modifier.fillMaxSize().padding(padding),
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showPgnDialog = true; pgnInput = ""; pgnError = false }) {
                        Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyse a PGN")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "${history.size} game${if (history.size == 1) "" else "s"} — tap to review",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                itemsIndexed(history) { idx, game ->
                    val isExpanded = expandedGameIdx == idx
                    GameHistoryCard(
                        index      = idx + 1,
                        game       = game,
                        isExpanded = isExpanded,
                        onTap      = { expandedGameIdx = if (isExpanded) null else idx },
                        onReview   = { onGameLoaded(game) },
                        onExport   = { vm.sharePgn(game.generatePgn()) },
                    )
                }
            }
        }
    }

    // ── Clear history dialog ──────────────────────────────────────────────────
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

    // ── Analyse PGN dialog ────────────────────────────────────────────────────
    if (showPgnDialog) {
        AlertDialog(
            onDismissRequest = { showPgnDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Analytics, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Analyse PGN", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a PGN to load the game for analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    OutlinedTextField(
                        value = pgnInput,
                        onValueChange = { pgnInput = it; pgnError = false },
                        placeholder = {
                            Text(
                                "1. e4 e5 2. Nf3 Nc6 3. Bb5 ...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            )
                        },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        isError = pgnError,
                        supportingText = if (pgnError) {
                            { Text("Could not parse PGN. Check the format and try again.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall) }
                        } else null,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = vm.loadGameFromPgn(pgnInput.trim())
                        if (ok) {
                            showPgnDialog = false
                            onPgnLoaded()
                        } else {
                            pgnError = true
                        }
                    },
                    enabled = pgnInput.isNotBlank(),
                ) { Text("Analyse") }
            },
            dismissButton = {
                TextButton(onClick = { showPgnDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Game history card ──────────────────────────────────────────────────────────

@Composable
private fun GameHistoryCard(
    index: Int,
    game: SavedGame,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onReview: () -> Unit,
    onExport: () -> Unit,
) {
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

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isExpanded) 4.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            if (isExpanded) 1.5.dp else 1.dp,
            if (isExpanded) resultColor.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        ),
    ) {
        Column {
            // ── Card header row — always visible ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTap)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
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
                        text = "${game.moveCount} moves  ·  $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

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

                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            // ── Expanded actions ──────────────────────────────────────────────
            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Review game button
                    Button(
                        onClick = onReview,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Review", style = MaterialTheme.typography.labelMedium)
                    }

                    // Export PGN button
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export PGN", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
