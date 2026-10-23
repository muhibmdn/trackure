package com.trackure.app.core.util

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.database.toDomain
import kotlinx.coroutines.CancellationException

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            enqueueAlarmRecovery(context)
        }
    }
}

internal fun enqueueAlarmRecovery(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("recover-lockscreen-alarms", ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<AlarmRecoveryWorker>().build())
}

/** Durable work keeps Room access and recurrence expansion outside the broadcast deadline. */
class AlarmRecoveryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val scheduler = AndroidLockscreenAlarmScheduler(applicationContext)
        val dao = TrackureDatabase.getInstance(applicationContext).trackureDao()
        val activities = dao.getLockscreenActivitiesOnce()
        activities.forEach {
            val today = java.time.LocalDate.now().toString()
            if (dao.getLogForActivityOnDate(it.id, today)?.isCompleted == true)
                scheduler.onActivityCompleted(it.toDomain(), today)
            else scheduler.scheduleNextOccurrence(it.toDomain())
        }
        scheduler.restoreSnoozes(activities.map { it.id }.toSet())
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }
}
