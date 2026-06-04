package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bhlangonijr.chesslib.Side
import com.ryzix.rdchess.ui.screens.components.ChessBoard
import com.ryzix.rdchess.ui.screens.components.GameBottomBar
import com.ryzix.rdchess.ui.screens.components.MovePanel
import com.ryzix.rdchess.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    onBack: () -> Unit,
    vm: GameViewModel = viewModel(),
) {
    val state by vm.gameState.collectAsState()
    val eval by vm.engineEval.collectAsState()
    val isThinking by vm.isEngineThinking.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.newGame(playAsWhite = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isThinking) "Computer thinking..." else "Play vs Computer",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Engine settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            GameBottomBar(
                onUndo = { vm.undoMove() },
                onFlip = { vm.flipBoard() },
                onNewGame = { showNewGameDialog = true },
                onSettings = { showSettingsSheet = true },
                canUndo = state.moves.size >= 2,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Player info top (computer)
            PlayerInfo(
                name = "Stockfish",
                isComputer = true,
                isActive = !state.isWhiteTurn && !state.isGameOver,
                isFlipped = state.isFlipped,
            )

            // Chess Board
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                ChessBoard(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    onSquareTap = { sq -> vm.onSquareTap(sq) },
                    showCoordinates = true,
                )
            }

            // Player info bottom (human)
            PlayerInfo(
                name = "You",
                isComputer = false,
                isActive = state.isWhiteTurn && !state.isGameOver,
                isFlipped = state.isFlipped,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Move panel
            MovePanel(
                moves = state.moves,
                currentMoveIndex = state.currentMoveIndex,
                eval = eval,
                isEngineThinking = isThinking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )
        }
    }

    // Game over dialog
    if (state.isGameOver && state.gameResult != null) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = when (state.gameResult) {
                        "1-0" -> "White wins!"
                        "0-1" -> "Black wins!"
                        else -> "Draw!"
                    },
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = when (state.gameResult) {
                        "1-0" -> "White won the game."
                        "0-1" -> "Black won the game."
                        else -> "The game ended in a draw."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showNewGameDialog = true }) {
                    Text("New Game")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            },
        )
    }

    // New game dialog
    if (showNewGameDialog) {
        NewGameDialog(
            onDismiss = { showNewGameDialog = false },
            onConfirm = { playAsWhite ->
                showNewGameDialog = false
                vm.newGame(playAsWhite = playAsWhite)
            },
        )
    }

    // Engine settings sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            InlineEngineSettings(
                prefs = prefs,
                onLevelChange = { vm.saveLevel(it) },
                onSearchTimeChange = { vm.saveSearchTime(it) },
                onMultiPvChange = { vm.saveMultiPv(it) },
                onThreadsChange = { vm.saveThreads(it) },
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerInfo(
    name: String,
    isComputer: Boolean,
    isActive: Boolean,
    isFlipped: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Text(
                text = if (isComputer) "AI" else "YOU",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (isActive) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        if (isActive) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(8.dp),
            ) {}
        }
    }
}

@Composable
private fun NewGameDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Choose your side:")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onConfirm(true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("⬜ White") }
                    OutlinedButton(
                        onClick = { onConfirm(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("⬛ Black") }
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
fun InlineEngineSettings(
    prefs: com.ryzix.rdchess.viewmodel.AppPrefs,
    onLevelChange: (Int) -> Unit,
    onSearchTimeChange: (Int) -> Unit,
    onMultiPvChange: (Int) -> Unit,
    onThreadsChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Chess engine",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        EngineRow(label = "Engine", value = "Stockfish 10")

        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

        Text(
            "Stockfish",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        SliderRow(
            label = "Level",
            value = (prefs.levelIndex + 1).toFloat(),
            valueRange = 1f..12f,
            steps = 10,
            display = "${prefs.levelIndex + 1}",
            onValueChange = { onLevelChange(it.toInt() - 1) },
        )

        SliderRow(
            label = "Search time",
            value = prefs.searchTimeMs.toFloat(),
            valueRange = 500f..5000f,
            steps = 8,
            display = "${prefs.searchTimeMs / 1000f}s",
            onValueChange = { onSearchTimeChange(it.toInt()) },
        )

        SliderRow(
            label = "Multiple lines",
            value = prefs.multiPv.toFloat(),
            valueRange = 1f..5f,
            steps = 3,
            display = "${prefs.multiPv}",
            onValueChange = { onMultiPvChange(it.toInt()) },
        )

        SliderRow(
            label = "CPUs",
            value = prefs.threads.toFloat(),
            valueRange = 1f..4f,
            steps = 2,
            display = "${prefs.threads}",
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
            modifier = Modifier.width(110.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            display,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
