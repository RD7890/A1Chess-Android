package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ryzix.rdchess.viewmodel.GameViewModel
import com.ryzix.rdchess.viewmodel.SavedGame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HBg      = Color(0xFF0D0D0D)
private val HSurf    = Color(0xFF181818)
private val HSurf2   = Color(0xFF1E1E1E)
private val HPrimary = Color(0xFFFF2541)
private val HPrimDk  = Color(0xFFC01D30)
private val HPrimBg  = Color(0xFF1E0A0B)
private val HBorder  = Color(0xFF3C3C3C)
private val HMuted   = Color(0xFF888888)

@Composable
fun HomeScreen(
    onPlay    : () -> Unit,
    onReview  : (SavedGame) -> Unit = {},
    onContinue: (SavedGame) -> Unit = {},
    vm        : GameViewModel = viewModel(),
) {
    val history by vm.gameHistory.collectAsState()
    val recent  = history.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HBg),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HPrimary)
                            .border(1.5.dp, HPrimDk, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Casino, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("A1 Chess", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Powered by Stockfish 16 & Ryzix", fontSize = 11.sp, color = HMuted)
                    }
                }
            }
        }

        // ── Play button ──────────────────────────────────────────────────────
        item {
            Button(
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HPrimary),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Play", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Recent games header ──────────────────────────────────────────────
        if (recent.isNotEmpty()) {
            item {
                Text(
                    "RECENT GAMES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = HMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
                )
            }

            // ── Game cards ──────────────────────────────────────────────────
            items(recent) { game ->
                RecentGameCard(
                    game       = game,
                    onReview   = { onReview(game) },
                    onContinue = { onContinue(game) },
                )
                Spacer(Modifier.height(10.dp))
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.History, null, tint = HBorder, modifier = Modifier.size(40.dp))
                        Text("No recent games", fontSize = 14.sp, color = HMuted)
                        Text("Play your first game to see it here", fontSize = 12.sp, color = Color(0xFF555555))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGameCard(
    game      : SavedGame,
    onReview  : () -> Unit,
    onContinue: () -> Unit,
) {
    val isInProgress = game.result == "*"
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val dateStr = fmt.format(Date(game.timestamp))

    val (resultLabel, resultColor) = when (game.result) {
        "1-0"     -> "White Won"  to Color(0xFF4CAF50)
        "0-1"     -> "Black Won"  to Color(0xFFEF5350)
        "1/2-1/2" -> "Draw"       to Color(0xFFFFB300)
        "*"       -> "In Progress" to Color(0xFF42A5F5)
        else      -> game.result  to HMuted
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = HSurf,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, HBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Result badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(resultColor.copy(alpha = 0.15f))
                        .border(1.dp, resultColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(resultLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = resultColor)
                }
                Text(dateStr, fontSize = 11.sp, color = HMuted)
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Schedule, null, tint = HMuted, modifier = Modifier.size(13.dp))
                Text("${game.moveCount} moves", fontSize = 12.sp, color = HMuted)
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isInProgress) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HPrimary),
                        contentPadding = PaddingValues(vertical = 10.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Continue", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onReview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, HBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.Analytics, null, modifier = Modifier.size(15.dp), tint = HMuted)
                    Spacer(Modifier.width(5.dp))
                    Text("Review", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HMuted)
                }
            }
        }
    }
}
