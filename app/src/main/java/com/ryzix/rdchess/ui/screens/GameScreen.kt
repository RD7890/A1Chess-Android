package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

    var showMoveList      by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showNewGameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.newGame(playAsWhite = true, otbMode = true)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    name      = "Black",
                    timeSecs  = blackTimeSecs,
                    isActive  = !state.isWhiteTurn && !state.isGameOver,
                    isPaused  = isTimerPaused,
                )
            }

            // ── Chess board ───────────────────────────────────────────────
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
        }
    }

    // ── Game-over dialog ──────────────────────────────────────────────────────
    if (state.isGameOver && state.gameResult != null) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = when (state.gameResult) {
                        "1-0" -> "White wins!"
                        "0-1" -> "Black wins!"
                        else  -> "Draw!"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = when (state.gameResult) {
                        "1-0" -> "White has won the game."
                        "0-1" -> "Black has won the game."
                        else  -> "The game ended in a draw."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showNewGameDialog = true }) { Text("New Game") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Back") }
            },
        )
    }

    // ── New game dialog ───────────────────────────────────────────────────────
    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            title = { Text("New Game", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose time control:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showNewGameDialog = false
                                vm.newGame(otbMode = true)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("5 min") }
                        OutlinedButton(
                            onClick = {
                                showNewGameDialog = false
                                vm.newGame(otbMode = true)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("10 min") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) { Text("Cancel") }
            },
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

// ────────────────────────────────────────────────────────────────────────────
//  Player timer row
// ────────────────────────────────────────────────────────────────────────────

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
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = when {
                isActive && !isPaused -> Color.White
                isActive && isPaused  -> Color(0xFFBBBBBB)
                else                  -> Color(0xFF3A3A3A)
            },
            tonalElevation = 0.dp,
        ) {
            Text(
                text = formatTime(timeSecs),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = when {
                    isActive -> Color.Black
                    else     -> Color(0xFFAAAAAA)
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

private fun formatTime(totalSecs: Int): String {
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

// ────────────────────────────────────────────────────────────────────────────
//  Engine settings sheet (inline)
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun InlineEngineSettings(
    prefs: com.ryzix.rdchess.viewmodel.AppPrefs,
    engineAvailable: Boolean = true,
    onLevelChange: (Int) -> Unit,
    onSearchTimeChange: (Int) -> Unit,
    onMultiPvChange: (Int) -> Unit,
    onThreadsChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Engine settings",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        EngineRow(
            label = "Engine",
            value = if (engineAvailable) "Stockfish 16 (ARM64)" else "Not available — requires build",
        )

        if (!engineAvailable) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = "The Stockfish binary was not found in assets. Install the APK built by CI (GitHub Actions) which includes the compiled engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        SliderRow(
            label      = "Level",
            value      = (prefs.levelIndex + 1).toFloat(),
            valueRange = 1f..12f,
            steps      = 10,
            display    = "${prefs.levelIndex + 1}",
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            steps         = steps,
            enabled       = enabled,
            modifier      = Modifier.weight(1f),
            colors        = SliderDefaults.colors(
                thumbColor       = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            display,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
            color    = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
