package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
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
    // Shown when a game ends; user can close it and manually review moves with ← →
    var showGameOverDialog by remember { mutableStateOf(true) }
    LaunchedEffect(state.isGameOver) { if (state.isGameOver) showGameOverDialog = true }

    // DO NOT call vm.newGame() here — game is restored from DataStore on ViewModel init,
    // and persists across tab switches since the ViewModel lives for the Activity lifetime.

    // Outer scrollable Column — allows the whole play screen to scroll in floating/pip windows.
    // No Scaffold/TopAppBar so no WindowInsets status-bar padding is injected.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Compact header row (zero window insets) ───────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isReviewMode) "Review" else "Over the board",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Fullscreen toggle — hides/shows the bottom nav bar
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                )
            }
            IconButton(onClick = { showNewGameDialog = true }) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = "New game")
            }
            IconButton(onClick = { showSettingsSheet = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Engine settings")
            }
        }

        // ── Last move hint (fixed height to avoid layout shift) ─────────────
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

    }  // end outer Column

    // ── Pawn promotion picker ─────────────────────────────────────────────────
    if (promotionPending != null) {
        PromotionDialog(
            isWhite   = state.isWhiteTurn,
            onPick    = { vm.confirmPromotion(it) },
            onDismiss = { vm.cancelPromotion() },
        )
    }

    // ── Game-over overlay ─────────────────────────────────────────────────────
    if (state.isGameOver && state.gameResult != null && showGameOverDialog) {
        GameOverDialog(
            result    = state.gameResult!!,
            pgn       = vm.getCurrentGamePgn(),
            onExport  = { pgn -> vm.sharePgn(pgn) },
            onNewGame = { showNewGameDialog = true },
            onAnalyse = { showGameOverDialog = false },
            onClose   = onBack,
        )
    }

    // ── New game dialog ───────────────────────────────────────────────────────
    if (showNewGameDialog) {
        NewGameDialog(
            onStart = { whiteAtBottom ->
                showNewGameDialog = false
                // otbMode = true → both sides played by humans, Stockfish only shows arrows/hints
                vm.newGame(otbMode = true, playerIsWhite = whiteAtBottom)
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
    onAnalyse: () -> Unit,
    onClose: () -> Unit,
) {
    val (title, subtitle) = when (result) {
        "1-0"     -> "White Wins!" to "Checkmate — well played!"
        "0-1"     -> "Black Wins!" to "Checkmate — well played!"
        "1/2-1/2" -> "Draw!" to "The game ended in a draw."
        else      -> "Game Over" to result
    }

    AlertDialog(
        onDismissRequest = onAnalyse,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text  = { Text(subtitle) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
                    Text("New Game")
                }
                OutlinedButton(
                    onClick = onAnalyse,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Analyse Manually")
                }
                OutlinedButton(
                    onClick = { onExport(pgn) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Export PGN")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

// ── New game dialog ───────────────────────────────────────────────────────────

@Composable
private fun NewGameDialog(
    onStart: (whiteAtBottom: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Which side at bottom?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "Stockfish will only show hints & arrows — both sides are played by you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // White at bottom
                    ColorPickerTile(
                        symbol = "♔",
                        label  = "White",
                        bg     = Color(0xFFF0D9B5),
                        fg     = Color(0xFF1A1A1A),
                        modifier = Modifier.weight(1f),
                        onClick  = { onStart(true) },
                    )
                    // Random orientation
                    ColorPickerTile(
                        symbol = "?",
                        label  = "Random",
                        bg     = MaterialTheme.colorScheme.surfaceVariant,
                        fg     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick  = { onStart((0..1).random() == 0) },
                    )
                    // Black at bottom
                    ColorPickerTile(
                        symbol = "♚",
                        label  = "Black",
                        bg     = Color(0xFF2C2C2C),
                        fg     = Color(0xFFF0D9B5),
                        modifier = Modifier.weight(1f),
                        onClick  = { onStart(false) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ColorPickerTile(
    symbol: String,
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = symbol, fontSize = 26.sp, color = fg)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
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
    val levelLabels = listOf("800","1200","1400","1600","1800","2000","2200","2400","2600","2700","2800","3200")

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
