package com.parkiroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Placeholder on-device detector. Model assets were removed; this keeps the
 * detection pipeline wired for future model integration.
 */
class ParkiroidObjectDetector(@Suppress("UNUSED_PARAMETER") context: Context) : AutoCloseable {

    fun detect(bitmap: Bitmap, confidenceThreshold: Float): DetectionResult {
        return DetectionResult(emptyList())
    }

    override fun close() = Unit

    companion object {
        fun decodeJpegForDetection(jpegFile: File): Bitmap? =
            BitmapFactory.decodeFile(jpegFile.absolutePath)

        fun logResult(result: DetectionResult) {
            if (result.detections.isNotEmpty()) {
                AppLogger.info("Detection", summarize(result))
            }
        }

        fun summarize(result: DetectionResult): String =
            if (result.detections.isEmpty()) {
                "No objects detected"
            } else {
                result.detections.joinToString { "${it.label} ${(it.confidence * 100).toInt()}%" }
            }
    }
}
