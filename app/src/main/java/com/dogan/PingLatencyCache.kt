package com.dogan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cached server ping latency for main-screen connect button. */
object PingLatencyCache {
    private val _latencyMs = MutableStateFlow(-1L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    @Volatile
    var lastUpdatedAt: Long = 0L
        private set

    fun update(latencyMs: Long) {
        _latencyMs.value = latencyMs
        lastUpdatedAt = System.currentTimeMillis()
    }

    fun clear() {
        _latencyMs.value = -1L
        lastUpdatedAt = 0L
    }
}
