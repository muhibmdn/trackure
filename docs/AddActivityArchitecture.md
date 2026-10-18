# Activities and schedule editor

The Add tab now opens a two-view screen: the scheduled-items list with calendar sync, and a dedicated full-page Add/Edit form. UI editor mode is local Compose state. A newly opened Add editor allocates one stable ID; editing copies the existing item so saving updates instead of duplicates. Cancel/back never persist.

`AddActivityController` depends on `TrackureRepository` and `LockscreenAlarmScheduler`. The shell injects the shared in-memory repository and `DeferredLockscreenAlarmScheduler`. The latter deliberately installs no Android alarms in this phase. Replace it with the AlarmManager/FullScreenIntent adapter without modifying the form. After repository save, enabled triggers call scheduleFullScreenTrigger; disabled triggers call cancelTrigger with the stable ID. Scheduling errors do not discard an already saved activity.

The model now includes optional `specificDate` and `repeatOnSpecificDate` fields with backward-compatible defaults. These are independent of `endDate`. Specific-date repeats are intended as annual repeats; recurrence expansion, timezone handling, permissions, and rescheduling belong to the future scheduling adapter. Interval and end-condition controls preserve the prototype's fixed choices; existing non-preset end values are preserved during edits.

Import uses GetContent(text/calendar), bounds UTF-8 input to 2 MB, and calls importIcsContent off the UI file-I/O path. Export first obtains successful repository content, then launches CreateDocument(text/calendar) and writes UTF-8 via ContentResolver on Dispatchers.IO. Cancellation is silent. Errors are displayed honestly. The current in-memory repository's ICS methods remain explicit unsupported-adapter results; this screen does not simulate successful import/export.

Custom categories are selectable immediately and persist on saved activities. Name, time, date, interval, and end-condition validation runs before save. Success returns to the list and shows Saved: <name>; repository streams update Home and Tracker.

Checks: compileDebugKotlin, testDebugUnitTest, lintDebug. Tests cover validation, stable-ID add/edit upserts, settings preservation, and serialization compatibility for old records. Device/file-provider/alarm testing is not part of the JVM checks.

All feature controllers are now owned by the shared TrackureViewModel; see [Notes and shared state](NotesAndSharedState.md) for the current composition root and journal integration.
