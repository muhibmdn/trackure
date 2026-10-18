# History and analytics

The History tab collects `TrackureRepository.activityLogs` through `HistoryController`. `HistoryScreen` is state/callback-driven and contains the Canvas chart and export dialog. It shares the same repository as Tracker, Add, and Home.

The prototype date is October 22, 2026. The chart remains the Monday–Sunday week of October 19–25, with Saturday initially selected. The range controls the summary and CSV export: 7, 30, 90, or 365 trailing days including the reference date. The two longer options use fixed day windows to match the repository contract.

Metrics are derived, not hardcoded: the original three completed logs out of five show 60% and 3. The prototype's 87% and 42 are illustrative values, not present in the seed data. Average completion is the arithmetic mean of daily completion percentages for dates with recorded occurrences. Days with no occurrence records do not enter that denominator. No historical schedule is fabricated from the current activity definitions. Duplicate activity/date records use the latest updatedAt; pending deletions and future completions are excluded.

Bars show daily completed counts. The trend is the trailing seven-calendar-day average of completed counts, including zero-count days, on the same count axis as the bars. Future days show zero, not predicted results. Tooltip values use recorded occurrences (0/0 where none exist). Both bar tap areas and accessible date labels select a day. Curves use midpoint cubic control points to avoid overshooting adjacent data values.

Export starts with Activity Name and Completed Time checked, Include Note unchecked. It calls exportHistoryData(config, selectedRangeDays), then CreateDocument(text/csv) and UTF-8 ContentResolver I/O on Dispatchers.IO. The success toast is emitted only after writing. Picker cancellation is silent and retryable; failures retain the dialog and display the error. The generated payload remains in the ViewModel while the file picker is open.

Checks: compileDebugKotlin, testDebugUnitTest, lintDebug. Analytics tests cover range boundaries, averaging, duplicate/deleted/future records, rolling counts, and Tracker-to-History/export integration. Device visual and file-provider testing remain separate.

All feature controllers are now owned by the shared TrackureViewModel; see [Notes and shared state](NotesAndSharedState.md) for the current composition root and journal integration.
