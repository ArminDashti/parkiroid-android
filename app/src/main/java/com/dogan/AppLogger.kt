package com.dogan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** In-memory ring buffer of recent app events shown on the Logs screen. */
object AppLogger {
    private const val MAX_LINES = 500
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun info(tag: String, message: String) = append("I", tag, message)

    fun warn(tag: String, message: String) = append("W", tag, message)

    fun error(tag: String, message: String) = append("E", tag, message)

    fun clear() {
        _lines.value = emptyList()
    }

    private fun append(level: String, tag: String, message: String) {
        val timestamp = LocalTime.now().format(formatter)
        val line = "$timestamp $level/$tag: $message"
        val updated = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = updated
    }
}
