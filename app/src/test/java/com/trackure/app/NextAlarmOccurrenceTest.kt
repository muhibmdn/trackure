package com.trackure.app

import com.trackure.app.core.util.nextAlarmOccurrence
import com.trackure.app.domain.model.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class NextAlarmOccurrenceTest {
    private fun item() = ActivityItem(id = "alarm", title = "Test", scheduledTime = "07:15",
        fullScreenLockscreenTrigger = true, startDateIso = "2026-10-19")
    private fun now(value: String) = ZonedDateTime.parse(value)

    @Test fun rollsToTomorrowAfterTodaysAlarm() {
        assertEquals("2026-10-20T07:15+08:00", nextAlarmOccurrence(item(), now("2026-10-19T07:15+08:00")).toString())
    }

    @Test fun includesFutureOneOffAndExcludesExpiredOneOff() {
        val once = item().copy(loopActivity = false, specificDate = "2026-10-21")
        assertEquals("2026-10-21T07:15+08:00", nextAlarmOccurrence(once, now("2026-10-19T08:00+08:00")).toString())
        assertNull(nextAlarmOccurrence(once, now("2026-10-21T08:00+08:00")))
    }

    @Test fun respectsWeekdaysAndInclusiveEndDate() {
        val weekly = item().copy(frequency = RecurrenceFreq.WEEKLY, repeatDaysOfWeek = setOf(3),
            endConditionType = EndConditionType.UNTIL_DATE, endDate = "2026-10-21")
        assertEquals("2026-10-21T07:15+08:00", nextAlarmOccurrence(weekly, now("2026-10-19T08:00+08:00")).toString())
        assertNull(nextAlarmOccurrence(weekly, now("2026-10-21T08:00+08:00")))
    }

    @Test fun occurrenceLimitCountsPastDates() {
        val limited = item().copy(endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 2)
        assertNotNull(nextAlarmOccurrence(limited, now("2026-10-20T07:00+08:00")))
        assertNull(nextAlarmOccurrence(limited, now("2026-10-20T08:00+08:00")))
    }

    @Test fun skipsInvalidMonthlyDate() {
        val monthly = item().copy(startDateIso = "2026-01-31", frequency = RecurrenceFreq.MONTHLY)
        assertEquals("2026-03-31T07:15+08:00", nextAlarmOccurrence(monthly, now("2026-02-01T00:00+08:00")).toString())
    }

    @Test fun resolvesSpringDstGapAndDoesNotDuplicateFallOverlap() {
        val dst = item().copy(startDateIso = "2026-01-01", scheduledTime = "02:30")
        assertEquals("2026-03-08T03:30-04:00[America/New_York]",
            nextAlarmOccurrence(dst, now("2026-03-08T00:00-05:00[America/New_York]")).toString())
        assertEquals("2026-11-02T01:30-05:00[America/New_York]",
            nextAlarmOccurrence(dst.copy(scheduledTime = "01:30"), now("2026-11-01T01:45-04:00[America/New_York]")).toString())
    }

    @Test fun disabledAndDeletedDoNotSchedule() {
        val date = now("2026-10-19T07:00+08:00")
        assertNull(nextAlarmOccurrence(item().copy(fullScreenLockscreenTrigger = false), date))
        assertNull(nextAlarmOccurrence(item().copy(syncStatus = SyncStatus.PENDING_DELETE), date))
    }
}
