package com.dogan

import android.content.Context
import java.io.File

/** Stores processed detection images and videos with per-mode retention. */
class DetectionMediaArchive(private val context: Context) {
    private val baseDir = File(context.filesDir, "detection-media").also { it.mkdirs() }

    fun directoryForMode(mode: OperatingMode): File =
        File(baseDir, mode.toStoredValue()).also { it.mkdirs() }

    fun saveProcessedImage(mode: OperatingMode, jpegBytes: ByteArray, suffix: String = "frame"): File? {
        return try {
            val dir = directoryForMode(mode)
            val file = File(dir, "${suffix}_${System.currentTimeMillis()}.jpg")
            file.writeBytes(jpegBytes)
            enforceRetention(mode)
            file
        } catch (e: Exception) {
            AppLogger.error("MediaArchive", "Failed to save image: ${e.message}")
            null
        }
    }

    fun enforceRetention(mode: OperatingMode, imageRetentionHours: Int, videoRetentionHours: Int) {
        val dir = directoryForMode(mode)
        val now = System.currentTimeMillis()
        val imageCutoff = now - imageRetentionHours * 3_600_000L
        val videoCutoff = now - videoRetentionHours * 3_600_000L
        dir.listFiles()?.forEach { file ->
            val isVideo = file.extension.equals("mp4", ignoreCase = true)
            val cutoff = if (isVideo) videoCutoff else imageCutoff
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun enforceRetention(mode: OperatingMode) {
        enforceRetention(mode, SettingsStore.DEFAULT_MEDIA_RETENTION_HOURS, SettingsStore.DEFAULT_MEDIA_RETENTION_HOURS)
    }

    fun flush(mode: OperatingMode) {
        val dir = directoryForMode(mode)
        dir.listFiles()?.forEach { it.delete() }
        AppLogger.info("Storage", "Flushed ${mode.displayName} disk")
    }

    fun usageBytes(mode: OperatingMode): Long =
        directoryForMode(mode).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        fun usageBytesForLogs(context: Context): Long {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) return 0L
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
