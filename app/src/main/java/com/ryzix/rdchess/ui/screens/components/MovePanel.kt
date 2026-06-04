package com.ryzix.rdchess.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ryzix.rdchess.chess.ChessMove
import kotlinx.coroutines.launch

@Composable
fun MovePanel(
    moves: List<ChessMove>,
    currentMoveIndex: Int,
    eval: Float,
    isEngineThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(moves.size) {
        if (moves.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(moves.size - 1) }
        }
    }

    Column(modifier = modifier) {
        // Eval bar
        EvalBar(eval = eval, isThinking = isEngineThinking)

        Spacer(modifier = Modifier.height(8.dp))

        // Move list
        if (moves.isEmpty()) {
            Text(
                text = "Game starts — make your move",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                val movePairs = moves.chunked(2)
                movePairs.forEachIndexed { pairIdx, pair ->
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "${pairIdx + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                            pair.forEachIndexed { idx, move ->
                                val absoluteIdx = pairIdx * 2 + idx
                                val isCurrentMove = absoluteIdx == currentMoveIndex
                                MoveChip(
                                    san = move.san,
                                    isActive = isCurrentMove,
                                )
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
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Text(
            text = san,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
            ),
            color = if (isActive) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EvalBar(eval: Float, isThinking: Boolean) {
    val clampedEval = eval.coerceIn(-5f, 5f)
    val whiteFraction = ((clampedEval + 5f) / 10f).coerceIn(0.05f, 0.95f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(whiteFraction)
                .fillMaxHeight()
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .weight(1f - whiteFraction)
                .fillMaxHeight()
                .background(Color(0xFF333333)),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val evalText = when {
            isThinking -> "..."
            kotlin.math.abs(eval) >= 9.9f -> if (eval > 0) "M" else "-M"
            else -> if (eval >= 0) "+%.1f".format(eval) else "%.1f".format(eval)
        }
        Text(
            text = evalText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        if (isThinking) {
            LinearProgressIndicator(
                modifier = Modifier
                    .width(60.dp)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
