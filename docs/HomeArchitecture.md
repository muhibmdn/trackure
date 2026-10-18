# Home architecture

`MainActivity` is the composition root: it supplies a `TrackureRepository` and reference date to `HomeController`. `HomeScreen` receives only state and callbacks. Replace `InMemoryTrackureRepository.shared` with the persistent implementation when Room/Supabase are connected.

The demo deliberately uses 2026-10-22 to match the supplied prototype. Its five daily activities have three completed logs (60%). The matrix covers 16 Monday-first calendar weeks, including inactive future cells in the last week; the active streak is 24 days. Production should supply a date source that updates at local midnight.

Models use string identifiers, UTC ISO-8601 `updatedAt`, ISO local dates, HH:mm scheduled times, and ISO weekday numbers 1–7. Mutations retain existing IDs and mark records `PENDING_UPLOAD`. Streak days are derived records keyed by date; export configuration is a value object, not a synced entity.

The in-memory adapter supports profile edits, activity upserts, completion upserts, notes, and configurable CSV history export. Auth methods throw explicit unsupported-operation errors; ICS methods return `Result.failure` until the next-phase adapters are implemented. The UI displays errors rather than claiming success. Data resets on process restart. The null seed avatar uses initials.

Profile updates run in the ViewModel, survive activity recreation, and emit success only after the repository completes. State is collected with lifecycle awareness. Backend implementations should perform database/network work off the main thread, publish immutable snapshots, and propagate cancellation.

Validation: `gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`. Repository tests cover seed/calendar alignment, serializable profile identity, idempotent completion and note updates, CSV options and escaping.

All feature controllers are now owned by the shared TrackureViewModel; see [Notes and shared state](NotesAndSharedState.md) for the current composition root and journal integration.
