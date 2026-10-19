package com.trackure.app.presentation.add

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Each result opens only the next settings screen; a denial never causes a request loop. */
@Composable
internal fun rememberAlarmPermissionRequester(onFinished: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    val fullScreenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { finished() }
    val requestFullScreen: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            try { fullScreenLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }
            catch (_: android.content.ActivityNotFoundException) { finished() }
        } else finished()
    }
    val latestFullScreen by rememberUpdatedState(requestFullScreen)
    val exactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { latestFullScreen() }
    val requestExact: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            try { exactLauncher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
            catch (_: android.content.ActivityNotFoundException) { latestFullScreen() }
        } else latestFullScreen()
    }
    val latestExact by rememberUpdatedState(requestExact)
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latestExact() }
    return {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else latestExact()
    }
}

internal fun alarmPermissionMessage(context: android.content.Context): String? = when {
    !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() -> "Enable notifications in app settings to receive lockscreen alarms."
    Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() -> "Exact alarms are disabled. Scheduled alarms may be delayed by 10 minutes or more."
    Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() -> "Enable full-screen intents in app settings to show alarms on the lockscreen."
    else -> null
}
