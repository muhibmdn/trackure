package com.trackure.app.presentation.add

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.domain.model.*
import com.trackure.app.ui.theme.*
import kotlinx.serialization.json.Json

private val ButtonSurface = Color(0xFF1C2025)
private val SmallShape = RoundedCornerShape(6.dp)
private val ItemSaver = Saver<ActivityItem, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<ActivityItem>(it) })

@Composable
fun AddActivityScreen(sharedViewModel: com.trackure.app.presentation.TrackureViewModel, modifier: Modifier = Modifier) {
    val viewModel = sharedViewModel.add
    var editorMode by remember { mutableStateOf<ActivityEditorMode?>(null) }
    val allActivities by viewModel.activities.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val activities = allActivities.filter { it.syncStatus != SyncStatus.PENDING_DELETE }
    val context = LocalContext.current
    val resolver = context.contentResolver
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importIcs {
            val bytes = requireNotNull(resolver.openInputStream(uri)) { "Unable to read calendar file" }.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 2 * 1024 * 1024) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 2 * 1024 * 1024) { "Calendar file exceeds 2 MB" }
            bytes.toString(Charsets.UTF_8)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri == null) viewModel.cancelExport()
        else viewModel.writeExport { content ->
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "Unable to write calendar file" }.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddActivityEvent.Saved -> { editorMode = null; Toast.makeText(context, "Saved: ${event.title}", Toast.LENGTH_SHORT).show() }
                is AddActivityEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                AddActivityEvent.ExportReady -> try { exportLauncher.launch("trackure_schedule.ics") }
                    catch (_: android.content.ActivityNotFoundException) {
                        viewModel.cancelExport()
                        Toast.makeText(context, "No file saver available", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
    BackHandler(editorMode != null) { if (!busy) editorMode = null }
    val mode = editorMode
    if (mode == null) {
        ActivityList(activities, busy, { editorMode = ActivityEditorMode.Add }, { editorMode = ActivityEditorMode.Edit(it) },
            {
                try { importLauncher.launch(arrayOf("text/calendar", "*/*")) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No file picker available", Toast.LENGTH_LONG).show()
                }
            }, viewModel::prepareExport, modifier)
    } else {
        key(mode) {
            ActivityEditor(mode, activities.mapNotNull { it.category }, busy, { editorMode = null }, viewModel::save, modifier)
        }
    }
}

@Composable
private fun ActivityList(activities: List<ActivityItem>, busy: Boolean, onAdd: () -> Unit, onEdit: (ActivityItem) -> Unit, onImport: () -> Unit, onExport: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().background(BackgroundCanvas), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ACTIVITIES", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                SquareButton(Icons.Default.Add, "Add activity", onAdd, 36, StravaOrangeHighlight, !busy)
            }
            HorizontalDivider(color = SurfaceBorder)
            Spacer(Modifier.height(16.dp))
            FlatBox {
                Label("CALENDAR SYNC (.ICS)", bold = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncButton("Import .ics", Icons.Default.FileUpload, StravaOrangeHighlight, onImport, !busy, Modifier.weight(1f))
                    SyncButton("Export .ics", Icons.Default.FileDownload, SportyBluePrimary, onExport, !busy, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("SCHEDULED ITEMS")
                Text("${activities.size} items", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceBorder)
        }
        items(activities, key = { it.id }) { activity ->
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(activity.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(activity.scheduledTime, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = StravaOrangeHighlight)
                        Text("â€¢ ${recurrenceSummary(activity)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
                SquareButton(Icons.Default.Edit, "Edit ${activity.title}", { onEdit(activity) }, 32, ButtonSurface, !busy)
            }
            HorizontalDivider(color = SurfaceBorder)
        }
    }
}

@Composable
private fun ActivityEditor(mode: ActivityEditorMode, existingCategories: List<String>, busy: Boolean, onCancel: () -> Unit, onSave: (ActivityItem) -> Unit, modifier: Modifier) {
    var item by rememberSaveable(stateSaver = ItemSaver) {
        mutableStateOf((mode as? ActivityEditorMode.Edit)?.activity ?: ActivityItem(title = "", scheduledTime = "08:00", repeatDaysOfWeek = (1..7).toSet()))
    }
    var customCategories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showCategoryInput by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var pendingTest by rememberSaveable { mutableStateOf(false) }
    val requestAlarmPermissions = rememberAlarmPermissionRequester {
        val warning = alarmPermissionMessage(context)
        if (warning != null) Toast.makeText(context, warning, Toast.LENGTH_LONG).show()
        if (pendingTest && warning == null) {
            try {
                com.trackure.app.core.util.AndroidLockscreenAlarmScheduler(context)
                    .triggerTestLockscreenInSeconds(item.title, item.scheduledTime)
                Toast.makeText(context, "Lock your phone screen now! Triggering in 5 seconds...", Toast.LENGTH_LONG).show()
            } catch (_: RuntimeException) {
                Toast.makeText(context, "Unable to schedule test alarm. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
        pendingTest = false
    }
    val categories = (listOf("Study / Kuliah", "Running", "Gym / Strength", "Coding / Dev", "Personal") + existingCategories + customCategories + listOfNotNull(item.category)).distinct()
    Column(modifier.fillMaxSize().background(BackgroundCanvas).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SquareButton(Icons.AutoMirrored.Filled.ArrowBack, "Back to activities", onCancel, 36, ButtonSurface, !busy)
            Text(if (mode is ActivityEditorMode.Add) "ADD ACTIVITY" else "EDIT ACTIVITY", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onCancel, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Cancel", color = TextSecondary, fontSize = 12.sp) }
        }
        HorizontalDivider(color = SurfaceBorder)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("ACTIVITY NAME")
                CleanInput(item.title, { item = item.copy(title = it) }, "Activity name", enabled = !busy)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Label("CATEGORY (OPTIONAL)")
                    Text("+ Add Category", color = StravaOrangeHighlight, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable(enabled = !busy) { showCategoryInput = !showCategoryInput })
                }
                Selector(item.category ?: "No Category (None)", listOf("No Category (None)") + categories, !busy) { selection ->
                    item = item.copy(category = selection.takeUnless { it == "No Category (None)" })
                }
                if (showCategoryInput) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CleanInput(newCategory, { newCategory = it }, "New category", Modifier.weight(1f), placeholder = "Type new category name...", enabled = !busy)
                        TextButton({
                            val category = newCategory.trim()
                            if (category.isNotEmpty()) {
                                customCategories = (customCategories + category).distinct()
                                item = item.copy(category = category)
                                newCategory = ""; showCategoryInput = false
                            }
                        }, enabled = !busy && newCategory.isNotBlank()) { Text("Add", color = StravaOrangeHighlight) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("ACTIVITY TIME")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CleanInput(item.scheduledTime, { item = item.copy(scheduledTime = it) }, "Activity time", enabled = !busy, modifier = Modifier.width(100.dp), monospace = true)
                    Text("SCHEDULED", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = StravaOrangeHighlight)
                }
            }
            FlatBox(padding = 12) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.fullScreenLockscreenTrigger, {
                        item = item.copy(fullScreenLockscreenTrigger = it)
                        if (it) { pendingTest = false; requestAlarmPermissions() }
                    }, enabled = !busy, colors = orangeCheckbox(), modifier = Modifier.semantics { contentDescription = "Full-screen lockscreen trigger" })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Full-Screen Lockscreen Trigger", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Overlay schedule on screen even when phone is locked or screen is off, like Google Calendar alarms.", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                OutlinedButton({ pendingTest = true; requestAlarmPermissions() }, enabled = !busy,
                    border = BorderStroke(1.dp, Color(0xFFFC4C02)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFC4C02))) {
                    Text("Test Lockscreen Trigger (5s)", fontSize = 12.sp)
                }
            }
            FlatBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("RECURRENCE RULES", modifier = Modifier.weight(1f))
                    Checkbox(item.loopActivity, { item = item.copy(loopActivity = it) }, enabled = !busy, colors = orangeCheckbox(), modifier = Modifier.size(32.dp).semantics { contentDescription = "Loop Activity" })
                    Text("Loop Activity", color = TextPrimary, fontSize = 11.sp)
                }
                RecurrenceFreq.entries.chunked(3).forEach { frequencies ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        frequencies.forEach { frequency ->
                            Choice(frequencyLabel(frequency), item.frequency == frequency, !busy, Modifier.weight(1f)) {
                                item = item.copy(frequency = frequency, specificDate = if (frequency == RecurrenceFreq.SPECIFIC_DATE) item.specificDate ?: "2026-10-25" else item.specificDate)
                            }
                        }
                    }
                }
                if (item.frequency in listOf(RecurrenceFreq.DAILY, RecurrenceFreq.WEEKLY, RecurrenceFreq.CUSTOM)) {
                    Label("REPEAT ON DAYS")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, day ->
                            Choice(day, index + 1 in item.repeatDaysOfWeek, !busy, Modifier.weight(1f), compact = true) {
                                val number = index + 1
                                item = item.copy(repeatDaysOfWeek = if (number in item.repeatDaysOfWeek) item.repeatDaysOfWeek - number else item.repeatDaysOfWeek + number)
                            }
                        }
                    }
                }
                if (item.frequency == RecurrenceFreq.SPECIFIC_DATE) {
                    CleanInput(item.specificDate.orEmpty(), { item = item.copy(specificDate = it) }, "Specific date", placeholder = "2026-10-25", enabled = !busy, monospace = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.repeatOnSpecificDate, { item = item.copy(repeatOnSpecificDate = it) }, enabled = !busy, colors = orangeCheckbox(), modifier = Modifier.semantics { contentDescription = "Repeat on this date" })
                        Text("Repeat on this date", fontSize = 12.sp, color = TextPrimary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("INTERVAL")
                        val intervals = ((1..4).toList() + item.intervalCount).distinct().sorted()
                        Selector(intervalLabel(item.intervalCount, item), intervals.map { intervalLabel(it, item) }, !busy) { label ->
                            item = item.copy(intervalCount = intervals.first { intervalLabel(it, item) == label })
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("END CONDITION")
                        val currentEnd = when (item.endConditionType) {
                            EndConditionType.NEVER -> "Never (Indefinite)"
                            EndConditionType.UNTIL_DATE -> if (item.endDate == "2026-12-20") "Until 20 Dec 2026" else "Until ${item.endDate}"
                            EndConditionType.AFTER_OCCURRENCES -> "After ${item.endOccurrences} times"
                        }
                        Selector(currentEnd, (listOf("Never (Indefinite)", "Until 20 Dec 2026", "After 10 times") + currentEnd).distinct(), !busy) { value ->
                            item = when (value) {
                                "Never (Indefinite)" -> item.copy(endConditionType = EndConditionType.NEVER, endDate = null, endOccurrences = null)
                                "Until 20 Dec 2026" -> item.copy(endConditionType = EndConditionType.UNTIL_DATE, endDate = "2026-12-20", endOccurrences = null)
                                "After 10 times" -> item.copy(endConditionType = EndConditionType.AFTER_OCCURRENCES, endDate = null, endOccurrences = 10)
                                else -> item
                            }
                        }
                    }
                }
            }
            validationError?.let { Text(it, color = StravaOrangeHighlight, fontSize = 12.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Button({
                validationError = validateActivity(item)
                if (validationError == null) onSave(item)
            }, Modifier.fillMaxWidth().height(44.dp), enabled = !busy, shape = SmallShape, colors = ButtonDefaults.buttonColors(containerColor = StravaOrangeHighlight, contentColor = Color.White)) {
                Text(if (busy) "SAVING..." else "SAVE ACTIVITY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun frequencyLabel(frequency: RecurrenceFreq) = when (frequency) {
    RecurrenceFreq.SPECIFIC_DATE -> "Specific Date"
    else -> frequency.name.lowercase().replaceFirstChar { it.uppercase() }
}
private fun intervalLabel(count: Int, item: ActivityItem) = "Every $count " +
    recurrenceIntervalUnit(item).replaceFirstChar { it.uppercase() } + if (count == 1) "" else "s"

@Composable
private fun orangeCheckbox() = CheckboxDefaults.colors(checkedColor = StravaOrangeHighlight, checkmarkColor = Color.White, uncheckedColor = TextSecondary)

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(text, modifier, color = if (bold) TextPrimary else TextSecondary, fontSize = if (bold) 12.sp else 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
}

@Composable
private fun FlatBox(padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(SurfaceCard, SmallShape).border(1.dp, SurfaceBorder, SmallShape).padding(padding.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun SquareButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Int, background: Color, enabled: Boolean) {
    Box(Modifier.size(size.dp).clip(SmallShape).background(background).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(if (size == 32) 16.dp else 20.dp), tint = Color.White)
    }
}

@Composable
private fun SyncButton(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(onClick, modifier, enabled = enabled, shape = SmallShape, border = BorderStroke(1.dp, Color(0xFF31353B)), contentPadding = PaddingValues(horizontal = 8.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = ButtonSurface, contentColor = TextPrimary)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun CleanInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String = "", enabled: Boolean = true, monospace: Boolean = false) {
    BasicTextField(value, onChange, modifier.fillMaxWidth().height(44.dp).background(SurfaceCard, SmallShape).border(1.dp, SurfaceBorder, SmallShape).semantics { contentDescription = label }, enabled = enabled, singleLine = true,
        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default), cursorBrush = SolidColor(StravaOrangeHighlight),
        decorationBox = { input -> Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(placeholder, color = TextSecondary, fontSize = 12.sp); input() } })
}

@Composable
private fun Selector(value: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(SmallShape).background(SurfaceCard).border(1.dp, SurfaceBorder, SmallShape).clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), color = TextPrimary, fontSize = 11.sp)
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp), tint = TextSecondary)
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = SurfaceCard) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option, fontSize = 12.sp, color = TextPrimary) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Box(modifier.height(if (compact) 32.dp else 38.dp).clip(RoundedCornerShape(4.dp)).background(if (selected) StravaOrangeHighlight else ButtonSurface)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { this.selected = selected }, contentAlignment = Alignment.Center) {
        Text(label, fontSize = if (compact) 9.sp else 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.White else TextSecondary)
    }
}
