package com.trackure.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.trackure.app.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackureDao {
    @Query("SELECT * FROM user_profile WHERE id = 'local_user'")
    fun observeUserProfile(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id = 'local_user'")
    suspend fun getUserProfileOnce(): UserProfileEntity?
    @Upsert suspend fun upsertUserProfile(profile: UserProfileEntity)
    @Query("SELECT * FROM activities WHERE syncStatus != 'PENDING_DELETE' ORDER BY scheduledTime, id")
    fun observeAllActivities(): Flow<List<ActivityEntity>>
    @Query("SELECT * FROM activities WHERE syncStatus != 'PENDING_DELETE' ORDER BY scheduledTime, id")
    suspend fun getAllActivitiesOnce(): List<ActivityEntity>
    @Query("SELECT * FROM activities WHERE fullScreenLockscreenTrigger = 1 AND syncStatus != 'PENDING_DELETE'")
    suspend fun getLockscreenActivitiesOnce(): List<ActivityEntity>
    @Query("SELECT * FROM activities WHERE id = :id AND syncStatus != 'PENDING_DELETE' LIMIT 1")
    suspend fun getActivityOnce(id: String): ActivityEntity?
    @Upsert suspend fun upsertActivity(activity: ActivityEntity)
    @Upsert suspend fun upsertActivities(activities: List<ActivityEntity>)
    @Query("UPDATE activities SET syncStatus = 'PENDING_DELETE', updatedAt = CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE id = :id")
    suspend fun deleteActivity(id: String)
    @Query("SELECT * FROM activity_logs WHERE syncStatus != 'PENDING_DELETE' ORDER BY dateIso, scheduledTime, id")
    fun observeAllLogs(): Flow<List<ActivityLogEntity>>
    @Query("SELECT * FROM activity_logs WHERE syncStatus != 'PENDING_DELETE' ORDER BY dateIso, scheduledTime, id")
    suspend fun getAllLogsOnce(): List<ActivityLogEntity>
    @Query("SELECT * FROM activity_logs WHERE activityId = :activityId AND dateIso = :dateIso LIMIT 1")
    suspend fun getLogForActivityOnDate(activityId: String, dateIso: String): ActivityLogEntity?
    @Upsert suspend fun upsertActivityLog(log: ActivityLogEntity)
    @Query("SELECT * FROM activities WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncActivities(): List<ActivityEntity>
    @Query("SELECT * FROM activity_logs WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncLogs(): List<ActivityLogEntity>
}
