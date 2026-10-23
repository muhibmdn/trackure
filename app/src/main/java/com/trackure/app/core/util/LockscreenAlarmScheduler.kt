package com.trackure.app.core.util

import com.trackure.app.domain.model.ActivityItem

/** Platform adapter seam. Implementations must replace alarms by stable activity ID.
 * The AlarmManager adapter owns exact-alarm/full-screen-intent permission checks,
 * PendingIntent identity, timezone/recurrence expansion, and reboot rescheduling.
 */
interface LockscreenAlarmScheduler {
    fun scheduleFullScreenTrigger(activity: ActivityItem)
    fun cancelTrigger(activityId: String)
    fun onActivityCompleted(activity: ActivityItem, dateIso: String) = Unit
}

/** This phase stores preferences only; it does not install Android alarms. */
object DeferredLockscreenAlarmScheduler : LockscreenAlarmScheduler {
    override fun scheduleFullScreenTrigger(activity: ActivityItem) = Unit
    override fun cancelTrigger(activityId: String) = Unit
}
