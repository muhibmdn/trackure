package com.trackure.app

import com.trackure.app.core.util.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlarmStagesTest {
    private val event = Instant.parse("2026-10-24T00:10:00Z").toEpochMilli()

    @Test fun allStagesCrossMidnightAndPastStagesAreSkipped() {
        val stages = futureReminderStages(event, event - 31 * 60_000L)
        assertEquals(listOf(30, 15, 10, 5, 0), stages.map { it.first })
        assertEquals(Instant.parse("2026-10-23T23:40:00Z").toEpochMilli(), stages.first().second)
        assertEquals(listOf(10, 5, 0), futureReminderStages(event, event - 15 * 60_000L).map { it.first })
        assertTrue(futureReminderStages(event, event).isEmpty())
    }

    @Test fun countdownTransitionsBeforeAtAndAfterEvent() {
        assertEquals("Dimulai dalam 29 mnt", alarmCountdownText(event, event - 29 * 60_000L))
        assertEquals("Dimulai dalam 28 mnt", alarmCountdownText(event, event - 28 * 60_000L))
        assertEquals("Jadwal dimulai sekarang", alarmCountdownText(event, event))
        assertEquals("Sedang berlangsung (15 mnt lalu)", alarmCountdownText(event, event + 15 * 60_000L))
    }
}
