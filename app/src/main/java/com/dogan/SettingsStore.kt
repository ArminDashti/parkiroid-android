package com.dogan

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val Context.dataStore by preferencesDataStore("dogan_settings")

/** Per-mode settings bundle. */
data class ModeSettings(
    val fps: Float,
    val minConfidence: Float,
    val recordVideo: Boolean,
    val videoAudioMode: VideoAudioMode,
    val imageRetentionHours: Int,
    val videoRetentionHours: Int,
    val imageUploadPolicy: ImageUploadPolicy,
    val historyRetentionFrames: Int,
    val frameQuality: ImageQuality,
)

/** Snapshot of all user-configurable Dogan settings. */
data class AppSettings(
    val apiEndpoint: String,
    val apiPort: Int,
    val streamEndpoint: String,
    val streamPort: Int,
    val username: String,
    val password: String,
    val captureIntervalMs: Long,
    val telemetryIntervalSec: Int,
    val activeCamera: CameraFacing,
    val operatingMode: OperatingMode,
    val aiModel: AiModel,
    val objectDetectionOnDevice: Boolean,
    val screenOnIntervalMin: Int,
    val detectionImageQuality: ImageQuality,
    val sendingImageQuality: ImageQuality,
    val alertVolume: AlertVolume,
    val alertDuration: AlertDuration,
    val streamMode: StreamMode,
    val showBoundingBoxes: Boolean,
    val settingsSyncIntervalSec: Int,
    val telemetryRetentionHours: Int,
    val joltSensitivity: SensitivityLevel,
    val soundSensitivity: SensitivityLevel,
    val customJoltScale: Float,
    val customSoundThreshold: Double,
    val logRetentionDays: Int,
    val watcherSettings: ModeSettings,
    val spotterSettings: ModeSettings,
    val copilotSettings: ModeSettings,
    val copilotAlertVolume: AlertVolume,
    val copilotAlertsEnabled: Boolean,
    val copilotDistanceControlEnabled: Boolean,
    val copilotVideoChunkMinutes: Int,
    val recordingFps: Float,
    val recordingChunkMinutes: Int,
    val recordingQuality: ImageQuality,
    val recordingEnabled: Boolean,
    val recordingSoundEnabled: Boolean,
    val recordingRetentionHours: Int,
) {
    val serverBaseUrl: String get() = EndpointUrlBuilder.buildApiBaseUrl(apiEndpoint, apiPort)
    val streamBaseUrl: String get() = EndpointUrlBuilder.buildStreamUrl(streamEndpoint, streamPort)
    val telemetryIntervalMs: Long get() = telemetryIntervalSec * 1000L
    val intervalSec: Float get() = captureIntervalMs / 1000f
    val periodSec: Int get() = (captureIntervalMs / 1000L).toInt().coerceAtLeast(1)

    fun fpsForMode(mode: OperatingMode): Float = when (mode) {
        OperatingMode.WATCHER -> watcherSettings.fps
        OperatingMode.SPOTTER -> spotterSettings.fps
        OperatingMode.COPILOT -> copilotSettings.fps
        OperatingMode.OFF -> 0f
    }

    fun confidenceForMode(mode: OperatingMode): Float = when (mode) {
        OperatingMode.WATCHER -> watcherSettings.minConfidence
        OperatingMode.SPOTTER -> spotterSettings.minConfidence
        OperatingMode.COPILOT -> copilotSettings.minConfidence
        OperatingMode.OFF -> SettingsStore.DEFAULT_MIN_CONFIDENCE
    }

    fun modeSettings(mode: OperatingMode): ModeSettings = when (mode) {
        OperatingMode.WATCHER -> watcherSettings
        OperatingMode.SPOTTER -> spotterSettings
        OperatingMode.COPILOT -> copilotSettings
        OperatingMode.OFF -> watcherSettings
    }

    fun historyRetentionForMode(mode: OperatingMode): Int =
        modeSettings(mode).historyRetentionFrames

    fun frameQualityForMode(mode: OperatingMode): ImageQuality =
        modeSettings(mode).frameQuality

    fun imageUploadPolicyForMode(mode: OperatingMode): ImageUploadPolicy =
        modeSettings(mode).imageUploadPolicy

    /** Legacy alias for migration and alert playback. */
    val minDetectionConfidence: Float get() = confidenceForMode(operatingMode)
    val realtimeFps: Float get() = fpsForMode(operatingMode)
}

/** Persists and exposes Dogan settings via DataStore preferences. */
class SettingsStore(private val context: Context) {
    private val legacyUrlKey = stringPreferencesKey("server_url")
    private val apiEndpointKey = stringPreferencesKey("api_endpoint")
    private val apiPortKey = intPreferencesKey("api_port")
    private val streamEndpointKey = stringPreferencesKey("stream_endpoint")
    private val streamPortKey = intPreferencesKey("stream_port")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")
    private val intervalKey = longPreferencesKey("capture_interval_ms")
    private val telemetryIntervalSecKey = intPreferencesKey("telemetry_interval_sec")
    private val legacyTelemetryIntervalKey = longPreferencesKey("telemetry_interval_ms")
    private val cameraKey = stringPreferencesKey("active_camera")
    private val operatingModeKey = stringPreferencesKey("operating_mode")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val onDeviceDetectionKey = booleanPreferencesKey("on_device_detection")
    private val screenOnIntervalMinKey = intPreferencesKey("screen_on_interval_min")
    private val legacyScreenOnIntervalSecKey = intPreferencesKey("screen_on_interval_sec")
    private val detectionQualityKey = stringPreferencesKey("detection_image_quality")
    private val sendingQualityKey = stringPreferencesKey("sending_image_quality")
    private val legacyRealtimeFpsKey = intPreferencesKey("realtime_fps")
    private val alertVolumeKey = stringPreferencesKey("alert_volume")
    private val alertDurationKey = stringPreferencesKey("alert_duration")
    private val streamModeKey = stringPreferencesKey("stream_mode")
    private val showBoundingBoxesKey = booleanPreferencesKey("show_bounding_boxes")
    private val settingsSyncIntervalKey = intPreferencesKey("settings_sync_interval_sec")
    private val telemetryRetentionHoursKey = intPreferencesKey("telemetry_retention_hours")
    private val joltSensitivityKey = stringPreferencesKey("jolt_sensitivity")
    private val soundSensitivityKey = stringPreferencesKey("sound_sensitivity")
    private val customJoltScaleKey = floatPreferencesKey("custom_jolt_scale")
    private val customSoundThresholdKey = floatPreferencesKey("custom_sound_threshold")
    private val logRetentionDaysKey = intPreferencesKey("log_retention_days")
    private val copilotAlertVolumeKey = stringPreferencesKey("copilot_alert_volume")
    private val copilotAlertsEnabledKey = booleanPreferencesKey("copilot_alerts_enabled")
    private val copilotDistanceControlKey = booleanPreferencesKey("copilot_distance_control_enabled")
    private val copilotVideoChunkMinKey = intPreferencesKey("copilot_video_chunk_minutes")
    private val recordingFpsKey = floatPreferencesKey("recording_fps")
    private val recordingChunkMinKey = intPreferencesKey("recording_chunk_minutes")
    private val recordingQualityKey = stringPreferencesKey("recording_quality")
    private val recordingEnabledKey = booleanPreferencesKey("recording_enabled")
    private val recordingSoundKey = booleanPreferencesKey("recording_sound_enabled")
    private val recordingRetentionKey = intPreferencesKey("recording_retention_hours")

    private fun modeFpsKey(mode: String) = floatPreferencesKey("${mode}_fps_f")
    private fun modeFpsLegacyIntKey(mode: String) = intPreferencesKey("${mode}_fps")
    private fun modeMinConfidenceKey(mode: String) = floatPreferencesKey("${mode}_min_confidence")
    private fun modeRecordVideoKey(mode: String) = booleanPreferencesKey("${mode}_record_video")
    private fun modeVideoAudioKey(mode: String) = stringPreferencesKey("${mode}_video_audio_mode")
    private fun modeImageRetentionKey(mode: String) = intPreferencesKey("${mode}_image_retention_hours")
    private fun modeVideoRetentionKey(mode: String) = intPreferencesKey("${mode}_video_retention_hours")
    private fun modeImageUploadKey(mode: String) = stringPreferencesKey("${mode}_image_upload_policy")
    private fun modeHistoryFramesKey(mode: String) = intPreferencesKey("${mode}_history_retention_frames")
    private fun modeFrameQualityKey(mode: String) = stringPreferencesKey("${mode}_frame_quality")
    private val legacyMinConfidenceKey = floatPreferencesKey("min_detection_confidence")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        val legacyUrl = pref[legacyUrlKey]
        val legacyMinConf = pref[legacyMinConfidenceKey] ?: DEFAULT_MIN_CONFIDENCE
        val legacyTelemetryMs = pref[legacyTelemetryIntervalKey]

        val apiEndpoint = pref[apiEndpointKey]
            ?: legacyUrl?.let { EndpointUrlBuilder.defaultEndpointFromLegacyUrl(it) }
            ?: DEFAULT_API_ENDPOINT
        val apiPort = pref[apiPortKey]
            ?: legacyUrl?.let { EndpointUrlBuilder.defaultPortFromLegacyUrl(it) }
            ?: DEFAULT_API_PORT

        AppSettings(
            apiEndpoint = apiEndpoint,
            apiPort = apiPort,
            streamEndpoint = pref[streamEndpointKey] ?: DEFAULT_STREAM_ENDPOINT,
            streamPort = pref[streamPortKey] ?: DEFAULT_STREAM_PORT,
            username = pref[usernameKey] ?: DEFAULT_USERNAME,
            password = pref[passwordKey] ?: DEFAULT_PASSWORD,
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
            telemetryIntervalSec = normalizeTelemetryIntervalSec(
                pref[telemetryIntervalSecKey]
                    ?: legacyTelemetryMs?.let { (it / 1000).toInt() }
                    ?: DEFAULT_TELEMETRY_INTERVAL_SEC,
            ),
            activeCamera = CameraFacing.fromStoredValue(pref[cameraKey]),
            operatingMode = OperatingMode.fromStoredValue(pref[operatingModeKey]),
            aiModel = AiModel.fromStoredValue(pref[aiModelKey]),
            objectDetectionOnDevice = true,
            screenOnIntervalMin = readScreenOnIntervalMin(pref),
            detectionImageQuality = ImageQuality.fromStoredValue(pref[detectionQualityKey]),
            sendingImageQuality = ImageQuality.fromStoredValue(pref[sendingQualityKey]),
            alertVolume = AlertVolume.fromStoredValue(pref[alertVolumeKey]),
            alertDuration = AlertDuration.fromStoredValue(pref[alertDurationKey]),
            streamMode = StreamMode.fromStoredValue(pref[streamModeKey]),
            showBoundingBoxes = pref[showBoundingBoxesKey] ?: true,
            settingsSyncIntervalSec = normalizeSettingsSyncIntervalSec(
                pref[settingsSyncIntervalKey] ?: DEFAULT_SETTINGS_SYNC_INTERVAL_SEC,
            ),
            telemetryRetentionHours = normalizeRetentionHours(
                pref[telemetryRetentionHoursKey] ?: DEFAULT_TELEMETRY_RETENTION_HOURS,
            ),
            joltSensitivity = SensitivityLevel.fromStoredValue(pref[joltSensitivityKey]),
            soundSensitivity = SensitivityLevel.fromStoredValue(pref[soundSensitivityKey]),
            customJoltScale = normalizeCustomJoltScale(
                pref[customJoltScaleKey] ?: DEFAULT_CUSTOM_JOLT_SCALE,
            ),
            customSoundThreshold = normalizeCustomSoundThreshold(
                (pref[customSoundThresholdKey] ?: DEFAULT_CUSTOM_SOUND_THRESHOLD.toFloat()).toDouble(),
            ),
            logRetentionDays = normalizeLogRetentionDays(pref[logRetentionDaysKey] ?: DEFAULT_LOG_RETENTION_DAYS),
            watcherSettings = readModeSettings(pref, "watcher", DEFAULT_WATCHMAN_FPS, legacyMinConf),
            spotterSettings = readModeSettings(pref, "spotter", DEFAULT_SPOTTER_FPS, legacyMinConf),
            copilotSettings = readModeSettings(pref, "copilot", DEFAULT_COPILOT_FPS, legacyMinConf),
            copilotAlertVolume = AlertVolume.fromStoredValue(pref[copilotAlertVolumeKey]),
            copilotAlertsEnabled = pref[copilotAlertsEnabledKey] ?: false,
            copilotDistanceControlEnabled = pref[copilotDistanceControlKey] ?: false,
            copilotVideoChunkMinutes = normalizeVideoChunkMinutes(
                pref[copilotVideoChunkMinKey] ?: DEFAULT_COPILOT_VIDEO_CHUNK_MINUTES,
            ),
            recordingFps = normalizeFps(pref[recordingFpsKey] ?: DEFAULT_RECORDING_FPS),
            recordingChunkMinutes = normalizeVideoChunkMinutes(
                pref[recordingChunkMinKey] ?: DEFAULT_COPILOT_VIDEO_CHUNK_MINUTES,
            ),
            recordingQuality = ImageQuality.fromStoredValue(pref[recordingQualityKey]),
            recordingEnabled = pref[recordingEnabledKey] ?: DEFAULT_RECORDING_ENABLED,
            recordingSoundEnabled = pref[recordingSoundKey] ?: true,
            recordingRetentionHours = normalizeRecordingRetentionHours(
                pref[recordingRetentionKey] ?: DEFAULT_RECORDING_RETENTION_HOURS,
            ),
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { pref ->
            pref[apiEndpointKey] = settings.apiEndpoint.trim()
            pref[apiPortKey] = settings.apiPort
            pref[streamEndpointKey] = settings.streamEndpoint.trim()
            pref[streamPortKey] = settings.streamPort
            pref[usernameKey] = settings.username.trim()
            pref[passwordKey] = settings.password
            pref[legacyUrlKey] = settings.serverBaseUrl
            pref[intervalKey] = normalizeIntervalMs(settings.captureIntervalMs)
            pref[telemetryIntervalSecKey] = settings.telemetryIntervalSec
            pref[cameraKey] = settings.activeCamera.toStoredValue()
            pref[operatingModeKey] = settings.operatingMode.toStoredValue()
            pref[aiModelKey] = settings.aiModel.toStoredValue()
            pref[onDeviceDetectionKey] = true
            pref[screenOnIntervalMinKey] = normalizeScreenOnIntervalMin(settings.screenOnIntervalMin)
            pref.remove(legacyScreenOnIntervalSecKey)
            pref[detectionQualityKey] = settings.detectionImageQuality.toStoredValue()
            pref[sendingQualityKey] = settings.sendingImageQuality.toStoredValue()
            pref[alertVolumeKey] = settings.alertVolume.toStoredValue()
            pref[alertDurationKey] = settings.alertDuration.toStoredValue()
            pref[streamModeKey] = settings.streamMode.toStoredValue()
            pref[showBoundingBoxesKey] = settings.showBoundingBoxes
            pref[settingsSyncIntervalKey] = settings.settingsSyncIntervalSec
            pref[telemetryRetentionHoursKey] = settings.telemetryRetentionHours
            pref[joltSensitivityKey] = settings.joltSensitivity.toStoredValue()
            pref[soundSensitivityKey] = settings.soundSensitivity.toStoredValue()
            pref[customJoltScaleKey] = settings.customJoltScale
            pref[customSoundThresholdKey] = settings.customSoundThreshold.toFloat()
            pref[logRetentionDaysKey] = settings.logRetentionDays
            pref[copilotAlertVolumeKey] = settings.copilotAlertVolume.toStoredValue()
            pref[copilotAlertsEnabledKey] = settings.copilotAlertsEnabled
            pref[copilotDistanceControlKey] = settings.copilotDistanceControlEnabled
            pref[copilotVideoChunkMinKey] = settings.copilotVideoChunkMinutes
            pref[recordingFpsKey] = settings.recordingFps
            pref[recordingChunkMinKey] = settings.recordingChunkMinutes
            pref[recordingQualityKey] = settings.recordingQuality.toStoredValue()
            pref[recordingEnabledKey] = settings.recordingEnabled
            pref[recordingSoundKey] = settings.recordingSoundEnabled
            pref[recordingRetentionKey] = settings.recordingRetentionHours
            writeModeSettings(pref, "watcher", settings.watcherSettings)
            writeModeSettings(pref, "spotter", settings.spotterSettings)
            writeModeSettings(pref, "copilot", settings.copilotSettings)
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

    private fun readModeSettings(
        pref: Preferences,
        mode: String,
        defaultFps: Float,
        legacyMinConf: Float,
    ): ModeSettings {
        val fpsFloat = pref[modeFpsKey(mode)]
        val fpsLegacy = pref[modeFpsLegacyIntKey(mode)]?.toFloat()
        return ModeSettings(
            fps = normalizeFps(fpsFloat ?: fpsLegacy ?: defaultFps),
            minConfidence = normalizeConfidence(pref[modeMinConfidenceKey(mode)] ?: legacyMinConf),
            recordVideo = pref[modeRecordVideoKey(mode)] ?: false,
            videoAudioMode = VideoAudioMode.fromStoredValue(pref[modeVideoAudioKey(mode)]),
            imageRetentionHours = normalizeRetentionHours(
                pref[modeImageRetentionKey(mode)] ?: DEFAULT_MEDIA_RETENTION_HOURS,
            ),
            videoRetentionHours = normalizeRetentionHours(
                pref[modeVideoRetentionKey(mode)] ?: DEFAULT_MEDIA_RETENTION_HOURS,
            ),
            imageUploadPolicy = ImageUploadPolicy.fromStoredValue(pref[modeImageUploadKey(mode)]),
            historyRetentionFrames = normalizeHistoryFrames(
                pref[modeHistoryFramesKey(mode)] ?: DEFAULT_HISTORY_RETENTION_FRAMES,
            ),
            frameQuality = ImageQuality.fromStoredValue(pref[modeFrameQualityKey(mode)]),
        )
    }

    private fun writeModeSettings(
        pref: androidx.datastore.preferences.core.MutablePreferences,
        mode: String,
        settings: ModeSettings,
    ) {
        pref[modeFpsKey(mode)] = settings.fps
        pref[modeMinConfidenceKey(mode)] = settings.minConfidence
        pref[modeRecordVideoKey(mode)] = settings.recordVideo
        pref[modeVideoAudioKey(mode)] = settings.videoAudioMode.toStoredValue()
        pref[modeImageRetentionKey(mode)] = settings.imageRetentionHours
        pref[modeVideoRetentionKey(mode)] = settings.videoRetentionHours
        pref[modeImageUploadKey(mode)] = settings.imageUploadPolicy.toStoredValue()
        pref[modeHistoryFramesKey(mode)] = settings.historyRetentionFrames
        pref[modeFrameQualityKey(mode)] = settings.frameQuality.toStoredValue()
    }

    private fun readScreenOnIntervalMin(pref: Preferences): Int {
        pref[screenOnIntervalMinKey]?.let { return normalizeScreenOnIntervalMin(it) }
        val legacySec = pref[legacyScreenOnIntervalSecKey]
        if (legacySec != null) {
            return normalizeScreenOnIntervalMin((legacySec + 59) / 60)
        }
        return DEFAULT_SCREEN_ON_INTERVAL_MIN
    }

    companion object {
        const val DEFAULT_USERNAME = "armin"
        const val DEFAULT_PASSWORD = "dogan123"
        const val DEFAULT_API_ENDPOINT = "dogan-api.xaigrok.ir"
        const val DEFAULT_API_PORT = 443
        const val DEFAULT_STREAM_ENDPOINT = "dogan-livekit.xaigrok.ir"
        const val DEFAULT_STREAM_PORT = 443
        const val DEFAULT_CUSTOM_JOLT_SCALE = 1.0f
        const val DEFAULT_CUSTOM_SOUND_THRESHOLD = 2500.0
        const val DEFAULT_INTERVAL_MS = 15_000L
        const val DEFAULT_TELEMETRY_INTERVAL_SEC = 1
        const val MIN_INTERVAL_MS = 100L
        const val MAX_INTERVAL_MS = 3_600_000L
        const val MIN_TELEMETRY_INTERVAL_SEC = 1
        const val MAX_TELEMETRY_INTERVAL_SEC = 60
        const val DEFAULT_SCREEN_ON_INTERVAL_MIN = 0
        const val MAX_SCREEN_ON_INTERVAL_MIN = 60
        const val DEFAULT_SETTINGS_SYNC_INTERVAL_SEC = 15
        const val MIN_SETTINGS_SYNC_INTERVAL_SEC = 10
        const val MAX_SETTINGS_SYNC_INTERVAL_SEC = 3600
        const val DEFAULT_FPS = 5f
        const val DEFAULT_WATCHMAN_FPS = 0.125f
        const val DEFAULT_SPOTTER_FPS = 0.125f
        const val DEFAULT_COPILOT_FPS = 4f
        const val DEFAULT_RECORDING_FPS = 15f
        const val DEFAULT_RECORDING_ENABLED = false
        const val DEFAULT_MIN_CONFIDENCE = 0.7f
        const val DEFAULT_TELEMETRY_RETENTION_HOURS = 72
        const val DEFAULT_MEDIA_RETENTION_HOURS = 24
        const val DEFAULT_RECORDING_RETENTION_HOURS = 12
        const val DEFAULT_LOG_RETENTION_DAYS = 7
        const val DEFAULT_COPILOT_VIDEO_CHUNK_MINUTES = 15
        const val DEFAULT_HISTORY_RETENTION_FRAMES = 100
        val ALLOWED_FPS = listOf(0.125f, 0.25f, 0.5f, 1f, 2f, 4f, 5f, 10f, 15f, 24f, 30f)
        val ALLOWED_REALTIME_FPS = ALLOWED_FPS

        fun normalizeIntervalMs(intervalMs: Long): Long =
            intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

        fun normalizeTelemetryIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_TELEMETRY_INTERVAL_SEC, MAX_TELEMETRY_INTERVAL_SEC)

        fun normalizeScreenOnIntervalMin(minutes: Int): Int =
            minutes.coerceIn(0, MAX_SCREEN_ON_INTERVAL_MIN)

        fun normalizeSettingsSyncIntervalSec(seconds: Int): Int =
            seconds.coerceIn(MIN_SETTINGS_SYNC_INTERVAL_SEC, MAX_SETTINGS_SYNC_INTERVAL_SEC)

        fun normalizeFps(fps: Float): Float =
            ALLOWED_FPS.minByOrNull { abs(it - fps) } ?: DEFAULT_FPS

        fun normalizeFps(fps: Int): Float = normalizeFps(fps.toFloat())

        fun normalizeRealtimeFps(fps: Float): Float = normalizeFps(fps)

        fun normalizeConfidence(value: Float): Float =
            value.coerceIn(0.10f, 0.95f)

        fun normalizeRetentionHours(hours: Int): Int =
            hours.coerceIn(1, 720)

        fun normalizeRecordingRetentionHours(hours: Int): Int =
            hours.coerceIn(1, 12)

        fun normalizeHistoryFrames(frames: Int): Int =
            frames.coerceIn(1, 500)

        fun normalizeLogRetentionDays(days: Int): Int =
            days.coerceIn(1, 365)

        fun normalizeVideoChunkMinutes(minutes: Int): Int =
            minutes.coerceIn(1, 120)

        fun normalizeCustomJoltScale(scale: Float): Float =
            scale.coerceIn(0.1f, 5.0f)

        fun normalizeCustomSoundThreshold(threshold: Double): Double =
            threshold.coerceIn(100.0, 20_000.0)

        fun stepFpsDown(current: Float): Float {
            val normalized = normalizeFps(current)
            val index = ALLOWED_FPS.indexOf(normalized).coerceAtLeast(0)
            return ALLOWED_FPS[(index - 1).coerceAtLeast(0)]
        }

        fun stepFpsUp(current: Float): Float {
            val normalized = normalizeFps(current)
            val index = ALLOWED_FPS.indexOf(normalized).coerceAtLeast(0)
            return ALLOWED_FPS[(index + 1).coerceAtMost(ALLOWED_FPS.lastIndex)]
        }

        fun formatFps(fps: Float): String =
            if (fps < 1f) fps.toString() else fps.toInt().toString()
    }
}
