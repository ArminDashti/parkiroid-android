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

/** Persistent ring buffer of recent app events shown on the Logs screen. */
object AppLogger {
    private const val MAX_LINES = 500
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var logFile: File? = null

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also { it.mkdirs() }
        logFile = File(dir, "dogan.log")
        loadFromDisk()
        purgeOlderThan(context, SettingsStore.DEFAULT_LOG_RETENTION_DAYS)
    }

    fun info(tag: String, message: String) = append("I", tag, message)

    fun warn(tag: String, message: String) = append("W", tag, message)

    fun error(tag: String, message: String) = append("E", tag, message)

    fun clear() {
        _lines.value = emptyList()
        logFile?.writeText("")
    }

    fun flushDisk(context: Context) {
        val dir = File(context.filesDir, "logs")
        dir.listFiles()?.forEach { it.delete() }
        _lines.value = emptyList()
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

    private fun loadFromDisk() {
        val file = logFile ?: return
        if (!file.exists()) return
        val loaded = file.readLines().takeLast(MAX_LINES)
        _lines.value = loaded
    }

    private fun append(level: String, tag: String, message: String) {
        val timestamp = LocalTime.now().format(formatter)
        val line = "$timestamp $level/$tag: $message"
        val updated = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = updated
        try {
            logFile?.appendText("$line\n")
        } catch (_: Exception) {
        }
    }
}
