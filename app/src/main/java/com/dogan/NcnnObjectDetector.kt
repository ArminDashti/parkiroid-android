package com.dogan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * NCNN-based on-device object detector. Loads models extracted by [ModelAssetManager].
 */
class NcnnObjectDetector(
    private val modelAssetManager: ModelAssetManager,
) : AutoCloseable {
    @Volatile
    private var currentModel: AiModel? = null

    fun detect(bitmap: Bitmap, confidenceThreshold: Float, aiModel: AiModel): DetectionResult {
        val modelId = aiModel.toStoredValue()
        if (!modelAssetManager.ensureModelReady(modelId)) {
            AppLogger.error("Detection", "Model not ready: $modelId")
            return DetectionResult(emptyList())
        }
        if (currentModel != aiModel) {
            if (!modelAssetManager.loadModel(aiModel)) {
                AppLogger.error("Detection", "Failed to load NCNN model: $modelId")
                return DetectionResult(emptyList())
            }
            currentModel = aiModel
        }
        if (!NcnnNative.isAvailable()) {
            AppLogger.error("Detection", "NCNN native library unavailable")
            return DetectionResult(emptyList())
        }

        // Native detector letterboxes to 640x640; pass original pixels so unletterbox is correct.
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val raw = try {
            NcnnNative.detect(pixels, bitmap.width, bitmap.height, confidenceThreshold)
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.error("Detection", "JNI detect failed: ${e.message}")
            floatArrayOf()
        }

        val labels = modelAssetManager.getLabelsForModel(aiModel)
        val detections = NcnnNative.parseDetections(raw, labels)
            .filter { it.confidence >= confidenceThreshold && it.label in ALLOWED_LABELS }
        return DetectionResult(detections)
    }

    override fun close() {
        // Clear Kotlin-side model pointer; CaptureService may restart in the same process.
        currentModel = null
    }

    companion object {
        private val ALLOWED_LABELS = setOf("person", "car")

        fun decodeJpegForDetection(jpegFile: File): Bitmap? =
            BitmapFactory.decodeFile(jpegFile.absolutePath)

        fun logResult(result: DetectionResult) {
            if (result.detections.isEmpty()) {
                AppLogger.info("Detection", "No objects detected")
            } else {
                val summary = result.detections.joinToString { "${it.label}(${"%.0f".format(it.confidence * 100)}%)" }
                AppLogger.info("Detection", "Detected: $summary")
            }
        }

        fun summarize(result: DetectionResult): String {
            if (result.detections.isEmpty()) return "On-device detection: no objects"
            val top = result.detections.take(3).joinToString { it.label }
            return "On-device detection: $top"
        }
    }
}
