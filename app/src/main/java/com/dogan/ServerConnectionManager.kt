package com.dogan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Runtime server connection state, separate from persisted settings. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

object ServerConnectionManager {
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    fun connect(context: Context, settings: AppSettings): Boolean {
        _status.value = ConnectionStatus.CONNECTING
        lastError = null
        AppLogger.info("Server", "Connecting to ${settings.serverBaseUrl}")

        val deviceId = DeviceIdentity.resolveDeviceId(context)
        val client = DoganApiClient(deviceId = deviceId)
        val ok = client.testConnection(settings.serverBaseUrl, settings.apiKey)

        if (ok) {
            _status.value = ConnectionStatus.CONNECTED
            AppLogger.info("Server", "Connected")
        } else {
            _status.value = ConnectionStatus.FAILED
            lastError = "Authentication or health check failed"
            AppLogger.error("Server", lastError!!)
        }
        return ok
    }

    fun disconnect() {
        _status.value = ConnectionStatus.DISCONNECTED
        lastError = null
        AppLogger.info("Server", "Disconnected")
    }

    fun isConnected(): Boolean = _status.value == ConnectionStatus.CONNECTED
}
