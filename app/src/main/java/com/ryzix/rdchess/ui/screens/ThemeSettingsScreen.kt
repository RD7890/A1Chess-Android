package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryzix.rdchess.ui.theme.BoardDarkGreen
import com.ryzix.rdchess.ui.theme.BoardLightGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
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
        ) {
            // Board preview
            BoardPreviewMini(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            ThemeRow(label = "Background", value = "Default")
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            ThemeRowWithColor(label = "Board", value = "Green", color = BoardDarkGreen)
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            ThemeRow(label = "Piece set", value = "Staunty")
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            ThemeRowWithColor(label = "Drawn shape color", value = "Green", color = BoardDarkGreen)
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Board coordinates", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "(A-H, 1-8)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Switch(checked = true, onCheckedChange = {})
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show border", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = false, onCheckedChange = {})
            }
        }
    }
}

@Composable
private fun BoardPreviewMini(modifier: Modifier = Modifier) {
    val squareSize = 30.dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            for (row in 0..7) {
                Row {
                    for (col in 0..7) {
                        val isLight = (row + col) % 2 == 0
                        Box(
                            modifier = Modifier
                                .size(squareSize)
                                .background(if (isLight) BoardLightGreen else BoardDarkGreen),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ThemeRowWithColor(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
