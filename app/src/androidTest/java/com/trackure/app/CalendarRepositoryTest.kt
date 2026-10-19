package com.trackure.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.util.IcsCalendarHelper
import com.trackure.app.core.util.LockscreenAlarmScheduler
import com.trackure.app.data.repository.RoomTrackureRepository
import com.trackure.app.domain.model.*
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CalendarRepositoryTest {
    @Test fun importIsAtomicIdempotentAndSchedulesPersistedEventsThenExportsFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackureDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scheduled = mutableListOf<ActivityItem>()
        val scheduler = object : LockscreenAlarmScheduler {
            override fun scheduleFullScreenTrigger(activity: ActivityItem) { scheduled.add(activity) }
            override fun cancelTrigger(activityId: String) = Unit
        }
        try {
            val repo = RoomTrackureRepository(db, scope, { LocalDate.parse("2026-10-22") }, scheduler)
            val calendar = IcsCalendarHelper().generateIcsFromActivities(listOf(
                ActivityItem(id = "import-1", title = "Imported study", scheduledTime = "07:15", startDateIso = "2026-10-22"),
                ActivityItem(id = "import-2", title = "Imported run", scheduledTime = "16:30", startDateIso = "2026-10-22")))
            assertEquals(2, repo.importIcsContent(calendar).getOrThrow())
            assertEquals(2, scheduled.size)
            assertTrue(scheduled.all { it.fullScreenLockscreenTrigger })
            assertEquals(2, db.trackureDao().getAllActivitiesOnce().size)
            assertEquals(2, repo.importIcsContent(calendar).getOrThrow())
            assertEquals(2, db.trackureDao().getAllActivitiesOnce().size)
            assertTrue(repo.importIcsContent(calendar.replace("SUMMARY:Imported run", "RRULE:FREQ=HOURLY\r\nSUMMARY:Imported run")).isFailure)
            assertEquals(4, scheduled.size)
            repo.logActivityCompletion("import-1", "2026-10-22", "7", "12", "Saved, note")
            val ics = repo.exportIcsContent().getOrThrow()
            val csv = repo.exportHistoryData(ExportHistoryConfig(true, true, true), 1).getOrThrow()
            // Exercise the same UTF-8 ContentResolver streams used by the document callbacks.
            listOf("calendar.ics" to ics, "history.csv" to csv).forEach { (name, content) ->
                val file = java.io.File(context.cacheDir, name)
                try {
                    val uri = android.net.Uri.fromFile(file)
                    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    val restored = context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    assertEquals(content, restored)
                } finally { file.delete() }
            }
            assertEquals(2, IcsCalendarHelper().parseIcsToActivities(ics).size)
            assertTrue(csv.contains("2026-10-22 • 07:12"))
            assertTrue(csv.contains("Saved, note"))
            assertFalse(csv.contains("Imported run"))
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            db.close()
        }
    }
}
