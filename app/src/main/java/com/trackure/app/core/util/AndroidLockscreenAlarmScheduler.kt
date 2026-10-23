package com.trackure.app.core.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.trackure.app.domain.model.ActivityItem
import java.time.ZonedDateTime

class AndroidLockscreenAlarmScheduler(context: Context) : LockscreenAlarmScheduler {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)
    private val snoozes = this.context.getSharedPreferences("lockscreen_snoozes", Context.MODE_PRIVATE)

    override fun scheduleFullScreenTrigger(activity: ActivityItem) {
        cancelTrigger(activity.id)
        scheduleNextOccurrence(activity)
    }

    internal fun scheduleNextOccurrence(activity: ActivityItem, after: ZonedDateTime = ZonedDateTime.now()) {
        cancelStages(activity.id)
        val next = nextAlarmOccurrence(activity, after)
        if (next == null) {
            cancelPending(activity.id, "scheduled")
            return
        }
        val eventAt = next.toInstant().toEpochMilli()
        futureReminderStages(eventAt, System.currentTimeMillis()).forEach { (offset, at) ->
            schedule(activity.id, activity.title, activity.scheduledTime, "stage_$offset", at, eventAt)
        }
    }

    override fun onActivityCompleted(activity: ActivityItem, dateIso: String) {
        val now = ZonedDateTime.now()
        if (dateIso != now.toLocalDate().toString()) return
        cancelTrigger(activity.id)
        scheduleNextOccurrence(activity, maxOf(now, now.toLocalDate()
            .atTime(java.time.LocalTime.parse(activity.scheduledTime)).atZone(now.zone)))
    }

    private fun cancelStages(id: String) {
        cancelPending(id, "scheduled") // Cancel alarms installed by older app versions.
        reminderOffsets.forEach { cancelPending(id, "stage_$it") }
    }

    override fun cancelTrigger(activityId: String) {
        cancelStages(activityId)
        listOf("scheduled", "snooze").forEach { cancelPending(activityId, it) }
        clearSnooze(activityId)
        AlarmReceiver.cancelNotification(context, activityId)
    }

    private fun cancelPending(id: String, kind: String) {
        PendingIntent.getBroadcast(context, requestCode(id, kind), alarmIntent(id, kind),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
    }

    internal fun clearSnooze(id: String) { snoozes.edit().remove(id).apply() }

    internal fun restoreSnoozes(enabledIds: Set<String>) {
        val now = System.currentTimeMillis()
        snoozes.all.forEach { (id, value) ->
            val record = runCatching { org.json.JSONObject(value as String) }.getOrNull()
            val at = record?.optLong("at") ?: 0L
            if (record == null || (id !in enabledIds && id != "test") || at < now - 5 * 60_000L) {
                clearSnooze(id)
            } else {
                schedule(id, record.getString("title"), record.getString("time"), "snooze", maxOf(at, now + 1_000L), record.optLong("eventAt", 0L))
            }
        }
    }

    fun scheduleSnoozeTrigger(activityId: String, title: String, scheduledTime: String, snoozeMinutes: Int, eventAt: Long = 0L) {
        require(snoozeMinutes in 1..60)
        val at = System.currentTimeMillis() + snoozeMinutes * 60_000L
        schedule(activityId, title, scheduledTime, "snooze", at, eventAt)
        val record = org.json.JSONObject().put("title", title).put("time", scheduledTime).put("at", at).put("eventAt", eventAt)
        snoozes.edit().putString(activityId, record.toString()).apply()
    }

    fun triggerTestLockscreenInSeconds(title: String, scheduledTime: String, delaySeconds: Int = 5) {
        require(delaySeconds > 0)
        schedule("test", title.ifBlank { "Jadwal Tes" }, scheduledTime, "test", System.currentTimeMillis() + delaySeconds * 1_000L)
    }

    private fun alarmIntent(id: String, kind: String) = Intent(context, AlarmReceiver::class.java)
        .setData(Uri.Builder().scheme("trackure-alarm").authority(kind).appendPath(id).build())
        .putExtra("activityId", id).putExtra("kind", kind)

    private fun requestCode(id: String, kind: String) = if (kind.startsWith("stage_")) (id + "_" + kind).hashCode() else 0

    private fun schedule(id: String, title: String, time: String, kind: String, at: Long, eventAt: Long = 0L) {
        val intent = alarmIntent(id, kind).putExtra("title", title).putExtra("scheduledTime", time)
            .putExtra("eventAt", eventAt)
        val pending = PendingIntent.getBroadcast(context, requestCode(id, kind), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) { /* Access can be revoked between the check and call. */ }
        }
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pending)
    }
}
