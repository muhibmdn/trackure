# Calendar and history files

Add Activity uses Android's Storage Access Framework: OpenDocument for imports and CreateDocument for exports. All streams are closed with `use`, and UTF-8 reads/writes and parsing run off the main thread. No broad storage permission is needed. The default filenames are `trackure_schedule.ics` and `Trackure_History_Export.csv`.

`IcsCalendarHelper` uses the existing Biweekly dependency. It supports folded/escaped text, floating/UTC/timezone dates, categories, and daily/weekly/monthly/yearly recurrence with supported BYDAY, INTERVAL, COUNT and inclusive UNTIL. Imported DTSTART populates both `specificDate` (the domain field corresponding to `specificDateIso`) and `startDateIso`. No RRULE means a one-time activity; date-only events become midnight activities. Imported alarms are enabled by default.

Imports are limited to 2 MB / 5000 events and validated completely before the Room transaction. Stable UID-derived IDs make re-imports update existing imported events. Scheduling happens after commit, independently of UI Flow updates. If scheduling fails, the error explicitly states that activities were imported; app-start alarm recovery can retry them.

Trackure's current model cannot represent arbitrary RFC-5545 recurrence sets. Ordinal/monthly/yearly BYDAY, recurrence exceptions and overrides, sub-daily rules, multiple month dates, and foreign DST recurrences are rejected with an error rather than silently changing their meaning. Foreign timezone conversion that shifts a recurring event to another date is also rejected. One-time events are converted to device-local time. Existing event duration/location/attendee data is not represented by ActivityItem.

Exports contain VERSION, PRODID, stable UID, DTSTAMP, DTSTART, SUMMARY, optional CATEGORIES/RRULE, and a DISPLAY VALARM seven minutes before the start. Dates are floating local times because Trackure schedules follow the device's local clock. Recurring DTSTART is advanced to the first actual occurrence when selected weekdays exclude the anchor date. Exported alarm blocks are included for every activity, as requested.

History exports contain only completed, nondeleted activity logs within the inclusive range ending today. General daily notes and future/unfinished records are excluded. Only selected columns appear: Nama Aktivitas, Tanggal & Waktu Terlaksana, Catatan. Empty results contain the header; selecting no columns is rejected. CSV quotes embedded commas, quotes and newlines. Markdown is available separately through `buildHistoryMarkdownContent`; Markdown text is never appended to CSV.

Validation covers calendar round-trips, recurrence dates, Unicode/escaped/folded content, timezone handling, invalid calendars, CSV filtering/escaping, and Room import/export with ContentResolver streams. Test the actual document picker with a local Downloads destination and with any cloud document providers you support. Google/Samsung calendar interoperability should also be checked on their respective devices/accounts.

Library reference: [Biweekly](https://github.com/mangstadt/biweekly).
