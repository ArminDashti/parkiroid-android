package com.dogan

/** Bridges detection results from CaptureService to MainActivity for overlay rendering. */
object DetectionOverlayBridge {
    @Volatile
    var listener: ((List<VehicleDetection>, Int, Int) -> Unit)? = null

    fun publish(detections: List<VehicleDetection>, imageWidth: Int, imageHeight: Int) {
        listener?.invoke(detections, imageWidth, imageHeight)
    }

    fun clear() {
        listener?.invoke(emptyList(), 0, 0)
    }
}
