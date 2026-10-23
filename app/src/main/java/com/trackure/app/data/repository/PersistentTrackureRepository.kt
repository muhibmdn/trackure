package com.trackure.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.trackure.app.core.database.*
import com.trackure.app.core.util.*
import com.trackure.app.domain.model.*
import com.trackure.app.domain.repository.TrackureRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock

/** Reads for mutations happen inside Room transactions, independently of UI flow latency. */
class PersistentTrackureRepository(
    private val database: TrackureDatabase,
    private val scope: CoroutineScope,
    private val dateProvider: () -> LocalDate = { LocalDate.now() },
    private val alarmScheduler: LockscreenAlarmScheduler = DeferredLockscreenAlarmScheduler,
    private val onDataChanged: () -> Unit = {},
    private val signOut: (suspend () -> Unit)? = null,
    private val onAlarmRecovery: () -> Unit = {},
    private val immediateSync: suspend (com.trackure.app.core.database.dao.TrackureDao) -> Boolean =
        com.trackure.app.core.network.TrackureCloudManager::syncAllPendingToSupabase,
) : TrackureRepository {
    companion object {
        @Volatile private var instance: PersistentTrackureRepository? = null
        fun getInstance(context: Context): PersistentTrackureRepository = instance ?: synchronized(this) {
            val appContext = context.applicationContext
            instance ?: PersistentTrackureRepository(TrackureDatabase.getInstance(appContext),
                CoroutineScope(SupervisorJob() + Dispatchers.IO), alarmScheduler = AndroidLockscreenAlarmScheduler(appContext),
                onDataChanged = { com.trackure.app.core.network.TrackureSyncWorker.enqueueOneTimeSync(appContext) },
                signOut = { com.trackure.app.core.network.TrackureCloudManager.signOut(appContext) },
                onAlarmRecovery = { enqueueAlarmRecovery(appContext) }).also { repository ->
                    instance = repository
                    repository.scope.launch {
                        com.trackure.app.core.network.TrackureCloudManager.initialize(appContext)
                        repository.requestSync()
                    }
                }
        }
        private val defaultProfile = UserProfile(id = "local_user", name = "Muhib Mdn",
            profileUrlOrEmail = "https://github.com/muhibmdn", motto = "One Sure Step Everyday")
    }
    override val isAuthenticatedOrLocalActive = com.trackure.app.core.network.TrackureCloudManager.isAuthenticatedOrLocalActive
    val syncStatus = com.trackure.app.core.network.TrackureCloudManager.syncStatus
    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        com.trackure.app.core.network.TrackureCloudManager.sessionMutex.withLock {
            dao.markWorkspacePendingUpload(dao.activeUserId())
        }
        immediateSync(dao)
    }
    suspend fun continueOffline(context: Context) = com.trackure.app.core.network.TrackureCloudManager.continueOffline(context)
    suspend fun authenticateEmail(context: Context, email: String, password: String, name: String, register: Boolean) =
        com.trackure.app.core.network.TrackureCloudManager.authenticateEmail(context, email, password, name, register)
    override suspend fun deleteActivity(id: String) {
        persistAndSync { dao.deleteActivity(id) }
        withContext(Dispatchers.IO) { alarmScheduler.cancelTrigger(id) }
    }
    private val dao = database.trackureDao()
    private fun nextTimestamp(previous: String?) = Instant.ofEpochMilli(
        maxOf(System.currentTimeMillis(), previous?.let { Instant.parse(it).toEpochMilli() + 1 } ?: 0)
    ).toString()
    // Enqueue only after the transaction commits. Failed scheduling never rolls back user data.
    private fun requestSync() {
        scope.launch(Dispatchers.IO) {
            immediateSync(dao)
        }
        try { onDataChanged() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.w("TrackureRepository", "Sync enqueue failed; pending data will retry at startup", e) }
    }
    private suspend fun <T> persistAndSync(block: suspend () -> T): T {
        val result = database.withTransaction { block() }
        requestSync()
        return result
    }

    suspend fun switchGoogleAccount(activityContext: Context): Result<UserProfile> =
        signInGoogleAccount(activityContext, forceAccountPicker = true)

    suspend fun signInGoogleAccount(activityContext: Context, forceAccountPicker: Boolean = false): Result<UserProfile> =
        // The cloud manager commits the profile/account transition to Room and enqueues sync.
        com.trackure.app.core.network.TrackureCloudManager.signInOrSwitchGoogleAccount(activityContext, forceAccountPicker)

    suspend fun connectLocalAccount(context: Context, name: String, link: String, motto: String) =
        com.trackure.app.core.network.TrackureCloudManager.connectLocalAccount(context.applicationContext, name, link, motto)

    override val userProfile = dao.observeUserProfile().map { it?.toDomain() ?: defaultProfile }
        .stateIn(scope, SharingStarted.Eagerly, defaultProfile)
    override val activities = dao.observeAllActivities().map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val activityLogs = dao.observeAllLogs().map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val dates = flow {
        while (currentCoroutineContext().isActive) { emit(dateProvider()); delay(60_000) }
    }.distinctUntilChanged()
    private val evaluator = ScheduleEvaluator()
    private val streakState = combine(activityLogs, dates) { logs, date ->
        evaluator.calculateStreakMatrix(logs, date)
    }.stateIn(scope, SharingStarted.Eagerly, evaluator.calculateStreakMatrix(emptyList(), dateProvider()))
    override val streakMatrix = streakState.map { it.second }
        .stateIn(scope, SharingStarted.Eagerly, streakState.value.second)
    val activeStreakCount: StateFlow<Int> = streakState.map { it.first }
        .stateIn(scope, SharingStarted.Eagerly, streakState.value.first)

    override suspend fun updateProfile(name: String, link: String, motto: String) = persistAndSync {
        require(name.isNotBlank()) { "Name is required" }
        val old = dao.getUserProfileOnce()?.toDomain() ?: defaultProfile
        dao.upsertUserProfile(old.copy(name = name.trim(), profileUrlOrEmail = link.trim(), motto = motto.trim(),
            updatedAt = nextTimestamp(old.updatedAt), syncStatus = SyncStatus.PENDING_UPLOAD).toEntity())
    }
    override suspend fun switchAccount(): Unit = throw com.trackure.app.core.network.LocalAccountFallback()
    override suspend fun logout() {
        signOut?.invoke() ?: database.withTransaction { dao.setActiveAccount(com.trackure.app.core.database.entity.ActiveAccountEntity()) }
    }

    override suspend fun saveActivity(item: ActivityItem) {
        val saved = persistAndSync {
            require(item.title.isNotBlank())
            LocalTime.parse(item.scheduledTime)
            LocalDate.parse(item.startDateIso)
            require(item.intervalCount > 0 && item.repeatDaysOfWeek.all { it in 1..7 })
            if (item.frequency == RecurrenceFreq.SPECIFIC_DATE) LocalDate.parse(requireNotNull(item.specificDate))
            item.specificDate?.let(LocalDate::parse)
            if (item.endConditionType == EndConditionType.UNTIL_DATE) LocalDate.parse(requireNotNull(item.endDate))
            if (item.endConditionType == EndConditionType.AFTER_OCCURRENCES) require((item.endOccurrences ?: 0) > 0)
            val old = dao.getAllActivitiesOnce().find { it.id == item.id }
            val entity = item.copy(startDateIso = old?.startDateIso ?: item.startDateIso,
                updatedAt = nextTimestamp(old?.let { Instant.ofEpochMilli(it.updatedAt).toString() }), syncStatus = SyncStatus.PENDING_UPLOAD).toEntity().copy(ownerId = dao.activeUserId())
            dao.upsertActivity(entity)
            entity.toDomain()
        }
        try {
            withContext(Dispatchers.IO) {
                if (saved.fullScreenLockscreenTrigger) alarmScheduler.scheduleFullScreenTrigger(saved)
                else alarmScheduler.cancelTrigger(saved.id)
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            onAlarmRecovery()
            throw IllegalStateException("Activity saved; alarm could not be scheduled. Recovery will retry.", e)
        }
    }

    override suspend fun logActivityCompletion(activityId: String, dateIso: String, hour: String, minute: String, note: String) {
        val completed = persistAndSync {
            LocalDate.parse(dateIso)
            val time = LocalTime.of(hour.toInt(), minute.toInt())
            val item = dao.getAllActivitiesOnce().single { it.id == activityId }.toDomain()
            val old = dao.getLogForActivityOnDate(activityId, dateIso)?.toDomain()
                ?: ActivityLog(activityId = item.id, activityTitle = item.title, category = item.category, scheduledTime = item.scheduledTime, dateIso = dateIso)
            dao.upsertActivityLog(old.copy(isCompleted = true, completedHour = time.hour.toString().padStart(2, '0'),
                completedMinute = time.minute.toString().padStart(2, '0'), note = note,
                updatedAt = nextTimestamp(old.updatedAt), syncStatus = SyncStatus.PENDING_UPLOAD).toEntity().copy(ownerId = dao.activeUserId()))
            item
        }
        try {
            withContext(Dispatchers.IO) { alarmScheduler.onActivityCompleted(completed, dateIso) }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { onAlarmRecovery() }
    }

    override suspend fun saveOrUpdateNote(dateIso: String, activityTitle: String, noteContent: String, originalLogId: String?) = persistAndSync {
        LocalDate.parse(dateIso)
        val logs = dao.getAllLogsOnce().map { it.toDomain() }
        val items = dao.getAllActivitiesOnce().map { it.toDomain() }
        val original = originalLogId?.let { id -> requireNotNull(logs.find { it.id == id }) { "Original note is no longer available" } }
        val general = activityTitle == GeneralDailyNote.TITLE
        val unchanged = original != null && original.activityTitle == activityTitle
        val item = if (general) null else if (unchanged) items.find { it.id == original.activityId }
            else items.filter { it.title == activityTitle }.let { matches ->
                require(matches.size == 1) { "Activity name must match one activity; rename duplicate activities first" }
                matches.single()
            }
        val activityId = if (general) GeneralDailyNote.ACTIVITY_ID else item?.id ?: requireNotNull(original).activityId
        val target = dao.getLogForActivityOnDate(activityId, dateIso)?.toDomain()
        if (original != null && target?.id != original.id && target?.syncStatus != SyncStatus.PENDING_DELETE)
            require(target?.note.isNullOrBlank()) { "The selected activity and date already have a note. Edit that note instead." }
        val timestamp = nextTimestamp(listOfNotNull(original?.updatedAt, target?.updatedAt).maxOrNull())
        val saved = (target ?: ActivityLog(activityId = activityId, activityTitle = item?.title ?: activityTitle,
            category = item?.category, scheduledTime = item?.scheduledTime.orEmpty(), dateIso = dateIso))
            .copy(note = noteContent, updatedAt = timestamp, syncStatus = SyncStatus.PENDING_UPLOAD)
        if (original != null && original.id != saved.id)
            dao.upsertActivityLog(original.copy(note = null, updatedAt = timestamp, syncStatus = SyncStatus.PENDING_UPLOAD).toEntity().copy(ownerId = dao.activeUserId()))
        dao.upsertActivityLog(saved.toEntity().copy(ownerId = dao.activeUserId()))
    }

    override suspend fun importIcsContent(rawIcsText: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val imported = IcsCalendarHelper().parseIcsToActivities(rawIcsText)
            persistAndSync { dao.upsertActivities(imported.map { it.toEntity().copy(ownerId = dao.activeUserId()) }) }
            // Persist the complete import before scheduling. Stable UID-based IDs make retries idempotent.
            var failed = 0
            imported.forEach { item ->
                try { alarmScheduler.scheduleFullScreenTrigger(item) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed++ }
            }
            check(failed == 0) { "Imported ${imported.size} activities, but $failed alarms could not be scheduled. Reopen Trackure to retry alarms." }
            Result.success(imported.size)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun exportIcsContent(): Result<String> = withContext(Dispatchers.IO) {
        try { Result.success(IcsCalendarHelper().generateIcsFromActivities(dao.getAllActivitiesOnce().map { it.toDomain() })) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun exportHistoryData(config: ExportHistoryConfig, rangeDays: Int): Result<String> = withContext(Dispatchers.IO) {
        try { Result.success(HistoryExportHelper(dateProvider()).buildHistoryExportContent(dao.getAllLogsOnce().map { it.toDomain() }, config, rangeDays)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
}
