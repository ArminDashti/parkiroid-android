package com.dogan

import android.content.Context
import java.io.File
import java.util.Locale

/** Computes on-device storage usage and flush helpers. */
class DiskUsageManager(private val context: Context) {
    private val mediaArchive = DetectionMediaArchive(context)

    data class UsageSummary(
        val spotterBytes: Long,
        val watcherBytes: Long,
        val copilotBytes: Long,
        val logsBytes: Long,
    ) {
        val totalBytes: Long get() = spotterBytes + watcherBytes + copilotBytes + logsBytes
    }

    fun summarize(): UsageSummary = UsageSummary(
        spotterBytes = mediaArchive.usageBytes(OperatingMode.SPOTTER),
        watcherBytes = mediaArchive.usageBytes(OperatingMode.WATCHER),
        copilotBytes = mediaArchive.usageBytes(OperatingMode.COPILOT),
        logsBytes = DetectionMediaArchive.usageBytesForLogs(context),
    )

    fun flushSpotter() = mediaArchive.flush(OperatingMode.SPOTTER)
    fun flushWatcher() = mediaArchive.flush(OperatingMode.WATCHER)
    fun flushCopilot() = mediaArchive.flush(OperatingMode.COPILOT)
    fun flushLogs() = AppLogger.flushDisk(context)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
