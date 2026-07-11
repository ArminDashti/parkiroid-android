package com.dogan

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/** HTTP client for Dogan server auth, telemetry, models, sounds, and diagnostics. */
class DoganApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val deviceId: String,
) {
    @Volatile private var bearerToken: String? = null
    @Volatile private var tokenExpiresAtEpochMillis: Long = 0L

    fun testConnection(baseUrl: String, apiKey: String): Boolean {
        if (!isValidBaseUrl(baseUrl)) return false
        if (pingHealth(baseUrl) != null) return true
        return authenticate(baseUrl, apiKey)
    }

    fun pingHealthWithLatency(baseUrl: String): HealthResult? {
        if (!isValidBaseUrl(baseUrl)) return null
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url("$baseUrl/dogan/api/v1/health")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                HealthResult(success = response.isSuccessful, latencyMs = latency, httpCode = response.code)
            }
        } catch (e: Exception) {
            HealthResult(success = false, latencyMs = System.currentTimeMillis() - start, error = e.message)
        }
    }

    fun authenticateWithResult(baseUrl: String, apiKey: String): AuthResult {
        if (!isValidBaseUrl(baseUrl)) return AuthResult(false, error = "Invalid base URL")
        val start = System.currentTimeMillis()
        val ok = authenticate(baseUrl, apiKey)
        return AuthResult(ok, latencyMs = System.currentTimeMillis() - start, token = if (ok) bearerToken else null)
    }

    fun submitTelemetry(baseUrl: String, apiKey: String, payload: JSONObject): Boolean {
        if (!ensureAuthenticated(baseUrl, apiKey)) return false
        return postAuthenticated(baseUrl, apiKey, "/dogan/api/v1/telemetry", payload.toString()) == PostResult.Success
    }

    fun fetchModelsManifest(baseUrl: String, apiKey: String): JSONArray? {
        if (!ensureAuthenticated(baseUrl, apiKey)) return null
        return getAuthenticatedJson(baseUrl, "/dogan/api/v1/models")
    }

    fun fetchSoundsManifest(baseUrl: String, apiKey: String): JSONArray? {
        if (!ensureAuthenticated(baseUrl, apiKey)) return null
        return getAuthenticatedJson(baseUrl, "/dogan/api/v1/sounds")
    }

    fun downloadFile(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun createWebRtcSession(baseUrl: String, apiKey: String): WebRtcSessionResult? {
        if (!ensureAuthenticated(baseUrl, apiKey)) return null
        val payload = JSONObject().put("device_id", deviceId).toString()
        val result = postAuthenticatedWithBody(baseUrl, apiKey, "/dogan/api/v1/webrtc/session", payload)
        if (result.body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(result.body!!)
            WebRtcSessionResult(
                sessionId = json.optString("session_id"),
                signalingUrl = json.optString("signaling_url"),
                iceServers = json.optJSONArray("ice_servers"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun submitDiagnosticAudio(
        baseUrl: String,
        apiKey: String,
        wavFile: File,
        metadata: JSONObject,
    ): Boolean {
        if (!ensureAuthenticated(baseUrl, apiKey)) return false
        val token = bearerToken ?: return false
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("metadata", metadata.toString())
            .addFormDataPart(
                "audio",
                wavFile.name,
                wavFile.asRequestBody("audio/wav".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("$baseUrl/dogan/api/v1/diagnostic-audio")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return try {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun getBearerToken(): String? = bearerToken

    private fun postAuthenticated(baseUrl: String, apiKey: String, path: String, body: String): PostResult {
        val firstAttempt = executePost(baseUrl, path, body, bearerToken)
        if (firstAttempt.result != PostResult.Unauthorized) return firstAttempt.result
        clearToken()
        if (!authenticate(baseUrl, apiKey)) return PostResult.Failed
        return executePost(baseUrl, path, body, bearerToken).result
    }

    private fun postAuthenticatedWithBody(baseUrl: String, apiKey: String, path: String, body: String): PostResponse {
        val first = executePost(baseUrl, path, body, bearerToken)
        if (first.result != PostResult.Unauthorized) return first
        clearToken()
        if (!authenticate(baseUrl, apiKey)) return PostResponse(PostResult.Failed, null)
        return executePost(baseUrl, path, body, bearerToken)
    }

    private fun getAuthenticatedJson(baseUrl: String, path: String): JSONArray? {
        val token = bearerToken ?: return null
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                json.optJSONArray("models") ?: json.optJSONArray("sounds") ?: JSONArray(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pingHealth(baseUrl: String): HealthResult? = pingHealthWithLatency(baseUrl)

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun ensureAuthenticated(baseUrl: String, apiKey: String): Boolean {
        val token = bearerToken
        val refreshAt = tokenExpiresAtEpochMillis - TOKEN_REFRESH_LEAD_MS
        if (token != null && System.currentTimeMillis() < refreshAt) return true
        return authenticate(baseUrl, apiKey)
    }

    private fun authenticate(baseUrl: String, apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) return false
        val payload = JSONObject().put("api_key", trimmedKey).toString()
        val request = Request.Builder()
            .url("$baseUrl/dogan/api/v1/auth")
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

    private fun executePost(baseUrl: String, path: String, body: String, token: String?): PostResponse {
        if (token.isNullOrBlank()) return PostResponse(PostResult.Unauthorized, null)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                val result = when {
                    response.isSuccessful -> PostResult.Success
                    response.code == 401 -> PostResult.Unauthorized
                    else -> PostResult.Failed
                }
                PostResponse(result, responseBody)
            }
        } catch (_: Exception) {
            PostResponse(PostResult.Failed, null)
        }
    }

    private fun parseExpiresAt(expiresAt: String): Long {
        if (expiresAt.isBlank()) return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)
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

    private enum class PostResult { Success, Unauthorized, Failed }

    data class HealthResult(
        val success: Boolean,
        val latencyMs: Long,
        val httpCode: Int = 0,
        val error: String? = null,
    )

    data class AuthResult(
        val success: Boolean,
        val latencyMs: Long = 0,
        val token: String? = null,
        val error: String? = null,
    )

    data class WebRtcSessionResult(
        val sessionId: String,
        val signalingUrl: String,
        val iceServers: JSONArray?,
    )

    private data class PostResponse(val result: PostResult, val body: String?)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val TOKEN_REFRESH_LEAD_MS = 5 * 60 * 1000L
    }
}
