package com.dogan

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts embedded NCNN model files from APK assets into [filesDir] and loads them via JNI.
 */
class ModelAssetManager(
    private val context: Context,
) {
    @Volatile
    private var loadedModelId: String? = null

    fun modelsDir(): File = File(context.filesDir, "models").also { it.mkdirs() }

    fun modelDir(id: String): File = File(modelsDir(), id).also { it.mkdirs() }

    fun isModelReady(id: String): Boolean {
        val dir = modelDir(id)
        return File(dir, "model.param").exists() && File(dir, "model.bin").exists()
    }

    /** Copy model files from assets if missing or stale vs the embedded APK copy. */
    fun ensureModelReady(id: String): Boolean {
        val dir = modelDir(id)
        val paramFile = File(dir, "model.param")
        val binFile = File(dir, "model.bin")
        val stampFile = File(dir, "asset.stamp")
        val assetParam = "models/$id/model.param"
        val assetBin = "models/$id/model.bin"
        return try {
            val stamp = assetStamp(assetParam, assetBin)
            val upToDate = paramFile.exists() && binFile.exists() &&
                stampFile.exists() && stampFile.readText() == stamp
            if (!upToDate) {
                copyAsset(assetParam, paramFile)
                copyAsset(assetBin, binFile)
                stampFile.writeText(stamp)
                AppLogger.info("Models", "Extracted embedded NCNN model $id")
            }
            true
        } catch (e: Exception) {
            AppLogger.error("Models", "Failed to extract model $id: ${e.message}")
            false
        }
    }

    /** Prefer asset byte lengths; fall back to a fixed build stamp when assets are compressed. */
    private fun assetStamp(assetParam: String, assetBin: String): String {
        return try {
            val paramLen = context.assets.openFd(assetParam).use { it.length }
            val binLen = context.assets.openFd(assetBin).use { it.length }
            "$paramLen:$binLen"
        } catch (_: Exception) {
            // Compressed assets cannot use openFd; use APK version so reinstall refreshes files.
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            } catch (_: Exception) {
                0
            }
            "v$version"
        }
    }

    private fun copyAsset(assetPath: String, dest: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun ensureSelectedModelReady(aiModel: AiModel): Boolean =
        ensureModelReady(aiModel.toStoredValue())

    fun getLabelsForModel(@Suppress("UNUSED_PARAMETER") aiModel: AiModel): List<String> = COCO_LABELS

    fun loadModel(aiModel: AiModel): Boolean {
        if (!NcnnNative.isAvailable()) {
            AppLogger.error("Models", "Cannot load model: NCNN native library unavailable")
            return false
        }
        val id = aiModel.toStoredValue()
        if (!ensureModelReady(id)) {
            AppLogger.error("Models", "Cannot load model: files not ready for $id")
            return false
        }
        if (loadedModelId == id) return true
        val dir = modelDir(id)
        val ok = NcnnNative.loadModel(
            File(dir, "model.param").absolutePath,
            File(dir, "model.bin").absolutePath,
            id,
        )
        if (ok) {
            loadedModelId = id
            AppLogger.info("Models", "Loaded NCNN model $id")
        } else {
            AppLogger.error("Models", "NCNN loadModel returned false for $id")
        }
        return ok
    }

    companion object {
        /** COCO-80 class names matching Ultralytics YOLO26 metadata.yaml. */
        val COCO_LABELS: List<String> = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush",
        )
    }
}

/** Shared result type for download/extract operations. */
data class DownloadResult(val success: Boolean, val message: String)
