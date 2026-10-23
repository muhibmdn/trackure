# Lockscreen alarms

The Android scheduler is connected to the activity save flow. It schedules future stages at 30, 15, 10, 5, and 0 minutes before the next local-time occurrence, then advances recurrence after the zero-minute stage. Editing, deleting, or disabling the option cancels the stages and snooze. Each stage has a deterministic request code and a URI containing the complete activity ID and stage, so hash collisions cannot merge alarms.

Completion cancels the remaining stages for that day and schedules the next recurrence. The receiver also checks Room completion before displaying a reminder, and recovery skips today's completed occurrence. Event epoch milliseconds travel with reminders and snoozes so the one-second countdown handles midnight correctly.

Room remains the source of truth for recurrence. Recovery runs through WorkManager after boot, exact-alarm access grants, clock/timezone changes, application updates, and app startup. Snooze timestamps are stored locally and restored after reboot; snoozes overdue by more than five minutes are discarded. The device must be unlocked once after boot before credential-protected Room data is available.

Notifications use a high-importance alarm channel and a full-screen PendingIntent. Android decides whether to show a full-screen activity or a heads-up notification. Direct activity launch is attempted only on pre-Android-10 devices or when overlay access is already granted. The screen displays above the keyguard. Buka Trackure stops alerts, requests system keyguard dismissal, and opens the Tracker tab only after successful unlock. Denied notification/full-screen access and user-disabled channels cannot be overridden.

The permission flow requests notifications, exact alarms, and full-screen intents sequentially. Normal schedules fall back to a ten-minute inexact window if exact-alarm access is unavailable; the five-second test requires permissions and explains missing access instead of promising exact delivery. Both exact-alarm permissions requested in the task are declared; USE_EXACT_ALARM is intended for qualifying alarm/calendar apps and needs distribution-policy review before publishing.

Sound and vibration stop on dismiss, opening Trackure, snooze, activity destruction, replacement by another alarm, or a five-minute safety timeout. The timeout leaves the live countdown visible. Vibration repeats the 0/600/400/600/800 ms pattern with alarm usage attributes; device interruption policies still apply. An alarm delivered without opening the activity uses the notification channel's sound/vibration instead.

## Device verification

1. Open Add/Edit Activity, enable Full-Screen Lockscreen Trigger, and complete each system permission screen.
2. Tap Test Lockscreen Trigger (5s), lock the device, and verify the gradient screen, title/time, sound, and vibration.
3. Tap Abaikan; verify sound/vibration stop and the task closes. Repeat and snooze for one minute; verify it returns.
4. Save a recurring activity a few minutes ahead, reboot, unlock once, then lock again. Verify the alarm and the next recurring occurrence.
5. Disable notifications/full-screen intents/exact access individually in Settings. Check the explanatory messages and notification/inexact fallback behavior. Exact access may be automatically available because USE_EXACT_ALARM is declared.
6. Test a physical device in Doze as well as an emulator. OEM power policies and Android full-screen notification restrictions affect delivery.

References: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms), [time-sensitive notifications](https://developer.android.com/develop/ui/views/notifications/time-sensitive), [background activity launch restrictions](https://developer.android.com/guide/components/activities/background-starts).

## Validation performed

- Kotlin 2.4.10 / AGP 9.4.1 debug compilation, 37 unit tests, and lint (no errors).
- Four instrumentation tests passed on the API 37 emulator, including Compose snooze/dismiss controls, PendingIntent collision isolation, and Room persistence.
- Manually enabled the trigger through its notification permission dialog, tapped the five-second test, turned off the emulator screen, and confirmed the full-screen alarm woke it. Inspected the rendered gradient layout and dismissed the alarm.
- Physical-device audio/vibration, Doze, denied-permission variants, and reboot delivery remain manual verification items.
