package com.trackure.app

import biweekly.Biweekly
import com.trackure.app.core.util.IcsCalendarHelper
import com.trackure.app.core.util.ScheduleEvaluator
import com.trackure.app.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class IcsCalendarHelperTest {
    private val helper = IcsCalendarHelper(ZoneId.of("Asia/Taipei"))
    private fun calendar(event: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Test//EN\r\nBEGIN:VEVENT\r\nUID:test@example.com\r\nDTSTAMP:20261019T000000Z\r\n$event\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

    @Test fun importsEscapedFoldedTextAndWeeklyRule() {
        val item = helper.parseIcsToActivities(calendar("SUMMARY:Study\\, notes\\;\r\n  continued\\nNext\r\nDTSTART:20261019T071500\r\nCATEGORIES:Study\\, Work\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;INTERVAL=2;COUNT=10")).single()
        assertEquals("Study, notes; continued\nNext", item.title)
        assertEquals("Study, Work", item.category)
        assertEquals("07:15", item.scheduledTime)
        assertEquals("2026-10-19", item.startDateIso)
        assertEquals(setOf(1, 3, 5), item.repeatDaysOfWeek)
        assertEquals(2, item.intervalCount)
        assertEquals(10, item.endOccurrences)
        assertEquals(EndConditionType.AFTER_OCCURRENCES, item.endConditionType)
        assertTrue(item.fullScreenLockscreenTrigger)
    }

    @Test fun convertsUtcAndTzidAndSupportsDateOnly() {
        val utc = helper.parseIcsToActivities(calendar("SUMMARY:UTC\r\nDTSTART:20261019T233000Z")).single()
        assertEquals("07:30", utc.scheduledTime)
        assertEquals("2026-10-20", utc.specificDate)
        assertFalse(utc.loopActivity)
        val zoned = helper.parseIcsToActivities(calendar("SUMMARY:Zoned\r\nDTSTART;TZID=Asia/Taipei:20261019T071500")).single()
        assertEquals("07:15", zoned.scheduledTime)
        val allDay = helper.parseIcsToActivities(calendar("SUMMARY:Holiday\r\nDTSTART;VALUE=DATE:20261019")).single()
        assertEquals("00:00", allDay.scheduledTime)
        assertEquals("2026-10-19", allDay.specificDate)
    }

    @Test fun untilBeforeScheduledTimeExcludesThatDate() {
        val item = helper.parseIcsToActivities(calendar("SUMMARY:Morning\r\nDTSTART:20261019T071500\r\nRRULE:FREQ=DAILY;UNTIL=20261021T230000Z")).single()
        assertEquals("2026-10-21", item.endDate)
    }

    @Test fun rejectsUnsupportedRecurrencesAndTruncatedOrMalformedInput() {
        listOf("FREQ=MONTHLY;BYDAY=1MO", "FREQ=WEEKLY;BYDAY=MO;INTERVAL=0", "FREQ=DAILY;COUNT=2;UNTIL=20261020T000000Z",
            "FREQ=HOURLY", "FREQ=DAILY;BYHOUR=9,10").forEach { rule ->
            assertTrue(rule, runCatching { helper.parseIcsToActivities(calendar("SUMMARY:Invalid\r\nDTSTART:20261019T071500\r\nRRULE:$rule")) }.isFailure)
        }
        assertTrue(runCatching { helper.parseIcsToActivities("not a calendar") }.isFailure)
        assertTrue(runCatching { helper.parseIcsToActivities(calendar("SUMMARY:Missing date")) }.isFailure)
        assertTrue(runCatching { helper.parseIcsToActivities(calendar("SUMMARY:Truncated\r\nDTSTART:20261019T071500").substringBefore("END:VEVENT")) }.isFailure)
        assertTrue(runCatching { helper.parseIcsToActivities(calendar("SUMMARY:Except\r\nDTSTART:20261019T071500\r\nEXDATE:20261020T071500")) }.isFailure)
    }

    @Test fun repeatedImportHasStableIdentity() {
        val input = calendar("SUMMARY:Test\r\nDTSTART:20261019T071500")
        assertEquals(helper.parseIcsToActivities(input).single().id, helper.parseIcsToActivities(input).single().id)
    }

    @Test fun floatingRecurringTimeRemainsWallClockAcrossDstGap() {
        val localHelper = IcsCalendarHelper(ZoneId.of("America/New_York"))
        val item = localHelper.parseIcsToActivities(calendar("SUMMARY:Daily\r\nDTSTART:20260308T023000\r\nRRULE:FREQ=DAILY")).single()
        assertEquals("02:30", item.scheduledTime)
        assertTrue(runCatching { helper.parseIcsToActivities(calendar("SUMMARY:Unknown zone\r\nDTSTART;TZID=Invalid/Zone:20261019T071500")) }.isFailure)
    }

    @Test fun exportedCalendarHasAlarmAndRoundTripsSchedules() {
        val base = ActivityItem(id = "roundtrip", title = "中文, Study; \\ notes\n" + "Long title ".repeat(20), category = "Study, Work",
            scheduledTime = "07:15", startDateIso = "2026-10-19", fullScreenLockscreenTrigger = true)
        val variants = listOf(
            base.copy(loopActivity = false, specificDate = "2026-10-25"),
            base.copy(frequency = RecurrenceFreq.DAILY, repeatDaysOfWeek = setOf(1, 3, 5), intervalCount = 2, endConditionType = EndConditionType.AFTER_OCCURRENCES, endOccurrences = 10),
            base.copy(frequency = RecurrenceFreq.WEEKLY, repeatDaysOfWeek = setOf(3, 5), intervalCount = 2),
            base.copy(frequency = RecurrenceFreq.MONTHLY, endConditionType = EndConditionType.UNTIL_DATE, endDate = "2027-04-19"),
            base.copy(frequency = RecurrenceFreq.YEARLY),
            base.copy(frequency = RecurrenceFreq.CUSTOM, repeatDaysOfWeek = setOf(1, 4)),
            base.copy(frequency = RecurrenceFreq.SPECIFIC_DATE, specificDate = "2026-10-25", repeatOnSpecificDate = true))
        variants.forEach { original ->
            val output = helper.generateIcsFromActivities(listOf(original))
            assertTrue(output.contains("PRODID:-//Trackure//One Sure Step Everyday//EN"))
            assertTrue(output.contains("TRIGGER:-PT7M") || output.contains("TRIGGER;RELATED=START:-PT7M"))
            assertTrue(output.contains("ACTION:DISPLAY"))
            assertTrue(output.endsWith("\r\n"))
            assertNotNull(Biweekly.parse(output).first())
            val imported = helper.parseIcsToActivities(output).single()
            assertEquals(original.id, imported.id)
            assertEquals(original.title.trim(), imported.title)
            assertEquals(original.category, imported.category)
            assertEquals(original.scheduledTime, imported.scheduledTime)
            val evaluator = ScheduleEvaluator()
            repeat(800) { offset ->
                val date = LocalDate.parse("2026-10-19").plusDays(offset.toLong())
                assertEquals("${original.frequency} $date", evaluator.isActivityScheduledForDate(original, date), evaluator.isActivityScheduledForDate(imported, date))
            }
        }
    }
}
