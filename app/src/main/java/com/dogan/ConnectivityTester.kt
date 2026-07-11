package com.dogan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs connectivity diagnostics for internet, server API, and WebRTC. */
class ConnectivityTester(
    private val apiClient: DoganApiClient,
) {
    data class TestResult(
        val name: String,
        val passed: Boolean,
        val latencyMs: Long,
        val detail: String,
    )

    suspend fun testInternet(): TestResult = withContext(Dispatchers.IO) {
        val latency = NetworkInfoCollector.measureInternetLatencyMs()
        val reachable = latency >= 0
        TestResult(
            name = "Internet",
            passed = reachable,
            latencyMs = latency.coerceAtLeast(0),
            detail = if (reachable) "Internet reachable" else "No internet connection",
        )
    }

    suspend fun testServerApi(baseUrl: String, apiKey: String): TestResult = withContext(Dispatchers.IO) {
        val health = apiClient.pingHealthWithLatency(baseUrl)
        if (health == null || !health.success) {
            return@withContext TestResult(
                name = "Server API",
                passed = false,
                latencyMs = health?.latencyMs ?: 0,
                detail = health?.error ?: "Health check failed (HTTP ${health?.httpCode ?: 0})",
            )
        }
        val auth = apiClient.authenticateWithResult(baseUrl, apiKey)
        TestResult(
            name = "Server API",
            passed = auth.success,
            latencyMs = health.latencyMs + auth.latencyMs,
            detail = if (auth.success) "Health + auth OK" else "Auth failed: ${auth.error ?: "unknown"}",
        )
    }

    suspend fun testServerWebRtc(baseUrl: String, apiKey: String): TestResult = withContext(Dispatchers.IO) {
        val session = apiClient.createWebRtcSession(baseUrl, apiKey)
        if (session == null) {
            return@withContext TestResult(
                name = "Server WebRTC",
                passed = false,
                latencyMs = 0,
                detail = "Could not create WebRTC session",
            )
        }
        val hasSignaling = session.signalingUrl.isNotBlank()
        val hasIce = session.iceServers != null && session.iceServers.length() > 0
        TestResult(
            name = "Server WebRTC",
            passed = hasSignaling && hasIce,
            latencyMs = 0,
            detail = "Session=${session.sessionId}, signaling=$hasSignaling, ICE=$hasIce",
        )
    }

    suspend fun runAll(baseUrl: String, apiKey: String): List<TestResult> {
        return listOf(
            testInternet(),
            testServerApi(baseUrl, apiKey),
            testServerWebRtc(baseUrl, apiKey),
        )
    }
}
