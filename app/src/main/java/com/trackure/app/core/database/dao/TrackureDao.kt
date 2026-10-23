package com.trackure.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.trackure.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackureDao {
    @Query("SELECT * FROM user_profile WHERE id = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user')")
    fun observeUserProfile(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user')")
    suspend fun getUserProfileOnce(): UserProfileEntity?
    @Upsert suspend fun upsertUserProfile(profile: UserProfileEntity)
    @Query("SELECT * FROM activities WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED') ORDER BY scheduledTime, id")
    fun observeAllActivities(): Flow<List<ActivityEntity>>
    @Query("SELECT * FROM activities WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED') ORDER BY scheduledTime, id")
    suspend fun getAllActivitiesOnce(): List<ActivityEntity>
    @Query("SELECT * FROM activities WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND fullScreenLockscreenTrigger = 1 AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED')")
    suspend fun getLockscreenActivitiesOnce(): List<ActivityEntity>
    @Query("SELECT * FROM activities WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND id = :id AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED') LIMIT 1")
    suspend fun getActivityOnce(id: String): ActivityEntity?
    @Upsert suspend fun upsertActivity(activity: ActivityEntity)
    @Upsert suspend fun upsertActivities(activities: List<ActivityEntity>)
    @Query("UPDATE activities SET syncStatus = 'PENDING_DELETE', updatedAt = MAX(updatedAt + 1, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)) WHERE id = :id AND ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user')")
    suspend fun deleteActivity(id: String)
    @Query("SELECT * FROM activity_logs WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED') ORDER BY dateIso, scheduledTime, id")
    fun observeAllLogs(): Flow<List<ActivityLogEntity>>
    @Query("SELECT * FROM activity_logs WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus NOT IN ('PENDING_DELETE', 'DELETED') ORDER BY dateIso, scheduledTime, id")
    suspend fun getAllLogsOnce(): List<ActivityLogEntity>
    @Query("SELECT * FROM activity_logs WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND activityId = :activityId AND dateIso = :dateIso LIMIT 1")
    suspend fun getLogForActivityOnDate(activityId: String, dateIso: String): ActivityLogEntity?
    @Upsert suspend fun upsertActivityLog(log: ActivityLogEntity)
    @Query("SELECT * FROM activities WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus IN ('PENDING_UPLOAD', 'PENDING_DELETE')")
    suspend fun getPendingSyncActivities(): List<ActivityEntity>
    @Query("SELECT * FROM activity_logs WHERE ownerId = COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user') AND syncStatus IN ('PENDING_UPLOAD', 'PENDING_DELETE')")
    suspend fun getPendingSyncLogs(): List<ActivityLogEntity>

    @Query("SELECT COALESCE((SELECT userId FROM active_account WHERE slot = 0), 'local_user')")
    suspend fun activeUserId(): String
    @Upsert suspend fun setActiveAccount(account: ActiveAccountEntity)
    @Query("SELECT * FROM user_profile WHERE id = :id")
    suspend fun profileFor(id: String): UserProfileEntity?
    @Query("SELECT * FROM activities WHERE ownerId = :owner")
    suspend fun activitiesFor(owner: String): List<ActivityEntity>
    @Query("SELECT * FROM activity_logs WHERE ownerId = :owner")
    suspend fun logsFor(owner: String): List<ActivityLogEntity>
    @Query("UPDATE user_profile SET syncStatus = 'SYNCED' WHERE id = :id AND updatedAt = :version AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markProfileSynced(id: String, version: Long)
    @Query("UPDATE activities SET syncStatus = 'SYNCED' WHERE id = :id AND ownerId = :owner AND updatedAt = :version AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markActivitySynced(id: String, owner: String, version: Long)
    @Query("UPDATE activity_logs SET syncStatus = 'SYNCED' WHERE id = :id AND ownerId = :owner AND updatedAt = :version AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markLogSynced(id: String, owner: String, version: Long)
    @Query("UPDATE activities SET syncStatus = 'DELETED' WHERE id = :id AND ownerId = :owner AND updatedAt = :version AND syncStatus = 'PENDING_DELETE'")
    suspend fun markActivityDeleted(id: String, owner: String, version: Long)
    @Query("UPDATE activity_logs SET syncStatus = 'DELETED' WHERE id = :id AND ownerId = :owner AND updatedAt = :version AND syncStatus = 'PENDING_DELETE'")
    suspend fun markLogDeleted(id: String, owner: String, version: Long)
    @Query("DELETE FROM activity_logs WHERE id = :id AND ownerId = :owner")
    suspend fun removeLog(id: String, owner: String)
}
