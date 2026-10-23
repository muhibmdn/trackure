package com.trackure.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.util.LockscreenAlarmScheduler
import com.trackure.app.data.repository.PersistentTrackureRepository
import com.trackure.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PersistentRepositoryTest {
    @Test fun writesPublishReactiveStateSchedulePersistedActivitiesAndQueueSync() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TrackureDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val scheduled = mutableListOf<ActivityItem>()
        val cancelled = mutableListOf<String>()
        var syncs = 0
        val scheduler = object : LockscreenAlarmScheduler {
            override fun scheduleFullScreenTrigger(activity: ActivityItem) { scheduled += activity }
            override fun cancelTrigger(activityId: String) { cancelled += activityId }
        }
        val today = LocalDate.of(2026, 10, 22)
        val repo = PersistentTrackureRepository(db, scope, { today }, scheduler, onDataChanged = { syncs++ })
        try {
            repo.updateProfile("Offline", "me@example.com", "Keep going")
            val item = ActivityItem(id = "persisted", title = "Study", scheduledTime = "10:00",
                startDateIso = today.toString(), fullScreenLockscreenTrigger = true)
            repo.saveActivity(item)
            assertEquals(1, scheduled.size)
            assertEquals(db.trackureDao().getActivityOnce(item.id)!!.startDateIso, scheduled.single().startDateIso)
            repo.saveActivity(item.copy(fullScreenLockscreenTrigger = false, startDateIso = "2026-10-23"))
            assertEquals(listOf(item.id), cancelled)
            assertEquals(today.toString(), db.trackureDao().getActivityOnce(item.id)!!.startDateIso)
            repo.logActivityCompletion(item.id, today.toString(), "10", "15", "Completed offline")
            repo.saveOrUpdateNote(today.toString(), item.title, "Edited note")
            withTimeout(5_000) {
                assertEquals("Offline", repo.userProfile.first { it.name == "Offline" }.name)
                assertEquals(1, repo.activities.first { it.isNotEmpty() }.size)
                val logs = repo.activityLogs.first { it.singleOrNull()?.note == "Edited note" }
                assertTrue(logs.single().isCompleted)
                assertEquals(SyncStatus.PENDING_UPLOAD, logs.single().syncStatus)
                assertEquals(1, repo.activeStreakCount.first { it == 1 })
                val matrix = repo.streakMatrix.first { rows -> rows.any { it.dateIso == today.toString() && it.level > 0 } }
                assertEquals(112, matrix.size)
            }
            assertEquals(5, syncs)
            try { repo.saveActivity(item.copy(title = "")); fail("Invalid save should fail") }
            catch (_: IllegalArgumentException) { }
            assertEquals(5, syncs)
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            db.close()
        }
    }

    @Test fun alarmFailureLeavesCommittedDataPendingAndRequestsRecovery() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TrackureDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var syncs = 0
        var recoveries = 0
        val scheduler = object : LockscreenAlarmScheduler {
            override fun scheduleFullScreenTrigger(activity: ActivityItem) { throw SecurityException("Revoked") }
            override fun cancelTrigger(activityId: String) = Unit
        }
        try {
            val repo = PersistentTrackureRepository(db, scope, alarmScheduler = scheduler,
                onDataChanged = { syncs++ }, onAlarmRecovery = { recoveries++ })
            try {
                repo.saveActivity(ActivityItem(id = "saved", title = "Study", scheduledTime = "10:00", fullScreenLockscreenTrigger = true))
                fail("Scheduling failure should be reported")
            } catch (e: IllegalStateException) { assertTrue(e.message!!.startsWith("Activity saved")) }
            assertEquals("PENDING_UPLOAD", db.trackureDao().getActivityOnce("saved")!!.syncStatus)
            assertEquals(1, syncs)
            assertEquals(1, recoveries)
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            db.close()
        }
    }
}
