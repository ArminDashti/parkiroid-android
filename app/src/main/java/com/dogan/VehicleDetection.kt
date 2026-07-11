package com.dogan

import android.graphics.RectF

/** A single detected object with label, confidence, and image-space bounds. */
data class VehicleDetection(
    val label: String,
    val confidence: Float,
    val bounds: RectF,
)

/** Result of an on-device inference pass. */
data class DetectionResult(
    val detections: List<VehicleDetection>,
)
