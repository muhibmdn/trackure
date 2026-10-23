package com.trackure.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.database.*
import com.trackure.app.data.repository.RoomTrackureRepository
import com.trackure.app.domain.model.*
import com.trackure.app.core.database.entity.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrackureDatabaseTest {
    @Test fun requeueIncludesSyncedRowsButPreservesDeletionsAndOtherAccounts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackureDatabase::class.java).build()
        try {
            val dao = db.trackureDao()
            val item = ActivityItem(id = "activity", title = "Run", scheduledTime = "10:00").toEntity()
            for (owner in listOf("account-a", "account-b")) {
                for (status in listOf("SYNCED", "PENDING_UPLOAD", "PENDING_DELETE", "DELETED")) {
                    dao.upsertActivity(item.copy(id = status, ownerId = owner, syncStatus = status, updatedAt = 123))
                    dao.upsertActivityLog(ActivityLogEntity(status, status, "Run", null, "10:00",
                        "2026-10-23", false, null, null, null, status, 123, owner))
                }
            }
            dao.markWorkspacePendingUpload("account-a")
            for (owner in listOf("account-a", "account-b")) {
                val expected = if (owner == "account-a") "PENDING_UPLOAD" else "SYNCED"
                assertEquals(expected, dao.activitiesFor(owner).first { it.id == "SYNCED" }.syncStatus)
                assertEquals(expected, dao.logsFor(owner).first { it.id == "SYNCED" }.syncStatus)
                for (status in listOf("PENDING_UPLOAD", "PENDING_DELETE", "DELETED")) {
                    assertEquals(status, dao.activitiesFor(owner).first { it.id == status }.syncStatus)
                    assertEquals(status, dao.logsFor(owner).first { it.id == status }.syncStatus)
                }
                assertTrue(dao.activitiesFor(owner).all { it.updatedAt == 123L })
                assertTrue(dao.logsFor(owner).all { it.updatedAt == 123L })
            }
        } finally { db.close() }
    }

    @Test fun accountWorkspacesAndUploadAcknowledgementsAreIsolated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TrackureDatabase::class.java).build()
        try {
            val dao = db.trackureDao()
            val item = ActivityItem(id = "same-import-id", title = "A", scheduledTime = "10:00").toEntity()
            dao.upsertActivity(item.copy(ownerId = "account-a", updatedAt = 10))
            dao.upsertActivity(item.copy(ownerId = "account-b", title = "B", updatedAt = 20))
            dao.setActiveAccount(ActiveAccountEntity(userId = "account-a"))
            assertEquals("A", dao.getAllActivitiesOnce().single().title)
            dao.markActivitySynced(item.id, "account-a", 9)
            assertEquals("PENDING_UPLOAD", dao.getAllActivitiesOnce().single().syncStatus)
            dao.markActivitySynced(item.id, "account-a", 10)
            assertEquals("SYNCED", dao.getAllActivitiesOnce().single().syncStatus)
            dao.deleteActivity(item.id)
            assertTrue(dao.getAllActivitiesOnce().isEmpty())
            assertTrue(dao.activitiesFor("account-a").single().updatedAt > 10)
            dao.setActiveAccount(ActiveAccountEntity(userId = "account-b"))
            assertEquals("B", dao.getAllActivitiesOnce().single().title)
            assertEquals("PENDING_UPLOAD", dao.getAllActivitiesOnce().single().syncStatus)
            dao.setActiveAccount(ActiveAccountEntity())
            assertTrue(dao.getAllActivitiesOnce().isEmpty())
        } finally { db.close() }
    }

    @Test fun versionOneMigrationPreservesLocalWorkspace() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE user_profile (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, profileUrlOrEmail TEXT NOT NULL, motto TEXT NOT NULL, avatarUrl TEXT, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE activities (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, category TEXT, scheduledTime TEXT NOT NULL, fullScreenLockscreenTrigger INTEGER NOT NULL, loopActivity INTEGER NOT NULL, frequency TEXT NOT NULL, repeatDaysOfWeekCsv TEXT NOT NULL, specificDateIso TEXT, intervalCount INTEGER NOT NULL, endConditionType TEXT NOT NULL, endDate TEXT, endOccurrences INTEGER, syncStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL, startDateIso TEXT NOT NULL, repeatOnSpecificDate INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE activity_logs (id TEXT NOT NULL PRIMARY KEY, activityId TEXT NOT NULL, activityTitle TEXT NOT NULL, category TEXT, scheduledTime TEXT NOT NULL, dateIso TEXT NOT NULL, isCompleted INTEGER NOT NULL, completedHour TEXT, completedMinute TEXT, note TEXT, syncStatus TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_activity_logs_activityId_dateIso ON activity_logs(activityId, dateIso)")
            old.execSQL("INSERT INTO user_profile VALUES ('local_user','Preserved','test@example.com','Keep going',NULL,123)")
            old.execSQL("INSERT INTO activities VALUES ('original','Preserved',NULL,'10:00',0,1,'DAILY','',NULL,1,'NEVER',NULL,NULL,'PENDING_UPLOAD',123,'2026-10-22',0)")
            old.execSQL("INSERT INTO activity_logs VALUES ('log','original','Preserved',NULL,'10:00','2026-10-22',1,'10','15','Offline note','PENDING_UPLOAD',123)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, TrackureDatabase::class.java, name)
            .addMigrations(TrackureDatabase.MIGRATION_1_2).build()
        try {
            val dao = db.trackureDao()
            assertEquals("Preserved", dao.getUserProfileOnce()!!.name)
            assertEquals("local_user", dao.getAllActivitiesOnce().single().ownerId)
            assertEquals("Offline note", dao.getAllLogsOnce().single().note)
            dao.setActiveAccount(ActiveAccountEntity(userId = "other"))
            assertTrue(dao.getAllActivitiesOnce().isEmpty())
            dao.setActiveAccount(ActiveAccountEntity())
            assertEquals("original", dao.getAllActivitiesOnce().single().id)
        } finally { db.close(); context.deleteDatabase(name) }
    }

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
