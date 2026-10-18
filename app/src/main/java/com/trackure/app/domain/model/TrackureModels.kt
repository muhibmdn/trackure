package com.trackure.app.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class SyncStatus { SYNCED, PENDING_UPLOAD, PENDING_DELETE }

@Serializable
enum class RecurrenceFreq { DAILY, WEEKLY, MONTHLY, YEARLY, SPECIFIC_DATE, CUSTOM }

@Serializable
enum class EndConditionType { NEVER, UNTIL_DATE, AFTER_OCCURRENCES }

// Timestamps are UTC ISO-8601 strings; dates are ISO local dates and times are HH:mm.
@Serializable
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val profileUrlOrEmail: String,
    val motto: String,
    val avatarUrl: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val updatedAt: String = Instant.now().toString(),
)

@Serializable
data class ActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String? = null,
    val scheduledTime: String,
    val fullScreenLockscreenTrigger: Boolean = false,
    val loopActivity: Boolean = true,
    val frequency: RecurrenceFreq = RecurrenceFreq.DAILY,
    val repeatDaysOfWeek: Set<Int> = emptySet(), // ISO weekday: Monday = 1, Sunday = 7.
    val intervalCount: Int = 1,
    val endConditionType: EndConditionType = EndConditionType.NEVER,
    val endDate: String? = null,
    val endOccurrences: Int? = null,
    val specificDate: String? = null, // Independent of recurrence endDate.
    val repeatOnSpecificDate: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val updatedAt: String = Instant.now().toString(),
)

@Serializable
data class ActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val activityId: String,
    val activityTitle: String,
    val category: String? = null,
    val scheduledTime: String,
    val dateIso: String,
    val isCompleted: Boolean = false,
    val completedHour: String? = null,
    val completedMinute: String? = null,
    val note: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,
    val updatedAt: String = Instant.now().toString(),
)

@Serializable
data class StreakDay(
    val dateIso: String,
    val level: Int,
    val id: String = dateIso,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: String = Instant.now().toString(),
) {
    init { require(level in 0..3) }
}

@Serializable
data class ExportHistoryConfig(
    val includeActivityName: Boolean = true,
    val includeCompletedTime: Boolean = true,
    val includeNote: Boolean = false,
)
