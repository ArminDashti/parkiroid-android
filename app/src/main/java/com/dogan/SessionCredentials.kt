package com.dogan

/**
 * In-memory username/password used by [DoganApiClient] for API and LiveKit
 * (both use the same bearer token from `POST /api/v1/auth`).
 */
object SessionCredentials {
    @Volatile
    var username: String = SettingsStore.DEFAULT_USERNAME
        private set

    @Volatile
    var password: String = SettingsStore.DEFAULT_PASSWORD
        private set

    fun update(username: String, password: String) {
        this.username = username.trim()
        this.password = password
    }

    fun updateFrom(settings: AppSettings) {
        update(settings.username, settings.password)
    }

    fun clear() {
        username = ""
        password = ""
    }
}
