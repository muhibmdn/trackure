package com.trackure.app.presentation.add

import kotlinx.coroutines.CoroutineScope
import com.trackure.app.domain.model.*
import com.trackure.app.domain.repository.TrackureRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ActivityEditorMode {
    data object Add : ActivityEditorMode
    data class Edit(val activity: ActivityItem) : ActivityEditorMode
}

internal fun validateActivity(item: ActivityItem): String? = when {
    item.title.isBlank() -> "Enter an activity name"
    !Regex("\\d{2}:\\d{2}").matches(item.scheduledTime) || runCatching { LocalTime.parse(item.scheduledTime) }.isFailure -> "Enter a valid time (HH:mm)"
    item.intervalCount < 1 -> "Interval must be at least 1"
    item.repeatDaysOfWeek.any { it !in 1..7 } -> "Choose valid repeat days"
    item.frequency == RecurrenceFreq.SPECIFIC_DATE && runCatching { LocalDate.parse(item.specificDate.orEmpty()) }.isFailure -> "Enter a valid date (YYYY-MM-DD)"
    item.endConditionType == EndConditionType.UNTIL_DATE && runCatching { LocalDate.parse(item.endDate.orEmpty()) }.isFailure -> "Enter a valid end date"
    item.endConditionType == EndConditionType.AFTER_OCCURRENCES && (item.endOccurrences ?: 0) < 1 -> "Occurrence count must be positive"
    else -> null
}

internal fun recurrenceSummary(item: ActivityItem): String {
    if (!item.loopActivity) return item.specificDate ?: "Once"
    val base = when (item.frequency) {
        RecurrenceFreq.DAILY -> "Daily"
        RecurrenceFreq.WEEKLY -> "Weekly"
        RecurrenceFreq.MONTHLY -> "Monthly"
        RecurrenceFreq.YEARLY -> "Yearly"
        RecurrenceFreq.SPECIFIC_DATE -> item.specificDate.orEmpty() + if (item.repeatOnSpecificDate) " · repeats yearly" else ""
        RecurrenceFreq.CUSTOM -> "Custom"
    }
    val days = item.repeatDaysOfWeek.sorted().mapNotNull { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrNull(it - 1) }
    val interval = if (item.intervalCount > 1) " · every ${item.intervalCount} ${recurrenceIntervalUnit(item)}s" else ""
    val dayText = if (item.frequency in listOf(RecurrenceFreq.DAILY, RecurrenceFreq.WEEKLY, RecurrenceFreq.CUSTOM) && days.isNotEmpty()) " · ${days.joinToString(", ")}" else ""
    return base + interval + dayText
}

internal fun recurrenceIntervalUnit(item: ActivityItem): String = when (item.frequency) {
    RecurrenceFreq.DAILY -> "day"
    RecurrenceFreq.WEEKLY -> "week"
    RecurrenceFreq.MONTHLY -> "month"
    RecurrenceFreq.YEARLY, RecurrenceFreq.SPECIFIC_DATE -> "year"
    RecurrenceFreq.CUSTOM -> if (item.repeatDaysOfWeek.isEmpty()) "day" else "week"
}

sealed interface AddActivityEvent {
    data class Message(val text: String) : AddActivityEvent
    data class Saved(val title: String) : AddActivityEvent
    data object Deleted : AddActivityEvent
    data object ExportReady : AddActivityEvent
}

class AddActivityController(private val repository: TrackureRepository, private val scope: CoroutineScope) {
    val activities = repository.activities
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val channel = Channel<AddActivityEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()
    private var exportContent: String? = null
    private var awaitingExportDestination = false
    private fun work(block: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { channel.send(AddActivityEvent.Message(e.message ?: "Operation failed. Please try again.")) }
            finally { working.value = awaitingExportDestination }
        }
    }
    fun save(item: ActivityItem) = work {
        validateActivity(item)?.let { throw IllegalArgumentException(it) }
        val saved = item.copy(title = item.title.trim(), category = item.category?.trim()?.takeIf { it.isNotEmpty() })
        repository.saveActivity(saved)
        // Persistence succeeds independently of platform permission or scheduling failures.
        channel.send(AddActivityEvent.Saved(saved.title))

    }
    fun delete(activityId: String) = work {
        repository.deleteActivity(activityId)
        channel.send(AddActivityEvent.Deleted)
    }
    fun importIcs(read: suspend () -> String) = work {
        val content = withContext(Dispatchers.IO) { read() }
        val count = withContext(Dispatchers.IO) { repository.importIcsContent(content).getOrThrow() }
        channel.send(AddActivityEvent.Message("Imported $count activities from .ics"))
    }
    fun prepareExport() = work {
        exportContent = withContext(Dispatchers.IO) { repository.exportIcsContent().getOrThrow() }
        awaitingExportDestination = true
        channel.send(AddActivityEvent.ExportReady)
    }
    fun cancelExport() { exportContent = null; awaitingExportDestination = false; working.value = false }
    fun writeExport(write: suspend (String) -> Unit) {
        val content = exportContent ?: return
        exportContent = null
        awaitingExportDestination = false
        working.value = false
        work {
            withContext(Dispatchers.IO) { write(content) }
            channel.send(AddActivityEvent.Message("Exported schedule to trackure_schedule.ics"))
        }
    }
}
