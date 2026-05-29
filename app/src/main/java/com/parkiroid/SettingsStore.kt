package com.parkiroid

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("parkiroid_settings")

data class AppSettings(
    val serverBaseUrl: String,
    val periodSec: Int,
    /** Acceleration magnitude (m/s²) above which a violent-jolt alarm fires. */
    val maxShakeMagnitude: Float,
    /** Comma-separated E.164 or local numbers for alarm SMS alerts. */
    val alertPhoneNumbers: String
) {
    /** Jarring-noise tier uses 60% of the violent-jolt threshold. */
    val jarringShakeMagnitude: Float get() = maxShakeMagnitude * 0.6f
}

class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val periodKey = intPreferencesKey("period_sec")
    private val maxShakeKey = floatPreferencesKey("max_shake_magnitude")
    private val alertPhonesKey = stringPreferencesKey("alert_phones")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: "",
            periodSec = pref[periodKey] ?: 15,
            maxShakeMagnitude = pref[maxShakeKey] ?: DEFAULT_MAX_SHAKE_MAGNITUDE,
            alertPhoneNumbers = pref[alertPhonesKey] ?: ""
        )
    }

    suspend fun save(
        serverUrl: String,
        maxShakeMagnitude: Float,
        alertPhoneNumbers: String,
        periodSec: Int = 15
    ) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[periodKey] = periodSec.coerceAtLeast(5)
            pref[maxShakeKey] = maxShakeMagnitude.coerceIn(MIN_SHAKE_MAGNITUDE, MAX_SHAKE_MAGNITUDE)
            pref[alertPhonesKey] = alertPhoneNumbers.trim()
        }
    }

    fun parsePhoneNumbers(raw: String): List<String> =
        raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val DEFAULT_MAX_SHAKE_MAGNITUDE = 30f
        const val MIN_SHAKE_MAGNITUDE = 5f
        const val MAX_SHAKE_MAGNITUDE = 80f
    }
}
