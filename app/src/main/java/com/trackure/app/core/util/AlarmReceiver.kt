package com.trackure.app.core.util

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.database.toDomain
import com.trackure.app.presentation.lockscreen.LockscreenTriggerActivity
import kotlinx.coroutines.*
import java.time.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("activityId") ?: return
        val kind = intent.getStringExtra("kind") ?: return
        val pending = goAsync()
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trackure:alarm")
        wakeLock.acquire(10_000L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = TrackureDatabase.getInstance(context).trackureDao()
                val activity = if (id == "test") null else dao.getActivityOnce(id)?.toDomain()
                val scheduler = AndroidLockscreenAlarmScheduler(context)
                if (kind == "snooze") AndroidLockscreenAlarmScheduler(context).clearSnooze(id)
                if (id != "test" && activity?.fullScreenLockscreenTrigger != true) return@launch
                val title = activity?.title ?: intent.getStringExtra("title") ?: "Jadwal Tes"
                val time = activity?.scheduledTime ?: intent.getStringExtra("scheduledTime").orEmpty()
                val now = ZonedDateTime.now()
                val eventAt = intent.getLongExtra("eventAt", 0L).takeIf { it > 0 }
                    ?: now.toLocalDate().atTime(LocalTime.parse(time)).atZone(now.zone).toInstant().toEpochMilli()
                val event = Instant.ofEpochMilli(eventAt).atZone(now.zone)
                if (activity != null && dao.getLogForActivityOnDate(id, event.toLocalDate().toString())?.isCompleted == true) {
                    scheduler.cancelTrigger(id)
                    scheduler.scheduleNextOccurrence(activity, maxOf(now, event))
                    return@launch
                }
                showAlarm(context, id, title, time, eventAt)
                // Pre-reminders must leave the remaining stages of this occurrence intact.
                if ((kind == "scheduled" || kind == "stage_0") && activity != null)
                    scheduler.scheduleNextOccurrence(activity, maxOf(now, event))
            } catch (e: Exception) {
                Log.e("TrackureAlarm", "Unable to deliver alarm", e)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                pending.finish()
            }
        }
    }

    private fun showAlarm(context: Context, id: String, title: String, time: String, eventAt: Long) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Lockscreen alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Scheduled activity alarms"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 800)
        })
        val launch = Intent(context, LockscreenTriggerActivity::class.java)
            .setData(android.net.Uri.Builder().scheme("trackure-alarm").authority("display").appendPath(id).build())
            .putExtra("activityId", id).putExtra("title", title).putExtra("scheduledTime", time)
            .putExtra("eventAt", eventAt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else if (Build.VERSION.SDK_INT >= 35) {
            @Suppress("DEPRECATION")
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        val fullScreen = PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle())
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.trackure.app.R.drawable.ic_alarm_notification)
            .setContentTitle(title).setContentText("$time · ${alarmCountdownText(eventAt, System.currentTimeMillis())}")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setContentIntent(fullScreen)
            .setAutoCancel(true).setTimeoutAfter(5 * 60_000L)
        if (Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent())
            notification.setFullScreenIntent(fullScreen, true)
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            try { notifications.notify(id, NOTIFICATION_ID, notification.build()) }
            catch (e: SecurityException) { Log.w("TrackureAlarm", "Notification access revoked", e) }
        }
        // Only use the direct path when the platform permits background launches.
        // The regular path is the system-controlled full-screen notification.
        if (Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context)) {
            try { context.startActivity(launch) }
            catch (e: RuntimeException) { Log.w("TrackureAlarm", "Direct launch unavailable", e) }
        }
    }

    companion object {
        private const val CHANNEL = "trackure_lockscreen_alarms"
        private const val NOTIFICATION_ID = 1001
        fun cancelNotification(context: Context, activityId: String) {
            context.getSystemService(NotificationManager::class.java).cancel(activityId, NOTIFICATION_ID)
        }
    }
}
