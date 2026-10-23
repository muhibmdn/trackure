package com.trackure.app.core.network

import android.content.Context
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
    internal val sessionMutex = Mutex()
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.trim().isNotEmpty() && BuildConfig.SUPABASE_ANON_KEY.trim().isNotEmpty()
    internal val client by lazy {
        if (!isConfigured) null else runCatching {
            createSupabaseClient(BuildConfig.SUPABASE_URL.trim(), BuildConfig.SUPABASE_ANON_KEY.trim()) {
                install(Auth)
                install(Postgrest)
            }
        }.getOrNull()
    }

    suspend fun signInOrSwitchGoogleAccount(activityContext: Context, forceAccountPicker: Boolean): Result<UserProfile> {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) return Result.failure(LocalAccountFallback())
        return try {
            val credentials = CredentialManager.create(activityContext)
            if (forceAccountPicker) credentials.clearCredentialState(ClearCredentialStateRequest())
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID.trim())
                .setAutoSelectEnabled(false).build()
            val response = credentials.getCredential(context = activityContext,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = response.credential
            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val profile = sessionMutex.withLock {
                val cloud = client
                val id = if (cloud != null) {
                    cloud.auth.awaitInitialization()
                    val signedIn = withTimeoutOrNull(15_000) {
                        cloud.auth.signInWith(IDToken) { idToken = google.idToken; provider = Google }
                        true
                    } ?: false
                    check(signedIn) { "Cloud authentication is unavailable" }
                    requireNotNull(cloud.auth.currentUserOrNull()).id
                } else {
                    check(!isConfigured) { "Cloud configuration is invalid" }
                    localId(google.email ?: google.uniqueId)
                }
                val db = TrackureDatabase.getInstance(activityContext)
                val dao = db.trackureDao()
                val old = dao.profileFor(id)
                val email = google.email ?: google.uniqueId
                val row = UserProfileEntity(id, google.displayName ?: email, email,
                    old?.motto ?: "One Sure Step Everyday", google.profilePictureUri?.toString(), System.currentTimeMillis())
                activate(activityContext, row)
                row.toDomain()
            }
            // Authentication succeeds even when PostgREST is temporarily unavailable.
            TrackureSyncWorker.enqueueOneTimeSync(activityContext)
            Result.success(profile)
        } catch (e: CancellationException) { throw e
        } catch (e: GetCredentialException) { Result.failure(LocalAccountFallback(e))
        } catch (e: Exception) { Result.failure(LocalAccountFallback(e)) }
    }

    suspend fun connectLocalAccount(context: Context, name: String, link: String, motto: String) {
        require(name.isNotBlank() && link.isNotBlank()) { "Name and email or GitHub URL are required" }
        sessionMutex.withLock {
            clearCloudSession()
            activate(context, UserProfileEntity(localId(link), name.trim(), link.trim(), motto.trim(), null, System.currentTimeMillis()))
        }
    }

    private fun localId(link: String) = "local_" + UUID.nameUUIDFromBytes(link.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))

    private suspend fun activate(context: Context, profile: UserProfileEntity) {
        val db = TrackureDatabase.getInstance(context)
        val dao = db.trackureDao()
        val scheduler = AndroidLockscreenAlarmScheduler(context)
        dao.getAllActivitiesOnce().forEach { scheduler.cancelTrigger(it.id) }
        db.withTransaction {
            dao.upsertUserProfile(profile)
            dao.setActiveAccount(ActiveAccountEntity(userId = profile.id))
        }
        enqueueAlarmRecovery(context)
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

    suspend fun signOut(context: Context) = sessionMutex.withLock {
        // Always leave the authenticated workspace, including when the device is offline.
        val dao = TrackureDatabase.getInstance(context).trackureDao()
        val guest = dao.profileFor("local_user") ?: UserProfileEntity("local_user", "Guest", "", "", null, System.currentTimeMillis())
        activate(context, guest)
        clearCloudSession()
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Play Services may not exist on an offline emulator. */ }
    }
}
