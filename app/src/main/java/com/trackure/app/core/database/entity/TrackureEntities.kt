package com.trackure.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable

@Entity(tableName = "active_account")
data class ActiveAccountEntity(@PrimaryKey val slot: Int = 0, val userId: String = "local_user")

@Entity(tableName = "user_profile")
@Serializable
data class UserProfileEntity(
    @PrimaryKey val id: String = "local_user",
    val name: String,
    val profileUrlOrEmail: String,
    val motto: String,
    val avatarUrl: String?,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'PENDING_UPLOAD'") val syncStatus: String = "PENDING_UPLOAD",
)

@Entity(tableName = "activities", primaryKeys = ["id", "ownerId"])
@Serializable
data class ActivityEntity(
    val id: String,
    val title: String,
    val category: String?,
    val scheduledTime: String,
    val fullScreenLockscreenTrigger: Boolean,
    val loopActivity: Boolean,
    val frequency: String,
    val repeatDaysOfWeekCsv: String,
    val specificDateIso: String?,
    val intervalCount: Int,
    val endConditionType: String,
    val endDate: String?,
    val endOccurrences: Int?,
    val syncStatus: String,
    val updatedAt: Long,
    // Stable recurrence anchor: edits must never reset interval or occurrence counting.
    val startDateIso: String = java.time.LocalDate.now().toString(),
    val repeatOnSpecificDate: Boolean = false,
    @ColumnInfo(defaultValue = "'local_user'") val ownerId: String = "local_user",
)

// History survives activity deletion; general journal entries have no parent activity.
@Entity(tableName = "activity_logs", primaryKeys = ["id", "ownerId"], indices = [Index(value = ["ownerId", "activityId", "dateIso"], unique = true)])
@Serializable
data class ActivityLogEntity(
    val id: String,
    val activityId: String,
    val activityTitle: String,
    val category: String?,
    val scheduledTime: String,
    val dateIso: String,
    val isCompleted: Boolean,
    val completedHour: String?,
    val completedMinute: String?,
    val note: String?,
    val syncStatus: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'local_user'") val ownerId: String = "local_user",
)
