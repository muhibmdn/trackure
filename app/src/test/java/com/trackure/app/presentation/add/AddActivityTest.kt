package com.trackure.app.presentation.add

import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AddActivityTest {
    @Test fun deleteQueuesRepositoryDeletionBeforeReturningToList() = runBlocking {
        val repository = InMemoryTrackureRepository.create()
        val item = ActivityItem(title = "Delete me", scheduledTime = "08:00")
        repository.saveActivity(item)
        val controller = AddActivityController(repository, this)
        controller.delete(item.id)
        assertEquals(AddActivityEvent.Deleted, controller.events.first())
        assertFalse(repository.activities.value.any { it.id == item.id && it.syncStatus != SyncStatus.PENDING_DELETE })
    }

    @Test fun validatesRequiredNameTimeAndRecurrenceFields() {
        val item = ActivityItem(title = "Study", scheduledTime = "08:00")
        assertNull(validateActivity(item))
        assertNotNull(validateActivity(item.copy(title = " ")))
        listOf("8:00", "24:00", "08:60", "invalid").forEach { assertNotNull(validateActivity(item.copy(scheduledTime = it))) }
        assertNotNull(validateActivity(item.copy(frequency = RecurrenceFreq.SPECIFIC_DATE)))
        assertNotNull(validateActivity(item.copy(frequency = RecurrenceFreq.SPECIFIC_DATE, specificDate = "2026-02-30")))
        assertNotNull(validateActivity(item.copy(endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 0)))
        assertNotNull(validateActivity(item.copy(endConditionType = EndConditionType.UNTIL_DATE, endDate = "bad")))
    }
    @Test fun specificDateAndEndDateRoundTripIndependently() {
        val item = ActivityItem(title = "Conference", scheduledTime = "08:00", frequency = RecurrenceFreq.SPECIFIC_DATE,
            specificDate = "2026-10-25", repeatOnSpecificDate = true, endConditionType = EndConditionType.UNTIL_DATE, endDate = "2028-12-20")
        assertNull(validateActivity(item))
        val decoded = Json.decodeFromString<ActivityItem>(Json.encodeToString(item))
        assertEquals(item, decoded)
        assertEquals("2026-10-25", decoded.specificDate)
        assertEquals("2028-12-20", decoded.endDate)
    }
    @Test fun addAndEditKeepOneStableItemWithAllSettings() = runBlocking {
        val repository = InMemoryTrackureRepository.create()
        val item = ActivityItem(title = "New activity", category = "Custom category", scheduledTime = "08:00",
            fullScreenLockscreenTrigger = true, frequency = RecurrenceFreq.CUSTOM, repeatDaysOfWeek = setOf(1, 3, 5), intervalCount = 2,
            endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 10)
        repository.saveActivity(item)
        assertEquals(6, repository.activities.value.size)
        repository.saveActivity(item.copy(title = "Edited", scheduledTime = "09:30", fullScreenLockscreenTrigger = false))
        assertEquals(6, repository.activities.value.size)
        val saved = repository.activities.value.single { it.id == item.id }
        assertEquals("Edited", saved.title)
        assertEquals("Custom category", saved.category)
        assertEquals(setOf(1, 3, 5), saved.repeatDaysOfWeek)
        assertEquals(2, saved.intervalCount)
        assertEquals(10, saved.endOccurrences)
        assertFalse(saved.fullScreenLockscreenTrigger)
        assertEquals(SyncStatus.PENDING_UPLOAD, saved.syncStatus)
    }
    @Test fun oldSerializedItemsUseCompatibleDefaults() {
        val old = """{"id":"legacy","title":"Gym","scheduledTime":"07:00"}"""
        val decoded = Json.decodeFromString<ActivityItem>(old)
        assertNull(decoded.specificDate)
        assertFalse(decoded.repeatOnSpecificDate)
    }
}
