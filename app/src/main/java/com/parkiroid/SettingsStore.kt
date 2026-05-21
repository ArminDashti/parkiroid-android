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
    val periodSec: Int
)

class SettingsStore(private val context: Context) {
    private val urlKey = stringPreferencesKey("server_url")
    private val periodKey = intPreferencesKey("period_sec")

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { pref ->
        AppSettings(
            serverBaseUrl = pref[urlKey] ?: "",
            periodSec = pref[periodKey] ?: 15
        )
    }

    suspend fun save(serverUrl: String, periodSec: Int) {
        context.dataStore.edit { pref ->
            pref[urlKey] = serverUrl.trim().trimEnd('/')
            pref[periodKey] = periodSec.coerceAtLeast(5)
        }
    }
}
