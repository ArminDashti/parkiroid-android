package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.math.roundToInt
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("parkiroid_settings")

data class AppSettings(
    val serverBaseUrl: String,
    val apiKey: String,
    val captureIntervalMs: Long,
    /** Where object detection runs: on the phone or on the server. */
    val objectDetectionMode: ObjectDetectionMode,
    /** Minimum detection confidence (0–1) required to show a bounding box. */
    val confidenceThreshold: Float,
    /** Draw bounding boxes on the in-app camera preview. */
    val showBoundingBoxes: Boolean
)

class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val intervalKey = longPreferencesKey("capture_interval_ms")
    private val objectDetectionModeKey = stringPreferencesKey("object_detection_mode")
    private val confidenceThresholdKey = floatPreferencesKey("confidence_threshold")
    private val showBoundingBoxesKey = booleanPreferencesKey("show_bounding_boxes")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
            objectDetectionMode = ObjectDetectionMode.fromStoredValue(pref[objectDetectionModeKey]),
            confidenceThreshold = normalizeConfidenceThreshold(
                pref[confidenceThresholdKey] ?: DEFAULT_CONFIDENCE_THRESHOLD
            ),
            showBoundingBoxes = pref[showBoundingBoxesKey] ?: DEFAULT_SHOW_BOUNDING_BOXES
        )
    }

    suspend fun save(
        serverUrl: String,
        apiKey: String,
        objectDetectionMode: ObjectDetectionMode,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        showBoundingBoxes: Boolean = DEFAULT_SHOW_BOUNDING_BOXES
    ) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[apiKeyKey] = apiKey.trim()
            pref[intervalKey] = normalizeIntervalMs(intervalMs)
            pref[objectDetectionModeKey] = objectDetectionMode.toStoredValue()
            pref[confidenceThresholdKey] = normalizeConfidenceThreshold(confidenceThreshold)
            pref[showBoundingBoxesKey] = showBoundingBoxes
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://parkiroid.xaigrok.ir"
        const val DEFAULT_API_KEY = "parkiroid-dev-key"
        const val DEFAULT_INTERVAL_MS = 15000L
        const val MIN_INTERVAL_MS = 500L
        const val MAX_INTERVAL_MS = 120000L
        const val INTERVAL_STEP_MS = 500L
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
        const val DEFAULT_SHOW_BOUNDING_BOXES = false
        const val MIN_CONFIDENCE_THRESHOLD = 0.10f
        const val MAX_CONFIDENCE_THRESHOLD = 0.90f
        const val CONFIDENCE_THRESHOLD_STEP = 0.05f

        fun normalizeIntervalMs(intervalMs: Long): Long {
            val clamped = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
            val steps = ((clamped - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).roundToInt()
            return MIN_INTERVAL_MS + steps * INTERVAL_STEP_MS
        }

        fun normalizeConfidenceThreshold(value: Float): Float {
            val clamped = value.coerceIn(MIN_CONFIDENCE_THRESHOLD, MAX_CONFIDENCE_THRESHOLD)
            val steps = ((clamped - MIN_CONFIDENCE_THRESHOLD) / CONFIDENCE_THRESHOLD_STEP).roundToInt()
            return MIN_CONFIDENCE_THRESHOLD + steps * CONFIDENCE_THRESHOLD_STEP
        }
    }
}
