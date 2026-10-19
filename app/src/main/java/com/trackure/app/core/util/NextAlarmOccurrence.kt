package com.trackure.app.core.util

import com.trackure.app.domain.model.*
import java.time.*

/** Local wall-clock recurrence; atZone resolves DST gaps forward and overlaps to the first offset. */
internal fun nextAlarmOccurrence(activity: ActivityItem, now: ZonedDateTime): ZonedDateTime? {
    if (!activity.fullScreenLockscreenTrigger || activity.syncStatus == SyncStatus.PENDING_DELETE) return null
    require(activity.intervalCount > 0)
    val time = LocalTime.parse(activity.scheduledTime)
    val anchor = LocalDate.parse(if (!activity.loopActivity || activity.frequency == RecurrenceFreq.SPECIFIC_DATE)
        activity.specificDate ?: activity.startDateIso else activity.startDateIso)
    val once = !activity.loopActivity || (activity.frequency == RecurrenceFreq.SPECIFIC_DATE && !activity.repeatOnSpecificDate)
    val counted = activity.endConditionType == EndConditionType.AFTER_OCCURRENCES
    var date = if (counted) anchor else maxOf(anchor, now.toLocalDate())
    val limit = if (once) anchor else maxOf(anchor, now.toLocalDate()).plusYears(8L * activity.intervalCount)
    val end = if (activity.endConditionType == EndConditionType.UNTIL_DATE)
        minOf(limit, LocalDate.parse(requireNotNull(activity.endDate))) else limit
    val evaluator = ScheduleEvaluator()
    val rule = if (counted) activity.copy(endConditionType = EndConditionType.NEVER) else activity
    var occurrences = 0
    while (date <= end) {
        if (evaluator.isActivityScheduledForDate(rule, date)) {
            if (counted && ++occurrences > (activity.endOccurrences ?: 0)) return null
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.toInstant() > now.toInstant()) return candidate
        }
        date = date.plusDays(1)
    }
    return null
}
