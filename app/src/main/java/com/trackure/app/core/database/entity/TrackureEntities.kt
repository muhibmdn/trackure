package com.trackure.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = "local_user",
    val name: String,
    val profileUrlOrEmail: String,
    val motto: String,
    val avatarUrl: String?,
    val updatedAt: Long,
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
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
)

// History survives activity deletion; general journal entries have no parent activity.
@Entity(tableName = "activity_logs", indices = [Index(value = ["activityId", "dateIso"], unique = true)])
data class ActivityLogEntity(
    @PrimaryKey val id: String,
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
)
