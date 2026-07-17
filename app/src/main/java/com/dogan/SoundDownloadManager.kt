package com.dogan

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** Downloads alert sounds from the Dogan server. */
class SoundDownloadManager(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    data class SoundEntry(
        val id: String,
        val url: String,
        val sha256: String,
        val alertType: AlertType,
        val format: String,
    )

    fun soundsDir(): File = File(context.filesDir, "sounds").also { it.mkdirs() }

    fun soundFile(id: String, format: String): File = File(soundsDir(), "$id.$format")

    fun fetchAndDownloadAll(baseUrl: String): DownloadResult {
        val manifest = apiClient.fetchSoundsManifest(baseUrl)
            ?: return DownloadResult(false, "Could not fetch sounds manifest")
        val entries = parseManifest(manifest)
        var failed = 0
        for (entry in entries) {
            if (!downloadSound(baseUrl, entry)) failed++
        }
        return DownloadResult(failed == 0, if (failed > 0) "$failed sound(s) failed" else "All sounds ready")
    }

    fun downloadSound(baseUrl: String, entry: SoundEntry): Boolean {
        val file = soundFile(entry.id, entry.format)
        if (file.exists()) return true
        val bytes = apiClient.downloadFile(baseUrl, entry.url) ?: return false
        if (entry.sha256.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val hex = digest.joinToString("") { "%02x".format(it) }
            if (!hex.equals(entry.sha256, ignoreCase = true)) return false
        }
        file.writeBytes(bytes)
        AppLogger.info("Sounds", "Downloaded alert sound ${entry.id}")
        return true
    }

    fun getSoundFile(alertType: AlertType): File? {
        val dir = soundsDir()
        return dir.listFiles()?.firstOrNull { file ->
            file.nameWithoutExtension == alertType.soundId ||
                file.name.startsWith(alertType.soundId)
        }
    }

    private fun parseManifest(array: JSONArray): List<SoundEntry> {
        val entries = mutableListOf<SoundEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val alertTypeStr = obj.optString("alert_type", "generic_warning")
            val alertType = AlertType.fromSoundId(alertTypeStr) ?: AlertType.GENERIC_WARNING
            entries.add(
                SoundEntry(
                    id = obj.getString("id"),
                    url = obj.getString("url"),
                    sha256 = obj.optString("sha256", ""),
                    alertType = alertType,
                    format = obj.optString("format", "ogg"),
                ),
            )
        }
        return entries
    }
}
