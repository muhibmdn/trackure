package com.trackure.app.core.network

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.database.entity.*
import com.trackure.app.core.util.enqueueAlarmRecovery
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/** Owner columns are separate from the payload and enforced by PostgreSQL RLS. */
@Serializable
internal data class CloudRow(val id: String, val owner_id: String, val payload: JsonObject,
    val updated_at: Long, val deleted: Boolean = false)

class TrackureSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        withTimeoutOrNull(45_000) {
            TrackureCloudManager.sessionMutex.withLock {
                val cloud = TrackureCloudManager.client ?: return@withLock Result.success()
                cloud.auth.awaitInitialization()
                val owner = cloud.auth.currentUserOrNull()?.id ?: return@withLock Result.success()
                val db = TrackureDatabase.getInstance(applicationContext)
                val dao = db.trackureDao()
                if (dao.activeUserId() != owner) return@withLock Result.success()
                val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                dao.profileFor(owner)?.takeIf { it.syncStatus == "PENDING_UPLOAD" }?.let {
                    cloud.from("profiles").upsert(CloudRow(owner, owner, json.encodeToJsonElement(it).jsonObject, it.updatedAt))
                    dao.markProfileSynced(owner, it.updatedAt)
                }
                dao.activitiesFor(owner).filter { it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "PENDING_DELETE" }.forEach {
                    cloud.from("activities").upsert(CloudRow(it.id, owner, json.encodeToJsonElement(it).jsonObject, it.updatedAt, it.syncStatus == "PENDING_DELETE"))
                    if (it.syncStatus == "PENDING_DELETE") dao.markActivityDeleted(it.id, owner, it.updatedAt)
                    else dao.markActivitySynced(it.id, owner, it.updatedAt)
                }
                dao.logsFor(owner).filter { it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "PENDING_DELETE" }.forEach {
                    cloud.from("activity_logs").upsert(CloudRow(it.id, owner, json.encodeToJsonElement(it).jsonObject, it.updatedAt, it.syncStatus == "PENDING_DELETE"))
                    if (it.syncStatus == "PENDING_DELETE") dao.markLogDeleted(it.id, owner, it.updatedAt)
                    else dao.markLogSynced(it.id, owner, it.updatedAt)
                }
                // Pull on every sync, including login/switch. Page beyond PostgREST's default row limit.
                suspend fun pull(table: String): List<CloudRow> {
                    val all = mutableListOf<CloudRow>()
                    var offset = 0L
                    while (true) {
                        val page = cloud.from(table).select {
                            filter { eq("owner_id", owner) }
                            order("id", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                            range(offset, offset + 499)
                        }.decodeList<CloudRow>()
                        all.addAll(page)
                        if (page.size < 500) return all
                        offset += page.size
                    }
                }
                val activities = pull("activities")
                val logs = pull("activity_logs")
                db.withTransaction {
                    val localActivities = dao.activitiesFor(owner).associateBy { it.id }
                    activities.filter { it.owner_id == owner }.forEach { row ->
                        val old = localActivities[row.id]
                        if (canRestore(old?.syncStatus, old?.updatedAt, row.updated_at)) {
                            dao.upsertActivity(json.decodeFromJsonElement<ActivityEntity>(row.payload).copy(
                                id = row.id, ownerId = owner, updatedAt = row.updated_at, syncStatus = if (row.deleted) "DELETED" else "SYNCED"))
                        }
                    }
                    logs.filter { it.owner_id == owner }.forEach { row ->
                        val incoming = json.decodeFromJsonElement<ActivityLogEntity>(row.payload)
                        val old = dao.logsFor(owner).find { it.id == row.id || (it.activityId == incoming.activityId && it.dateIso == incoming.dateIso) }
                        if (canRestore(old?.syncStatus, old?.updatedAt, row.updated_at)) {
                            if (old != null && old.id != row.id) dao.removeLog(old.id, owner)
                            dao.upsertActivityLog(incoming.copy(id = row.id, ownerId = owner, updatedAt = row.updated_at,
                                syncStatus = if (row.deleted) "DELETED" else "SYNCED"))
                        }
                    }
                }
                enqueueAlarmRecovery(applicationContext)
                Result.success()
            }
        } ?: Result.retry()
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) { Result.retry() }

    companion object {
        internal fun canRestore(status: String?, localVersion: Long?, remoteVersion: Long): Boolean =
            status == null || ((status == "SYNCED" || status == "DELETED") && remoteVersion >= (localVersion ?: 0))

        fun enqueueOneTimeSync(context: Context) {
            if (!TrackureCloudManager.isConfigured) return
            val request = OneTimeWorkRequestBuilder<TrackureSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("trackure-cloud-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
