package com.trackure.app.presentation.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.ui.theme.*

@Composable
fun HomeRoute(
    sharedViewModel: com.trackure.app.presentation.TrackureViewModel,
    onOpenProfileSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by sharedViewModel.home.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, onOpenProfileSheet, modifier)
}
@Composable
fun HomeScreen(state: HomeUiState, onOpenProfileSheet: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(BackgroundCanvas).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(state.dateLabel, fontSize = 12.sp, color = TextSecondary)
                Text("Good Morning, ${state.profile.name.substringBefore(" ")}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
            OutlinedButton(onOpenProfileSheet, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SurfaceBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceBorder, contentColor = TextPrimary), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Settings, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Profile", fontSize = 11.sp)
            }
        }
        HomeCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sure-Step Matrix", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                MonoText("${state.streakCount} Day Streak", StravaOrangeHighlight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { Text(it, fontSize = 10.sp, lineHeight = 12.sp, color = TextSecondary) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    state.matrix.chunked(7).forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { day ->
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(matrixColor(day.level)).semantics { contentDescription = "${day.dateIso}: activity level ${day.level}" })
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activity Level", fontSize = 10.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Inactive", fontSize = 10.sp, color = TextSecondary)
                    (0..3).forEach { Box(Modifier.size(10.dp).background(matrixColor(it), RoundedCornerShape(2.dp))) }
                    Text("Peak", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
        HomeCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp), tint = SportyGreenPrimary)
                Text("Today's Momentum", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                MonoText("${state.completionPercent}% Done", SportyGreenPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.completedCount} of ${state.totalCount} Activities Completed", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                MonoText("${state.totalCount - state.completedCount} Pending", TextSecondary)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(BackgroundCanvas).border(1.dp, SurfaceBorder, CircleShape).semantics { progressBarRangeInfo = ProgressBarRangeInfo(state.completionPercent / 100f, 0f..1f) }) {
                Box(Modifier.fillMaxWidth(state.completionPercent / 100f).fillMaxHeight().background(Brush.horizontalGradient(listOf(SportyBluePrimary, SportyGreenPrimary)), CircleShape))
            }
        }
    }
}

private fun matrixColor(level: Int) = when (level) { 1 -> MatrixBlueCell; 2 -> SportyGreenPrimary; 3 -> StravaOrangeHighlight; else -> MatrixEmptyCell }

@Composable
private fun MonoText(text: String, color: Color) = Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)

@Composable
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = SurfaceCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
