package com.trackure.app.presentation.notes

import com.trackure.app.domain.model.*
import com.trackure.app.domain.repository.TrackureRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NotesUiState(val query: String, val date: LocalDate?, val notes: List<ActivityLog>, val activities: List<ActivityItem>)

internal fun filteredNotes(logs: List<ActivityLog>, query: String, date: LocalDate?): List<ActivityLog> = logs
    .filter { it.syncStatus != SyncStatus.PENDING_DELETE && !it.note.isNullOrBlank() }
    .filter { (date == null || it.dateIso == date.toString()) &&
        (it.activityTitle.contains(query.trim(), ignoreCase = true) || it.note.orEmpty().contains(query.trim(), ignoreCase = true)) }
    .sortedWith(compareByDescending<ActivityLog> { it.dateIso }.thenByDescending { "${it.completedHour.orEmpty()}:${it.completedMinute.orEmpty()}" }.thenBy { it.activityTitle })

data class NoteDraft(
    val originalLogId: String? = null,
    val dateIso: String,
    val activityTitle: String,
    val content: String,
    val saving: Boolean = false,
    val error: String? = null,
)

class NotesController(private val repository: TrackureRepository, val today: LocalDate, private val scope: CoroutineScope) {
    private val query = MutableStateFlow("")
    private val date = MutableStateFlow<LocalDate?>(today)
    private fun state(logs: List<ActivityLog>, activities: List<ActivityItem>, query: String, date: LocalDate?) =
        NotesUiState(query, date, filteredNotes(logs, query, date), activities.filter { it.syncStatus != SyncStatus.PENDING_DELETE })
    val uiState = combine(repository.activityLogs, repository.activities, query, date, ::state)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), state(repository.activityLogs.value, repository.activities.value, query.value, date.value))
    private val editor = MutableStateFlow<NoteDraft?>(null)
    val draft = editor.asStateFlow()
    fun search(value: String) { query.value = value }
    fun filterDate(value: LocalDate?) { date.value = value }
    fun newNote() { editor.value = NoteDraft(dateIso = today.toString(), activityTitle = GeneralDailyNote.TITLE, content = "") }
    fun editNote(logId: String) {
        val log = repository.activityLogs.value.find { it.id == logId && it.syncStatus != SyncStatus.PENDING_DELETE } ?: return
        editor.value = NoteDraft(log.id, log.dateIso, log.activityTitle, log.note.orEmpty())
    }
    fun dismiss() { if (editor.value?.saving != true) editor.value = null }
    private fun edit(change: (NoteDraft) -> NoteDraft) { editor.value?.takeUnless { it.saving }?.let { editor.value = change(it).copy(error = null) } }
    fun editDate(value: LocalDate) = edit { it.copy(dateIso = value.toString()) }
    fun editActivity(value: String) = edit { it.copy(activityTitle = value) }
    fun editContent(value: String) = edit { it.copy(content = value) }
    fun save() {
        val note = editor.value ?: return
        if (note.saving) return
        if (note.content.isBlank()) { editor.value = note.copy(error = "Write a note before saving"); return }
        editor.value = note.copy(saving = true, error = null)
        scope.launch {
            try {
                repository.saveOrUpdateNote(note.dateIso, note.activityTitle, note.content, note.originalLogId)
                // Keep the saved note visible even if a previous filter/search would hide it.
                date.value = LocalDate.parse(note.dateIso)
                query.value = ""
                editor.value = null
            } catch (e: CancellationException) { editor.value = note; throw e
            } catch (e: Exception) { editor.value = note.copy(error = e.message ?: "Unable to save note") }
        }
    }
}
