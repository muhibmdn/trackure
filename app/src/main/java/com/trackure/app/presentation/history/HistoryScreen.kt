package com.trackure.app.presentation.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.domain.model.ExportHistoryConfig
import com.trackure.app.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChartGreen = Color(0xFF05E777)
private val ChartBlue = Color(0xFF0099DF)
private val TrendOrange = Color(0xFFFF571B)
private val MetricSurface = Color(0xFF1C2025)

@Composable
fun HistoryRoute(sharedViewModel: com.trackure.app.presentation.TrackureViewModel, modifier: Modifier = Modifier) {
    val viewModel = sharedViewModel.history
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var showExport by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val resolver = context.contentResolver
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) viewModel.cancelExport()
        else viewModel.writeExport { csv ->
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Unable to open export destination" }
                .bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryEvent.ExportReady -> {
                    try { saveFile.launch("trackure-history.csv") }
                    catch (e: Exception) { viewModel.cancelExport(); error = e.message ?: "No file saver available" }
                }
                HistoryEvent.Exported -> {
                    showExport = false; error = null
                    Toast.makeText(context, "History data exported successfully", Toast.LENGTH_SHORT).show()
                }
                is HistoryEvent.Error -> error = event.message
            }
        }
    }
    HistoryScreen(state, viewModel::selectRange, { error = null; showExport = true }, modifier)
    if (showExport) ExportHistoryModal(busy, error, { showExport = false; error = null }) {
        error = null; viewModel.prepareExport(it)
    }
}

@Composable
fun HistoryScreen(state: HistoryUiState, onRangeSelected: (HistoryRange) -> Unit, onExport: () -> Unit, modifier: Modifier = Modifier) {
    var selectedDay by rememberSaveable { mutableIntStateOf(5) }
    Column(modifier.fillMaxSize().background(BackgroundCanvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("HISTORY & ANALYTICS", Modifier.weight(1f), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceBorder).clickable(role = Role.Button, onClick = onExport).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Export", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Row(Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(12.dp)).padding(4.dp)) {
            HistoryRange.entries.forEach { range ->
                val active = range == state.range
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (active) StravaOrangeHighlight else Color.Transparent)
                    .clickable(role = Role.Tab) { onRangeSelected(range) }.semantics { selected = active }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(range.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (active) Color.White else TextSecondary, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Text("Daily Activities Completed", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().background(MetricSurface, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        MetricText(state.averageCompletion.toString())
                        MetricText("%", ChartGreen)
                    }
                    Text("AVG COMPLETION RATE", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetricText(state.totalCompleted.toString())
                    Text("TOTAL", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = StravaOrangeHighlight)
                }
            }
            Spacer(Modifier.height(24.dp))
            HistoryChart(state.days, selectedDay) { selectedDay = it }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().background(MetricSurface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Brush.verticalGradient(listOf(ChartGreen, ChartBlue)), RoundedCornerShape(2.dp)))
                    Text("Activities Executed", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(14.dp).height(3.dp).background(TrendOrange))
                    Text("7D Rolling Velocity", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TrendOrange)
                }
            }
        }
    }
}

@Composable
private fun MetricText(text: String, color: Color = TextPrimary) {
    Text(text, fontSize = 36.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun HistoryChart(days: List<HistoryDay>, selected: Int, onSelect: (Int) -> Unit) {
    if (days.isEmpty()) return
    val selectedIndex = selected.coerceIn(days.indices)
    val day = days[selectedIndex]
    val tooltip = "${day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))} â€¢ ${day.completed}/${day.total} Done (${day.percent}%)"
    // A clamped alignment keeps the badge visible near edge bars on narrow devices.
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = BiasAlignment(((selectedIndex + 0.5f) / days.size * 2f - 1f), 0f)) {
        Row(Modifier.background(Color(0xFF31353B), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(ChartGreen))
            Text(tooltip, color = TextPrimary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(180.dp).pointerInput(days) {
        detectTapGestures { position -> onSelect((position.x / (size.width.toFloat() / days.size)).toInt().coerceIn(days.indices)) }
    }.semantics { contentDescription = "Daily completed activities with seven-day rolling average. Select a day using the labels below." }) {
        val column = size.width / days.size
        val top = 10.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val plotHeight = bottom - top
        val maxValue = maxOf(1f, days.maxOf { maxOf(it.completed.toFloat(), it.rollingVelocity) })
        fun y(value: Float) = bottom - value / maxValue * plotHeight
        repeat(4) { index ->
            val gridY = top + plotHeight * index / 3f
            drawLine(Color(0xFF31353B), Offset(0f, gridY), Offset(size.width, gridY), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
        val barWidth = minOf(18.dp.toPx(), column * 0.5f)
        days.forEachIndexed { index, entry ->
            val barTop = y(entry.completed.toFloat())
            if (entry.completed > 0) drawRoundRect(Brush.verticalGradient(listOf(ChartGreen, ChartBlue), startY = barTop, endY = bottom),
                Offset(column * (index + 0.5f) - barWidth / 2, barTop), Size(barWidth, bottom - barTop), CornerRadius(4.dp.toPx()), alpha = if (index == selectedIndex) 1f else 0.75f)
        }
        val points = days.mapIndexed { index, entry -> Offset(column * (index + 0.5f), y(entry.rollingVelocity)) }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.zipWithNext().forEach { (previous, next) ->
                val midX = (previous.x + next.x) / 2
                cubicTo(midX, previous.y, midX, next.y, next.x, next.y)
            }
        }
        drawPath(path, TrendOrange, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, point ->
            if (index == selectedIndex) {
                drawCircle(TrendOrange, 5.dp.toPx(), point)
                drawCircle(Color.White, 5.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(BackgroundCanvas, 3.dp.toPx(), point)
                drawCircle(TrendOrange, 3.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        days.forEachIndexed { index, entry ->
            Text(entry.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)), Modifier.weight(1f)
                .clickable(role = Role.Button) { onSelect(index) }.semantics { this.selected = index == selectedIndex; contentDescription = "${entry.date}: ${entry.completed} of ${entry.total} completed" }
                .padding(vertical = 6.dp), textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal, color = if (index == selectedIndex) ChartGreen else TextSecondary)
        }
    }
}

@Composable
fun ExportHistoryModal(busy: Boolean, error: String?, onDismiss: () -> Unit, onExport: (ExportHistoryConfig) -> Unit) {
    var includeName by rememberSaveable { mutableStateOf(true) }
    var includeTime by rememberSaveable { mutableStateOf(true) }
    var includeNote by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(12.dp)).border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Export History", Modifier.weight(1f), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onDismiss, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Close export history" }) { Text("âœ•", color = TextSecondary) }
            }
            Text("Pilih data aktivitas yang ingin diekspor.", fontSize = 12.sp, color = TextSecondary)
            ExportOption("Activity Name (Nama Aktivitas)", includeName, !busy) { includeName = it }
            ExportOption("Completed Time (Waktu Terlaksana)", includeTime, !busy) { includeTime = it }
            ExportOption("Include Note (Sertakan Catatan)", includeNote, !busy) { includeNote = it }
            error?.let { Text(it, color = StravaOrangeHighlight, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onDismiss, Modifier.weight(1f), enabled = !busy, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorder, contentColor = TextPrimary)) { Text("Cancel", fontSize = 12.sp) }
                Button({ onExport(ExportHistoryConfig(includeName, includeTime, includeNote)) }, Modifier.weight(1.3f), enabled = !busy, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = StravaOrangeHighlight, contentColor = Color.White)) { Text(if (busy) "Exporting..." else "Export Data", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ExportOption(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Checkbox) { onChange(!checked) }.semantics { toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = TrendOrange, checkmarkColor = Color.White, uncheckedColor = TextSecondary))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = TextPrimary)
    }
}
