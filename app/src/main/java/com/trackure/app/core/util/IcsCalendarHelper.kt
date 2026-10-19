package com.trackure.app.core.util

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.io.text.ICalReader
import biweekly.property.Trigger
import biweekly.util.DateTimeComponents
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.trackure.app.domain.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.UUID

/** Trackure stores minute-precision local schedules, not arbitrary RFC recurrence sets. */
class IcsCalendarHelper(private val zone: ZoneId = ZoneId.systemDefault()) {
    fun parseIcsToActivities(rawIcsContent: String): List<ActivityItem> {
        require(rawIcsContent.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "Calendar file exceeds 2 MB" }
        val content = rawIcsContent.removePrefix("\uFEFF")
        // Biweekly tolerates truncated components; imports must instead be atomic and complete.
        val stack = mutableListOf<String>()
        content.lineSequence().filterNot { it.startsWith(' ') || it.startsWith('\t') }.forEach { line ->
            val normalized = line.trimEnd().uppercase()
            when {
                normalized.startsWith("BEGIN:") -> stack.add(normalized.substringAfter(':'))
                normalized.startsWith("END:") -> require(stack.removeLastOrNull() == normalized.substringAfter(':')) { "Unbalanced calendar component" }
            }
        }
        require(stack.isEmpty()) { "Calendar file is incomplete" }
        val result = mutableListOf<ActivityItem>()
        ICalReader(content).use { reader ->
            reader.defaultTimezone = TimeZone.getTimeZone(zone)
            var calendars = 0
            while (true) {
                val calendar = reader.readNext() ?: break
                calendars++
                // Warning 37 is the valid IANA-TZID fallback when VTIMEZONE is omitted.
                // Resolve/validate the identifier below instead of silently accepting unknown zones.
                val errors = reader.warnings.filterNot { it.code == 37 }
                require(errors.isEmpty()) { "Calendar contains invalid fields: ${errors.firstOrNull()}" }
                calendar.events.forEach { event ->
                    require(result.size < 5_000) { "Calendar contains too many events (maximum 5000)" }
                    result += parseEvent(calendar, event)
                }
            }
            require(calendars > 0) { "Choose a valid VCALENDAR .ics file" }
        }
        require(result.isNotEmpty()) { "Calendar contains no activities" }
        require(result.map { it.id }.distinct().size == result.size) { "Calendar contains duplicate event UIDs" }
        return result
    }

    private fun parseEvent(calendar: ICalendar, event: VEvent): ActivityItem {
        val title = event.summary?.value?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim().orEmpty()
        require(title.isNotEmpty()) { "An event is missing SUMMARY" }
        fun supported(condition: Boolean, detail: String) = require(condition) { "$title: $detail" }
        supported(event.getProperties(biweekly.property.RecurrenceRule::class.java).size <= 1 &&
            event.getProperties(biweekly.property.DateStart::class.java).size == 1, "duplicate DTSTART or RRULE properties")
        supported(event.recurrenceId == null && event.exceptionDates.isEmpty() && event.exceptionRules.isEmpty() && event.recurrenceDates.isEmpty(),
            "recurrence exceptions (RECURRENCE-ID, EXDATE, RDATE, EXRULE) are not supported")
        supported(event.status?.value != "CANCELLED", "cancelled events cannot be imported")
        val start = requireNotNull(event.dateStart) { "$title: missing DTSTART" }
        val value = requireNotNull(start.value) { "$title: invalid DTSTART" }
        val raw = value.rawComponents
        val wall = LocalDate.of(raw.year, raw.month, raw.date).atTime(if (raw.hasTime()) LocalTime.of(raw.hour, raw.minute, raw.second) else LocalTime.MIDNIGHT)
        supported(wall.second == 0, "second-precision schedules are not supported")
        val assignment = calendar.timezoneInfo.getTimezone(start)
        val tzid = start.getParameter("TZID")
        val sourceZone = when {
            raw.isUtc -> ZoneOffset.UTC
            assignment != null -> null // Biweekly has resolved the supplied VTIMEZONE.
            tzid != null -> try { ZoneId.of(tzid) } catch (_: DateTimeException) { error("$title: unknown timezone $tzid") }
            else -> zone
        }
        val local = when {
            !raw.hasTime() -> wall
            sourceZone == zone || assignment?.timeZone?.hasSameRules(TimeZone.getTimeZone(zone)) == true -> wall
            assignment != null -> value.toInstant().atZone(zone).toLocalDateTime()
            else -> wall.atZone(requireNotNull(sourceZone)).withZoneSameInstant(zone).toLocalDateTime()
        }
        val rule = event.recurrenceRule?.value
        var frequency = RecurrenceFreq.SPECIFIC_DATE
        var days = emptySet<Int>()
        var interval = 1
        var endType = EndConditionType.NEVER
        var endDate: String? = null
        var count: Int? = null
        if (rule != null) {
            val sourceTimeZone = assignment?.timeZone ?: TimeZone.getTimeZone(sourceZone)
            supported(sourceTimeZone.hasSameRules(TimeZone.getTimeZone(zone)) ||
                (!sourceTimeZone.useDaylightTime() && zone.rules.isFixedOffset),
                "recurring events in a different daylight-saving timezone cannot be represented as a local schedule")
            supported(local.toLocalDate() == wall.toLocalDate(), "timezone conversion moves recurrence to a different date")
            frequency = when (rule.frequency) {
                Frequency.DAILY -> RecurrenceFreq.DAILY
                Frequency.WEEKLY -> RecurrenceFreq.WEEKLY
                Frequency.MONTHLY -> RecurrenceFreq.MONTHLY
                Frequency.YEARLY -> RecurrenceFreq.YEARLY
                else -> error("$title: unsupported recurrence frequency ${rule.frequency}")
            }
            supported(rule.bySecond.isEmpty() && rule.byMinute.isEmpty() && rule.byHour.isEmpty() && rule.byYearDay.isEmpty() &&
                rule.byWeekNo.isEmpty() && rule.bySetPos.isEmpty() && rule.xRules.isEmpty(), "unsupported RRULE selectors")
            supported(rule.byMonthDay.isEmpty() || rule.byMonthDay == listOf(wall.dayOfMonth), "multiple/relative month days are not supported")
            supported(rule.byMonth.isEmpty() || (frequency == RecurrenceFreq.YEARLY && rule.byMonth == listOf(wall.monthValue)), "unsupported BYMONTH")
            supported(rule.byMonthDay.isEmpty() || frequency in setOf(RecurrenceFreq.MONTHLY, RecurrenceFreq.YEARLY), "unsupported BYMONTHDAY")
            supported(rule.byDay.all { it.num == null } && (rule.byDay.isEmpty() || frequency in setOf(RecurrenceFreq.DAILY, RecurrenceFreq.WEEKLY)),
                "monthly/yearly or ordinal BYDAY rules are not supported")
            interval = rule.interval ?: 1
            supported(interval > 0, "INTERVAL must be positive")
            supported(rule.workweekStarts == null || rule.workweekStarts == biweekly.util.DayOfWeek.MONDAY || interval == 1,
                "multi-week rules must use WKST=MO")
            days = rule.byDay.map { java.time.DayOfWeek.valueOf(it.day.name).value }.toSet()
            supported(rule.count == null || rule.until == null, "RRULE cannot contain both COUNT and UNTIL")
            rule.count?.let { supported(it > 0, "COUNT must be positive"); count = it; endType = EndConditionType.AFTER_OCCURRENCES }
            rule.until?.let { until ->
                val parts = until.rawComponents
                val cutoff = if (parts.isUtc) until.toInstant().atZone(zone).toLocalDateTime()
                    else LocalDate.of(parts.year, parts.month, parts.date).atTime(if (parts.hasTime()) LocalTime.of(parts.hour, parts.minute, parts.second) else LocalTime.MAX)
                endDate = (if (cutoff.toLocalTime() < local.toLocalTime()) cutoff.toLocalDate().minusDays(1) else cutoff.toLocalDate()).toString()
                endType = EndConditionType.UNTIL_DATE
            }
        }
        val uid = event.uid?.value?.takeIf { it.isNotBlank() } ?: "$title|$local|${event.categories}"
        val id = if (uid.startsWith("trackure-") && uid.endsWith("@trackure.local")) uid.removePrefix("trackure-").removeSuffix("@trackure.local")
            else UUID.nameUUIDFromBytes("ics:$uid".toByteArray(Charsets.UTF_8)).toString()
        return ActivityItem(id = id, title = title, category = event.categories.flatMap { it.values }.joinToString(", ").ifBlank { null },
            scheduledTime = local.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")), specificDate = local.toLocalDate().toString(),
            startDateIso = local.toLocalDate().toString(), fullScreenLockscreenTrigger = true, loopActivity = rule != null,
            frequency = frequency, repeatDaysOfWeek = days, intervalCount = interval, endConditionType = endType, endDate = endDate, endOccurrences = count)
    }

    fun generateIcsFromActivities(activities: List<ActivityItem>): String {
        val calendar = ICalendar().apply { setProductId("-//Trackure//One Sure Step Everyday//EN") }
        activities.filter { it.syncStatus != SyncStatus.PENDING_DELETE }.forEach { item ->
            require(item.title.isNotBlank() && item.intervalCount > 0) { "Invalid activity: ${item.title}" }
            val recurring = item.loopActivity && (item.frequency != RecurrenceFreq.SPECIFIC_DATE || item.repeatOnSpecificDate)
            var date = LocalDate.parse(if (!item.loopActivity || item.frequency == RecurrenceFreq.SPECIFIC_DATE) item.specificDate ?: item.startDateIso else item.startDateIso)
            if (recurring) {
                val evaluator = ScheduleEvaluator()
                val last = if (item.endConditionType == EndConditionType.UNTIL_DATE)
                    minOf(date.plusYears(8L * item.intervalCount), LocalDate.parse(requireNotNull(item.endDate)))
                    else date.plusYears(8L * item.intervalCount)
                while (date <= last && !evaluator.isActivityScheduledForDate(item, date)) date = date.plusDays(1)
                require(date <= last) { "${item.title}: recurrence has no occurrences" }
            }
            val time = LocalTime.parse(item.scheduledTime)
            fun floating(at: LocalDateTime) = ICalDate(DateTimeComponents(at.year, at.monthValue, at.dayOfMonth, at.hour, at.minute, at.second, false), true)
            val event = VEvent().apply {
                setUid("trackure-${item.id}@trackure.local")
                setSummary(item.title)
                setDateStart(floating(date.atTime(time)))
                item.category?.takeIf { it.isNotBlank() }?.let { addCategories(it) }
            }
            calendar.timezoneInfo.setFloating(event.dateStart, true)
            if (recurring) {
                val frequency = when (item.frequency) {
                    RecurrenceFreq.DAILY -> Frequency.DAILY
                    RecurrenceFreq.WEEKLY -> Frequency.WEEKLY
                    RecurrenceFreq.MONTHLY -> Frequency.MONTHLY
                    RecurrenceFreq.YEARLY, RecurrenceFreq.SPECIFIC_DATE -> Frequency.YEARLY
                    RecurrenceFreq.CUSTOM -> if (item.repeatDaysOfWeek.isEmpty()) Frequency.DAILY else Frequency.WEEKLY
                }
                val builder = Recurrence.Builder(frequency).interval(item.intervalCount)
                if (frequency in setOf(Frequency.DAILY, Frequency.WEEKLY) && item.repeatDaysOfWeek.isNotEmpty())
                    builder.byDay(item.repeatDaysOfWeek.sorted().map { biweekly.util.DayOfWeek.valueOf(java.time.DayOfWeek.of(it).name) })
                if (frequency == Frequency.WEEKLY) builder.workweekStarts(biweekly.util.DayOfWeek.MONDAY)
                when (item.endConditionType) {
                    EndConditionType.NEVER -> Unit
                    EndConditionType.AFTER_OCCURRENCES -> builder.count(requireNotNull(item.endOccurrences).also { require(it > 0) })
                    EndConditionType.UNTIL_DATE -> builder.until(floating(LocalDate.parse(requireNotNull(item.endDate)).atTime(23, 59, 59)))
                }
                event.setRecurrenceRule(builder.build())
                calendar.timezoneInfo.setFloating(event.recurrenceRule, true)
            }
            event.addAlarm(VAlarm.display(Trigger(biweekly.util.Duration.parse("-PT7M"), biweekly.parameter.Related.START), item.title))
            calendar.addEvent(event)
        }
        return Biweekly.write(calendar).go()
    }
}
