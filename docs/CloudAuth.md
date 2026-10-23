# Trackure authentication and direct sync

Set SUPABASE_URL, SUPABASE_ANON_KEY (public key only), and GOOGLE_WEB_CLIENT_ID in the root, gitignored local.properties, then rebuild and install the new APK. Gradle loads rootProject.file("local.properties"); changing that file cannot change an already installed APK. Never use a service-role key in the app.

Both Gradle and the runtime trim whitespace/surrounding quotes and normalize https://YOUR_PROJECT.supabase.co/rest/v1/ (also /rest/v1 and a trailing slash) to https://YOUR_PROJECT.supabase.co. Configuration accepts a HTTPS URL plus any nonblank key: both sb_publishable_... and legacy eyJ... anon JWT keys are supported. The key is passed to the Supabase SDK unchanged after trimming; it is not parsed as a JWT or used as a user session. Email buttons are controlled by form validity and busy state, never by key format. Configuration/client construction failures appear only when attempting auth, with their diagnostic message.

The welcome screen offers Google Credential Manager, email/password Sign In and Register, and an explicit offline account. Enable Email in Supabase Auth. When email confirmation is enabled, registration displays a confirmation instruction and stays on the welcome screen until the user confirms and signs in. It does not claim cloud access without a session.

Google additionally requires the web OAuth client in Supabase and an Android OAuth client matching com.trackure.app and the installed APK's SHA-1. Email/password does not depend on Google SHA-1 configuration.

After Credential Manager returns a valid Google credential, its name, email, and avatar are committed to a local Room workspace before the Supabase ID-token exchange. A successful exchange promotes that workspace to the Supabase user UUID, activates the shared navigation StateFlow, and starts immediate sync. A failed exchange (including a 15-second timeout or invalid configuration) still activates the Google profile locally and opens Home. Logcat receives the exception and a Toast displays its message. That fallback persists across restart, shows Local Mode, and cannot upload with a local ID or a stale cloud session. Retrying Google sign-in later can promote the local workspace to cloud ownership.

Both provider completion flows run in application scope so leaving AuthScreen cannot cancel profile activation or prevent the sync launch. MainActivity directly observes repository.isAuthenticatedOrLocalActive; TrackureViewModel does not maintain a separate auth flag. Email sign-in and auto-confirmed registration activate Home only after Supabase supplies a session. Email auth errors are displayed verbatim as selectable text on AuthScreen. Confirmation-required registration remains on AuthScreen with an instruction to confirm the email and sign in.

SUPABASE_SCHEMA.sql now defines the exact flat columns, authenticated UUID ownership, and RLS policies. updated_at is timestamptz (ISO-8601 on the wire). Activity/log IDs and activity_id are text to support existing seed/ICS/general-note identifiers; completed_hour and completed_minute are nullable text. The script does not migrate the previous payload schema and aborts if it detects one. Existing flat tables must have matching types and authenticated SELECT/INSERT/UPDATE/DELETE policies. No SQL is applied automatically by the Android app.

On successful authentication, the active offline workspace is moved into the authenticated user's Room workspace and marked pending. Another cloud user's workspace is never transferred. Cached cloud-account profile settings are retained when available; the authenticated profile ID is always the Supabase Auth UUID. Offline mode must be explicitly selected on first launch or after logout. Logout immediately closes the main shell, persists the logged-out gate, cancels that workspace's alarms, clears the Supabase session even offline, and clears Credential Manager state.

Every repository mutation commits Room first and immediately launches the shared upload/download routine on Dispatchers.IO. WorkManager remains the connected-network retry path. Authentication and manual Sync Now use the same routine. Uploads acknowledge only the exact Room version sent; transactional pulls never overwrite pending edits or deletes. Account transitions and sync share a mutex. Profiles, activities, and logs are pulled with owner filtering; activity/log pulls are paginated.

Logcat tag: TrackureSupabase. It reports authentication, each upload/delete, pull counts, completion, and failure exceptions without logging passwords or tokens. The profile sheet shows Cloud Synced (Supabase Active) only after a successful transfer with no pending rows; failures show pending status, and offline accounts show Local Mode.

## Schema limits

The requested columns do not include deletion tombstones or the local recurrence anchor (startDateIso/repeatOnSpecificDate). Deletes remove remote activities while retaining history. A local deleted marker prevents a pull from restoring a deleted row on that device; this contract cannot provide full offline cross-device deletion conflict resolution. Existing Room recurrence anchors are preserved on pull; a fresh device uses specific_date_iso or updated_at's UTC date as a fallback. Persisting exact interval anchors across devices requires additional cloud columns.

## Device verification

1. Fresh launch shows AuthScreen. Choose offline, add an activity and note, then restart: offline mode persists.
2. Sign out: AuthScreen appears immediately. Restart offline: it remains logged out.
3. Register with Email. Confirm the email if required, sign in, and verify the authenticated UUID in profiles.id and activities/activity_logs.user_id.
4. Create/edit/delete an activity, complete it, edit its note and profile. Verify immediate requests in Logcat and flat rows in Supabase without waiting for WorkManager.
5. Disable network, edit, reconnect, and use Sync Now. Confirm pending changes survive and become synced.
6. Sign in on another device to check pulls. Switch accounts to verify workspace isolation. Test Google with valid and invalid SHA-1 configuration.

References: [Supabase email signup](https://supabase.com/docs/reference/kotlin/auth-signup), [email sign-in](https://supabase.com/docs/reference/kotlin/auth-signinwithpassword), [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg).

Configuration reference: [Supabase API keys](https://supabase.com/docs/guides/getting-started/api-keys). Publishable keys are supported client-side; switching to a legacy key is not a general prerequisite.
