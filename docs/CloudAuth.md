# Trackure authentication and offline sync

Set these in the root `local.properties` (already gitignored), then rebuild:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_OR_PUBLISHABLE_KEY
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_OAUTH_CLIENT_ID.apps.googleusercontent.com
```

Use the `trackure` project's public key, never a service-role key. These values are compiled into the APK. Missing properties default to empty strings and leave local tracking operational.

Run `SUPABASE_SCHEMA.sql` in that project's SQL Editor. This is the schema for this implementation: entity fields are stored inside `payload` JSONB, with independently enforced `owner_id`, `id`, `updated_at`, and deletion markers. It does not migrate an existing, differently shaped cloud schema.

Enable Google's provider in Supabase Auth. Configure the web OAuth client ID there and in `local.properties`. Register an Android OAuth client for `com.trackure.app` with the installed build's signing certificate SHA-1. Debug and release certificates differ.

Login opens Credential Manager with automatic selection disabled. Switch Account additionally clears credential state. Available accounts and picker appearance are controlled by Google Play Services. Missing configuration, credential-provider errors, and unavailable authentication open the interactive **Switch / Connect Account** local profile dialog. These profiles never acquire Supabase permissions merely by entering an email. Enter the same email/URL again to reopen its local workspace.

Each account has a separate Room workspace. Existing v1 data is migrated without deletion and stays in the original local workspace, accessible after logout. It is not automatically assigned to a Google identity. Logging into a Google account restores its cloud activities and logs; pending edits remain on the device when switching away and upload when that account authenticates again. Logout clears the persisted Supabase session even offline and returns to the original local workspace.

Room is the source for all tracking screens. Pending writes enqueue connected-network WorkManager jobs; app startup and authentication also enqueue sync. Failed transfers retry with backoff. Upload acknowledgements compare the local timestamp to avoid clearing a newer pending edit. Pulls are paginated and do not replace pending local changes. Deletions are cloud tombstones to prevent deleted activities from reappearing. Cloud conflict resolution uses device timestamps, so devices should use automatic time. Account transitions and worker sessions share a mutex so a request cannot change identities mid-sync.

Verification on a Play-enabled device:

1. With blank keys and airplane mode, launch, add/edit activities, record completion, edit notes, restart, and use the local account dialog.
2. With configured keys, Login with Google, edit profile/activity/note, and verify owner-scoped rows in Supabase.
3. Switch between two Google accounts, including accounts with the same imported calendar IDs; confirm each sees only its own data and alarms.
4. Edit offline, reconnect, and confirm pending changes upload; repeat with a deletion and restart.
5. Log out offline and restart; verify cloud credentials are gone and local tracking still works.
6. Try missing Play Services, invalid OAuth setup, picker cancellation, and a temporarily unavailable PostgREST endpoint.

References: [Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg), [Supabase ID-token authentication](https://supabase.com/docs/reference/kotlin/auth-signinwithidtoken), [Supabase Kotlin upsert](https://supabase.com/docs/reference/kotlin/upsert).
