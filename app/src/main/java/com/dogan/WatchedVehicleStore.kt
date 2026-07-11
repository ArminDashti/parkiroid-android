package com.dogan

import android.graphics.RectF

/** A vehicle the user selected to watch in Spotter mode. */
data class WatchedVehicle(
    val id: String,
    val label: String,
    val normalizedBounds: RectF,
    val addedAtMs: Long,
    var absentFrameCount: Int = 0,
)

/** Persists and manages watched vehicles for Spotter mode. */
class WatchedVehicleStore {
    private val watched = mutableListOf<WatchedVehicle>()

    fun add(label: String, bounds: RectF, imageWidth: Int, imageHeight: Int): WatchedVehicle {
        val normalized = RectF(
            bounds.left / imageWidth,
            bounds.top / imageHeight,
            bounds.right / imageWidth,
            bounds.bottom / imageHeight,
        )
        val vehicle = WatchedVehicle(
            id = "${label}_${System.currentTimeMillis()}",
            label = label,
            normalizedBounds = normalized,
            addedAtMs = System.currentTimeMillis(),
        )
        watched.add(vehicle)
        AppLogger.info("Spotter", "Watching $label")
        return vehicle
    }

    fun remove(id: String) {
        watched.removeAll { it.id == id }
    }

    fun all(): List<WatchedVehicle> = watched.toList()

    fun clear() = watched.clear()

    fun findMatch(detection: VehicleDetection, imageWidth: Int, imageHeight: Int): WatchedVehicle? {
        val detNorm = RectF(
            detection.bounds.left / imageWidth,
            detection.bounds.top / imageHeight,
            detection.bounds.right / imageWidth,
            detection.bounds.bottom / imageHeight,
        )
        return watched.firstOrNull { vehicle ->
            vehicle.label == detection.label && iou(vehicle.normalizedBounds, detNorm) > 0.3f
        }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersectLeft = maxOf(a.left, b.left)
        val intersectTop = maxOf(a.top, b.top)
        val intersectRight = minOf(a.right, b.right)
        val intersectBottom = minOf(a.bottom, b.bottom)
        if (intersectRight <= intersectLeft || intersectBottom <= intersectTop) return 0f
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return intersectArea / (aArea + bArea - intersectArea)
    }
}
