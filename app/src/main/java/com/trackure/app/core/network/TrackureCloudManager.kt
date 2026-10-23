package com.trackure.app.core.network

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.trackure.app.core.database.dao.TrackureDao
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.room.withTransaction
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.trackure.app.BuildConfig
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.database.entity.ActiveAccountEntity
import com.trackure.app.core.database.entity.UserProfileEntity
import com.trackure.app.core.database.toDomain
import com.trackure.app.core.util.AndroidLockscreenAlarmScheduler
import com.trackure.app.core.util.enqueueAlarmRecovery
import com.trackure.app.domain.model.UserProfile
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** A local profile is useful offline, but never grants cloud access. */
class LocalAccountFallback(cause: Throwable? = null) : Exception("Connect locally while Google sign-in is unavailable", cause)

object TrackureCloudManager {
    private const val TAG = "TrackureSupabase"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = MutableStateFlow(false)
    val isAuthenticatedOrLocalActive = active.asStateFlow()
    private val status = MutableStateFlow("Local Mode")
    val syncStatus = status.asStateFlow()
    private fun prefs(context: Context) = context.getSharedPreferences("trackure_auth", Context.MODE_PRIVATE)

    suspend fun initialize(context: Context) = sessionMutex.withLock {
        val owner = TrackureDatabase.getInstance(context).trackureDao().activeUserId()
        if (prefs(context).getBoolean("logged_out", false)) {
            active.value = false
            return@withLock
        }
        // Restoring an explicitly active local/Google fallback account needs no cloud client.
        if (owner.startsWith("local_") && prefs(context).getBoolean("local_active", false)) {
            active.value = true
            status.value = "Local Mode"
            return@withLock
        }
        try {
            val cloud = client
            cloud?.auth?.awaitInitialization()
            active.value = cloud?.auth?.currentUserOrNull()?.id == owner
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            active.value = false
            Log.e(TAG, "Could not restore Supabase session; sign-in remains available", e)
        }
    }

    private fun launchSync(context: Context) {
        scope.launch {
            if (syncAllPendingToSupabase(TrackureDatabase.getInstance(context).trackureDao())) enqueueAlarmRecovery(context)
        }
        try { TrackureSyncWorker.enqueueOneTimeSync(context) }
        catch (e: Exception) { Log.e(TAG, "Background retry enqueue failed; immediate sync remains active", e) }
    }

    suspend fun authenticateEmail(context: Context, email: String, password: String, name: String, register: Boolean): Result<Boolean> =
        scope.async { authenticateEmailInternal(context.applicationContext, email, password, name, register) }.await()

    private suspend fun authenticateEmailInternal(context: Context, email: String, password: String, name: String, register: Boolean): Result<Boolean> = try {
        val authenticated = sessionMutex.withLock {
            val cloud = clientResult.getOrThrow()
            cloud.auth.awaitInitialization()
            if (register) cloud.auth.signUpWith(Email) {
                this.email = email.trim(); this.password = password
                data = buildJsonObject { put("name", name.trim()) }
            } else cloud.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
            val user = cloud.auth.currentUserOrNull() ?: run {
                check(register) { "Supabase sign-in returned no authenticated session" }
                return@withLock false
            }
            val old = TrackureDatabase.getInstance(context).trackureDao().profileFor(user.id)
            activate(context, old ?: UserProfileEntity(user.id,
                user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull ?: name.ifBlank { email.substringBefore('@') },
                email.trim(), "One Sure Step Everyday", null, System.currentTimeMillis()))
            Log.d(TAG, "Email authentication succeeded")
            true
        }
        if (authenticated) launchSync(context)
        Result.success(authenticated)
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        Log.e(TAG, "Email authentication failed", e)
        Result.failure(e)
    }

    suspend fun continueOffline(context: Context) = sessionMutex.withLock {
        clearCloudSession()
        val dao = TrackureDatabase.getInstance(context).trackureDao()
        val owner = dao.activeUserId().takeIf { it.startsWith("local_") }
            ?: prefs(context).getString("local_workspace", "local_user")!!
        activate(context, dao.profileFor(owner) ?: UserProfileEntity(owner, "Local Athlete", "", "One Sure Step Everyday", null, System.currentTimeMillis()))
    }
    internal val sessionMutex = Mutex()
    fun isSupabaseConfigured(): Boolean = SupabaseConfiguration.isConfigured(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val isConfigured: Boolean get() = isSupabaseConfigured()
    private val clientResult by lazy {
        runCatching {
            check(isConfigured) { "Set a HTTPS SUPABASE_URL and non-blank SUPABASE_ANON_KEY in local.properties, then rebuild the APK." }
            createSupabaseClient(SupabaseConfiguration.normalizeUrl(BuildConfig.SUPABASE_URL), SupabaseConfiguration.clean(BuildConfig.SUPABASE_ANON_KEY)) {
                install(Auth)
                install(Postgrest)
            }
        }.onFailure { Log.e(TAG, "Supabase client initialization failed", it) }
    }

    internal val client get() = if (isConfigured) clientResult.getOrNull() else null

    suspend fun signInOrSwitchGoogleAccount(activityContext: Context, forceAccountPicker: Boolean): Result<UserProfile> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return Result.failure(LocalAccountFallback())
        return try {
            val credentials = CredentialManager.create(activityContext)
            if (forceAccountPicker) credentials.clearCredentialState(ClearCredentialStateRequest())
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(SupabaseConfiguration.clean(BuildConfig.GOOGLE_WEB_CLIENT_ID))
                .setAutoSelectEnabled(false).build()
            val response = credentials.getCredential(context = activityContext,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = response.credential
            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            // Application scope survives AuthScreen leaving composition after activation.
            scope.async {
                completeGoogleSignIn(activityContext.applicationContext, google.displayName,
                    google.email ?: google.uniqueId, google.profilePictureUri?.toString(), exchangeToken = {
                    val cloud = clientResult.getOrThrow()
                    val userId = withTimeoutOrNull(15_000) {
                        cloud.auth.awaitInitialization()
                        cloud.auth.signInWith(IDToken) { idToken = google.idToken; provider = Google }
                        requireNotNull(cloud.auth.currentUserOrNull()) { "Supabase returned no authenticated user" }.id
                    }
                    checkNotNull(userId) { "Supabase Google authentication timed out after 15 seconds" }
                })
            }.await()
        } catch (e: CancellationException) { throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Credential Manager failed", e)
            Result.failure(IllegalStateException("Google sign-in is unavailable. Try email/password or offline mode.", e))
        } catch (e: Exception) { Log.e(TAG, "Google authentication failed", e); Result.failure(e) }
    }

    /** Credential validation has already succeeded. Cloud failure must not undo local access. */
    internal suspend fun completeGoogleSignIn(
        context: Context,
        displayName: String?,
        email: String,
        avatarUrl: String?,
        exchangeToken: suspend () -> String,
        database: TrackureDatabase = TrackureDatabase.getInstance(context),
        requestSync: () -> Unit = { launchSync(context) },
        clearStaleSession: suspend () -> Unit = { client?.auth?.clearSession(); Unit },
        reportCloudError: suspend (String) -> Unit = { message ->
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Google connected locally. Supabase: " + message, Toast.LENGTH_LONG).show()
            }
        },
    ): Result<UserProfile> = sessionMutex.withLock {
        val dao = database.trackureDao()
        val local = dao.profileFor(localId(email))
        val localProfile = UserProfileEntity(localId(email), displayName?.takeIf { it.isNotBlank() } ?: email,
            email, local?.motto ?: "One Sure Step Everyday", avatarUrl, System.currentTimeMillis())
        // Commit identity and the local workspace BEFORE attempting the cloud exchange.
        activate(context, localProfile, publishActive = false, transferLocalWorkspace = true, database = database)
        val userId = try {
            exchangeToken()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google cloud authentication failed; local Google profile remains active", e)
            // Do not let a stale session for another account be used by background sync.
            try { clearStaleSession() }
            catch (cleanup: Exception) { Log.e(TAG, "Could not clear stale cloud session", cleanup) }
            activate(context, localProfile, database = database)
            reportCloudError(e.message ?: e.toString())
            return@withLock Result.success(localProfile.toDomain())
        }
        val old = dao.profileFor(userId)
        val cloudProfile = localProfile.copy(id = userId, motto = old?.motto ?: localProfile.motto,
            updatedAt = maxOf(System.currentTimeMillis(), (old?.updatedAt ?: 0) + 1))
        activate(context, cloudProfile, database = database)
        requestSync()
        Log.d(TAG, "Google cloud profile activated; immediate sync requested")
        Result.success(cloudProfile.toDomain())
    }

    suspend fun connectLocalAccount(context: Context, name: String, link: String, motto: String) {
        require(name.isNotBlank() && link.isNotBlank()) { "Name and email or GitHub URL are required" }
        sessionMutex.withLock {
            clearCloudSession()
            activate(context, UserProfileEntity(localId(link), name.trim(), link.trim(), motto.trim(), null, System.currentTimeMillis()))
        }
    }

    private fun localId(link: String) = "local_" + UUID.nameUUIDFromBytes(link.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))

    private suspend fun activate(context: Context, profile: UserProfileEntity,
        publishActive: Boolean = true, transferLocalWorkspace: Boolean = !profile.id.startsWith("local_"),
        database: TrackureDatabase = TrackureDatabase.getInstance(context)) {
        val db = database
        val dao = db.trackureDao()
        val scheduler = AndroidLockscreenAlarmScheduler(context)
        dao.getAllActivitiesOnce().forEach {
            try { scheduler.cancelTrigger(it.id) }
            catch (e: Exception) { Log.e(TAG, "Alarm cancellation failed during account activation", e) }
        }
        db.withTransaction {
            val previous = dao.activeUserId().let {
                if (it == "signed_out") prefs(context).getString("upload_local_workspace", "signed_out")!! else it
            }
            // Move only the active local workspace. Never upload another cloud account's data.
            if (previous.startsWith("local_") && previous != profile.id && transferLocalWorkspace) {
                dao.activitiesFor(previous).forEach {
                    dao.upsertActivity(it.copy(ownerId = profile.id, syncStatus = if (it.syncStatus == "DELETED") "DELETED" else if (it.syncStatus == "PENDING_DELETE") "PENDING_DELETE" else "PENDING_UPLOAD"))
                }
                dao.logsFor(previous).forEach {
                    dao.upsertActivityLog(it.copy(ownerId = profile.id, syncStatus = if (it.syncStatus == "DELETED") "DELETED" else if (it.syncStatus == "PENDING_DELETE") "PENDING_DELETE" else "PENDING_UPLOAD"))
                }
                dao.removeWorkspaceActivities(previous)
                dao.removeWorkspaceLogs(previous)
            }
            dao.upsertUserProfile(profile)
            dao.setActiveAccount(ActiveAccountEntity(userId = profile.id))
        }
        prefs(context).edit().remove("upload_local_workspace").putBoolean("logged_out", false).putBoolean("local_active", profile.id.startsWith("local_")).commit()
        if (profile.id.startsWith("local_")) prefs(context).edit().putString("local_workspace", profile.id).commit()
        if (publishActive) active.value = true
        status.value = if (profile.id.startsWith("local_")) "Local Mode" else "Cloud sync pending"
        try { enqueueAlarmRecovery(context) }
        catch (e: Exception) { Log.e(TAG, "Alarm recovery enqueue failed after account activation", e) }
    }


    /** Shared by immediate writes, manual sync and WorkManager retries. */
    suspend fun syncAllPendingToSupabase(dao: TrackureDao): Boolean = sessionMutex.withLock {
        try {
            if (!active.value) return@withLock false
            val cloud = client ?: return@withLock false
            cloud.auth.awaitInitialization()
            val userId = cloud.auth.currentUserOrNull()?.id ?: return@withLock false
            if (dao.activeUserId() != userId || !active.value) return@withLock false
            status.value = "Syncing with Supabase..."
            Log.d(TAG, "Starting upload/download")
            dao.profileFor(userId)?.takeIf { it.syncStatus == "PENDING_UPLOAD" }?.let {
                Log.d(TAG, "Upsert profiles")
                cloud.from("profiles").upsert(it.toCloud())
                dao.markProfileSynced(it.id, it.updatedAt)
            }
            dao.activitiesFor(userId).forEach {
                when (it.syncStatus) {
                    "PENDING_UPLOAD" -> {
                        Log.d(TAG, "Upsert activities id=" + it.id)
                        cloud.from("activities").upsert(it.toCloud())
                        dao.markActivitySynced(it.id, userId, it.updatedAt)
                    }
                    "PENDING_DELETE" -> {
                        Log.d(TAG, "Delete activities id=" + it.id)
                        cloud.from("activities").delete { filter { eq("id", it.id); eq("user_id", userId) } }
                        dao.markActivityDeleted(it.id, userId, it.updatedAt)
                    }
                }
            }
            dao.logsFor(userId).forEach {
                when (it.syncStatus) {
                    "PENDING_UPLOAD" -> {
                        Log.d(TAG, "Upsert activity_logs id=" + it.id)
                        cloud.from("activity_logs").upsert(it.toCloud())
                        dao.markLogSynced(it.id, userId, it.updatedAt)
                    }
                    "PENDING_DELETE" -> {
                        cloud.from("activity_logs").delete { filter { eq("id", it.id); eq("user_id", userId) } }
                        dao.markLogDeleted(it.id, userId, it.updatedAt)
                    }
                }
            }
            cloud.from("profiles").select { filter { eq("id", userId) } }.decodeList<ProfileDto>()
                .filter { it.id == userId }.forEach { dao.mergeCloudProfile(it.toEntity()) }
            var offset = 0L
            do {
                val page = cloud.from("activities").select {
                    filter { eq("user_id", userId) }
                    order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    range(offset, offset + 499)
                }.decodeList<ActivityDto>()
                page.filter { it.userId == userId }.forEach { dao.mergeCloudActivity(it.toEntity()) }
                Log.d(TAG, "Pulled activities rows=" + page.size)
                offset += page.size
            } while (page.size == 500)
            offset = 0L
            do {
                val page = cloud.from("activity_logs").select {
                    filter { eq("user_id", userId) }
                    order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    range(offset, offset + 499)
                }.decodeList<ActivityLogDto>()
                page.filter { it.userId == userId }.forEach { dao.mergeCloudLog(it.toEntity()) }
                Log.d(TAG, "Pulled activity_logs rows=" + page.size)
                offset += page.size
            } while (page.size == 500)
            status.value = if (dao.getPendingSyncActivities().isEmpty() && dao.getPendingSyncLogs().isEmpty() &&
                dao.profileFor(userId)?.syncStatus != "PENDING_UPLOAD") "Cloud Synced (Supabase Active)" else "Cloud sync pending"
            Log.d(TAG, "Sync complete")
            true
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            status.value = "Cloud sync pending - retry available"
            Log.e(TAG, "Sync failed; local changes retained for retry", e)
            false
        }
    }

    private suspend fun clearCloudSession() {
        val cloud = client ?: return
        cloud.auth.awaitInitialization()
        try {
            withTimeoutOrNull(5_000) {
                try { cloud.auth.signOut() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Local logout must also work offline. */ }
            }
        } finally {
            withContext(NonCancellable) { cloud.auth.clearSession() }
        }
    }

    suspend fun signOut(context: Context) = withContext(NonCancellable + Dispatchers.IO) {
        prefs(context).edit().putBoolean("logged_out", true).putBoolean("local_active", false).commit()
        active.value = false
        status.value = "Local Mode"
        sessionMutex.withLock {
            val dao = TrackureDatabase.getInstance(context).trackureDao()
            try {
                val previous = dao.activeUserId()
                if (previous.startsWith("local_")) prefs(context).edit().putString("upload_local_workspace", previous).commit()
                val activities = dao.getAllActivitiesOnce()
                dao.setActiveAccount(ActiveAccountEntity(userId = "signed_out"))
                activities.forEach {
                    try { AndroidLockscreenAlarmScheduler(context).cancelTrigger(it.id) }
                    catch (e: Exception) { Log.e(TAG, "Alarm cancellation failed during logout", e) }
                }
            } finally {
                try { clearCloudSession() }
                finally {
                    try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
                    catch (e: Exception) { Log.e(TAG, "Credential provider could not clear state", e) }
                }
            }
            Log.d(TAG, "Local sign-out finished")
        }
    }
}
