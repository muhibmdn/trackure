package com.trackure.app.core.util

import java.time.Duration
import java.time.Instant

internal val reminderOffsets = listOf(30, 15, 10, 5, 0)

internal fun futureReminderStages(eventAt: Long, now: Long): List<Pair<Int, Long>> =
    reminderOffsets.map { it to eventAt - it * 60_000L }.filter { it.second > now }

internal fun alarmCountdownText(eventAt: Long, now: Long): String {
    val minutes = Duration.between(Instant.ofEpochMilli(now), Instant.ofEpochMilli(eventAt)).toMinutes()
    return when {
        minutes > 0 -> "Dimulai dalam $minutes mnt"
        minutes == 0L -> "Jadwal dimulai sekarang"
        else -> "Sedang berlangsung (${kotlin.math.abs(minutes)} mnt lalu)"
    }
}
