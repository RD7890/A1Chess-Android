package com.ryzix.rdchess.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * In-game control bar:
 *   Menu | Bolt | ChevronLeft | ChevronRight | Undo | Share(PGN)
 *
 * Timer/pause button removed — no time controls.
 */
@Composable
fun GameBottomBar(
    onMenu: () -> Unit,
    onEngineStrength: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onUndo: () -> Unit,
    onExportPgn: () -> Unit,
    canBack: Boolean,
    canForward: Boolean,
    isReviewMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(icon = Icons.Rounded.Menu, desc = "Move list", onClick = onMenu)

            BarButton(icon = Icons.Rounded.Bolt, desc = "Engine settings", onClick = onEngineStrength)

            BarButton(
                icon = Icons.Rounded.ChevronLeft,
                desc = "Previous move",
                onClick = onBack,
                enabled = canBack,
            )

            BarButton(
                icon = Icons.Rounded.ChevronRight,
                desc = "Next move",
                onClick = onForward,
                enabled = canForward,
            )

            // Undo only available during a live (non-review) game
            BarButton(
                icon = Icons.Rounded.Undo,
                desc = "Undo move",
                onClick = onUndo,
                enabled = !isReviewMode,
            )

            BarButton(
                icon = Icons.Rounded.Share,
                desc = "Export PGN",
                onClick = onExportPgn,
            )
        }
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(26.dp),
        )
    }
}
