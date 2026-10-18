package com.trackure.app.presentation.notes

import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.*
import com.trackure.app.presentation.home.HomeController
import com.trackure.app.presentation.tracker.TrackerController
import com.trackure.app.presentation.history.HistoryController
import com.trackure.app.presentation.history.HistoryRange
import com.trackure.app.presentation.history.historyState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NotesIntegrationTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun searchCombinesTitleContentAndSingleDateFilter() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        repo.saveOrUpdateNote("2026-10-21", GeneralDailyNote.TITLE, "Yesterday JOURNAL")
        assertEquals(1, filteredNotes(repo.activityLogs.value, "gym", today).size)
        assertEquals(1, filteredNotes(repo.activityLogs.value, "binary SEARCH", today).size)
        assertEquals(0, filteredNotes(repo.activityLogs.value, "journal", today).size)
        assertEquals(1, filteredNotes(repo.activityLogs.value, "journal", null).size)
        assertEquals(4, filteredNotes(repo.activityLogs.value, "", null).size)
    }

    @Test fun generalNoteUpsertsWithoutChangingActivityOrCompletionCounts() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        repo.saveOrUpdateNote(today.toString(), GeneralDailyNote.TITLE, "One step")
        repo.saveOrUpdateNote(today.toString(), GeneralDailyNote.TITLE, "Another step")
        val journal = repo.activityLogs.value.filter { it.activityId == GeneralDailyNote.ACTIVITY_ID }
        assertEquals(1, journal.size)
        assertEquals("Another step", journal.single().note)
        assertFalse(journal.single().isCompleted)
        assertEquals(5, repo.activities.value.size)
        assertEquals(60, historyState(repo.activityLogs.value, today, HistoryRange.MONTH).averageCompletion)
    }

    @Test fun movingNotePreservesBothCompletionRecordsAndRejectsCollisions() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        val original = repo.activityLogs.value.first()
        val occupied = repo.activityLogs.value[1]
        val before = repo.activityLogs.value
        assertTrue(runCatching { repo.saveOrUpdateNote(occupied.dateIso, occupied.activityTitle, "Moved", original.id) }.isFailure)
        assertEquals(before, repo.activityLogs.value)
        repo.saveOrUpdateNote("2026-10-21", original.activityTitle, "Moved", original.id)
        val old = repo.activityLogs.value.single { it.id == original.id }
        assertNull(old.note)
        assertTrue(old.isCompleted)
        assertEquals("07", old.completedHour)
        assertEquals("12", old.completedMinute)
        val target = repo.activityLogs.value.single { it.activityId == original.activityId && it.dateIso == "2026-10-21" }
        assertEquals("Moved", target.note)
        assertFalse(target.isCompleted)
    }

    @Test fun allControllersObserveTheSameRepositoryAndNotesPreserveTrackerCompletion() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val notes = NotesController(repo, today, scope)
            val tracker = TrackerController(repo, today, scope)
            val home = HomeController(repo, today, scope)
            val history = HistoryController(repo, today, scope)
            listOf(notes.uiState, tracker.uiState, home.uiState, history.uiState).forEach { state -> scope.launch { state.collect() } }
            val original = repo.activityLogs.value.first()
            notes.editNote(original.id)
            notes.editContent("Shared update")
            notes.save()
            withTimeout(2_000) { tracker.uiState.first { it.rows.first().log?.note == "Shared update" } }
            assertEquals("Shared update", notes.uiState.value.notes.single { it.id == original.id }.note)
            assertEquals(3, home.uiState.value.completedCount)
            assertEquals(3, history.uiState.value.totalCompleted)
            tracker.openActivity(repo.activities.value[3].id)
            tracker.editHour("20"); tracker.editMinute("15"); tracker.editNote("Tracker to journal"); tracker.saveLog()
            withTimeout(2_000) { notes.uiState.first { state -> state.notes.any { it.note == "Tracker to journal" } } }
            withTimeout(2_000) { home.uiState.first { it.completedCount == 4 } }
            withTimeout(2_000) { history.uiState.first { it.totalCompleted == 4 } }
            assertNull(notes.draft.value)
        } finally { scope.cancel() }
    }

    @Test fun cancellingOrBlankDraftDoesNotWrite() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = NotesController(repo, today, scope)
            val before = repo.activityLogs.value
            controller.newNote(); controller.editContent("Unsaved"); controller.dismiss()
            assertEquals(before, repo.activityLogs.value)
            controller.newNote(); controller.save()
            assertNotNull(controller.draft.value?.error)
            assertEquals(before, repo.activityLogs.value)
        } finally { scope.cancel() }
    }
}
