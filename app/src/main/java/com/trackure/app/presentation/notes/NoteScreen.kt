package com.trackure.app.presentation.notes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.domain.model.GeneralDailyNote
import com.trackure.app.presentation.TrackureViewModel
import com.trackure.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NoteOrange = Color(0xFFFF571B)

@Composable
fun NoteScreen(sharedViewModel: TrackureViewModel, modifier: Modifier = Modifier) {
    val controller = sharedViewModel.notes
    val state by controller.uiState.collectAsStateWithLifecycle()
    val draft by controller.draft.collectAsStateWithLifecycle()
    var showFilterPicker by rememberSaveable { mutableStateOf(false) }
    NotesContent(state, controller::search, { showFilterPicker = true }, { controller.filterDate(null) }, controller::newNote, controller::editNote, modifier)
    if (showFilterPicker) NoteDatePicker(state.date ?: controller.today, { showFilterPicker = false }) {
        controller.filterDate(it); showFilterPicker = false
    }
    draft?.let { note ->
        NoteModal(note, (state.activities.map { it.title } + GeneralDailyNote.TITLE + note.activityTitle).distinct(),
            controller::editDate, controller::editActivity, controller::editContent, controller::dismiss, controller::save)
    }
}

@Composable
fun NotesContent(
    state: NotesUiState,
    onSearch: (String) -> Unit,
    onSelectDate: () -> Unit,
    onAllDates: () -> Unit,
    onNewNote: () -> Unit,
    onEditNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(BackgroundCanvas)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 88.dp)) {
            item {
                Text("NOTES & JOURNAL", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth().semantics { contentDescription = "Search notes" },
                    placeholder = { Text("Search notes...", color = TextSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) }, singleLine = true,
                    shape = RoundedCornerShape(8.dp), colors = noteFieldColors(SurfaceCard))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SurfaceCard).border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = "Choose note filter date", onClick = onSelectDate).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp), tint = NoteOrange)
                        Text("Date:", fontSize = 12.sp, color = TextSecondary)
                        Text(state.date?.toString() ?: "All Dates", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
                    }
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAllDates).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("All Dates", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            }
            items(state.notes, key = { it.id }) { note ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        val date = runCatching { LocalDate.parse(note.dateIso).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) }.getOrDefault(note.dateIso)
                        val time = if (note.isCompleted && note.completedHour != null && note.completedMinute != null) " • ${note.completedHour}:${note.completedMinute}" else ""
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = NoteOrange, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)) { append(date + time) }
                            append(" ")
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)) { append(note.activityTitle) }
                        }, Modifier.weight(1f), lineHeight = 20.sp)
                        Text("Edit", Modifier.padding(start = 8.dp).clickable(role = Role.Button, onClickLabel = "Edit ${note.activityTitle}") { onEditNote(note.id) }.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 12.sp, color = Color(0xFF9E9E9E))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(note.note.orEmpty(), color = Color(0xFFD4D4D4), fontSize = 12.sp, lineHeight = 18.sp)
                }
                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            }
            if (state.notes.isEmpty()) item {
                Text("No notes found.", Modifier.padding(vertical = 20.dp), color = TextSecondary, fontSize = 12.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp).clip(RoundedCornerShape(50)).background(NoteOrange)
            .clickable(role = Role.Button, onClick = onNewNote).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("+ New Note", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        }
    }
}

@Composable
private fun NoteModal(
    draft: NoteDraft,
    activities: List<String>,
    onDate: (LocalDate) -> Unit,
    onActivity: (String) -> Unit,
    onContent: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var dropdown by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!draft.saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().background(SurfaceCard, RoundedCornerShape(12.dp)).border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (draft.originalLogId == null) "NEW NOTE" else "EDIT NOTE", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onDismiss, enabled = !draft.saving, modifier = Modifier.semantics { contentDescription = "Close note editor" }) { Text("✕", color = TextSecondary) }
            }
            NoteLabel("Date")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(BackgroundCanvas).border(1.dp, SurfaceBorder, RoundedCornerShape(4.dp))
                .clickable(enabled = !draft.saving, role = Role.Button, onClickLabel = "Choose note date") { showDatePicker = true }.padding(8.dp)) {
                Text(draft.dateIso, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextPrimary)
            }
            NoteLabel("Activity")
            Box {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(BackgroundCanvas).border(1.dp, SurfaceBorder, RoundedCornerShape(4.dp))
                    .clickable(enabled = !draft.saving, role = Role.DropdownList) { dropdown = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.activityTitle, Modifier.weight(1f), fontSize = 12.sp, color = TextPrimary)
                    Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary)
                }
                DropdownMenu(dropdown, { dropdown = false }, containerColor = SurfaceCard) {
                    activities.forEach { title -> DropdownMenuItem(text = { Text(title, color = TextPrimary, fontSize = 12.sp) }, onClick = { onActivity(title); dropdown = false }) }
                }
            }
            NoteLabel("Note Content")
            OutlinedTextField(draft.content, onContent, Modifier.fillMaxWidth().semantics { contentDescription = "Note content" }, enabled = !draft.saving,
                placeholder = { Text("Write activity note or reflection...", fontSize = 12.sp, color = TextSecondary) }, minLines = 3, maxLines = 3,
                shape = RoundedCornerShape(4.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary), colors = noteFieldColors(BackgroundCanvas))
            draft.error?.let { Text(it, color = NoteOrange, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onDismiss, Modifier.weight(1f), enabled = !draft.saving) { Text("Cancel", color = TextSecondary) }
                Button(onSave, Modifier.weight(1f), enabled = !draft.saving && draft.content.isNotBlank(), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = StravaOrangeHighlight, contentColor = Color.White)) {
                    Text(if (draft.saving) "Saving..." else "Save Note", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showDatePicker) NoteDatePicker(LocalDate.parse(draft.dateIso), { showDatePicker = false }) { onDate(it); showDatePicker = false }
}

@Composable
private fun NoteLabel(text: String) { Text(text, color = TextSecondary, fontSize = 12.sp) }

@Composable
private fun noteFieldColors(background: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = background, unfocusedContainerColor = background, disabledContainerColor = background,
    focusedBorderColor = StravaOrangeHighlight, unfocusedBorderColor = SurfaceBorder, cursorColor = StravaOrangeHighlight,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDatePicker(initial: LocalDate, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    // Material DatePicker stores UTC midnight, independent of the user's timezone.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton({ picker.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }, enabled = picker.selectedDateMillis != null) { Text("OK", color = StravaOrangeHighlight) }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancel", color = TextSecondary) } }) {
        DatePicker(picker)
    }
}
