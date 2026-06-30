package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.edit
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
) {
    val intervalSec: Float get() = captureIntervalMs / 1000f
    val periodSec: Int get() = (captureIntervalMs / 1000L).toInt()
}

/** Persists and exposes Parkiroid settings via DataStore preferences. */
class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val intervalKey = longPreferencesKey("capture_interval_ms")

    /** Reactive stream of current settings with defaults applied. */
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            captureIntervalMs = normalizeIntervalMs(pref[intervalKey] ?: DEFAULT_INTERVAL_MS),
        )
    }

    /** Writes normalized settings to DataStore. */
    suspend fun save(
        serverUrl: String,
        apiKey: String,
        intervalSec: Float,
    ) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[apiKeyKey] = apiKey.trim()
            pref[intervalKey] = normalizeIntervalMs((intervalSec * 1000L).roundToInt().toLong())
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://parkiroid.xaigrok.ir"
        const val DEFAULT_API_KEY = "parkiroid-dev-key"
        const val DEFAULT_INTERVAL_MS = 15000L
        const val MIN_INTERVAL_MS = 1000L
        const val MAX_INTERVAL_MS = 60000L
        val ALLOWED_INTERVALS_SEC = listOf(1, 5, 10, 15, 30, 60)

        /** Clamps and snaps a capture interval to the allowed millisecond step grid. */
        fun normalizeIntervalMs(intervalMs: Long): Long {
            val clamped = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
            val steps = ((clamped - MIN_INTERVAL_MS) / INTERVAL_STEP_MS).roundToInt()
            return MIN_INTERVAL_MS + steps * INTERVAL_STEP_MS
        }

        fun normalizeFrameUploadIntervalSec(intervalSec: Float): Float {
            val seconds = intervalSec.roundToInt().coerceIn(1, 60)
            return ALLOWED_INTERVALS_SEC.minByOrNull { kotlin.math.abs(it - seconds) }?.toFloat()
                ?: DEFAULT_INTERVAL_MS / 1000f
        }

        /** Clamps and snaps a confidence threshold to the allowed step grid. */
        fun normalizeConfidenceThreshold(value: Float): Float {
            val clamped = value.coerceIn(MIN_CONFIDENCE_THRESHOLD, MAX_CONFIDENCE_THRESHOLD)
            val steps = ((clamped - MIN_CONFIDENCE_THRESHOLD) / CONFIDENCE_THRESHOLD_STEP).roundToInt()
            return MIN_CONFIDENCE_THRESHOLD + steps * CONFIDENCE_THRESHOLD_STEP
        }
        }
    }
}
