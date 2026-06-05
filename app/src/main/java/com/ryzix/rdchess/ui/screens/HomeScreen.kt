package com.ryzix.rdchess.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF0D0D0D)
private val Surf = Color(0xFF181818)
private val Surf2 = Color(0xFF1E1E1E)
private val Primary = Color(0xFFFF2541)
private val PrimaryDark = Color(0xFFC01D30)
private val PrimaryBg = Color(0xFF1E0A0B)
private val Border = Color(0xFF3C3C3C)
private val Muted = Color(0xFF888888)
private val Muted2 = Color(0xFF333333)

@Composable
fun HomeScreen(
    onPlayVsComputer: () -> Unit,
) {
    var selectedTime by remember { mutableStateOf("5+0") }
    var selectedTimeSub by remember { mutableStateOf("5 min · Blitz") }
    var selectedLevel by remember { mutableIntStateOf(3) }

    val levelLabels = listOf("800","1200","1600","2000","2400","2800")
    val levelDetails = listOf("Skill 0","Skill 4","Skill 8","Skill 12","Skill 16","Skill 20")
    val timeOptions = listOf("1+0" to "1 min · Bullet","3+0" to "3 min · Blitz","5+0" to "5 min · Blitz","10+0" to "10 min · Rapid")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Primary)
                            .border(1.5.dp, PrimaryDark, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Casino, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text("A1 Chess", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Text("Powered by Stockfish 16", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp, start = 48.dp))
            }
            MattIconBtn(Icons.Rounded.Bolt, "Activity")
        }

        // ── Quick Play card ──
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 22.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surf)
                .border(1.5.dp, Border, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("QUICK PLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(selectedTime, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(selectedTimeSub, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    timeOptions.forEach { (t, sub) ->
                        ChipButton(
                            label = t,
                            selected = selectedTime == t,
                            onClick = { selectedTime = t; selectedTimeSub = sub },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPlayVsComputer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Play Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Engine Level ──
        Text("ENGINE LEVEL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted,
            letterSpacing = 1.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            levelLabels.forEachIndexed { i, lbl ->
                ChipButton(
                    label = lbl,
                    selected = selectedLevel == i,
                    onClick = { selectedLevel = i },
                    modifier = Modifier.weight(1f),
                    fontSize = 11,
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Memory, null, tint = Primary, modifier = Modifier.size(13.dp))
            Text("${levelDetails[selectedLevel]} · ${listOf(500,700,900,1200,1500,2000)[selectedLevel]}ms think time",
                fontSize = 12.sp, color = Muted)
        }

        // ── Game Modes ──
        Text("GAME MODES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Muted,
            letterSpacing = 1.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModeRow(
                icon = Icons.Rounded.SmartToy,
                iconBg = PrimaryBg, iconTint = Primary, iconBorderColor = Color(0xFF5A1520),
                title = "vs Engine", sub = "Play against Stockfish 16",
                onClick = onPlayVsComputer,
            )
            ModeRow(
                icon = Icons.Rounded.People,
                iconBg = Surf2, iconTint = Muted, iconBorderColor = Border,
                title = "Two Players", sub = "Pass & play on one device",
                onClick = {},
            )
            ModeRow(
                icon = Icons.Rounded.Extension,
                iconBg = Surf2, iconTint = Muted, iconBorderColor = Border,
                title = "Puzzles", sub = "Train with daily puzzles",
                onClick = {},
            )
        }
    }
}

@Composable
private fun MattIconBtn(icon: ImageVector, desc: String, modifier: Modifier = Modifier) {
    Surface(
        onClick = {},
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = Surf2,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Border),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 12,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Primary else Surf2,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) PrimaryDark else Border),
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label, fontSize = fontSize.sp, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Muted)
        }
    }
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    iconBorderColor: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Surf,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Border),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg)
                    .border(1.5.dp, iconBorderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(sub, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Border, modifier = Modifier.size(18.dp))
        }
    }
}
