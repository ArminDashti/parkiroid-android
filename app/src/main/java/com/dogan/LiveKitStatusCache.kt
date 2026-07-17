package com.dogan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cached LiveKit publisher + server session status for Connectivity UI. */
object LiveKitStatusCache {
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _activeSessionCount = MutableStateFlow(0)
    val activeSessionCount: StateFlow<Int> = _activeSessionCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setStreaming(connected: Boolean) {
        _streaming.value = connected
        if (connected) _lastError.value = null
    }

    fun setActiveSessionCount(count: Int) {
        _activeSessionCount.value = count.coerceAtLeast(0)
    }

    fun setError(message: String?) {
        _lastError.value = message
    }

    fun clear() {
        _streaming.value = false
        _activeSessionCount.value = 0
        _lastError.value = null
    }

    val isConnected: Boolean
        get() = _streaming.value || _activeSessionCount.value > 0
}
