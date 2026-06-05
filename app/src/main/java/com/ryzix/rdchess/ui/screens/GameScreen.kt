package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryzix.rdchess.ui.screens.components.ChessBoard
import com.ryzix.rdchess.ui.screens.components.GameBottomBar
import com.ryzix.rdchess.ui.screens.components.StockfishPanel
import com.ryzix.rdchess.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    vm: GameViewModel = viewModel(),
) {
    val state            by vm.gameState.collectAsState()
    val eval             by vm.engineEval.collectAsState()
    val isThinking       by vm.isEngineThinking.collectAsState()
    val engineEnabled    by vm.engineEnabled.collectAsState()
    val engineAvailable  by vm.engineAvailable.collectAsState()
    val analysisLines    by vm.analysisLines.collectAsState()
    val moveGrade        by vm.lastMoveGrade.collectAsState()
    val prefs            by vm.prefs.collectAsState()
    val promotionPending by vm.promotionPending.collectAsState()
    val isReviewMode     by vm.isReviewMode.collectAsState()

    var showMoveList      by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }

    // DO NOT call vm.newGame() here — game is restored from DataStore on ViewModel init,
    // and persists across tab switches since the ViewModel lives for the Activity lifetime.

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isReviewMode) "Review" else "Over the board",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (isReviewMode) {
                            Text(
                                text = "Read-only — tap ← → to step",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
                actions = {
                    // New game button
                    IconButton(onClick = { showNewGameDialog = true }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "New game")
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Engine settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // No vertical scroll on the board area — prevents accidental swipe-away.
                // The analysis panel below the board scrolls independently if needed.
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Last move hint (fixed height to avoid layout shift) ───────────
            val lastMoveText = remember(state.moves) {
                val moves = state.moves
                if (moves.isEmpty()) return@remember ""
                val moveNum     = (moves.size + 1) / 2
                val isWhiteMove = moves.size % 2 == 1
                val san         = moves.lastOrNull()?.san ?: ""
                if (isWhiteMove) "$moveNum. $san" else "$moveNum... $san"
            }
            // Always reserve exactly the same height so the board never shifts
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (lastMoveText.isNotEmpty()) {
                    Text(
                        text = lastMoveText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }

            // ── Chess board — fills all available width with minimal side margin ──
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    // Very small horizontal padding so the board is visible even in a
                    // small floating window, while still not touching screen edges.
                    .padding(horizontal = 2.dp)
                    .aspectRatio(1f),
            ) {
                ChessBoard(
                    modifier        = Modifier.fillMaxSize(),
                    state           = state,
                    onSquareTap     = { sq -> vm.onSquareTap(sq) },
                    showCoordinates = true,
                )
            }

            // ── In-game control bar ───────────────────────────────────────────
            GameBottomBar(
                onMenu           = { showMoveList = !showMoveList },
                onEngineStrength = { showSettingsSheet = true },
                onBack           = { vm.navigateBack() },
                onForward        = { vm.navigateForward() },
                onUndo           = { vm.undoOtbMove() },
                onExportPgn      = {
                    val pgn = vm.getCurrentGamePgn()
                    vm.sharePgn(pgn)
                },
                canBack          = state.canGoBack,
                canForward       = state.canGoForward,
                isReviewMode     = isReviewMode,
            )

            // ── Stockfish analysis strip + optional move list ─────────────────
            // Wrapped in a scrollable column so it doesn't push the board off screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                StockfishPanel(
                    eval            = eval,
                    isThinking      = isThinking,
                    engineEnabled   = engineEnabled,
                    engineAvailable = engineAvailable,
                    analysisLines   = analysisLines,
                    moves           = state.moves,
                    showMoveList    = showMoveList,
                    moveGrade       = moveGrade,
                    onToggleEngine  = { vm.toggleEngine() },
                    modifier        = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // ── Pawn promotion picker ─────────────────────────────────────────────────
    if (promotionPending != null) {
        PromotionDialog(
            isWhite   = state.isWhiteTurn,
            onPick    = { vm.confirmPromotion(it) },
            onDismiss = { vm.cancelPromotion() },
        )
    }

    // ── Game-over overlay ─────────────────────────────────────────────────────
    if (state.isGameOver && state.gameResult != null) {
        GameOverDialog(
            result    = state.gameResult!!,
            pgn       = vm.getCurrentGamePgn(),
            onExport  = { pgn -> vm.sharePgn(pgn) },
            onNewGame = { showNewGameDialog = true },
            onBack    = onBack,
        )
    }

    // ── New game dialog ───────────────────────────────────────────────────────
    if (showNewGameDialog) {
        NewGameDialog(
            onStart   = {
                showNewGameDialog = false
                vm.newGame(otbMode = true)
            },
            onDismiss = { showNewGameDialog = false },
        )
    }

    // ── Engine settings sheet ─────────────────────────────────────────────────
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            InlineEngineSettings(
                prefs              = prefs,
                engineAvailable    = engineAvailable,
                onLevelChange      = { vm.saveLevel(it) },
                onSearchTimeChange = { vm.saveSearchTime(it) },
                onMultiPvChange    = { vm.saveMultiPv(it) },
                onThreadsChange    = { vm.saveThreads(it) },
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Promotion picker dialog ────────────────────────────────────────────────────

@Composable
private fun PromotionDialog(
    isWhite: Boolean,
    onPick: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    val pieces = listOf('q' to "Queen", 'r' to "Rook", 'b' to "Bishop", 'n' to "Knight")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote pawn", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a piece for ${if (isWhite) "White" else "Black"}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    pieces.forEach { (char, name) ->
                        OutlinedButton(
                            onClick = { onPick(char) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (char) { 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; else -> "♞" },
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Game-over dialog ──────────────────────────────────────────────────────────

@Composable
private fun GameOverDialog(
    result: String,
    pgn: String,
    onExport: (String) -> Unit,
    onNewGame: () -> Unit,
    onBack: () -> Unit,
) {
    val (title, subtitle) = when (result) {
        "1-0"     -> "White Wins!" to "Checkmate — well played!"
        "0-1"     -> "Black Wins!" to "Checkmate — well played!"
        "1/2-1/2" -> "Draw!" to "The game ended in a draw."
        else      -> "Game Over" to result
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(subtitle) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                    Text("New Game")
                }
                OutlinedButton(
                    onClick = { onExport(pgn) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export PGN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back") }
        },
    )
}

// ── New game dialog ───────────────────────────────────────────────────────────

@Composable
private fun NewGameDialog(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game?", fontWeight = FontWeight.Bold) },
        text  = { Text("Start a fresh game? The current game will be lost.") },
        confirmButton = {
            Button(onClick = onStart) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Engine settings sheet ─────────────────────────────────────────────────────

@Composable
fun InlineEngineSettings(
    prefs: com.ryzix.rdchess.viewmodel.AppPrefs,
    engineAvailable: Boolean = true,
    onLevelChange: (Int) -> Unit,
    onSearchTimeChange: (Int) -> Unit,
    onMultiPvChange: (Int) -> Unit,
    onThreadsChange: (Int) -> Unit,
) {
    val levelLabels = listOf("800","1200","1400","1600","1800","2000","2200","2400","2600","2700","2800","Max")

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Engine settings",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        EngineRow(
            label = "Engine",
            value = if (engineAvailable) "Stockfish 16 (ARM64)" else "Not available",
        )

        if (!engineAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "Stockfish binary not found. Install the APK built by GitHub Actions CI which includes the compiled engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        val levelLabel = levelLabels.getOrElse(prefs.levelIndex) { "${prefs.levelIndex + 1}" }
        SliderRow(
            label      = "Level",
            value      = (prefs.levelIndex + 1).toFloat(),
            valueRange = 1f..12f,
            steps      = 10,
            display    = levelLabel,
            enabled    = engineAvailable,
            onValueChange = { onLevelChange(it.toInt() - 1) },
        )

        SliderRow(
            label      = "Search time",
            value      = prefs.searchTimeMs.toFloat(),
            valueRange = 3000f..8000f,
            steps      = 9,
            display    = "${prefs.searchTimeMs / 1000f}s",
            enabled    = engineAvailable,
            onValueChange = { onSearchTimeChange(it.toInt()) },
        )

        SliderRow(
            label      = "Lines (MultiPV)",
            value      = prefs.multiPv.toFloat(),
            valueRange = 1f..5f,
            steps      = 3,
            display    = "${prefs.multiPv}",
            enabled    = engineAvailable,
            onValueChange = { onMultiPvChange(it.toInt()) },
        )

        SliderRow(
            label      = "CPUs",
            value      = prefs.threads.toFloat(),
            valueRange = 1f..4f,
            steps      = 2,
            display    = "${prefs.threads}",
            enabled    = engineAvailable,
            onValueChange = { onThreadsChange(it.toInt()) },
        )
    }
}

@Composable
private fun EngineRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(120.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor       = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            display,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(48.dp),
            color    = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
