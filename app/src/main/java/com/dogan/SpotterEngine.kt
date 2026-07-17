package com.dogan

/** Spotter mode: watch selected parked vehicles and alert on departure. */
class SpotterEngine(
    private val watchedVehicleStore: WatchedVehicleStore,
    private val alertManager: AlertManager,
) {
    companion object {
        const val ABSENT_FRAME_THRESHOLD = 4
    }

    fun onDetections(
        detections: List<VehicleDetection>,
        imageWidth: Int,
        imageHeight: Int,
        settings: AppSettings,
    ) {
        val watched = watchedVehicleStore.all()
        if (watched.isEmpty()) return

        val minConfidence = settings.confidenceForMode(OperatingMode.SPOTTER)
        val presentVehicles = detections.filter {
            it.label == "car" && it.confidence >= minConfidence
        }

        for (vehicle in watched) {
            val matched = presentVehicles.any { det ->
                watchedVehicleStore.findMatch(det, imageWidth, imageHeight)?.id == vehicle.id
            }
            if (matched) {
                vehicle.absentFrameCount = 0
            } else {
                vehicle.absentFrameCount++
                if (vehicle.absentFrameCount >= ABSENT_FRAME_THRESHOLD) {
                    alertManager.trigger(
                        alertType = AlertType.VEHICLE_DEPARTED,
                        settings = settings,
                        title = "Watched vehicle left",
                        body = "${vehicle.label} has left the scene",
                        channelId = "dogan_spotter",
                    )
                    watchedVehicleStore.remove(vehicle.id)
                }
            }
        }
    }

    fun addWatchedVehicle(label: String, bounds: android.graphics.RectF, imageWidth: Int, imageHeight: Int) {
        watchedVehicleStore.add(label, bounds, imageWidth, imageHeight)
    }
}
