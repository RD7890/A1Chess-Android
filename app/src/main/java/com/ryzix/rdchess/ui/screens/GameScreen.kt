package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryzix.rdchess.ui.screens.components.ChessBoard
import com.ryzix.rdchess.ui.screens.components.GameBottomBar
import com.ryzix.rdchess.ui.screens.components.StockfishPanel
import com.ryzix.rdchess.viewmodel.GameViewModel

// Time-control options: label → seconds
private val TIME_OPTIONS = listOf(
    "1 min"  to 60,
    "3 min"  to 180,
    "5 min"  to 300,
    "10 min" to 600,
    "15 min" to 900,
    "30 min" to 1800,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    vm: GameViewModel = viewModel(),
) {
    val state           by vm.gameState.collectAsState()
    val eval            by vm.engineEval.collectAsState()
    val isThinking      by vm.isEngineThinking.collectAsState()
    val engineEnabled   by vm.engineEnabled.collectAsState()
    val engineAvailable by vm.engineAvailable.collectAsState()
    val analysisLines   by vm.analysisLines.collectAsState()
    val moveGrade       by vm.lastMoveGrade.collectAsState()
    val prefs           by vm.prefs.collectAsState()
    val whiteTimeSecs   by vm.whiteTimeSecs.collectAsState()
    val blackTimeSecs   by vm.blackTimeSecs.collectAsState()
    val isTimerPaused   by vm.isTimerPaused.collectAsState()
    val promotionPending by vm.promotionPending.collectAsState()

    var showMoveList      by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.newGame(playAsWhite = true, otbMode = true, timeSecs = 300)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Over the board",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        // Scrollable column so analysis panel + move list are always reachable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Last move hint ────────────────────────────────────────────
            val lastMoveText = remember(state.moves) {
                val moves = state.moves
                if (moves.isEmpty()) return@remember ""
                val moveNum     = (moves.size + 1) / 2
                val isWhiteMove = moves.size % 2 == 1
                val san         = moves.lastOrNull()?.san ?: ""
                if (isWhiteMove) "$moveNum. $san" else "$moveNum... $san"
            }
            if (lastMoveText.isNotEmpty()) {
                Text(
                    text = lastMoveText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // ── Black player row (rotated so Black can read it) ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = 180f },
            ) {
                PlayerTimerRow(
                    name     = "Black",
                    timeSecs = blackTimeSecs,
                    isActive = !state.isWhiteTurn && !state.isGameOver,
                    isPaused = isTimerPaused,
                )
            }

            // ── Chess board (always square = full screen width) ───────────
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                ChessBoard(
                    modifier        = Modifier.fillMaxSize(),
                    state           = state,
                    onSquareTap     = { sq -> vm.onSquareTap(sq) },
                    showCoordinates = true,
                )
            }

            // ── White player row ──────────────────────────────────────────
            PlayerTimerRow(
                name     = "White",
                timeSecs = whiteTimeSecs,
                isActive = state.isWhiteTurn && !state.isGameOver,
                isPaused = isTimerPaused,
            )

            // ── In-game control bar ───────────────────────────────────────
            GameBottomBar(
                onMenu           = { showMoveList = !showMoveList },
                onEngineStrength = { showSettingsSheet = true },
                onPauseToggle    = { vm.toggleTimer() },
                onBack           = { vm.navigateBack() },
                onForward        = { vm.navigateForward() },
                onUndo           = { vm.undoOtbMove() },
                isPaused         = isTimerPaused,
                canBack          = state.canGoBack,
                canForward       = state.canGoForward,
            )

            // ── Stockfish analysis strip + optional move list ─────────────
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

            // Bottom padding so panel doesn't get cut by nav bar
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Pawn promotion picker ─────────────────────────────────────────────────
    if (promotionPending != null) {
        PromotionDialog(
            isWhite = state.isWhiteTurn,
            onPick  = { vm.confirmPromotion(it) },
            onDismiss = { vm.cancelPromotion() },
        )
    }

    // ── Game-over overlay ─────────────────────────────────────────────────────
    if (state.isGameOver && state.gameResult != null) {
        GameOverDialog(
            result = state.gameResult!!,
            onNewGame = { showNewGameDialog = true },
            onBack = onBack,
        )
    }

    // ── New game dialog ───────────────────────────────────────────────────────
    if (showNewGameDialog) {
        NewGameDialog(
            onStart = { timeSecs ->
                showNewGameDialog = false
                vm.newGame(otbMode = true, timeSecs = timeSecs)
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
    val pieces = listOf(
        'q' to "Queen",
        'r' to "Rook",
        'b' to "Bishop",
        'n' to "Knight",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Promote pawn", fontWeight = FontWeight.Bold)
        },
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Game-over dialog ──────────────────────────────────────────────────────────

@Composable
private fun GameOverDialog(
    result: String,
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
            Button(onClick = onNewGame) { Text("New Game") }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("Back") }
        },
    )
}

// ── New game dialog ───────────────────────────────────────────────────────────

@Composable
private fun NewGameDialog(
    onStart: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTime by remember { mutableIntStateOf(300) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose time control:", style = MaterialTheme.typography.bodyMedium)
                // 3-column grid of time chips
                val chunked = TIME_OPTIONS.chunked(3)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (label, secs) ->
                            val selected = selectedTime == secs
                            OutlinedButton(
                                onClick = { selectedTime = secs },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(selectedTime) }) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Player timer row ──────────────────────────────────────────────────────────

@Composable
private fun PlayerTimerRow(
    name: String,
    timeSecs: Int,
    isActive: Boolean,
    isPaused: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = when {
                isActive && !isPaused -> Color(0xFFFF2541)
                isActive && isPaused  -> Color(0xFFBB1E30)
                else                  -> Color(0xFF1E1E1E)
            },
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isActive) Color(0xFFC01D30) else Color(0xFF3C3C3C),
            ),
            tonalElevation = 0.dp,
        ) {
            Text(
                text = formatTime(timeSecs),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = if (isActive) Color.White else Color(0xFF666666),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

private fun formatTime(totalSecs: Int): String {
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
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

        // Level slider — show ELO label
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
            valueRange = 500f..5000f,
            steps      = 8,
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
