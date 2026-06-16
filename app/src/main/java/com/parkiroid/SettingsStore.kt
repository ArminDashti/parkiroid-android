package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("parkiroid_settings")

data class AppSettings(
    val serverBaseUrl: String,
    val apiKey: String,
    val periodSec: Int,
    /** Where object detection runs: on the phone or on the server. */
    val objectDetectionMode: ObjectDetectionMode
)

class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val periodKey = intPreferencesKey("period_sec")
    private val objectDetectionModeKey = stringPreferencesKey("object_detection_mode")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: DEFAULT_SERVER_BASE_URL,
            apiKey = pref[apiKeyKey] ?: DEFAULT_API_KEY,
            periodSec = normalizeFrameUploadInterval(pref[periodKey] ?: DEFAULT_PERIOD_SEC),
            objectDetectionMode = ObjectDetectionMode.fromStoredValue(pref[objectDetectionModeKey])
        )
    }

    suspend fun save(
        serverUrl: String,
        apiKey: String,
        objectDetectionMode: ObjectDetectionMode,
        periodSec: Int = 15
    ) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[apiKeyKey] = apiKey.trim()
            pref[periodKey] = normalizeFrameUploadInterval(periodSec)
            pref[objectDetectionModeKey] = objectDetectionMode.toStoredValue()
        }
    }

    companion object {
        const val DEFAULT_SERVER_BASE_URL = "https://parkiroid.xaigrok.ir"
        const val DEFAULT_API_KEY = "parkiroid-dev-key"
        const val DEFAULT_PERIOD_SEC = 15
        val ALLOWED_FRAME_UPLOAD_INTERVALS_SEC = listOf(1, 5, 10, 15, 30, 45, 60)

        fun normalizeFrameUploadInterval(seconds: Int): Int =
            if (seconds in ALLOWED_FRAME_UPLOAD_INTERVALS_SEC) seconds else DEFAULT_PERIOD_SEC
    }
}
