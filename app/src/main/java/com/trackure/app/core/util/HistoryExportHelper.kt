package com.trackure.app.core.util

import com.trackure.app.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HistoryExportHelper(private val today: LocalDate = LocalDate.now()) {
    /** RFC-4180 CSV. Markdown is a separate format, never appended to the CSV payload. */
    fun buildHistoryExportContent(logs: List<ActivityLog>, config: ExportHistoryConfig, rangeDays: Int): String =
        table(logs, config, rangeDays).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
        }

    fun buildHistoryMarkdownContent(logs: List<ActivityLog>, config: ExportHistoryConfig, rangeDays: Int): String {
        val rows = table(logs, config, rangeDays)
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>")
        fun row(values: List<String>) = values.joinToString(" | ", "| ", " |", transform = ::escape)
        return "# Trackure History\n\n" + row(rows.first()) + "\n" + row(rows.first().map { "---" }) + "\n" +
            rows.drop(1).joinToString("\n", postfix = if (rows.size > 1) "\n" else "", transform = ::row)
    }

    private fun table(logs: List<ActivityLog>, config: ExportHistoryConfig, rangeDays: Int): List<List<String>> {
        require(rangeDays > 0) { "Export range must be positive" }
        require(config.includeActivityName || config.includeCompletedTime || config.includeNote) { "Select at least one export column" }
        val header = buildList {
            if (config.includeActivityName) add("Nama Aktivitas")
            if (config.includeCompletedTime) add("Tanggal & Waktu Terlaksana")
            if (config.includeNote) add("Catatan")
        }
        val start = today.minusDays(rangeDays.toLong() - 1)
        val rows = logs.groupBy { it.activityId to it.dateIso }.values.map { versions -> versions.maxBy { it.updatedAt } }
            .filter { it.isCompleted && it.syncStatus != SyncStatus.PENDING_DELETE && it.activityId != GeneralDailyNote.ACTIVITY_ID &&
                runCatching { LocalDate.parse(it.dateIso) in start..today }.getOrDefault(false) }
            .sortedWith(compareBy<ActivityLog> { it.dateIso }.thenBy { it.completedHour?.padStart(2, '0') }.thenBy { it.completedMinute?.padStart(2, '0') }.thenBy { it.activityId })
            .map { log ->
                buildList {
                    if (config.includeActivityName) add(log.activityTitle)
                    if (config.includeCompletedTime) {
                        val time = runCatching { LocalTime.of(requireNotNull(log.completedHour).toInt(), requireNotNull(log.completedMinute).toInt())
                            .format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
                        add(log.dateIso + (time?.let { " • $it" } ?: ""))
                    }
                    if (config.includeNote) add(log.note.orEmpty())
                }
            }
        return listOf(header) + rows
    }
}
