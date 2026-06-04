package com.ryzix.rdchess.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GameBottomBar(
    onUndo: () -> Unit,
    onFlip: () -> Unit,
    onNewGame: () -> Unit,
    onSettings: () -> Unit,
    canUndo: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.Rounded.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }

            IconButton(onClick = onFlip) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Flip board",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(onClick = onNewGame) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "New game",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
