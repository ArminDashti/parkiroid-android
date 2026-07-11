package com.dogan

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("dogan_settings")

/** Snapshot of all user-configurable Dogan settings. */
data class AppSettings(
    val serverBaseUrl: String,
    val apiKey: String,
    val captureIntervalMs: Long,
    val telemetryIntervalMs: Long,
    val activeCamera: CameraFacing,
    val operatingMode: OperatingMode,
    val aiModel: AiModel,
    val objectDetectionOnDevice: Boolean,
    val screenOnIntervalSec: Int,
    val detectionImageQuality: ImageQuality,
    val sendingImageQuality: ImageQuality,
    val realtimeFps: Int,
    val alertVolume: AlertVolume,
    val alertDuration: AlertDuration,
    val minDetectionConfidence: Float,
    val streamMode: StreamMode,
    val wifiOnlyDownloads: Boolean,
) {
    val intervalSec: Float get() = captureIntervalMs / 1000f
    val periodSec: Int get() = (captureIntervalMs / 1000L).toInt().coerceAtLeast(1)
}

/** Persists and exposes Dogan settings via DataStore preferences. */
class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val intervalKey = longPreferencesKey("capture_interval_ms")
    private val telemetryIntervalKey = longPreferencesKey("telemetry_interval_ms")
    private val cameraKey = stringPreferencesKey("active_camera")
    private val operatingModeKey = stringPreferencesKey("operating_mode")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val onDeviceDetectionKey = booleanPreferencesKey("on_device_detection")
    private val screenOnIntervalKey = intPreferencesKey("screen_on_interval_sec")
    private val detectionQualityKey = stringPreferencesKey("detection_image_quality")
    private val sendingQualityKey = stringPreferencesKey("sending_image_quality")
    private val realtimeFpsKey = intPreferencesKey("realtime_fps")
    private val alertVolumeKey = stringPreferencesKey("alert_volume")
    private val alertDurationKey = stringPreferencesKey("alert_duration")
    private val minConfidenceKey = floatPreferencesKey("min_detection_confidence")
    private val streamModeKey = stringPreferencesKey("stream_mode")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only_downloads")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
            telemetryIntervalMs = normalizeTelemetryIntervalMs(pref[telemetryIntervalKey] ?: DEFAULT_TELEMETRY_INTERVAL_MS),
            activeCamera = CameraFacing.fromStoredValue(pref[cameraKey]),
            operatingMode = OperatingMode.fromStoredValue(pref[operatingModeKey]),
            aiModel = AiModel.fromStoredValue(pref[aiModelKey]),
            objectDetectionOnDevice = pref[onDeviceDetectionKey] ?: false,
            screenOnIntervalSec = normalizeScreenOnInterval(pref[screenOnIntervalKey] ?: DEFAULT_SCREEN_ON_INTERVAL_SEC),
            detectionImageQuality = ImageQuality.fromStoredValue(pref[detectionQualityKey]),
            sendingImageQuality = ImageQuality.fromStoredValue(pref[sendingQualityKey]),
            realtimeFps = normalizeRealtimeFps(pref[realtimeFpsKey] ?: DEFAULT_REALTIME_FPS),
            alertVolume = AlertVolume.fromStoredValue(pref[alertVolumeKey]),
            alertDuration = AlertDuration.fromStoredValue(pref[alertDurationKey]),
            minDetectionConfidence = normalizeConfidence(pref[minConfidenceKey] ?: DEFAULT_MIN_CONFIDENCE),
            streamMode = StreamMode.fromStoredValue(pref[streamModeKey]),
            wifiOnlyDownloads = pref[wifiOnlyKey] ?: false,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { pref ->
            pref[urlKey] = settings.serverBaseUrl.trim().trimEnd('/')
            pref[apiKeyKey] = settings.apiKey.trim()
            pref[intervalKey] = normalizeIntervalMs(settings.captureIntervalMs)
            pref[telemetryIntervalKey] = normalizeTelemetryIntervalMs(settings.telemetryIntervalMs)
            pref[cameraKey] = settings.activeCamera.toStoredValue()
            pref[operatingModeKey] = settings.operatingMode.toStoredValue()
            pref[aiModelKey] = settings.aiModel.toStoredValue()
            pref[onDeviceDetectionKey] = settings.objectDetectionOnDevice
            pref[screenOnIntervalKey] = normalizeScreenOnInterval(settings.screenOnIntervalSec)
            pref[detectionQualityKey] = settings.detectionImageQuality.toStoredValue()
            pref[sendingQualityKey] = settings.sendingImageQuality.toStoredValue()
            pref[realtimeFpsKey] = normalizeRealtimeFps(settings.realtimeFps)
            pref[alertVolumeKey] = settings.alertVolume.toStoredValue()
            pref[alertDurationKey] = settings.alertDuration.toStoredValue()
            pref[minConfidenceKey] = normalizeConfidence(settings.minDetectionConfidence)
            pref[streamModeKey] = settings.streamMode.toStoredValue()
            pref[wifiOnlyKey] = settings.wifiOnlyDownloads
        }
    }

    suspend fun updateActiveCamera(camera: CameraFacing) {
        context.dataStore.edit { pref ->
            pref[cameraKey] = camera.toStoredValue()
        }
    }

    suspend fun updateOperatingMode(mode: OperatingMode) {
        context.dataStore.edit { pref ->
            pref[operatingModeKey] = mode.toStoredValue()
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://dogan.xaigrok.ir"
        const val DEFAULT_API_KEY = "dogan-dev-key"
        const val DEFAULT_INTERVAL_MS = 15_000L
        const val DEFAULT_TELEMETRY_INTERVAL_MS = 1_000L
        const val MIN_INTERVAL_MS = 100L
        const val MAX_INTERVAL_MS = 3_600_000L
        const val MIN_TELEMETRY_INTERVAL_MS = 500L
        const val MAX_TELEMETRY_INTERVAL_MS = 60_000L
        const val DEFAULT_SCREEN_ON_INTERVAL_SEC = 0
        const val DEFAULT_REALTIME_FPS = 5
        const val DEFAULT_MIN_CONFIDENCE = 0.45f
        val ALLOWED_REALTIME_FPS = listOf(1, 2, 5, 10, 15, 24, 30)

        fun normalizeIntervalMs(intervalMs: Long): Long =
            intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

        fun normalizeTelemetryIntervalMs(intervalMs: Long): Long =
            intervalMs.coerceIn(MIN_TELEMETRY_INTERVAL_MS, MAX_TELEMETRY_INTERVAL_MS)

        fun normalizeScreenOnInterval(seconds: Int): Int =
            seconds.coerceIn(0, 3600)

        fun normalizeRealtimeFps(fps: Int): Int =
            ALLOWED_REALTIME_FPS.minByOrNull { kotlin.math.abs(it - fps) } ?: DEFAULT_REALTIME_FPS

        fun normalizeConfidence(value: Float): Float =
            value.coerceIn(0.10f, 0.95f)
    }
}
