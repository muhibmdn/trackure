package com.trackure.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.database.*
import com.trackure.app.data.repository.RoomTrackureRepository
import com.trackure.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrackureDatabaseTest {
    @Test fun seededDataAndEditsSurviveReopenWithoutReseeding() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, TrackureDatabase::class.java, name)
            .addCallback(TrackureDatabase.Companion.SeedCallback).build()
        var db = open()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dao = db.trackureDao()
            assertEquals(5, dao.getAllActivitiesOnce().size)
            assertEquals(3, dao.getAllLogsOnce().count { it.isCompleted })
            val repo = RoomTrackureRepository(db, scope)
            repo.updateProfile("Persisted", "https://example.com", "Keep going")
            val item = dao.getAllActivitiesOnce().first().toDomain()
            repo.saveActivity(item.copy(title = "Edited"))
            coroutineScope {
                repeat(5) { launch { repo.logActivityCompletion(item.id, "2026-10-19", "10", "15", "Saved note") } }
            }
            val saved = dao.getLogForActivityOnDate(item.id, "2026-10-19")!!
            repo.saveOrUpdateNote("2026-10-20", GeneralDailyNote.TITLE, "Moved", saved.id)
            assertNull(dao.getLogForActivityOnDate(item.id, "2026-10-19")!!.note)
            assertTrue(dao.getLogForActivityOnDate(item.id, "2026-10-19")!!.isCompleted)
            dao.getAllActivitiesOnce().forEach { dao.deleteActivity(it.id) }
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            db.close()
            db = open()
            assertEquals("Persisted", db.trackureDao().observeUserProfile().first()!!.name)
            assertTrue(db.trackureDao().getAllActivitiesOnce().isEmpty())
            assertEquals(5, db.trackureDao().getPendingSyncActivities().count { it.syncStatus == "PENDING_DELETE" })
            assertEquals("Moved", db.trackureDao().getLogForActivityOnDate(GeneralDailyNote.ACTIVITY_ID, "2026-10-20")!!.note)
            assertEquals(1, db.trackureDao().getAllLogsOnce().count { it.activityId == item.id && it.dateIso == "2026-10-19" })
        } finally {
            scope.cancel()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
