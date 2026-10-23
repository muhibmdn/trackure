package com.trackure.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.util.AlarmReceiver
import com.trackure.app.core.util.AndroidLockscreenAlarmScheduler
import com.trackure.app.presentation.lockscreen.LockscreenAlarmContent
import com.trackure.app.ui.theme.TrackureTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LockscreenAlarmTest {
    @get:Rule val compose = createComposeRule()

    @Test fun snoozeBoundsAndDismiss() {
        var snoozed = 0
        var dismissed = false
        var opened = false
        compose.setContent { TrackureTheme {
            LockscreenAlarmContent("Jadwal Tes", "07:15", { dismissed = true }, { snoozed = it }, { opened = true })
        } }
        compose.onNodeWithText("Hari ini 07.15").assertIsDisplayed()
        compose.onNodeWithText("Buka Trackure").performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithContentDescription("Decrease snooze").performClick()
        compose.onNodeWithContentDescription("Decrease snooze").performClick()
        compose.onNodeWithText("Snooze 1 menit").assertIsDisplayed()
        repeat(13) { compose.onNodeWithContentDescription("Increase snooze").performClick() }
        compose.onNodeWithText("Snooze 60 menit").performClick()
        compose.runOnIdle { assertEquals(60, snoozed) }
        compose.onNodeWithText("Abaikan").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun allProgressiveStagesAreDistinctAndCancelledTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AndroidLockscreenAlarmScheduler(context)
        val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
        val item = com.trackure.app.domain.model.ActivityItem(id = "stage-test", title = "Stages",
            scheduledTime = "12:45", fullScreenLockscreenTrigger = true, loopActivity = false, specificDate = tomorrow)
        fun pending(offset: Int) = PendingIntent.getBroadcast(context, (item.id + "_stage_" + offset).hashCode(),
            Intent(context, AlarmReceiver::class.java).setData(Uri.Builder()
                .scheme("trackure-alarm").authority("stage_$offset").appendPath(item.id).build()),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        try {
            scheduler.scheduleFullScreenTrigger(item)
            val offsets = listOf(30, 15, 10, 5, 0)
            offsets.forEach { assertNotNull(pending(it)) }
            assertEquals(5, offsets.map { pending(it) }.toSet().size)
            scheduler.cancelTrigger(item.id)
            offsets.forEach { assertNull(pending(it)) }
        } finally { scheduler.cancelTrigger(item.id) }
    }

    @Test fun distinctIdsWithSameHashHaveIndependentCancelableSnoozes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AndroidLockscreenAlarmScheduler(context)
        fun pending(id: String) = PendingIntent.getBroadcast(context, 0,
            Intent(context, AlarmReceiver::class.java).setData(Uri.Builder()
                .scheme("trackure-alarm").authority("snooze").appendPath(id).build()),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertEquals("Aa".hashCode(), "BB".hashCode())
        try {
            scheduler.scheduleSnoozeTrigger("Aa", "First", "07:15", 60)
            scheduler.scheduleSnoozeTrigger("BB", "Second", "07:15", 60)
            assertNotNull(pending("Aa"))
            assertNotNull(pending("BB"))
            assertNotEquals(pending("Aa"), pending("BB"))
            scheduler.cancelTrigger("Aa")
            assertNull(pending("Aa"))
            assertNotNull(pending("BB"))
        } finally {
            scheduler.cancelTrigger("Aa")
            scheduler.cancelTrigger("BB")
        }
    }
}
