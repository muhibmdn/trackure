package com.trackure.app

import com.trackure.app.core.database.Converters
import com.trackure.app.core.database.toDomain
import com.trackure.app.core.database.toEntity
import com.trackure.app.core.util.ScheduleEvaluator
import com.trackure.app.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleEvaluatorTest {
    private val evaluator = ScheduleEvaluator()
    private val base = ActivityItem(title = "Task", scheduledTime = "09:00", startDateIso = "2024-01-01")
    private fun scheduled(item: ActivityItem, date: String) = evaluator.isActivityScheduledForDate(item, LocalDate.parse(date))

    @Test fun dailyIntervalsAndInclusiveEndDate() {
        val item = base.copy(intervalCount = 2, endConditionType = EndConditionType.UNTIL_DATE, endDate = "2024-01-05")
        assertFalse(scheduled(item, "2023-12-31"))
        assertTrue(scheduled(item, "2024-01-01"))
        assertFalse(scheduled(item, "2024-01-02"))
        assertTrue(scheduled(item, "2024-01-05"))
        assertFalse(scheduled(item, "2024-01-07"))
        assertTrue(scheduled(item.copy(updatedAt = "2025-01-01T00:00:00Z"), "2024-01-05"))
    }
    @Test fun weeklyAndCustomCountScheduledOccurrences() {
        for (frequency in listOf(RecurrenceFreq.WEEKLY, RecurrenceFreq.CUSTOM)) {
            val item = base.copy(frequency = frequency, repeatDaysOfWeek = setOf(1, 3), intervalCount = 2,
                endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 3)
            assertTrue(scheduled(item, "2024-01-03"))
            assertFalse(scheduled(item, "2024-01-08"))
            assertTrue(scheduled(item, "2024-01-15"))
            assertFalse(scheduled(item, "2024-01-17"))
        }
        assertTrue(scheduled(base.copy(frequency = RecurrenceFreq.CUSTOM, intervalCount = 3), "2024-01-04"))
    }
    @Test fun monthlySkipsMissingDaysAndYearlySkipsNonLeapYears() {
        val month = base.copy(startDateIso = "2024-01-31", frequency = RecurrenceFreq.MONTHLY,
            endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 2)
        assertFalse(scheduled(month, "2024-02-29"))
        assertTrue(scheduled(month, "2024-03-31"))
        assertFalse(scheduled(month, "2024-05-31"))
        val year = base.copy(startDateIso = "2024-02-29", frequency = RecurrenceFreq.YEARLY)
        assertFalse(scheduled(year, "2025-02-28"))
        assertTrue(scheduled(year, "2028-02-29"))
    }
    @Test fun specificDatesAndNonLoopingActivities() {
        val item = base.copy(frequency = RecurrenceFreq.SPECIFIC_DATE, specificDate = "2024-06-10")
        assertTrue(scheduled(item, "2024-06-10"))
        assertFalse(scheduled(item, "2025-06-10"))
        assertTrue(scheduled(item.copy(repeatOnSpecificDate = true), "2025-06-10"))
        assertFalse(scheduled(item.copy(loopActivity = false, repeatOnSpecificDate = true), "2025-06-10"))
        assertTrue(scheduled(base.copy(loopActivity = false), "2024-01-01"))
        assertFalse(scheduled(base.copy(loopActivity = false), "2024-01-02"))
        assertFalse(scheduled(base.copy(syncStatus = SyncStatus.PENDING_DELETE), "2024-01-01"))
    }
    private fun log(date: LocalDate, id: String, complete: Boolean = true) = ActivityLog(
        activityId = id, activityTitle = "Task", scheduledTime = "09:00", dateIso = date.toString(), isCompleted = complete)

    @Test fun streakIsNotLimitedToMatrixAndYesterdayStaysActive() {
        val today = LocalDate.of(2024, 6, 20)
        val logs = (1..150).map { log(today.minusDays(it.toLong()), "task") }
        val (streak, matrix) = evaluator.calculateStreakMatrix(logs, today)
        assertEquals(150, streak)
        assertEquals(112, matrix.size)
        assertEquals(1, LocalDate.parse(matrix.first().dateIso).dayOfWeek.value)
        assertEquals(7, LocalDate.parse(matrix.last().dateIso).dayOfWeek.value)
        assertTrue(matrix.filter { it.dateIso >= today.toString() }.all { it.level == 0 })
        assertEquals(0, evaluator.calculateStreakMatrix(emptyList(), today).first)
    }
    @Test fun levelsIgnoreJournalsDeletedRowsAndFutureCompletions() {
        val today = LocalDate.of(2024, 6, 20)
        fun level(completed: Int, total: Int): Int {
            val logs = (1..total).map { log(today, "$it", it <= completed) }
            return evaluator.calculateStreakMatrix(logs, today).second.single { it.dateIso == today.toString() }.level
        }
        assertEquals(0, level(0, 5)); assertEquals(1, level(2, 5))
        assertEquals(2, level(3, 5)); assertEquals(3, level(5, 6)); assertEquals(3, level(1, 1))
        val logs = listOf(log(today, GeneralDailyNote.ACTIVITY_ID),
            log(today, "deleted").copy(syncStatus = SyncStatus.PENDING_DELETE), log(today.plusDays(1), "future"))
        assertEquals(0, evaluator.calculateStreakMatrix(logs, today).first)
        assertTrue(evaluator.calculateStreakMatrix(logs, today).second.all { it.level == 0 })
        val old = log(today, "a").copy(updatedAt = "2024-06-20T00:00:00Z")
        assertEquals(0, evaluator.calculateStreakMatrix(listOf(old, old.copy(isCompleted = false, updatedAt = "2024-06-20T01:00:00Z")), today).first)
    }
    @Test fun convertersAndActivityMappingPreserveRecurrence() {
        val converters = Converters()
        assertEquals("1,3,7", converters.setToString(setOf(7, 1, 3)))
        assertEquals(emptySet<Int>(), converters.stringToSet(""))
        assertEquals(setOf(1, 3, 7), converters.stringToSet("1,3,7"))
        RecurrenceFreq.entries.forEach { assertEquals(it, converters.stringToFrequency(converters.frequencyToString(it))) }
        EndConditionType.entries.forEach { assertEquals(it, converters.stringToEndCondition(converters.endConditionToString(it))) }
        val item = base.copy(repeatOnSpecificDate = true, specificDate = "2024-08-01", repeatDaysOfWeek = setOf(1, 5), updatedAt = "2024-01-01T00:00:00Z")
        assertEquals(item, item.toEntity().toDomain())
    }
}
