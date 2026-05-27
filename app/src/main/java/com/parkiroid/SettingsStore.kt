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
    val periodSec: Int,
    /** Comma-separated E.164 or local numbers for alarm SMS alerts. */
    val alertPhoneNumbers: String
)

class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val periodKey = intPreferencesKey("period_sec")
    private val alertPhonesKey = stringPreferencesKey("alert_phones")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: "",
            periodSec = pref[periodKey] ?: 15,
            alertPhoneNumbers = pref[alertPhonesKey] ?: ""
        )
    }

    suspend fun save(serverUrl: String, periodSec: Int, alertPhoneNumbers: String) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[periodKey] = periodSec.coerceAtLeast(5)
            pref[alertPhonesKey] = alertPhoneNumbers.trim()
        }
    }

    fun parsePhoneNumbers(raw: String): List<String> =
        raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
