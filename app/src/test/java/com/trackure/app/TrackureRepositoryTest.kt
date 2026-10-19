package com.trackure.app

import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.ExportHistoryConfig
import com.trackure.app.domain.model.SyncStatus
import com.trackure.app.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek

class TrackureRepositoryTest {
    @Test fun seedMatchesPrototypeAndCalendarAlignment() {
        val repo = InMemoryTrackureRepository.create()
        assertEquals(5, repo.activities.value.size)
        assertEquals(3, repo.activityLogs.value.count { it.isCompleted })
        assertEquals(112, repo.streakMatrix.value.size)
        assertEquals(DayOfWeek.MONDAY, LocalDate.parse(repo.streakMatrix.value.first().dateIso).dayOfWeek)
        assertEquals(112, repo.streakMatrix.value.map { it.dateIso }.distinct().size)
    }
    @Test fun profileUpdatesPreserveIdentityAndSerialize() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        val id = repo.userProfile.value.id
        repo.updateProfile("New Name", "test@example.com", "Keep going")
        val profile = repo.userProfile.value
        assertEquals(id, profile.id)
        assertEquals("New Name", profile.name)
        assertEquals(SyncStatus.PENDING_UPLOAD, profile.syncStatus)
        assertEquals(profile, Json.decodeFromString<UserProfile>(Json.encodeToString(profile)))
    }
    @Test fun completionIsAnUpsertAndNotesPreserveCompletion() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        val item = repo.activities.value[3]
        repeat(2) { repo.logActivityCompletion(item.id, "2026-10-22", "20", "05", "Done") }
        repo.saveOrUpdateNote("2026-10-22", item.title, "Updated note")
        assertEquals(5, repo.activityLogs.value.size)
        val log = repo.activityLogs.value.single { it.activityId == item.id }
        assertTrue(log.isCompleted)
        assertEquals("20", log.completedHour)
        assertEquals("Updated note", log.note)
        assertEquals(4, repo.activityLogs.value.count { it.isCompleted })
    }
    @Test fun historyRespectsConfigAndEscapesCsv() = runBlocking {
        val repo = InMemoryTrackureRepository.create()
        repo.saveOrUpdateNote("2026-10-22", repo.activities.value.first().title, "A, \"quote\"\nNext")
        val csv = repo.exportHistoryData(ExportHistoryConfig(includeNote = true), 1).getOrThrow()
        assertTrue(csv.contains("\"A, \"\"quote\"\"\nNext\""))
        assertTrue(repo.exportHistoryData(ExportHistoryConfig(false, false, false), 1).isFailure)
        assertTrue(repo.exportHistoryData(ExportHistoryConfig(), 0).isFailure)
    }
}
