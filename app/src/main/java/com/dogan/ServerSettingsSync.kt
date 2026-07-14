package com.dogan

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Polls the Dogan server for device settings and merges them into local storage. */
object ServerSettingsSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    fun start(context: Context) {
        if (syncJob?.isActive == true) return
        val appContext = context.applicationContext
        syncJob = scope.launch {
            val settingsStore = SettingsStore(appContext)
            val deviceId = DeviceIdentity.resolveDeviceId(appContext)
            val apiClient = DoganApiClient(deviceId = deviceId)

            while (isActive && ServerConnectionManager.isConnected()) {
                val local = settingsStore.settingsFlow.first()
                val intervalSec = local.settingsSyncIntervalSec.coerceAtLeast(
                    SettingsStore.MIN_SETTINGS_SYNC_INTERVAL_SEC,
                )

                if (isValidBaseUrl(local.serverBaseUrl)) {
                    val remote = apiClient.fetchSettings(local.serverBaseUrl, local.apiKey)
                    if (remote != null) {
                        val merged = mergeSettings(local, remote)
                        if (merged != local) {
                            settingsStore.save(merged)
                            AppLogger.info("SettingsSync", "Applied server settings")
                        }
                    }
                }

                delay(intervalSec * 1000L)
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    fun restart(context: Context) {
        stop()
        start(context)
    }

    private fun mergeSettings(local: AppSettings, remote: JSONObject): AppSettings {
        var updated = local

        if (remote.has("operating_mode")) {
            updated = updated.copy(
                operatingMode = OperatingMode.fromStoredValue(remote.getString("operating_mode")),
            )
        }
        if (remote.has("ai_model")) {
            updated = updated.copy(aiModel = parseAiModel(remote.getString("ai_model"), updated.aiModel))
        }
        if (remote.has("capture_interval_ms")) {
            updated = updated.copy(
                captureIntervalMs = SettingsStore.normalizeIntervalMs(remote.getLong("capture_interval_ms")),
            )
        }
        if (remote.has("telemetry_interval_ms")) {
            updated = updated.copy(
                telemetryIntervalMs = SettingsStore.normalizeTelemetryIntervalMs(
                    remote.getLong("telemetry_interval_ms"),
                ),
            )
        }
        if (remote.has("object_detection_on_device")) {
            updated = updated.copy(objectDetectionOnDevice = remote.getBoolean("object_detection_on_device"))
        }
        if (remote.has("screen_on_interval_min")) {
            updated = updated.copy(
                screenOnIntervalMin = SettingsStore.normalizeScreenOnIntervalMin(
                    remote.getInt("screen_on_interval_min"),
                ),
            )
        }
        if (remote.has("detection_image_quality")) {
            updated = updated.copy(
                detectionImageQuality = parseImageQuality(remote.getString("detection_image_quality"), updated.detectionImageQuality),
            )
        }
        if (remote.has("sending_image_quality")) {
            updated = updated.copy(
                sendingImageQuality = parseImageQuality(remote.getString("sending_image_quality"), updated.sendingImageQuality),
            )
        }
        if (remote.has("realtime_fps")) {
            updated = updated.copy(
                realtimeFps = SettingsStore.normalizeRealtimeFps(remote.getInt("realtime_fps")),
            )
        }
        if (remote.has("alert_volume")) {
            updated = updated.copy(alertVolume = AlertVolume.fromStoredValue(remote.getString("alert_volume")))
        }
        if (remote.has("alert_duration")) {
            updated = updated.copy(alertDuration = AlertDuration.fromStoredValue(remote.getString("alert_duration")))
        }
        if (remote.has("min_detection_confidence")) {
            updated = updated.copy(
                minDetectionConfidence = SettingsStore.normalizeConfidence(
                    remote.getDouble("min_detection_confidence").toFloat(),
                ),
            )
        }
        if (remote.has("stream_mode")) {
            updated = updated.copy(streamMode = StreamMode.fromStoredValue(remote.getString("stream_mode")))
        }
        if (remote.has("wifi_only_downloads")) {
            updated = updated.copy(wifiOnlyDownloads = remote.getBoolean("wifi_only_downloads"))
        }
        if (remote.has("settings_sync_interval_sec")) {
            updated = updated.copy(
                settingsSyncIntervalSec = SettingsStore.normalizeSettingsSyncIntervalSec(
                    remote.getInt("settings_sync_interval_sec"),
                ),
            )
        }

        return updated
    }

    private fun parseAiModel(value: String?, fallback: AiModel): AiModel {
        if (value.isNullOrBlank()) return fallback
        return AiModel.all.firstOrNull {
            it.toStoredValue() == value.lowercase() || it.name.equals(value, ignoreCase = true)
        } ?: fallback
    }

    private fun parseImageQuality(value: String?, fallback: ImageQuality): ImageQuality {
        if (value.isNullOrBlank()) return fallback
        return ImageQuality.entries.firstOrNull {
            it.toStoredValue() == value.lowercase() || it.name.equals(value, ignoreCase = true)
        } ?: fallback
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}
