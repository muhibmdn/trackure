package com.trackure.app.presentation.tracker

import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrackerStateTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun completionsAreScopedToSelectedDateAndActivityId() {
        val repository = InMemoryTrackureRepository.create()
        val activities = repository.activities.value
        val logs = repository.activityLogs.value
        assertEquals(3, trackerState(today, activities, logs).rows.count { it.isCompleted })
        assertEquals(0, trackerState(today.minusDays(1), activities, logs).rows.count { it.isCompleted })
        val renamed = activities.map { it.copy(title = "Same title") }
        assertEquals(3, trackerState(today, renamed, logs).rows.count { it.isCompleted })
        assertEquals("17", trackerState(today, activities, logs).rows[2].log?.completedHour)
    }

    @Test fun selectedDaySavePublishesNoteWithoutChangingOtherDates() = runBlocking {
        val repository = InMemoryTrackureRepository.create()
        val activity = repository.activities.value[3]
        val date = today.minusDays(1)
        // This fixture must actually be scheduled on the historical date under test.
        repository.saveActivity(activity.copy(startDateIso = date.toString()))
        repository.logActivityCompletion(activity.id, date.toString(), "20", "15", "Ready for tomorrow")
        val selected = trackerState(date, repository.activities.value, repository.activityLogs.value)
        assertTrue(selected.rows.single { it.activity.id == activity.id }.isCompleted)
        assertEquals("Ready for tomorrow", selected.rows.single { it.activity.id == activity.id }.log?.note)
        assertFalse(trackerState(today, repository.activities.value, repository.activityLogs.value).rows.single { it.activity.id == activity.id }.isCompleted)
        repository.logActivityCompletion(activity.id, date.toString(), "20", "30", "Revised")
        assertEquals(1, repository.activityLogs.value.count { it.activityId == activity.id && it.dateIso == date.toString() })
        assertEquals("Revised", repository.activityLogs.value.single { it.activityId == activity.id && it.dateIso == date.toString() }.note)
    }

    @Test fun deletedRecordsAreExcluded() {
        val repository = InMemoryTrackureRepository.create()
        val activities = repository.activities.value
        val logs = repository.activityLogs.value
        assertEquals(4, trackerState(today, activities.mapIndexed { index, item -> if (index == 0) item.copy(syncStatus = SyncStatus.PENDING_DELETE) else item }, logs).rows.size)
        assertFalse(trackerState(today, activities, logs.map { it.copy(syncStatus = SyncStatus.PENDING_DELETE) }).rows.any { it.isCompleted })
    }

    @Test fun timeValidationRejectsInvalidInputAndAllowsBoundaryTimes() {
        val item = InMemoryTrackureRepository.create().activities.value.first()
        val draft = ActivityLogDraft(item, today.toString(), "00", "00", "")
        assertTrue(draft.validTime)
        assertTrue(draft.copy(hour = "23", minute = "59").validTime)
        assertTrue(draft.copy(hour = "7", minute = "5").validTime)
        listOf("", "24", "-1", "aa", "123").forEach { assertFalse(draft.copy(hour = it).validTime) }
        listOf("", "60", "-1", "aa", "123").forEach { assertFalse(draft.copy(minute = it).validTime) }
    }
}
