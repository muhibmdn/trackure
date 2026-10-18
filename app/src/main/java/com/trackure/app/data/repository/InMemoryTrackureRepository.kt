package com.trackure.app.data.repository

import com.trackure.app.domain.model.*
import com.trackure.app.domain.repository.TrackureRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Demo-only storage. The factory supports isolated tests; the app uses [shared]. */
class InMemoryTrackureRepository private constructor(private val today: LocalDate) : TrackureRepository {
    companion object {
        val demoDate: LocalDate = LocalDate.of(2026, 10, 22)
        val shared: TrackureRepository by lazy { create() }
        fun create(today: LocalDate = demoDate) = InMemoryTrackureRepository(today)
    }
    private val mutex = Mutex()
    private val profile = MutableStateFlow(UserProfile(name = "Muhib Mdn", profileUrlOrEmail = "https://github.com/muhibmdn", motto = "One Sure Step Everyday"))
    override val userProfile = profile.asStateFlow()
    private val items = MutableStateFlow(listOf(
        ActivityItem(title = "Morning Gym Session", scheduledTime = "07:00", category = "Strength"),
        ActivityItem(title = "Algoritma & Struktur Data", scheduledTime = "09:00", category = "Academics"),
        ActivityItem(title = "Easy 5K Recovery Run", scheduledTime = "16:30", category = "Cardio"),
        ActivityItem(title = "Review Catatan Kuliah & Prep", scheduledTime = "20:00", category = "Deep Work"),
        ActivityItem(title = "Mobility & Sleep Routine", scheduledTime = "22:00", category = "Wellness"),
    ))
    override val activities = items.asStateFlow()
    private val logs = MutableStateFlow(items.value.mapIndexed { index, item ->
        val times = listOf("07:12", "09:05", "17:15")
        val notes = listOf("Upper body push & pull. Core stability.", "Binary search tree & recursion review completed.", "5.02 km @ 5:14/km. Heart rate avg 142 bpm. Felt smooth.")
        ActivityLog(activityId = item.id, activityTitle = item.title, category = item.category,
            scheduledTime = item.scheduledTime, dateIso = today.toString(), isCompleted = index < 3,
            completedHour = times.getOrNull(index)?.substringBefore(":"),
            completedMinute = times.getOrNull(index)?.substringAfter(":"), note = notes.getOrNull(index))
    })
    override val activityLogs = logs.asStateFlow()
    private val matrix = MutableStateFlow(List(112) { index ->
        val date = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(15).plusDays(index.toLong())
        val level = when {
            date > today -> 0
            date == today -> 3
            date == today.minusDays(24) -> 0
            date > today.minusDays(24) -> if (index % 3 == 0) 1 else 2
            index % 9 == 0 -> 0
            else -> if (index % 3 == 0) 1 else 2
        }
        StreakDay(date.toString(), level)
    })
    override val streakMatrix = matrix.asStateFlow()

    override suspend fun updateProfile(name: String, link: String, motto: String) = mutex.withLock {
        require(name.isNotBlank()) { "Name is required" }
        profile.value = profile.value.copy(name = name.trim(), profileUrlOrEmail = link.trim(), motto = motto.trim(), updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD)
    }
    override suspend fun switchAccount(): Unit = throw UnsupportedOperationException("Account switching will be available when authentication is connected")
    override suspend fun logout(): Unit = throw UnsupportedOperationException("Sign out will be available when authentication is connected")

    override suspend fun saveActivity(item: ActivityItem) = mutex.withLock {
        require(item.title.isNotBlank())
        LocalTime.parse(item.scheduledTime)
        require(item.intervalCount > 0 && item.repeatDaysOfWeek.all { it in 1..7 })
        if (item.endConditionType == EndConditionType.UNTIL_DATE) LocalDate.parse(requireNotNull(item.endDate))
        if (item.endConditionType == EndConditionType.AFTER_OCCURRENCES) require((item.endOccurrences ?: 0) > 0)
        val saved = item.copy(repeatDaysOfWeek = item.repeatDaysOfWeek.toSet(), updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD)
        items.value = if (items.value.any { it.id == item.id }) items.value.map { if (it.id == item.id) saved else it } else items.value + saved
    }
    override suspend fun logActivityCompletion(activityId: String, dateIso: String, hour: String, minute: String, note: String) = mutex.withLock {
        LocalDate.parse(dateIso)
        val time = LocalTime.of(hour.toInt(), minute.toInt())
        val item = items.value.single { it.id == activityId && it.syncStatus != SyncStatus.PENDING_DELETE }
        val old = logs.value.find { it.activityId == activityId && it.dateIso == dateIso }
            ?: ActivityLog(activityId = item.id, activityTitle = item.title, category = item.category, scheduledTime = item.scheduledTime, dateIso = dateIso)
        val saved = old.copy(isCompleted = true, completedHour = "%02d".format(time.hour), completedMinute = "%02d".format(time.minute), note = note, updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD)
        logs.value = logs.value.filterNot { it.id == old.id } + saved
        val count = logs.value.count { it.dateIso == dateIso && it.isCompleted && it.syncStatus != SyncStatus.PENDING_DELETE }
        matrix.value = matrix.value.map { if (it.dateIso == dateIso) it.copy(level = count.coerceIn(0, 3), updatedAt = saved.updatedAt, syncStatus = SyncStatus.PENDING_UPLOAD) else it }
    }
    override suspend fun saveOrUpdateNote(dateIso: String, activityTitle: String, noteContent: String) = mutex.withLock {
        LocalDate.parse(dateIso)
        val item = items.value.single { it.title == activityTitle && it.syncStatus != SyncStatus.PENDING_DELETE }
        val old = logs.value.find { it.activityId == item.id && it.dateIso == dateIso }
            ?: ActivityLog(activityId = item.id, activityTitle = item.title, category = item.category, scheduledTime = item.scheduledTime, dateIso = dateIso)
        logs.value = logs.value.filterNot { it.id == old.id } + old.copy(note = noteContent, updatedAt = Instant.now().toString(), syncStatus = SyncStatus.PENDING_UPLOAD)
    }
    override suspend fun importIcsContent(rawIcsText: String): Result<Int> = Result.failure(UnsupportedOperationException("ICS import is reserved for the Biweekly adapter"))
    override suspend fun exportIcsContent(): Result<String> = Result.failure(UnsupportedOperationException("ICS export is reserved for the Biweekly adapter"))
    override suspend fun exportHistoryData(config: ExportHistoryConfig, rangeDays: Int): Result<String> = runCatching {
        require(rangeDays > 0)
        val header = buildList {
            add("Date"); add("Completed")
            if (config.includeActivityName) add("Activity")
            if (config.includeCompletedTime) add("Completed time")
            if (config.includeNote) add("Note")
        }
        val rows = logs.value.filter { LocalDate.parse(it.dateIso) in today.minusDays(rangeDays.toLong() - 1)..today && it.syncStatus != SyncStatus.PENDING_DELETE }.map { log ->
            buildList {
                add(log.dateIso); add(log.isCompleted.toString())
                if (config.includeActivityName) add(log.activityTitle)
                if (config.includeCompletedTime) add(if (log.isCompleted) "${log.completedHour.orEmpty()}:${log.completedMinute.orEmpty()}" else "")
                if (config.includeNote) add(log.note.orEmpty())
            }
        }
        (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }
    }
}
