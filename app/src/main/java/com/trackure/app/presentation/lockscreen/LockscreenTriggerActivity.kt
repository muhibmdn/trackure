package com.trackure.app.presentation.lockscreen

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackure.app.core.util.AlarmReceiver
import com.trackure.app.core.util.AndroidLockscreenAlarmScheduler
import com.trackure.app.ui.theme.TrackureTheme

class LockscreenTriggerActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { dismiss() }
    private var alarmIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        acceptAlarm(intent)
        setContent {
            TrackureTheme {
                val current = alarmIntent ?: return@TrackureTheme
                BackHandler { dismiss() }
                key(current) {
                    LockscreenAlarmContent(current.getStringExtra("title") ?: "Jadwal Tes",
                        current.getStringExtra("scheduledTime").orEmpty(), current.getIntExtra("leadMinutes", 0),
                        onDismiss = ::dismiss, onSnooze = { minutes ->
                            try {
                                AndroidLockscreenAlarmScheduler(this).scheduleSnoozeTrigger(
                                    current.getStringExtra("activityId") ?: "test",
                                    current.getStringExtra("title") ?: "Jadwal Tes",
                                    current.getStringExtra("scheduledTime").orEmpty(), minutes)
                                Toast.makeText(this, "Snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
                                dismiss()
                            } catch (_: RuntimeException) {
                                Toast.makeText(this, "Unable to snooze. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        stopAlerts()
        setIntent(intent)
        acceptAlarm(intent)
    }

    private fun acceptAlarm(intent: Intent) {
        alarmIntent = intent
        AlarmReceiver.cancelNotification(this, intent.getStringExtra("activityId") ?: "test")
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        runCatching {
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = audio
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 250, 1000, 250, 2000), 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator?.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, audio)
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 5 * 60_000L)
    }

    private fun stopAlerts() {
        handler.removeCallbacks(timeout)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    private fun dismiss() {
        stopAlerts()
        AlarmReceiver.cancelNotification(this, alarmIntent?.getStringExtra("activityId") ?: "test")
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        stopAlerts()
        super.onDestroy()
    }
}

@Composable
internal fun LockscreenAlarmContent(title: String, scheduledTime: String, leadMinutes: Int,
    onDismiss: () -> Unit, onSnooze: (Int) -> Unit) {
    var snoozeMinutes by remember { mutableIntStateOf(5) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFF072227), Color(0xFF0F3842), Color(0xFFD6B96E))))) {
        Column(Modifier.fillMaxWidth().padding(top = maxHeight * 0.27f).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("Hari ini ${scheduledTime.replace(':', '.')}", fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
            Text(if (leadMinutes == 0) "Jadwal dimulai sekarang" else "Dimulai dalam $leadMinutes mnt",
                fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(36.dp))
            Button(onDismiss, Modifier.width(136.dp).height(48.dp), shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                Text("Abaikan", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp)
            .padding(horizontal = 28.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledIconButton({ snoozeMinutes = (snoozeMinutes - 5).coerceAtLeast(1) }, Modifier.size(52.dp),
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White)) {
                Icon(Icons.Default.Remove, "Decrease snooze")
            }
            Button({ onSnooze(snoozeMinutes) }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f), contentColor = Color.White)) {
                Text("Snooze $snoozeMinutes menit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            FilledIconButton({ snoozeMinutes = (snoozeMinutes + 5).coerceAtMost(60) }, Modifier.size(52.dp),
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White)) {
                Icon(Icons.Default.Add, "Increase snooze")
            }
        }
    }
}
