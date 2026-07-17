package com.dogan

/** NCNN-based speed limit sign OCR detector. */
class SignOcrDetector(
    private val ncnnObjectDetector: NcnnObjectDetector,
) {
    @Volatile
    private var lastDetectedLimit: Int? = null

    fun detectSpeedLimit(
        detections: List<VehicleDetection>,
        settings: AppSettings,
    ): Int? {
        val signs = detections.filter {
            it.label == "speed_limit_sign" && it.confidence >= settings.confidenceForMode(OperatingMode.COPILOT)
        }
        if (signs.isEmpty()) return lastDetectedLimit

        val best = signs.maxByOrNull { it.confidence } ?: return lastDetectedLimit
        val estimated = estimateLimitFromBounds(best.bounds)
        lastDetectedLimit = estimated
        return estimated
    }

    fun reset() {
        lastDetectedLimit = null
    }

    private fun estimateLimitFromBounds(bounds: android.graphics.RectF): Int {
        val aspect = bounds.width() / bounds.height().coerceAtLeast(1f)
        return when {
            aspect > 1.2f -> 60
            aspect > 0.8f -> 50
            else -> 30
        }
    }
}
