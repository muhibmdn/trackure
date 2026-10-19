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

/** Reads for mutations happen inside Room transactions, independently of UI flow latency. */
class RoomTrackureRepository(
    private val database: TrackureDatabase,
    scope: CoroutineScope,
    private val dateProvider: () -> LocalDate = { LocalDate.now() },
    private val alarmScheduler: LockscreenAlarmScheduler = DeferredLockscreenAlarmScheduler,
) : TrackureRepository {
    companion object {
        @Volatile private var instance: RoomTrackureRepository? = null
        fun getInstance(context: Context): RoomTrackureRepository = instance ?: synchronized(this) {
            instance ?: RoomTrackureRepository(TrackureDatabase.getInstance(context),
                CoroutineScope(SupervisorJob() + Dispatchers.IO), alarmScheduler = AndroidLockscreenAlarmScheduler(context)).also { instance = it }
        }
        private val defaultProfile = UserProfile(id = "local_user", name = "Muhib Mdn",
            profileUrlOrEmail = "https://github.com/muhibmdn", motto = "One Sure Step Everyday")
    }
    private val dao = database.trackureDao()
    override val userProfile = dao.observeUserProfile().map { it?.toDomain() ?: defaultProfile }
        .stateIn(scope, SharingStarted.Eagerly, defaultProfile)
    override val activities = dao.observeAllActivities().map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    override val activityLogs = dao.observeAllLogs().map { rows -> rows.map { it.toDomain() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val dates = flow {
        while (currentCoroutineContext().isActive) { emit(dateProvider()); delay(60_000) }
    }.distinctUntilChanged()
    override val streakMatrix = combine(activityLogs, dates) { logs, date ->
        ScheduleEvaluator().calculateStreakMatrix(logs, date).second
    }.stateIn(scope, SharingStarted.Eagerly, ScheduleEvaluator().calculateStreakMatrix(emptyList(), dateProvider()).second)

    override suspend fun updateProfile(name: String, link: String, motto: String) = database.withTransaction {
        require(name.isNotBlank()) { "Name is required" }
        val old = dao.getUserProfileOnce()?.toDomain() ?: defaultProfile
        dao.upsertUserProfile(old.copy(name = name.trim(), profileUrlOrEmail = link.trim(), motto = motto.trim(),
            updatedAt = Instant.now().toString()).toEntity())
    }
    override suspend fun switchAccount(): Unit = throw UnsupportedOperationException("Account switching will be available when authentication is connected")
    override suspend fun logout(): Unit = throw UnsupportedOperationException("Sign out will be available when authentication is connected")

    override suspend fun saveActivity(item: ActivityItem) = database.withTransaction {
        require(item.title.isNotBlank())
        LocalTime.parse(item.scheduledTime)
        LocalDate.parse(item.startDateIso)
        require(item.intervalCount > 0 && item.repeatDaysOfWeek.all { it in 1..7 })
        if (item.frequency == RecurrenceFreq.SPECIFIC_DATE) LocalDate.parse(requireNotNull(item.specificDate))
        item.specificDate?.let(LocalDate::parse)
        if (item.endConditionType == EndConditionType.UNTIL_DATE) LocalDate.parse(requireNotNull(item.endDate))
        if (item.endConditionType == EndConditionType.AFTER_OCCURRENCES) require((item.endOccurrences ?: 0) > 0)
        val old = dao.getAllActivitiesOnce().find { it.id == item.id }
        dao.upsertActivity(item.copy(startDateIso = old?.startDateIso ?: item.startDateIso,
            updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD).toEntity())
    }

    override suspend fun logActivityCompletion(activityId: String, dateIso: String, hour: String, minute: String, note: String) = database.withTransaction {
        LocalDate.parse(dateIso)
        val time = LocalTime.of(hour.toInt(), minute.toInt())
        val item = dao.getAllActivitiesOnce().single { it.id == activityId }.toDomain()
        val old = dao.getLogForActivityOnDate(activityId, dateIso)?.toDomain()
            ?: ActivityLog(activityId = item.id, activityTitle = item.title, category = item.category, scheduledTime = item.scheduledTime, dateIso = dateIso)
        dao.upsertActivityLog(old.copy(isCompleted = true, completedHour = time.hour.toString().padStart(2, '0'),
            completedMinute = time.minute.toString().padStart(2, '0'), note = note,
            updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD).toEntity())
    }

    override suspend fun saveOrUpdateNote(dateIso: String, activityTitle: String, noteContent: String, originalLogId: String?) = database.withTransaction {
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
        val timestamp = Instant.now().toString()
        val saved = (target ?: ActivityLog(activityId = activityId, activityTitle = item?.title ?: activityTitle,
            category = item?.category, scheduledTime = item?.scheduledTime.orEmpty(), dateIso = dateIso))
            .copy(note = noteContent, updatedAt = timestamp, syncStatus = SyncStatus.PENDING_UPLOAD)
        if (original != null && original.id != saved.id)
            dao.upsertActivityLog(original.copy(note = null, updatedAt = timestamp, syncStatus = SyncStatus.PENDING_UPLOAD).toEntity())
        dao.upsertActivityLog(saved.toEntity())
    }

    override suspend fun importIcsContent(rawIcsText: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val imported = IcsCalendarHelper().parseIcsToActivities(rawIcsText)
            database.withTransaction { dao.upsertActivities(imported.map { it.toEntity() }) }
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
