package com.trackure.app.core.network

import android.content.Context
import androidx.work.*
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.util.enqueueAlarmRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

class TrackureSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        TrackureCloudManager.initialize(applicationContext)
        if (!TrackureCloudManager.isAuthenticatedOrLocalActive.value || TrackureDatabase.getInstance(applicationContext).trackureDao().activeUserId().startsWith("local_")) Result.success()
        else {
            val synced = withTimeoutOrNull(45_000) {
                TrackureCloudManager.syncAllPendingToSupabase(TrackureDatabase.getInstance(applicationContext).trackureDao())
            } ?: false
            if (synced) { enqueueAlarmRecovery(applicationContext); Result.success() }
            else Result.retry()
        }
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) {
        android.util.Log.e("TrackureSupabase", "Background sync failed", e)
        Result.retry()
    }

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
