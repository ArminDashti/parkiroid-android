package com.dogan

/** JNI bridge to NCNN inference engine. */
object NcnnNative {
    init {
        try {
            System.loadLibrary("dogan_ncnn")
        } catch (_: UnsatisfiedLinkError) {
            // Native library not built; detector returns empty results.
        }
    }

    @Volatile
    private var libraryLoaded = false

    fun isAvailable(): Boolean {
        if (!libraryLoaded) {
            try {
                nativeVersion()
                libraryLoaded = true
            } catch (_: UnsatisfiedLinkError) {
                libraryLoaded = false
            }
        }
        return libraryLoaded
    }

    external fun nativeVersion(): String

    external fun loadModel(paramPath: String, binPath: String, modelId: String): Boolean

    external fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        confidenceThreshold: Float,
    ): FloatArray

    /** Parses flat float array [labelIndex, confidence, left, top, right, bottom, ...] into detections. */
    fun parseDetections(raw: FloatArray, labels: List<String>): List<VehicleDetection> {
        if (raw.isEmpty()) return emptyList()
        val detections = mutableListOf<VehicleDetection>()
        var i = 0
        while (i + 5 < raw.size) {
            val labelIndex = raw[i].toInt()
            val confidence = raw[i + 1]
            val left = raw[i + 2]
            val top = raw[i + 3]
            val right = raw[i + 4]
            val bottom = raw[i + 5]
            val label = labels.getOrElse(labelIndex) { "unknown" }
            detections.add(
                VehicleDetection(
                    label = label,
                    confidence = confidence,
                    bounds = android.graphics.RectF(left, top, right, bottom),
                ),
            )
            i += 6
        }
        return detections
    }
}
