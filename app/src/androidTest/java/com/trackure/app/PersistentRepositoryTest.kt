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
    @Test fun immediateSyncReadsCommittedDataWithoutBlockingSaveAndDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TrackureDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val observed = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val repo = PersistentTrackureRepository(db, scope, immediateSync = { dao ->
            observed.send(dao.activitiesFor("local_user").single().syncStatus)
            release.await()
            true
        })
        try {
            withTimeout(5_000) {
                repo.saveActivity(ActivityItem(id = "direct-sync", title = "Run", scheduledTime = "07:00"))
                assertEquals("PENDING_UPLOAD", observed.receive())
                repo.deleteActivity("direct-sync")
                assertEquals("PENDING_DELETE", observed.receive())
                assertNull(db.trackureDao().getActivityOnce("direct-sync"))
                // Both saves returned although the network callbacks are still suspended.
                assertFalse(release.isCompleted)
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
            scope.coroutineContext[Job]!!.join()
            db.close()
        }
    }

    @Test fun cloudMergeCannotClobberPendingEditsOrDeletedActivities() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TrackureDatabase::class.java).build()
        try {
            val dao = db.trackureDao()
            val row = com.trackure.app.core.database.entity.ActivityEntity("merge", "Local edit", null, "07:00",
                false, true, "DAILY", "", null, 1, "NEVER", null, null, "PENDING_UPLOAD", 10,
                startDateIso = "2026-01-01")
            dao.upsertActivity(row)
            val incoming = row.copy(title = "Remote", syncStatus = "SYNCED", updatedAt = 20, startDateIso = "2026-10-23")
            dao.mergeCloudActivity(incoming)
            assertEquals("Local edit", dao.getActivityOnce(row.id)!!.title)
            dao.markActivitySynced(row.id, row.ownerId, 10)
            dao.mergeCloudActivity(incoming)
            assertEquals("Remote", dao.getActivityOnce(row.id)!!.title)
            assertEquals("2026-01-01", dao.getActivityOnce(row.id)!!.startDateIso)
            dao.deleteActivity(row.id)
            val deleted = dao.activitiesFor(row.ownerId).single()
            dao.markActivityDeleted(row.id, row.ownerId, deleted.updatedAt)
            dao.mergeCloudActivity(incoming.copy(updatedAt = Long.MAX_VALUE))
            assertNull(dao.getActivityOnce(row.id))
        } finally { db.close() }
    }

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
