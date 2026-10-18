# Offline persistence

The application uses `RoomTrackureRepository` through the unchanged `TrackureRepository`
interface. Room 2.8.5 and KSP are already declared in the version catalog. No destructive
migration or main-thread database access is enabled.

`trackure.db` version 1 contains profile, activity, and log tables. The creation callback
inserts the default profile, five activities, and five current-date logs (three completed)
inside SQLite's creation transaction. Opening an existing database never seeds again,
including after deleting every activity. StateFlows update from DAO observations.
Writes read current database state inside transactions rather than using UI snapshots.
Completion upserts and note moves preserve completion metadata and reject note conflicts.
A unique activity/date index prevents duplicate logs. Activities are soft-deleted for
future synchronization; history and general journal notes have no cascading foreign key.

The requested activity schema has two additional persisted properties: `startDateIso`
and `repeatOnSpecificDate`. The first anchors interval and occurrence counting independently
of edit timestamps; the second preserves the existing yearly-repeat UI setting. Timestamp
mapping uses UTC epoch milliseconds in SQLite and ISO instants in domain models.

## Recurrence rules

- Dates before the anchor are excluded; a non-looping task appears only on its specific
  date, or its start date when no specific date exists.
- Daily intervals count days, weekly intervals count ISO Monday-based weeks, monthly
  intervals count months, and yearly intervals count years.
- Selected weekdays filter daily schedules. Weekly schedules with no selected weekdays
  use the anchor weekday. CUSTOM means every N weeks on selected weekdays, or every
  N days with no weekday selection.
- Monthly day 31 skips months without that day; yearly February 29 skips non-leap years.
- Specific-date schedules are one-off unless yearly repetition is enabled.
- End dates are inclusive. Occurrence limits count scheduled dates from the anchor,
  independent of completions. Editing an existing activity retains its original anchor.

## Streaks

The matrix contains 16 Monday-to-Sunday weeks including the current week, with future
cells at zero. Streak counting uses all logs and can exceed the 112-cell display window.
If today has no completion, yesterday can continue the current streak. General journal
entries and pending deletes are excluded. Duplicate task/date input uses its newest log.
Levels are zero for no completions, one for 1–2, two for 3–4, and three for 5+ or all
recorded task rows completed. A logs-only API cannot infer scheduled tasks missing from
the log list, so its 100% denominator is recorded task rows, not the activity catalog.

Authentication, cloud synchronization, alarm delivery, and ICS adapters remain separate
features; existing unsupported operations still report failure explicitly.

## Verification

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline`
builds the app and instrumentation APK and runs JVM tests. The Room instrumentation test
checks seeding, concurrent completion upserts, note moves, deletion markers, and disk
reopen persistence. Run `:app:connectedDebugAndroidTest` with a connected Android device
or emulator to execute it.
