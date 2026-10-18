package com.trackure.app.presentation.tracker

import kotlinx.coroutines.CoroutineScope
import com.trackure.app.domain.model.ActivityItem
import com.trackure.app.domain.model.ActivityLog
import com.trackure.app.domain.model.SyncStatus
import com.trackure.app.domain.repository.TrackureRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TrackerRow(val activity: ActivityItem, val log: ActivityLog?) {
    val isCompleted: Boolean get() = log?.isCompleted == true
}

data class TrackerUiState(val selectedDate: LocalDate, val rows: List<TrackerRow>)

internal fun trackerState(date: LocalDate, activities: List<ActivityItem>, logs: List<ActivityLog>): TrackerUiState {
    val dayLogs = logs.filter { it.dateIso == date.toString() && it.syncStatus != SyncStatus.PENDING_DELETE }.associateBy { it.activityId }
    return TrackerUiState(date, activities.filter { it.syncStatus != SyncStatus.PENDING_DELETE }
        .sortedBy { it.scheduledTime }.map { TrackerRow(it, dayLogs[it.id]) })
}

data class ActivityLogDraft(
    val activity: ActivityItem,
    val dateIso: String,
    val hour: String,
    val minute: String,
    val note: String,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val validTime: Boolean get() = hour.length in 1..2 && minute.length in 1..2 &&
        hour.all { it in '0'..'9' } && minute.all { it in '0'..'9' } &&
        hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59
}

class TrackerController(
    private val repository: TrackureRepository,
    private val today: LocalDate,
    private val scope: CoroutineScope,
    private val now: () -> LocalTime = { LocalTime.now() },
) {
    private val selectedDate = MutableStateFlow(today)
    val uiState = combine(selectedDate, repository.activities, repository.activityLogs, ::trackerState)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), trackerState(today, repository.activities.value, repository.activityLogs.value))
    private val editor = MutableStateFlow<ActivityLogDraft?>(null)
    val draft = editor.asStateFlow()

    fun selectDate(date: LocalDate) { if (editor.value == null) selectedDate.value = date }
    fun selectToday() = selectDate(today)
    fun openActivity(activityId: String) {
        if (editor.value != null) return
        val item = repository.activities.value.find { it.id == activityId && it.syncStatus != SyncStatus.PENDING_DELETE } ?: return
        val dateIso = selectedDate.value.toString()
        val log = repository.activityLogs.value.find { it.activityId == activityId && it.dateIso == dateIso && it.syncStatus != SyncStatus.PENDING_DELETE }
        val current = now()
        editor.value = ActivityLogDraft(item, dateIso,
            log?.completedHour ?: current.hour.toString().padStart(2, '0'),
            log?.completedMinute ?: current.minute.toString().padStart(2, '0'), log?.note.orEmpty())
    }
    fun dismissLog() { if (editor.value?.saving != true) editor.value = null }
    fun editHour(value: String) = edit { if (value.length <= 2 && value.all { it in '0'..'9' }) it.copy(hour = value, error = null) else it }
    fun editMinute(value: String) = edit { if (value.length <= 2 && value.all { it in '0'..'9' }) it.copy(minute = value, error = null) else it }
    fun editNote(value: String) = edit { it.copy(note = value, error = null) }
    private fun edit(change: (ActivityLogDraft) -> ActivityLogDraft) {
        editor.value?.takeUnless { it.saving }?.let { editor.value = change(it) }
    }
    fun saveLog() {
        val current = editor.value ?: return
        if (current.saving) return
        if (!current.validTime) { editor.value = current.copy(error = "Enter a valid time (00:00–23:59)"); return }
        editor.value = current.copy(saving = true, error = null)
        scope.launch {
            try {
                repository.logActivityCompletion(current.activity.id, current.dateIso,
                    current.hour.padStart(2, '0'), current.minute.padStart(2, '0'), current.note)
                editor.value = null
            } catch (e: CancellationException) {
                editor.value = current
                throw e
            } catch (e: Exception) {
                editor.value = current.copy(error = e.message ?: "Unable to save. Please try again.")
            }
        }
    }
}
