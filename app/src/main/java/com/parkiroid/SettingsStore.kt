package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.dataStore by preferencesDataStore("parkiroid_settings")

/** Snapshot of all user-configurable Parkiroid settings. */
data class AppSettings(
    val serverBaseUrl: String,
    val apiKey: String,
    val captureIntervalMs: Long,
    val activeCamera: CameraFacing,
    val aiModel: AiModel,
    val objectDetectionOnDevice: Boolean,
    val screenOnIntervalSec: Int,
    val detectionImageQuality: ImageQuality,
    val sendingImageQuality: ImageQuality,
    val realtimeFps: Int,
) {
    val intervalSec: Float get() = captureIntervalMs / 1000f
    val periodSec: Int get() = (captureIntervalMs / 1000L).toInt().coerceAtLeast(1)
}

/** Persists and exposes Parkiroid settings via DataStore preferences. */
class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val intervalKey = longPreferencesKey("capture_interval_ms")
    private val cameraKey = stringPreferencesKey("active_camera")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val onDeviceDetectionKey = booleanPreferencesKey("on_device_detection")
    private val screenOnIntervalKey = intPreferencesKey("screen_on_interval_sec")
    private val detectionQualityKey = stringPreferencesKey("detection_image_quality")
    private val sendingQualityKey = stringPreferencesKey("sending_image_quality")
    private val realtimeFpsKey = intPreferencesKey("realtime_fps")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
            activeCamera = CameraFacing.fromStoredValue(pref[cameraKey]),
            aiModel = AiModel.fromStoredValue(pref[aiModelKey]),
            objectDetectionOnDevice = pref[onDeviceDetectionKey] ?: false,
            screenOnIntervalSec = normalizeScreenOnInterval(pref[screenOnIntervalKey] ?: DEFAULT_SCREEN_ON_INTERVAL_SEC),
            detectionImageQuality = ImageQuality.fromStoredValue(pref[detectionQualityKey]),
            sendingImageQuality = ImageQuality.fromStoredValue(pref[sendingQualityKey]),
            realtimeFps = normalizeRealtimeFps(pref[realtimeFpsKey] ?: DEFAULT_REALTIME_FPS),
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { pref ->
            pref[urlKey] = settings.serverBaseUrl.trim().trimEnd('/')
            pref[apiKeyKey] = settings.apiKey.trim()
            pref[intervalKey] = normalizeIntervalMs(settings.captureIntervalMs)
            pref[cameraKey] = settings.activeCamera.toStoredValue()
            pref[aiModelKey] = settings.aiModel.toStoredValue()
            pref[onDeviceDetectionKey] = settings.objectDetectionOnDevice
            pref[screenOnIntervalKey] = normalizeScreenOnInterval(settings.screenOnIntervalSec)
            pref[detectionQualityKey] = settings.detectionImageQuality.toStoredValue()
            pref[sendingQualityKey] = settings.sendingImageQuality.toStoredValue()
            pref[realtimeFpsKey] = normalizeRealtimeFps(settings.realtimeFps)
        }
    }

    suspend fun updateActiveCamera(camera: CameraFacing) {
        context.dataStore.edit { pref ->
            pref[cameraKey] = camera.toStoredValue()
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://parkiroid.xaigrok.ir"
        const val DEFAULT_API_KEY = "parkiroid-dev-key"
        const val DEFAULT_INTERVAL_MS = 15_000L
        const val MIN_INTERVAL_MS = 100L
        const val MAX_INTERVAL_MS = 3_600_000L
        const val DEFAULT_SCREEN_ON_INTERVAL_SEC = 0
        const val DEFAULT_REALTIME_FPS = 5
        val ALLOWED_REALTIME_FPS = listOf(1, 2, 5, 10, 15, 24, 30)

        fun normalizeIntervalMs(intervalMs: Long): Long =
            intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

        fun normalizeScreenOnInterval(seconds: Int): Int =
            seconds.coerceIn(0, 3600)

        fun normalizeRealtimeFps(fps: Int): Int =
            ALLOWED_REALTIME_FPS.minByOrNull { kotlin.math.abs(it - fps) } ?: DEFAULT_REALTIME_FPS
    }
}
