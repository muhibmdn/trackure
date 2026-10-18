package com.trackure.app.core.util

import com.trackure.app.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** ISO weeks start Monday. CUSTOM uses selected weekdays every N weeks, or every N days
 * when no weekdays are selected. Invalid monthly/leap-day dates are skipped, never clamped.
 * UNTIL_DATE is inclusive; AFTER_OCCURRENCES counts scheduled dates, not completed logs.
 */
class ScheduleEvaluator {
    fun isActivityScheduledForDate(activity: ActivityItem, targetDate: LocalDate): Boolean {
        if (activity.syncStatus == SyncStatus.PENDING_DELETE || activity.intervalCount < 1) return false
        val start = LocalDate.parse(activity.startDateIso)
        val anchor = if (activity.frequency == RecurrenceFreq.SPECIFIC_DATE || !activity.loopActivity)
            activity.specificDate?.let(LocalDate::parse) ?: start else start
        if (targetDate < anchor) return false
        if (activity.endConditionType == EndConditionType.UNTIL_DATE &&
            targetDate > LocalDate.parse(activity.endDate ?: return false)) return false
        fun matches(date: LocalDate): Boolean {
            if (!activity.loopActivity) return date == anchor
            val interval = activity.intervalCount.toLong()
            val days = ChronoUnit.DAYS.between(anchor, date)
            val weekdays = activity.repeatDaysOfWeek
            fun weekly(): Boolean {
                val weeks = ChronoUnit.WEEKS.between(anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                return weeks % interval == 0L && date.dayOfWeek.value in weekdays.ifEmpty { setOf(anchor.dayOfWeek.value) }
            }
            fun yearly() = (date.year.toLong() - anchor.year) % interval == 0L && date.month == anchor.month && date.dayOfMonth == anchor.dayOfMonth
            return when (activity.frequency) {
                RecurrenceFreq.DAILY -> days % interval == 0L && (weekdays.isEmpty() || date.dayOfWeek.value in weekdays)
                RecurrenceFreq.WEEKLY -> weekly()
                RecurrenceFreq.MONTHLY -> ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1)) % interval == 0L && date.dayOfMonth == anchor.dayOfMonth
                RecurrenceFreq.YEARLY -> yearly()
                RecurrenceFreq.SPECIFIC_DATE -> if (activity.repeatOnSpecificDate) yearly() else date == anchor
                RecurrenceFreq.CUSTOM -> if (weekdays.isEmpty()) days % interval == 0L else weekly()
            }
        }
        if (!matches(targetDate)) return false
        if (activity.endConditionType != EndConditionType.AFTER_OCCURRENCES) return true
        val limit = activity.endOccurrences ?: return false
        if (limit < 1) return false
        var count = 0
        var date = anchor
        while (date < targetDate) {
            if (matches(date) && ++count >= limit) return false
            date = date.plusDays(1)
        }
        return true
    }

    /** 112 Monday-to-Sunday cells ending with the current week; future cells remain empty.
     * 100% means all recorded task rows for that day (logs alone cannot infer missing tasks).
     * An unfinished today preserves yesterday's streak. Journal notes never count as tasks.
     */
    fun calculateStreakMatrix(logs: List<ActivityLog>, today: LocalDate = LocalDate.now()): Pair<Int, List<StreakDay>> {
        val days = logs.filter { it.activityId != GeneralDailyNote.ACTIVITY_ID }
            .groupBy { it.activityId to it.dateIso }.values.map { versions -> versions.maxBy { it.updatedAt } }
            .filter { it.syncStatus != SyncStatus.PENDING_DELETE }
            .groupBy { LocalDate.parse(it.dateIso) }
        val active = days.filter { (date, rows) -> date <= today && rows.any { it.isCompleted } }.keys
        var cursor = if (today in active) today else today.minusDays(1)
        var streak = 0
        while (cursor in active) { streak++; cursor = cursor.minusDays(1) }
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(15)
        val matrix = List(112) { index ->
            val date = start.plusDays(index.toLong())
            val rows = days[date].orEmpty()
            val completed = rows.count { it.isCompleted }
            val level = when {
                date > today || completed == 0 -> 0
                completed >= 5 || completed == rows.size -> 3
                completed >= 3 -> 2
                else -> 1
            }
            StreakDay(date.toString(), level, updatedAt = "${today}T00:00:00Z")
        }
        return streak to matrix
    }
}
