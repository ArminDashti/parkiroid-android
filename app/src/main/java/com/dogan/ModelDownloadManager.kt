package com.dogan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** Downloads and manages NCNN model files from the Dogan server. */
class ModelDownloadManager(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    data class ModelEntry(
        val id: String,
        val paramUrl: String,
        val binUrl: String,
        val paramSha256: String,
        val binSha256: String,
        val labels: List<String>,
    )

    @Volatile
    private var loadedModelId: String? = null

    private val labelsByModelId = mutableMapOf<String, List<String>>()

    fun modelsDir(): File = File(context.filesDir, "models").also { it.mkdirs() }

    fun modelDir(id: String): File = File(modelsDir(), id).also { it.mkdirs() }

    fun isModelReady(id: String): Boolean {
        val dir = modelDir(id)
        return File(dir, "model.param").exists() && File(dir, "model.bin").exists()
    }

    fun fetchAndDownloadModel(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        wifiOnly: Boolean,
    ): DownloadResult {
        if (wifiOnly && !NetworkInfoCollector.isWifiConnected(context)) {
            return DownloadResult(false, "Wi-Fi required for model download")
        }
        val manifest = apiClient.fetchModelsManifest(baseUrl, apiKey)
            ?: return DownloadResult(false, "Could not fetch models manifest")
        val entries = parseManifest(manifest)
        cacheLabelsFromManifest(entries)
        val entry = entries.find { it.id == modelId }
            ?: return DownloadResult(false, "Model $modelId not in manifest")
        val ok = downloadModel(baseUrl, apiKey, entry)
        return DownloadResult(ok, if (ok) "Model $modelId ready" else "Failed to download $modelId")
    }

    fun fetchAndDownloadAll(baseUrl: String, apiKey: String, wifiOnly: Boolean): DownloadResult {
        if (wifiOnly && !NetworkInfoCollector.isWifiConnected(context)) {
            return DownloadResult(false, "Wi-Fi required for model download")
        }
        val manifest = apiClient.fetchModelsManifest(baseUrl, apiKey)
            ?: return DownloadResult(false, "Could not fetch models manifest")
        val entries = parseManifest(manifest)
        cacheLabelsFromManifest(entries)
        var failed = 0
        for (entry in entries) {
            if (!downloadModel(baseUrl, apiKey, entry)) failed++
        }
        return DownloadResult(failed == 0, if (failed > 0) "$failed model(s) failed" else "All models ready")
    }

    fun downloadModel(baseUrl: String, apiKey: String, entry: ModelEntry): Boolean {
        val dir = modelDir(entry.id)
        val paramFile = File(dir, "model.param")
        val binFile = File(dir, "model.bin")
        if (paramFile.exists() && binFile.exists()) return true

        val paramBytes = apiClient.downloadFile(baseUrl, apiKey, entry.paramUrl) ?: return false
        val binBytes = apiClient.downloadFile(baseUrl, apiKey, entry.binUrl) ?: return false
        if (!verifySha256(paramBytes, entry.paramSha256)) return false
        if (!verifySha256(binBytes, entry.binSha256)) return false

        paramFile.writeBytes(paramBytes)
        binFile.writeBytes(binBytes)
        AppLogger.info("Models", "Downloaded NCNN model ${entry.id}")
        return true
    }

    fun getLabelsForModel(aiModel: AiModel): List<String> {
        labelsByModelId[aiModel.toStoredValue()]?.let { return it }
        return when (aiModel) {
            AiModel.YOLOV8_NANO, AiModel.YOLOV8_SMALL ->
                listOf("person", "car", "motorcycle", "truck", "speed_camera", "speed_limit_sign")
            AiModel.MOBILENET_SSD ->
                listOf("person", "car", "motorcycle", "truck")
        }
    }

    fun loadModel(aiModel: AiModel): Boolean {
        if (!NcnnNative.isAvailable()) return false
        val id = aiModel.toStoredValue()
        if (!isModelReady(id)) return false
        if (loadedModelId == id) return true
        val dir = modelDir(id)
        val ok = NcnnNative.loadModel(
            File(dir, "model.param").absolutePath,
            File(dir, "model.bin").absolutePath,
            id,
        )
        if (ok) loadedModelId = id
        return ok
    }

    private fun cacheLabelsFromManifest(entries: List<ModelEntry>) {
        for (entry in entries) {
            if (entry.labels.isNotEmpty()) {
                labelsByModelId[entry.id] = entry.labels
            }
        }
    }

    private fun parseManifest(array: JSONArray): List<ModelEntry> {
        val entries = mutableListOf<ModelEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val labels = mutableListOf<String>()
            val labelsArray = obj.optJSONArray("labels")
            if (labelsArray != null) {
                for (j in 0 until labelsArray.length()) {
                    labels.add(labelsArray.getString(j))
                }
            }
            entries.add(
                ModelEntry(
                    id = obj.getString("id"),
                    paramUrl = obj.getString("param_url"),
                    binUrl = obj.getString("bin_url"),
                    paramSha256 = obj.getString("param_sha256"),
                    binSha256 = obj.getString("bin_sha256"),
                    labels = labels,
                ),
            )
        }
        return entries
    }

    private fun verifySha256(data: ByteArray, expected: String): Boolean {
        if (expected.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.equals(expected, ignoreCase = true)
    }

    data class DownloadResult(val success: Boolean, val message: String)
}
