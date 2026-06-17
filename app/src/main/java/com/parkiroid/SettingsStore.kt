package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.math.roundToInt
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("parkiroid_settings")

data class AppSettings(
    val serverBaseUrl: String,
    val apiKey: String,
    val periodSec: Int,
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
    private val periodKey = intPreferencesKey("period_sec")
    private val objectDetectionModeKey = stringPreferencesKey("object_detection_mode")
    private val confidenceThresholdKey = floatPreferencesKey("confidence_threshold")
    private val showBoundingBoxesKey = booleanPreferencesKey("show_bounding_boxes")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            periodSec = normalizeFrameUploadInterval(pref[periodKey] ?: DEFAULT_PERIOD_SEC),
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
        periodSec: Int = 15,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        showBoundingBoxes: Boolean = DEFAULT_SHOW_BOUNDING_BOXES
    ) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[apiKeyKey] = apiKey.trim()
            pref[periodKey] = normalizeFrameUploadInterval(periodSec)
            pref[objectDetectionModeKey] = objectDetectionMode.toStoredValue()
            pref[confidenceThresholdKey] = normalizeConfidenceThreshold(confidenceThreshold)
            pref[showBoundingBoxesKey] = showBoundingBoxes
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://parkiroid.xaigrok.ir"
        const val DEFAULT_API_KEY = "parkiroid-dev-key"
        const val DEFAULT_PERIOD_SEC = 15
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
        const val DEFAULT_SHOW_BOUNDING_BOXES = false
        const val MIN_CONFIDENCE_THRESHOLD = 0.10f
        const val MAX_CONFIDENCE_THRESHOLD = 0.90f
        const val CONFIDENCE_THRESHOLD_STEP = 0.05f
        val ALLOWED_FRAME_UPLOAD_INTERVALS_SEC = listOf(1, 5, 10, 15, 30, 45, 60)

        fun normalizeFrameUploadInterval(seconds: Int): Int =
            if (seconds in ALLOWED_FRAME_UPLOAD_INTERVALS_SEC) seconds else DEFAULT_PERIOD_SEC

        fun normalizeConfidenceThreshold(value: Float): Float {
            val clamped = value.coerceIn(MIN_CONFIDENCE_THRESHOLD, MAX_CONFIDENCE_THRESHOLD)
            val steps = ((clamped - MIN_CONFIDENCE_THRESHOLD) / CONFIDENCE_THRESHOLD_STEP).roundToInt()
            return MIN_CONFIDENCE_THRESHOLD + steps * CONFIDENCE_THRESHOLD_STEP
        }
    }
}
