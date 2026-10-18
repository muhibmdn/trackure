package com.trackure.app.presentation.history

import com.trackure.app.data.repository.InMemoryTrackureRepository
import com.trackure.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HistoryAnalyticsTest {
    private val today = LocalDate.of(2026, 10, 22)
    private fun log(id: String, date: LocalDate, done: Boolean = true) = ActivityLog(activityId = id, activityTitle = id, scheduledTime = "08:00", dateIso = date.toString(), isCompleted = done)

    @Test fun seedMetricsAndWeekComeFromLogs() {
        val repository = InMemoryTrackureRepository.create()
        val state = historyState(repository.activityLogs.value, today, HistoryRange.MONTH)
        assertEquals(60, state.averageCompletion)
        assertEquals(3, state.totalCompleted)
        assertEquals(today.minusDays(3), state.days.first().date)
        assertEquals(today.plusDays(3), state.days.last().date)
        assertEquals(3, state.days[3].completed)
        assertEquals(3f / 7f, state.days[3].rollingVelocity, 0.0001f)
        assertEquals(0, state.days[5].completed)
    }
    @Test fun rangeBoundariesFutureAndDeletedLogsAreHandled() {
        val logs = listOf(log("a", today), log("b", today.minusDays(6)), log("c", today.minusDays(7)),
            log("future", today.plusDays(1)), log("deleted", today).copy(syncStatus = SyncStatus.PENDING_DELETE))
        assertEquals(2, historyState(logs, today, HistoryRange.WEEK).totalCompleted)
        assertEquals(3, historyState(logs, today, HistoryRange.MONTH).totalCompleted)
        assertEquals(0, historyState(emptyList(), today, HistoryRange.YEAR).averageCompletion)
    }
    @Test fun duplicateOccurrencesUseLatestVersionAndDailyRatesAreAveraged() {
        val original = log("same", today, false).copy(updatedAt = "2026-10-22T08:00:00Z")
        val updated = original.copy(isCompleted = true, updatedAt = "2026-10-22T09:00:00Z")
        val logs = listOf(original, updated, log("missed", today.minusDays(1), false))
        val state = historyState(logs, today, HistoryRange.WEEK)
        assertEquals(1, state.totalCompleted)
        assertEquals(50, state.averageCompletion)
    }
    @Test fun trackerCompletionsAndNotesFlowIntoHistoryAndExport() = runBlocking {
        val repository = InMemoryTrackureRepository.create()
        val activity = repository.activities.value[3]
        repository.logActivityCompletion(activity.id, today.toString(), "20", "15", "Review finished")
        val state = historyState(repository.activityLogs.value, today, HistoryRange.MONTH)
        assertEquals(4, state.totalCompleted)
        assertEquals(80, state.averageCompletion)
        val csv = repository.exportHistoryData(ExportHistoryConfig(true, true, true), HistoryRange.MONTH.days).getOrThrow()
        assertTrue(csv.contains("Review finished"))
        assertTrue(csv.contains("20:15"))
        val withoutNotes = repository.exportHistoryData(ExportHistoryConfig(true, true, false), HistoryRange.MONTH.days).getOrThrow()
        assertFalse(withoutNotes.contains("Review finished"))
    }
}
