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

class ParkiroidApiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val deviceId: String
) {
    @Volatile private var bearerToken: String? = null
    @Volatile private var tokenExpiresAtEpochMillis: Long = 0L

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

    private fun postAuthenticated(baseUrl: String, apiKey: String, path: String, body: String): Boolean {
        val firstAttempt = executePost(baseUrl, path, body, bearerToken)
        if (firstAttempt != PostResult.Unauthorized) {
            return firstAttempt == PostResult.Success
        }
        clearToken()
        if (!authenticate(baseUrl, apiKey)) return false
        return executePost(baseUrl, path, body, bearerToken) == PostResult.Success
    }

    private fun ensureAuthenticated(baseUrl: String, apiKey: String): Boolean {
        val token = bearerToken
        val refreshAt = tokenExpiresAtEpochMillis - TOKEN_REFRESH_LEAD_MS
        if (token != null && System.currentTimeMillis() < refreshAt) {
            return true
        }
        return authenticate(baseUrl, apiKey)
    }

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

    private fun clearToken() {
        bearerToken = null
        tokenExpiresAtEpochMillis = 0L
    }

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
