package com.dogan

/** Copilot mode: driving assistance with intrusion, speed limit, and camera warnings. */
class CopilotEngine(
    private val suddenIntrusionDetector: SuddenIntrusionDetector,
    private val signOcrDetector: SignOcrDetector,
    private val locationTracker: LocationTracker,
    private val alertManager: AlertManager,
) {
    private var lastOverspeedAlertAt = 0L
    private var lastCameraAlertAt = 0L

    fun onDetections(detections: List<VehicleDetection>, imageWidth: Int, imageHeight: Int, settings: AppSettings) {
        if (!settings.copilotAlertsEnabled) return

        if (settings.copilotDistanceControlEnabled) {
            suddenIntrusionDetector.analyze(detections, imageWidth, imageHeight, settings) {
                alertManager.trigger(
                    alertType = AlertType.INTRUSION,
                    settings = settings,
                    title = "Road intrusion warning",
                    body = "Vehicle or person entering roadway",
                    channelId = "dogan_copilot",
                )
            }
        }

        val speedLimit = signOcrDetector.detectSpeedLimit(detections, settings)
        val currentSpeed = locationTracker.speedKmh()

        if (speedLimit != null && currentSpeed > speedLimit + 10f) {
            val now = System.currentTimeMillis()
            if (now - lastOverspeedAlertAt > 5_000L) {
                lastOverspeedAlertAt = now
                alertManager.trigger(
                    alertType = AlertType.OVERSPEED,
                    settings = settings,
                    title = "Overspeed warning",
                    body = "Speed ${currentSpeed.toInt()} km/h, limit $speedLimit km/h",
                    channelId = "dogan_overspeed",
                )
            }
        }

        val cameras = detections.filter {
            it.label == "speed_camera" && it.confidence >= settings.confidenceForMode(OperatingMode.COPILOT)
        }
        if (cameras.isNotEmpty() && speedLimit != null && currentSpeed > speedLimit) {
            val now = System.currentTimeMillis()
            if (now - lastCameraAlertAt > 5_000L) {
                lastCameraAlertAt = now
                alertManager.trigger(
                    alertType = AlertType.SPEED_CAMERA,
                    settings = settings,
                    title = "Speed camera ahead",
                    body = "Reduce speed to $speedLimit km/h",
                    channelId = "dogan_camera",
                )
            }
        }
    }

    fun reset() {
        suddenIntrusionDetector.reset()
        signOcrDetector.reset()
    }
}
