package com.trackure.app.presentation.history

import kotlinx.coroutines.CoroutineScope
import com.trackure.app.domain.model.ActivityLog
import com.trackure.app.domain.model.ExportHistoryConfig
import com.trackure.app.domain.model.SyncStatus
import com.trackure.app.domain.repository.TrackureRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryRange(val label: String, val days: Int) {
    WEEK("7 Days", 7), MONTH("30 Days", 30), QUARTER("3 Months", 90), YEAR("1 Year", 365)
}

data class HistoryDay(val date: LocalDate, val completed: Int, val total: Int, val rollingVelocity: Float) {
    val percent: Int get() = if (total == 0) 0 else (completed * 100f / total).roundToInt()
}

data class HistoryUiState(val range: HistoryRange, val averageCompletion: Int, val totalCompleted: Int, val days: List<HistoryDay>)

/** Completion rates use recorded occurrences, not today's schedule projected into the past.
 * Days without occurrence logs are excluded from the average; rolling counts use zero for them.
 */
internal fun historyState(logs: List<ActivityLog>, today: LocalDate, range: HistoryRange): HistoryUiState {
    val unique = logs.filter { it.activityId != com.trackure.app.domain.model.GeneralDailyNote.ACTIVITY_ID && it.syncStatus != SyncStatus.PENDING_DELETE && runCatching { LocalDate.parse(it.dateIso) }.isSuccess }
        .groupBy { it.activityId to it.dateIso }.values.map { versions -> versions.maxBy { it.updatedAt } }
        .filter { LocalDate.parse(it.dateIso) <= today }
    val byDay = unique.groupBy { LocalDate.parse(it.dateIso) }
    val start = today.minusDays(range.days.toLong() - 1)
    val window = byDay.filterKeys { it in start..today }
    val average = window.values.map { entries -> entries.count { it.isCompleted } * 100.0 / entries.size }.average()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = List(7) { offset ->
        val date = monday.plusDays(offset.toLong())
        val entries = byDay[date].orEmpty()
        val velocity = if (date > today) 0f else (0..6).sumOf { back -> byDay[date.minusDays(back.toLong())].orEmpty().count { it.isCompleted } } / 7f
        HistoryDay(date, entries.count { it.isCompleted }, entries.size, velocity)
    }
    return HistoryUiState(range, if (average.isNaN()) 0 else average.roundToInt(), window.values.sumOf { entries -> entries.count { it.isCompleted } }, days)
}

sealed interface HistoryEvent {
    data object ExportReady : HistoryEvent
    data object Exported : HistoryEvent
    data class Error(val message: String) : HistoryEvent
}

class HistoryController(private val repository: TrackureRepository, today: LocalDate, private val scope: CoroutineScope) {
    private val range = MutableStateFlow(HistoryRange.MONTH)
    val uiState = combine(repository.activityLogs, range) { logs, selected -> historyState(logs, today, selected) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), historyState(repository.activityLogs.value, today, range.value))
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val eventsChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private var pendingCsv: String? = null
    fun selectRange(value: HistoryRange) { if (!working.value) range.value = value }
    fun cancelExport() { pendingCsv = null; working.value = false }
    fun prepareExport(config: ExportHistoryConfig) {
        if (working.value) return
        working.value = true
        val selectedRangeDays = range.value.days
        scope.launch {
            try {
                pendingCsv = repository.exportHistoryData(config, selectedRangeDays).getOrThrow()
                eventsChannel.send(HistoryEvent.ExportReady)
            } catch (e: CancellationException) { cancelExport(); throw e
            } catch (e: Exception) { cancelExport(); eventsChannel.send(HistoryEvent.Error(e.message ?: "Unable to export history")) }
        }
    }
    fun writeExport(write: suspend (String) -> Unit) {
        val content = pendingCsv ?: return
        pendingCsv = null // Consume once, including duplicate picker callbacks.
        scope.launch {
            try {
                withContext(Dispatchers.IO) { write(content) }
                eventsChannel.send(HistoryEvent.Exported)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { eventsChannel.send(HistoryEvent.Error(e.message ?: "Unable to save history file"))
            } finally { working.value = false }
        }
    }
}
