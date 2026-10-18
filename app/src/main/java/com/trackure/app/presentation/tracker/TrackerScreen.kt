package com.trackure.app.presentation.tracker

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun TrackerRoute(viewModel: TrackerViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    TrackerScreen(state, viewModel::selectDate, viewModel::selectToday, viewModel::openActivity, modifier)
    draft?.let {
        ActivityLogModal(it, viewModel::editHour, viewModel::editMinute, viewModel::editNote, viewModel::dismissLog, viewModel::saveLog)
    }
}

@Composable
fun TrackerScreen(
    state: TrackerUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onOpenActivity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    LazyColumn(modifier.fillMaxSize().background(BackgroundCanvas), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onToday, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                    Text("TODAY", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { offset ->
                    val date = monday.plusDays(offset.toLong())
                    val active = date == state.selectedDate
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (active) StravaOrangeHighlight else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelectDate(date) }
                        .semantics { selected = active; contentDescription = date.toString() }
                        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, color = if (active) Color.White else TextSecondary)
                        Text(date.dayOfMonth.toString(), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(thickness = 1.dp, color = SurfaceBorder)
        }
        items(state.rows, key = { it.activity.id }) { row ->
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open activity log") { onOpenActivity(row.activity.id) }
                .semantics { stateDescription = if (row.isCompleted) "Completed" else "Not completed" }
                .padding(vertical = 14.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.activity.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.activity.scheduledTime, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                        if (row.isCompleted) {
                            Text("•", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.5f))
                            Text("${row.log?.completedHour.orEmpty()}:${row.log?.completedMinute.orEmpty()}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = SportyGreenPrimary)
                        }
                    }
                }
                // The indicator shares the row's full accessible click target; it opens the editor.
                Box(Modifier.size(24.dp).background(if (row.isCompleted) SportyGreenPrimary else Color.Transparent, RoundedCornerShape(4.dp))
                    .then(if (!row.isCompleted) Modifier.border(1.dp, Color(0xFF363A40), RoundedCornerShape(4.dp)) else Modifier), contentAlignment = Alignment.Center) {
                    if (row.isCompleted) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.Black)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = SurfaceBorder)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogModal(
    draft: ActivityLogDraft,
    onHourChange: (String) -> Unit,
    onMinuteChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !draft.saving })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SurfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), dragHandle = null,
        modifier = Modifier.semantics { paneTitle = "Activity Log" }) {
        Column(Modifier.fillMaxWidth().drawBehind { drawLine(SurfaceBorder, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(draft.activity.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${draft.activity.scheduledTime} • ${draft.activity.category ?: "General"}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }
                IconButton(onDismiss, enabled = !draft.saving) { Icon(Icons.Default.Close, "Close activity log", tint = TextSecondary) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LogLabel("COMPLETED AT")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeInput(draft.hour, onHourChange, "Hour", TextPrimary, !draft.saving)
                    Text(":", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TimeInput(draft.minute, onMinuteChange, "Minute", StravaOrangeHighlight, !draft.saving)
                }
                if (!draft.validTime) Text("Use 00–23 hours and 00–59 minutes", color = StravaOrangeHighlight, fontSize = 11.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LogLabel("ADD NOTE")
                OutlinedTextField(draft.note, onNoteChange, Modifier.fillMaxWidth().semantics { contentDescription = "Activity note" }, enabled = !draft.saving,
                    minLines = 2, maxLines = 3, shape = RoundedCornerShape(8.dp), textStyle = TextStyle(fontSize = 12.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF0A0E13), unfocusedContainerColor = Color(0xFF0A0E13), disabledContainerColor = Color(0xFF0A0E13), focusedBorderColor = StravaOrangeHighlight, unfocusedBorderColor = SurfaceBorder, cursorColor = StravaOrangeHighlight))
            }
            draft.error?.let { Text(it, color = StravaOrangeHighlight, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onDismiss, Modifier.weight(0.35f), enabled = !draft.saving, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2025), contentColor = TextPrimary)) { Text("Cancel") }
                Button(onSave, Modifier.weight(0.65f), enabled = !draft.saving && draft.validTime, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = StravaOrangeHighlight, contentColor = Color.White)) { Text(if (draft.saving) "Saving..." else "Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun LogLabel(text: String) { Text(text, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }

@Composable
private fun TimeInput(value: String, onChange: (String) -> Unit, label: String, color: Color, enabled: Boolean) {
    BasicTextField(value, onChange, Modifier.width(56.dp).heightIn(min = 40.dp)
        .background(Color(0xFF0A0E13), RoundedCornerShape(8.dp)).border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
        .semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(StravaOrangeHighlight),
        decorationBox = { input -> Box(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { input() } })
}
