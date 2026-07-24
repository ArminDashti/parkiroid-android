package com.dogan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One displayable log line with an optional section for filtering. */
data class LogEntry(
    val section: LogSection,
    val displayLine: String,
)

/** Persistent ring buffer of recent app events shown on the Logs screen. */
object AppLogger {
    private const val MAX_LINES = 500
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var logFile: File? = null

    /** When set, Detection/Capture/Camera tags inherit this section. */
    @Volatile
    var activeModeSection: LogSection? = null

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _displayLines = MutableStateFlow<List<String>>(emptyList())
    /** All display lines (main Logs screen). */
    val lines: StateFlow<List<String>> = _displayLines.asStateFlow()

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, "dogan.log")
        loadFromDisk()
        purgeOlderThan(context, SettingsStore.DEFAULT_LOG_RETENTION_DAYS)
    }

    fun info(tag: String, message: String) = append(null, "I", tag, message)

    fun info(section: LogSection, tag: String, message: String) = append(section, "I", tag, message)

    fun warn(tag: String, message: String) = append(null, "W", tag, message)

    fun warn(section: LogSection, tag: String, message: String) = append(section, "W", tag, message)

    fun error(tag: String, message: String) = append(null, "E", tag, message)

    fun error(section: LogSection, tag: String, message: String) = append(section, "E", tag, message)

    fun clear() {
        _entries.value = emptyList()
        _displayLines.value = emptyList()
        logFile?.writeText("")
    }

    fun flushDisk(context: Context) {
        val dir = File(context.filesDir, "logs")
        dir.listFiles()?.forEach { it.delete() }
        _entries.value = emptyList()
        _displayLines.value = emptyList()
        logFile = File(dir, "dogan.log").also { dir.mkdirs() }
    }

    fun purgeOlderThan(context: Context, retentionDays: Int) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) return
        val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
        loadFromDisk()
    }

    fun linesForSection(section: LogSection?): List<String> {
        val all = _entries.value
        if (section == null) return all.map { it.displayLine }
        return all.filter { it.section == section }.map { it.displayLine }
    }

    private fun loadFromDisk() {
        val file = logFile ?: return
        if (!file.exists()) return
        val loaded = file.readLines().takeLast(MAX_LINES).mapNotNull { parseStoredLine(it) }
        _entries.value = loaded
        _displayLines.value = loaded.map { it.displayLine }
    }

    private fun parseStoredLine(raw: String): LogEntry? {
        if (raw.isBlank()) return null
        // New format: HH:mm:ss.SSS L/[section]/tag: message
        val sectionMatch = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3}) ([IWE])/\[(\w+)]/([^:]+): (.*)$""")
            .matchEntire(raw)
        if (sectionMatch != null) {
            val (ts, level, section, tag, message) = sectionMatch.destructured
            val display = "$ts $level/$tag: $message"
            return LogEntry(LogSection.fromStoredValue(section), display)
        }
        // Legacy format: HH:mm:ss.SSS L/tag: message
        val legacyMatch = Regex("""^(\d{2}:\d{2}:\d{2}\.\d{3}) ([IWE])/([^:]+): (.*)$""")
            .matchEntire(raw)
        if (legacyMatch != null) {
            val (ts, level, tag, message) = legacyMatch.destructured
            val display = "$ts $level/$tag: $message"
            return LogEntry(sectionForTag(tag, null), display)
        }
        return LogEntry(LogSection.GENERAL, raw)
    }

    private fun append(explicit: LogSection?, level: String, tag: String, message: String) {
        val section = sectionForTag(tag, explicit)
        val timestamp = LocalTime.now().format(formatter)
        val display = "$timestamp $level/$tag: $message"
        val stored = "$timestamp $level/[${section.storedValue}]/$tag: $message"
        val entry = LogEntry(section, display)
        val updated = (_entries.value + entry).takeLast(MAX_LINES)
        _entries.value = updated
        _displayLines.value = updated.map { it.displayLine }
        try {
            logFile?.appendText("$stored\n")
        } catch (_: Exception) {
        }
    }

    private fun sectionForTag(tag: String, explicit: LogSection?): LogSection {
        if (explicit != null) return explicit
        return when (tag) {
            "Spotter" -> LogSection.SPOTTER
            "Audio", "Watcher", "Watchman" -> LogSection.WATCHMAN
            "VideoRecorder", "MediaArchive", "Storage" -> LogSection.RECORDING
            "Server", "LiveKit", "SSL", "SettingsSync", "Settings" -> LogSection.CONNECTIVITY
            "Detection", "Capture", "Camera" -> activeModeSection ?: LogSection.GENERAL
            else -> LogSection.GENERAL
        }
    }
}
