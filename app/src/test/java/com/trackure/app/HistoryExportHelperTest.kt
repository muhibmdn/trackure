package com.trackure.app

import com.trackure.app.core.util.HistoryExportHelper
import com.trackure.app.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoryExportHelperTest {
    private val helper = HistoryExportHelper(LocalDate.parse("2026-10-22"))
    private fun log(id: String, date: String, completed: Boolean = true) = ActivityLog(activityId = id,
        activityTitle = id, dateIso = date, scheduledTime = "07:00", isCompleted = completed, completedHour = "7", completedMinute = "12")

    @Test fun filtersCompletedWindowAndSelectedColumns() {
        val logs = listOf(log("today", "2026-10-22"), log("boundary", "2026-10-16"), log("old", "2026-10-15"),
            log("future", "2026-10-23"), log("unfinished", "2026-10-22", false), log(GeneralDailyNote.ACTIVITY_ID, "2026-10-22"))
        val csv = helper.buildHistoryExportContent(logs, ExportHistoryConfig(true, true, false), 7)
        assertTrue(csv.contains("2026-10-22 • 07:12"))
        assertTrue(csv.contains("boundary"))
        listOf("old", "future", "unfinished", "Catatan", "Completed").forEach { assertFalse(csv.contains(it)) }
        assertEquals(3, csv.trimEnd().split("\r\n").size)
    }

    @Test fun csvAndMarkdownEscapeNotesIndependently() {
        val logs = listOf(log("title", "2026-10-22").copy(note = "A, \"quote\"\nNext | <tag>"))
        val config = ExportHistoryConfig(false, false, true)
        val csv = helper.buildHistoryExportContent(logs, config, 1)
        assertTrue(csv.startsWith("\"Catatan\"\r\n"))
        assertTrue(csv.contains("\"A, \"\"quote\"\"\nNext | <tag>\""))
        assertFalse(csv.contains("title"))
        val markdown = helper.buildHistoryMarkdownContent(logs, config, 1)
        assertTrue(markdown.contains("<br>Next \\| &lt;tag&gt;"))
    }

    @Test fun latestTombstoneOrUndoRemovesOlderCompletion() {
        val old = log("same", "2026-10-22").copy(updatedAt = "2026-10-22T01:00:00Z")
        val newer = old.copy(syncStatus = SyncStatus.PENDING_DELETE, updatedAt = "2026-10-22T02:00:00Z")
        assertEquals("\"Nama Aktivitas\"\r\n", helper.buildHistoryExportContent(listOf(old, newer), ExportHistoryConfig(true, false, false), 1))
    }

    @Test fun emptyAndInvalidConfigurationsAreHandled() {
        assertEquals("\"Nama Aktivitas\"\r\n", helper.buildHistoryExportContent(emptyList(), ExportHistoryConfig(true, false, false), 1))
        assertTrue(runCatching { helper.buildHistoryExportContent(emptyList(), ExportHistoryConfig(false, false, false), 1) }.isFailure)
        assertTrue(runCatching { helper.buildHistoryExportContent(emptyList(), ExportHistoryConfig(), 0) }.isFailure)
    }
}
