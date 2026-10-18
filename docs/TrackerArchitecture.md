# Tracker

`TrackerScreen.kt` contains the stateless Tracker screen and Activity Log bottom sheet. `TrackerRoute` collects the ViewModel state with lifecycle awareness. `MainActivity` supplies the same repository instance used by Home, with the prototype reference date of October 22, 2026.

The week is Monday-first. The TODAY action resets selection to the injected reference date. Rows join repository activities and logs by activity ID plus selected ISO date; completion on one date does not check another date. The current daily demo activities appear throughout the week. Full recurrence expansion requires a schedule anchor/occurrence model in the next backend phase; it is not inferred from the mutable updatedAt timestamp.

The ViewModel owns the editor draft across activity recreation and tab changes. Existing logs supply time and note; otherwise local current time supplies hour/minute. Cancel/close writes nothing. Save validates 24-hour input, pads to two digits, prevents duplicate submissions, and invokes logActivityCompletion with the captured activity ID and selected date. Errors retain the draft for retry. The repository publishes the completion and note through activityLogs, so Home and future History/Note consumers share the same source. History and Note screens are not implemented by this task.

Validation: compileDebugKotlin, testDebugUnitTest, lintDebug. Tracker tests cover date isolation, stable-ID joins, deleted records, edit upserts, shared notes, and time boundaries. Device visual verification is separate.
