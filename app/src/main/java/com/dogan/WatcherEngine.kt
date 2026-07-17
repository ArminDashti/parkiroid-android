package com.dogan

/** Watcher mode: jolts, people near car, sharp sounds. */
class WatcherEngine(
    private val alertManager: AlertManager,
) {
    private var lastPersonAlertAt = 0L
    private var lastSoundAlertAt = 0L

    fun onBump(settings: AppSettings, peakMps2: Float) {
        alertManager.trigger(
            alertType = AlertType.BUMP,
            settings = settings,
            title = "Vehicle motion detected",
            body = "Bump detected (${"%.2f".format(peakMps2)} m/s²)",
            channelId = "dogan_motion",
        )
    }

    fun onDetections(detections: List<VehicleDetection>, settings: AppSettings) {
        val minConfidence = settings.confidenceForMode(OperatingMode.WATCHER)
        val persons = detections.filter {
            it.label == "person" && it.confidence >= minConfidence
        }
        if (persons.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastPersonAlertAt < 10_000L) return
        lastPersonAlertAt = now
        alertManager.trigger(
            alertType = AlertType.PERSON,
            settings = settings,
            title = "Person near vehicle",
            body = "Person detected near the car",
            channelId = "dogan_person",
        )
    }

    fun onSoundSpike(rms: Double, settings: AppSettings) {
        val now = System.currentTimeMillis()
        if (now - lastSoundAlertAt < 5_000L) return
        lastSoundAlertAt = now
        alertManager.trigger(
            alertType = AlertType.SOUND_SPIKE,
            settings = settings,
            title = "Sharp sound detected",
            body = "Sound spike (rms=${"%.0f".format(rms)})",
            channelId = "dogan_sound",
        )
    }
}
