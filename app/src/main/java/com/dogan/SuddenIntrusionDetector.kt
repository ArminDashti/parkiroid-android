package com.dogan

import android.graphics.RectF

/** Detects sudden intrusion of person/car/motorcycle into the roadway. */
class SuddenIntrusionDetector {
    private val previousCenters = mutableMapOf<String, Pair<Float, Float>>()
    private var lastAlertAt = 0L

    fun analyze(
        detections: List<VehicleDetection>,
        imageWidth: Int,
        imageHeight: Int,
        settings: AppSettings,
        onIntrusion: () -> Unit,
    ) {
        if (!settings.copilotDistanceControlEnabled) return
        val minConfidence = settings.confidenceForMode(OperatingMode.COPILOT)
        val targets = detections.filter {
            it.label in setOf("person", "car") && it.confidence >= minConfidence
        }
        val dangerZoneTop = imageHeight * 0.6f

        for (det in targets) {
            val cx = (det.bounds.left + det.bounds.right) / 2f
            val cy = (det.bounds.top + det.bounds.bottom) / 2f
            if (cy < dangerZoneTop) continue

            val key = "${det.label}_${cx.toInt() / 50}_${cy.toInt() / 50}"
            val prev = previousCenters[key]
            if (prev != null) {
                val dy = cy - prev.second
                val speed = dy / imageHeight
                if (speed > 0.05f) {
                    val now = System.currentTimeMillis()
                    if (now - lastAlertAt > 3_000L) {
                        lastAlertAt = now
                        onIntrusion()
                    }
                }
            }
            previousCenters[key] = cx to cy
        }
    }

    fun reset() {
        previousCenters.clear()
    }
}
