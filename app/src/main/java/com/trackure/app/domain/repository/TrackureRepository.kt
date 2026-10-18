package com.trackure.app.domain.repository

import com.trackure.app.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/** Implementations publish immutable snapshots and throw on failed mutations (never fake success).
 * Replacing the implementation is a composition-root change, independent of Compose screens.
 */
interface TrackureRepository {
    val userProfile: StateFlow<UserProfile>
    val activities: StateFlow<List<ActivityItem>>
    val activityLogs: StateFlow<List<ActivityLog>>
    val streakMatrix: StateFlow<List<StreakDay>>
    suspend fun updateProfile(name: String, link: String, motto: String)
    suspend fun switchAccount()
    suspend fun logout()
    suspend fun saveActivity(item: ActivityItem)
    suspend fun logActivityCompletion(activityId: String, dateIso: String, hour: String, minute: String, note: String)
    /** Titles must resolve uniquely; ambiguous titles fail instead of updating another activity. */
    suspend fun saveOrUpdateNote(dateIso: String, activityTitle: String, noteContent: String)
    suspend fun importIcsContent(rawIcsText: String): Result<Int>
    suspend fun exportIcsContent(): Result<String>
    /** CSV, including a header; rangeDays includes the reference day. */
    suspend fun exportHistoryData(config: ExportHistoryConfig, rangeDays: Int): Result<String>
}
