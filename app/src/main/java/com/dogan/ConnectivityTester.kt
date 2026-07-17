package com.dogan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs connectivity diagnostics for internet, server API, and LiveKit. */
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
        val latency = PingHelper.pingInternetAverageMs()
        if (latency >= 0) {
            return@withContext TestResult(
                name = "Internet",
                passed = true,
                latencyMs = latency,
                detail = "ICMP average over 1.1.1.1 and 8.8.8.8",
            )
        }
        val fallback = NetworkInfoCollector.measureInternetLatencyMs()
        TestResult(
            name = "Internet",
            passed = fallback >= 0,
            latencyMs = fallback.coerceAtLeast(0),
            detail = if (fallback >= 0) "HTTP fallback reachable" else "No internet connection",
        )
    }

    suspend fun testServerApi(baseUrl: String): TestResult = withContext(Dispatchers.IO) {
        val host = EndpointUrlBuilder.parseHostFromUrl(baseUrl)
        val icmp = PingHelper.pingAverageMs(host, 8)
        if (icmp >= 0) {
            val auth = apiClient.authenticateWithResult(baseUrl)
            return@withContext TestResult(
                name = "Server API",
                passed = auth.success,
                latencyMs = icmp,
                detail = if (auth.success) "ICMP + auth OK" else "Auth failed: ${auth.error ?: "unknown"}",
            )
        }
        val health = apiClient.pingHealthWithLatency(baseUrl)
        if (health == null || !health.success) {
            return@withContext TestResult(
                name = "Server API",
                passed = false,
                latencyMs = health?.latencyMs ?: 0,
                detail = health?.error ?: "Health check failed (HTTP ${health?.httpCode ?: 0})",
            )
        }
        val auth = apiClient.authenticateWithResult(baseUrl)
        TestResult(
            name = "Server API",
            passed = auth.success,
            latencyMs = health.latencyMs,
            detail = if (auth.success) "Health + auth OK (HTTP fallback)" else "Auth failed: ${auth.error ?: "unknown"}",
        )
    }

    suspend fun testLiveKit(baseUrl: String): TestResult = withContext(Dispatchers.IO) {
        val session = apiClient.createWebRtcSession(baseUrl)
        if (session == null) {
            return@withContext TestResult(
                name = "LiveKit",
                passed = false,
                latencyMs = 0,
                detail = "Could not create WebRTC session",
            )
        }
        val hasToken = session.token.isNotBlank()
        val hasUrl = session.url.isNotBlank()
        val hasRoom = session.room.isNotBlank()
        TestResult(
            name = "LiveKit",
            passed = hasToken && hasUrl && hasRoom,
            latencyMs = 0,
            detail = "Session=${session.sessionId}, token=$hasToken, url=$hasUrl, room=$hasRoom",
        )
    }

    suspend fun runAll(baseUrl: String): List<TestResult> {
        return listOf(
            testInternet(),
            testServerApi(baseUrl),
            testLiveKit(baseUrl),
        )
    }
}
