package com.ryzix.rdchess.ui.screens.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.ErrorOutline
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
import com.ryzix.rdchess.viewmodel.MoveGrade
import com.ryzix.rdchess.viewmodel.MoveGradeResult
import kotlinx.coroutines.launch

// ────────────────────────────────────────────────────────────────────────────
//  Public: Stockfish analysis strip + move list
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun StockfishPanel(
    eval: Float,
    isThinking: Boolean,
    engineEnabled: Boolean,
    engineAvailable: Boolean,
    analysisLines: List<AnalysisLine>,
    moves: List<ChessMove>,
    showMoveList: Boolean,
    moveGrade: MoveGradeResult?,
    onToggleEngine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    // No animateContentSize — it was causing excessive recompositions under rapid eval updates
    Column(modifier = modifier) {

        // ── Stockfish strip ───────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = engineEnabled && engineAvailable) {
                        if (isThinking || analysisLines.isNotEmpty()) expanded = !expanded
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
                    text = "Stockfish 16",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(10.dp))

                if (engineEnabled && engineAvailable) {
                    EvalChip(eval = eval, isThinking = isThinking)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (moveGrade != null && engineEnabled && engineAvailable) {
                    MoveGradeBadge(grade = moveGrade)
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!engineAvailable) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Engine unavailable",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        !engineAvailable -> MaterialTheme.colorScheme.errorContainer
                        engineEnabled    -> Color(0xFF2E7D32)
                        else             -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.clickable(enabled = engineAvailable) { onToggleEngine() },
                ) {
                    Text(
                        text = when {
                            !engineAvailable -> "N/A"
                            engineEnabled    -> "On"
                            else             -> "Off"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            !engineAvailable -> MaterialTheme.colorScheme.onErrorContainer
                            engineEnabled    -> Color.White
                            else             -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                if (engineEnabled && engineAvailable && (isThinking || analysisLines.isNotEmpty())) {
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

        // ── Analysis lines (only when expanded) ──────────────────────────
        if (expanded && engineEnabled && engineAvailable) {
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
                    if (isThinking && analysisLines.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "Engine thinking…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }
                    analysisLines.forEach { line ->
                        key(line.rank) {
                            AnalysisLineRow(
                                line = line,
                                bestEval = analysisLines.firstOrNull()?.eval ?: line.eval,
                                isThinking = isThinking,
                            )
                        }
                    }
                }
            }
        }

        // ── Move list ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMoveList,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit  = fadeOut(tween(100)) + shrinkVertically(tween(180)),
        ) {
            MoveListRow(moves = moves, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
//  Move grade badge
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoveGradeBadge(grade: MoveGradeResult) {
    val bgColor = Color(grade.grade.colorHex)
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bgColor.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = grade.grade.symbol,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = bgColor,
            )
            Text(
                text = grade.grade.label,
                style = MaterialTheme.typography.labelSmall,
                color = bgColor,
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
        isThinking && eval == 0f -> "…"
        kotlin.math.abs(eval) >= 9.9f -> if (eval > 0) "M" else "-M"
        eval >= 0 -> "+%.1f".format(eval)
        else      -> "%.1f".format(eval)
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
private fun AnalysisLineRow(line: AnalysisLine, bestEval: Float, isThinking: Boolean) {
    val diff = bestEval - line.eval
    val dotColor = when {
        line.rank == 1 -> Color(0xFF4CAF50)
        diff < 0.3f    -> Color(0xFFFFC107)
        else           -> Color(0xFFF44336)
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
                if (isThinking && line.depth > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "d${line.depth}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
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
                            pair.forEach { move -> MoveChip(san = move.san, isActive = false) }
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
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Text(
            text = san,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
            ),
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
