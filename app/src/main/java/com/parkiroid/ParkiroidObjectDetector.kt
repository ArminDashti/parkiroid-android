package com.parkiroid

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

class ParkiroidObjectDetector(context: Context) : AutoCloseable {
    private val detector: ObjectDetector

    init {
        val baseOptions = BaseOptions.builder()
            .setDelegate(Delegate.CPU)
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(SCORE_THRESHOLD)
            .setMaxResults(MAX_RESULTS)
            .setCategoryAllowlist(listOf(CAR_CATEGORY))
            .build()
        detector = ObjectDetector.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): ObjectDetectorResult = detector.detect(BitmapImageBuilder(bitmap).build())

    override fun close() {
        detector.close()
    }

    companion object {
        private const val TAG = "ParkiroidObjectDetector"
        const val MODEL_ASSET = "efficientdet-lite0.tflite"
        const val CAR_CATEGORY = "car"
        const val SCORE_THRESHOLD = 0.5f
        const val MAX_RESULTS = 5

        fun summarize(result: ObjectDetectorResult): String {
            val detections = result.detections()
            if (detections.isEmpty()) return "no cars"
            return detections.joinToString(", ") { detection ->
                val category = detection.categories().firstOrNull()
                val score = category?.score() ?: 0f
                "car (${"%.0f".format(score * 100)}%)"
            }
        }

        fun logResult(result: ObjectDetectorResult) {
            Log.d(TAG, "Object detection: ${summarize(result)}")
        }
    }
}
