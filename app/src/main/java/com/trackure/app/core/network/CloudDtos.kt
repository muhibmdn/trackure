package com.trackure.app.core.network

import com.trackure.app.core.database.entity.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.Instant

// Only PostgreSQL columns belong in wire DTOs; Room bookkeeping stays on device.
@Serializable
internal data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("profile_url_or_email") val profileUrlOrEmail: String,
    @SerialName("motto") val motto: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("updated_at") val updatedAt: Long,
) {
    fun toEntity() = UserProfileEntity(id, name, profileUrlOrEmail, motto, avatarUrl, updatedAt, "SYNCED")
}

@Serializable
internal data class ActivityDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String,
    @SerialName("category") val category: String?,
    @SerialName("scheduled_time") val scheduledTime: String,
    @SerialName("full_screen_lockscreen_trigger") val fullScreenLockscreenTrigger: Boolean,
    @SerialName("loop_activity") val loopActivity: Boolean,
    @SerialName("frequency") val frequency: String,
    @SerialName("repeat_days_of_week_csv") val repeatDaysOfWeekCsv: String,
    @SerialName("specific_date_iso") val specificDateIso: String?,
    @SerialName("interval_count") val intervalCount: Int,
    @SerialName("end_condition_type") val endConditionType: String,
    @SerialName("end_date") val endDate: String?,
    @SerialName("end_occurrences") val endOccurrences: Int?,
    @SerialName("updated_at") val updatedAt: Long,
) {
    fun toEntity() = ActivityEntity(id, title, category, scheduledTime.take(5), fullScreenLockscreenTrigger,
        loopActivity, frequency, repeatDaysOfWeekCsv, specificDateIso, intervalCount, endConditionType,
        endDate, endOccurrences, "SYNCED", updatedAt,
        startDateIso = specificDateIso ?: Instant.ofEpochMilli(updatedAt).atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString(), ownerId = userId)
}

@Serializable
internal data class ActivityLogDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("activity_id") val activityId: String,
    @SerialName("activity_title") val activityTitle: String,
    @SerialName("category") val category: String?,
    @SerialName("scheduled_time") val scheduledTime: String,
    @SerialName("date_iso") val dateIso: String,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("completed_hour") val completedHour: String?,
    @SerialName("completed_minute") val completedMinute: String?,
    @SerialName("note") val note: String?,
    @SerialName("updated_at") val updatedAt: Long,
) {
    fun toEntity() = ActivityLogEntity(id, activityId, activityTitle, category, scheduledTime.take(5), dateIso,
        isCompleted, completedHour, completedMinute, note, "SYNCED", updatedAt, userId)
}

internal fun UserProfileEntity.toCloud() = ProfileDto(id, name, profileUrlOrEmail, motto, avatarUrl, updatedAt)
internal fun ActivityEntity.toCloud() = ActivityDto(id, ownerId, title, category, scheduledTime, fullScreenLockscreenTrigger,
    loopActivity, frequency, repeatDaysOfWeekCsv, specificDateIso, intervalCount, endConditionType, endDate,
    endOccurrences, updatedAt)
internal fun ActivityLogEntity.toCloud() = ActivityLogDto(id, ownerId, activityId, activityTitle, category, scheduledTime,
    dateIso, isCompleted, completedHour, completedMinute, note, updatedAt)
