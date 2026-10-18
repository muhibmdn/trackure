package com.trackure.app.presentation

import com.trackure.app.core.util.DeferredLockscreenAlarmScheduler
import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.ActivityItem
import com.trackure.app.domain.model.ExportHistoryConfig
import com.trackure.app.presentation.add.AddActivityController
import com.trackure.app.presentation.history.HistoryController
import com.trackure.app.presentation.history.HistoryEvent
import com.trackure.app.presentation.home.HomeController
import com.trackure.app.presentation.notes.NotesController
import com.trackure.app.presentation.tracker.TrackerController
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SharedActionsIntegrationTest {
    @Test fun profileScheduleCompletionNoteAndExportUseOneRepository() = runBlocking {
        val repository = InMemoryTrackureRepository.create()
        val today = LocalDate.of(2026, 10, 22)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val home = HomeController(repository, today, scope)
            val tracker = TrackerController(repository, today, scope)
            val add = AddActivityController(repository, DeferredLockscreenAlarmScheduler, scope)
            val history = HistoryController(repository, today, scope)
            val notes = NotesController(repository, today, scope)
            listOf(home.uiState, tracker.uiState, history.uiState, notes.uiState).forEach { state -> scope.launch { state.collect() } }
            var profileSaved = false
            home.updateProfile("Updated Name", "https://example.com", "Keep going") { profileSaved = true }
            withTimeout(2_000) { home.uiState.first { it.profile.name == "Updated Name" } }
            assertTrue(profileSaved)
            assertEquals("Updated Name", repository.userProfile.value.name)

            val activity = ActivityItem(title = "Shared scheduled item", scheduledTime = "18:00")
            add.save(activity)
            withTimeout(2_000) { tracker.uiState.first { state -> state.rows.any { it.activity.id == activity.id } } }
            withTimeout(2_000) { home.uiState.first { it.totalCount == 6 } }
            add.save(activity.copy(title = "Edited scheduled item", scheduledTime = "18:30"))
            withTimeout(2_000) { tracker.uiState.first { state -> state.rows.any { it.activity.title == "Edited scheduled item" } } }
            assertEquals(6, repository.activities.value.size)

            tracker.openActivity(activity.id)
            tracker.editHour("18"); tracker.editMinute("35"); tracker.editNote("Completion note"); tracker.saveLog()
            val noteState = withTimeout(2_000) { notes.uiState.first { state -> state.notes.any { it.activityId == activity.id } } }
            val log = noteState.notes.single { it.activityId == activity.id }
            notes.editNote(log.id); notes.editContent("Journal revision"); notes.save()
            withTimeout(2_000) { tracker.uiState.first { state -> state.rows.any { it.log?.note == "Journal revision" } } }
            withTimeout(2_000) { home.uiState.first { it.completedCount == 4 } }
            withTimeout(2_000) { history.uiState.first { it.totalCompleted == 4 } }

            history.prepareExport(ExportHistoryConfig(includeNote = true))
            assertEquals(HistoryEvent.ExportReady, withTimeout(2_000) { history.events.first() })
            var csv = ""
            history.writeExport { csv = it }
            assertEquals(HistoryEvent.Exported, withTimeout(2_000) { history.events.first() })
            assertTrue(csv.contains("Edited scheduled item"))
            assertTrue(csv.contains("18:35"))
            assertTrue(csv.contains("Journal revision"))
        } finally { scope.cancel() }
    }
}
