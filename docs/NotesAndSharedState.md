# Shared state and journal

`MainActivity` creates exactly one lifecycle-owned `TrackureViewModel`, injects the shared repository, demo reference date (2026-10-22), and deferred alarm adapter, then passes that same ViewModel to all five screen routes. Home, Tracker, Add, History, and Notes controllers use its single viewModelScope. The former per-screen ViewModels are now plain controllers; no nested ViewModels or independent repositories are constructed. MainActivity retains saveable screen state by tab key.

Notes collects activityLogs and activities, filters note text and activity title case-insensitively, and combines that search with an optional single ISO date. The default is October 22; All Dates clears the date. Blank/deleted notes are excluded, and the compact list sorts newest date/time first. Material DatePicker timestamps are converted at UTC midnight, avoiding device-timezone date shifts. Uncompleted notes and journals display a date without inventing a completion time.

New/edit drafts belong to the shared controller, so their content survives activity recreation and tab switches. The activity dropdown comes from repository activities plus General Daily Note. Saving uses saveOrUpdateNote and retains the draft on validation/backend failure; cancel never writes. Success clears the search and selects the saved date so the result remains visible.

The repository contract now accepts optional originalLogId (existing three-argument callers still work). This identifies an edited note when its date or activity changes. The in-memory implementation moves note text atomically under its mutex, preserves source and destination completion metadata, and rejects a destination containing another note. An unchanged note updates its existing log ID. Ambiguous activity titles fail clearly instead of updating the wrong activity.

General Daily Note uses the reserved activity ID trackure:general-daily-note, one entry per date, and does not create a scheduled activity. General notes are excluded from completion analytics and activity-history CSV exports. Room/Supabase adapters should preserve this distinction and implement the same atomic note-edit behavior.

Checks: compileDebugKotlin, testDebugUnitTest, lintDebug. Notes integration tests exercise filtering, general-note upserts, collision handling, moves preserving completions, cancel/blank validation, and live cross-controller updates in both directions. Device visual verification remains separate. Persistence/auth/ICS/alarm implementations remain the previously documented next-phase adapters.

The global TRACKURE top bar now includes an accessible initials-avatar button on every tab. The shell owns the Profile & Settings sheet, Edit Profile dialog, and top snackbar; Home's Profile action delegates to that same flow. Profile changes update the global avatar directly from TrackureViewModel.userProfile. One shell-level event collector handles profile feedback regardless of the active tab.

SharedActionsIntegrationTest exercises profile edits, adding/editing an activity, logging completion, revising its note, and generating/writing CSV through all five controllers against the same repository. Full debug APK assembly is included in final validation.
