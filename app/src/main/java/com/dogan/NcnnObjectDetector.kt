package com.dogan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * NCNN-based on-device object detector. Loads models downloaded by [ModelDownloadManager].
 */
class NcnnObjectDetector(
    private val context: Context,
    private val modelDownloadManager: ModelDownloadManager,
) : AutoCloseable {
    @Volatile
    private var closed = false
    @Volatile
    private var currentModel: AiModel? = null

    fun detect(bitmap: Bitmap, confidenceThreshold: Float, aiModel: AiModel): DetectionResult {
        if (closed) return DetectionResult(emptyList())
        if (!modelDownloadManager.isModelReady(aiModel.toStoredValue())) {
            return DetectionResult(emptyList())
        }
        if (currentModel != aiModel) {
            if (!modelDownloadManager.loadModel(aiModel)) {
                return DetectionResult(emptyList())
            }
            currentModel = aiModel
        }
        if (!NcnnNative.isAvailable()) {
            return DetectionResult(emptyList())
        }

        val scaled = scaleBitmap(bitmap, 640, 640)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val raw = try {
            NcnnNative.detect(pixels, scaled.width, scaled.height, confidenceThreshold)
        } catch (_: UnsatisfiedLinkError) {
            floatArrayOf()
        }
        if (scaled !== bitmap) scaled.recycle()

        val labels = modelDownloadManager.getLabelsForModel(aiModel)
        val detections = NcnnNative.parseDetections(raw, labels)
            .filter { it.confidence >= confidenceThreshold }
        return DetectionResult(detections)
    }

    override fun close() {
        closed = true
        currentModel = null
    }

    companion object {
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

        private fun scaleBitmap(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
            if (source.width == targetW && source.height == targetH) return source
            return Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }
    }
}
