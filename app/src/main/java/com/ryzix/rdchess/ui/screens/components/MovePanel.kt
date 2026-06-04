package com.ryzix.rdchess.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryzix.rdchess.chess.AnalysisLine
import com.ryzix.rdchess.chess.ChessMove
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────────────────────────────────
//  Public: Stockfish analysis strip + move list
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun StockfishPanel(
    eval: Float,
    isThinking: Boolean,
    engineEnabled: Boolean,
    analysisLines: List<AnalysisLine>,
    moves: List<ChessMove>,
    showMoveList: Boolean,
    onToggleEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // ── Stockfish strip ──────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = engineEnabled && analysisLines.isNotEmpty()) {
                        expanded = !expanded
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stockfish",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(10.dp))

                // Eval chip
                if (engineEnabled) {
                    EvalChip(eval = eval, isThinking = isThinking)
                }

                Spacer(modifier = Modifier.weight(1f))

                // On / Off toggle button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (engineEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onToggleEngine() },
                ) {
                    Text(
                        text = if (engineEnabled) "On" else "Off",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (engineEnabled) Color.White
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                // Expand chevron (only when engine on and lines available)
                if (engineEnabled && analysisLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }

        // ── Expanded analysis lines ──────────────────────────────────────
        AnimatedVisibility(
            visible = expanded && engineEnabled && analysisLines.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    analysisLines.forEach { line ->
                        AnalysisLineRow(line = line, bestEval = analysisLines.firstOrNull()?.eval ?: line.eval)
                    }
                }
            }
        }

        // ── Move list ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMoveList,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            MoveListRow(
                moves = moves,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Private helpers
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun EvalChip(eval: Float, isThinking: Boolean) {
    val text = when {
        isThinking -> "..."
        kotlin.math.abs(eval) >= 9.9f -> if (eval > 0) "M" else "-M"
        eval >= 0 -> "+%.1f".format(eval)
        else -> "%.1f".format(eval)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AnalysisLineRow(line: AnalysisLine, bestEval: Float) {
    val diff = bestEval - line.eval
    val dotColor = when {
        line.rank == 1       -> Color(0xFF4CAF50) // best — green
        diff < 0.3f          -> Color(0xFFFFC107) // close — amber
        else                 -> Color(0xFFF44336) // worse — red
    }

    val moveText = uciToArrow(line.move)
    val evalText = if (line.isMate) {
        if (line.mateIn > 0) "M${line.mateIn}" else "-M${-line.mateIn}"
    } else {
        if (line.eval >= 0) "+%.1f".format(line.eval) else "%.1f".format(line.eval)
    }

    val contText = line.continuation.take(4).joinToString(" ") { uciToArrow(it) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Coloured dot
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = moveText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = evalText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (contText.isNotBlank()) {
                Text(
                    text = contText,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Converts UCI move "e2e4" → "e2→e4" */
private fun uciToArrow(uci: String): String {
    if (uci.length < 4) return uci
    return "${uci.substring(0, 2)}→${uci.substring(2, 4)}"
}

@Composable
private fun MoveListRow(moves: List<ChessMove>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) scope.launch { listState.animateScrollToItem(moves.size - 1) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        if (moves.isEmpty()) {
            Text(
                text = "No moves yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                val pairs = moves.chunked(2)
                pairs.forEachIndexed { pairIdx, pair ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "${pairIdx + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                            )
                            pair.forEach { move ->
                                MoveChip(san = move.san, isActive = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveChip(san: String, isActive: Boolean) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Text(
            text = san,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
            ),
            color = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
