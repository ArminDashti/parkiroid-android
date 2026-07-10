package com.parkiroid

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/** HTTP client for Parkiroid server auth, frame upload, and device metrics. */
class ParkiroidApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val deviceId: String
) {
    @Volatile private var bearerToken: String? = null
    @Volatile private var tokenExpiresAtEpochMillis: Long = 0L

    /** Verifies reachability and authentication against the configured server. */
    fun testConnection(baseUrl: String, apiKey: String): Boolean {
        if (!isValidBaseUrl(baseUrl)) return false
        if (pingHealth(baseUrl)) return true
        return authenticate(baseUrl, apiKey)
    }

    /** Uploads a captured JPEG frame to the server after ensuring a valid bearer token. */
    fun submitFrame(baseUrl: String, apiKey: String, jpegFile: File, capturedAt: Instant): Boolean {
        if (!ensureAuthenticated(baseUrl, apiKey)) return false
        val imageBase64 = Base64.encodeToString(jpegFile.readBytes(), Base64.NO_WRAP)
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("image_data", imageBase64)
            .put("captured_at", capturedAt.toString())
            .toString()
        return postAuthenticated(
            baseUrl = baseUrl,
            apiKey = apiKey,
            path = "/parkiroid/api/v1/frame",
            body = payload
        )
    }

    /** Sends current battery level and temperature readings to the server. */
    fun submitDeviceMetrics(
        baseUrl: String,
        apiKey: String,
        batteryLevelPercent: Int,
        temperatureCelsius: Float,
        recordedAt: Instant
    ): Boolean {
        if (!ensureAuthenticated(baseUrl, apiKey)) return false
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("battery_level_percent", batteryLevelPercent.toDouble())
            .put("temperature_celsius", temperatureCelsius.toDouble())
            .put("recorded_at", recordedAt.toString())
            .toString()
        return postAuthenticated(
            baseUrl = baseUrl,
            apiKey = apiKey,
            path = "/parkiroid/api/v1/device-metrics",
            body = payload
        )
    }

    /** Posts JSON with bearer auth, retrying once after re-authentication on 401. */
    private fun postAuthenticated(baseUrl: String, apiKey: String, path: String, body: String): Boolean {
        val firstAttempt = executePost(baseUrl, path, body, bearerToken)
        if (firstAttempt != PostResult.Unauthorized) {
            return firstAttempt == PostResult.Success
        }
        clearToken()
        if (!authenticate(baseUrl, apiKey)) return false
        return executePost(baseUrl, path, body, bearerToken) == PostResult.Success
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun pingHealth(baseUrl: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/parkiroid/api/v1/health")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true when a cached token exists and has not reached its refresh window. */
    private fun ensureAuthenticated(baseUrl: String, apiKey: String): Boolean {
        val token = bearerToken
        val refreshAt = tokenExpiresAtEpochMillis - TOKEN_REFRESH_LEAD_MS
        if (token != null && System.currentTimeMillis() < refreshAt) {
            return true
        }
        return authenticate(baseUrl, apiKey)
    }

    /** Exchanges the API key for a bearer token via POST /auth. */
    private fun authenticate(baseUrl: String, apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) return false
        val payload = JSONObject().put("api_key", trimmedKey).toString()
        val request = Request.Builder()
            .url("$baseUrl/parkiroid/api/v1/auth")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val responseBody = response.body?.string() ?: return false
                val json = JSONObject(responseBody)
                bearerToken = json.getString("token")
                tokenExpiresAtEpochMillis = parseExpiresAt(json.optString("expires_at"))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Executes an authenticated POST and classifies the HTTP outcome. */
    private fun executePost(baseUrl: String, path: String, body: String, token: String?): PostResult {
        if (token.isNullOrBlank()) return PostResult.Unauthorized
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> PostResult.Success
                    response.code == 401 -> PostResult.Unauthorized
                    else -> PostResult.Failed
                }
            }
        } catch (_: Exception) {
            PostResult.Failed
        }
    }

    /** Parses token expiry from ISO-8601, falling back to one hour from now. */
    private fun parseExpiresAt(expiresAt: String): Long {
        if (expiresAt.isBlank()) {
            return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        }
        return try {
            Instant.parse(expiresAt).toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
        }
    }

    /** Discards the cached bearer token so the next request re-authenticates. */
    private fun clearToken() {
        bearerToken = null
        tokenExpiresAtEpochMillis = 0L
    }

    /** Outcome of an authenticated POST attempt. */
    private enum class PostResult {
        Success,
        Unauthorized,
        Failed
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val TOKEN_REFRESH_LEAD_MS = 5 * 60 * 1000L
    }
}
