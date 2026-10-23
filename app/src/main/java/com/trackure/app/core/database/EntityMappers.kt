package com.trackure.app.core.database

import com.trackure.app.core.database.entity.*
import com.trackure.app.domain.model.*
import java.time.Instant

fun UserProfile.toEntity() = UserProfileEntity(id, name, profileUrlOrEmail, motto, avatarUrl, Instant.parse(updatedAt).toEpochMilli(), syncStatus.name)
fun UserProfileEntity.toDomain() = UserProfile(id, name, profileUrlOrEmail, motto, avatarUrl, syncStatus = SyncStatus.valueOf(syncStatus), updatedAt = Instant.ofEpochMilli(updatedAt).toString())
fun ActivityItem.toEntity() = ActivityEntity(id, title, category, scheduledTime, fullScreenLockscreenTrigger,
    loopActivity, frequency.name, Converters().setToString(repeatDaysOfWeek), specificDate, intervalCount,
    endConditionType.name, endDate, endOccurrences, syncStatus.name, Instant.parse(updatedAt).toEpochMilli(), startDateIso, repeatOnSpecificDate)
fun ActivityEntity.toDomain() = ActivityItem(id = id, title = title, category = category, scheduledTime = scheduledTime,
    fullScreenLockscreenTrigger = fullScreenLockscreenTrigger, loopActivity = loopActivity,
    frequency = RecurrenceFreq.valueOf(frequency), repeatDaysOfWeek = Converters().stringToSet(repeatDaysOfWeekCsv),
    intervalCount = intervalCount, endConditionType = EndConditionType.valueOf(endConditionType), endDate = endDate,
    endOccurrences = endOccurrences, specificDate = specificDateIso, repeatOnSpecificDate = repeatOnSpecificDate,
    startDateIso = startDateIso, syncStatus = SyncStatus.valueOf(syncStatus), updatedAt = Instant.ofEpochMilli(updatedAt).toString())
fun ActivityLog.toEntity() = ActivityLogEntity(id, activityId, activityTitle, category, scheduledTime, dateIso,
    isCompleted, completedHour, completedMinute, note, syncStatus.name, Instant.parse(updatedAt).toEpochMilli())
fun ActivityLogEntity.toDomain() = ActivityLog(id, activityId, activityTitle, category, scheduledTime, dateIso,
    isCompleted, completedHour, completedMinute, note, SyncStatus.valueOf(syncStatus), Instant.ofEpochMilli(updatedAt).toString())
