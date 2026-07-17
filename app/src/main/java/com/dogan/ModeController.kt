package com.dogan

/** Dispatches frame analysis to the active operating mode engine(s). */
class ModeController(
    private val watcherEngine: WatcherEngine,
    private val spotterEngine: SpotterEngine,
    private val copilotEngine: CopilotEngine,
) {
    fun processFrame(
        detections: List<VehicleDetection>,
        imageWidth: Int,
        imageHeight: Int,
        settings: AppSettings,
    ) {
        when (settings.operatingMode) {
            OperatingMode.OFF -> Unit
            OperatingMode.WATCHER -> {
                watcherEngine.onDetections(detections, settings)
            }
            OperatingMode.SPOTTER -> {
                watcherEngine.onDetections(detections, settings)
                spotterEngine.onDetections(detections, imageWidth, imageHeight, settings)
            }
            OperatingMode.COPILOT -> {
                copilotEngine.onDetections(detections, imageWidth, imageHeight, settings)
            }
        }
    }

    fun onBump(settings: AppSettings, peakMps2: Float) {
        if (settings.operatingMode == OperatingMode.WATCHER ||
            settings.operatingMode == OperatingMode.SPOTTER
        ) {
            watcherEngine.onBump(settings, peakMps2)
        }
    }

    fun onSoundSpike(rms: Double, settings: AppSettings) {
        if (settings.operatingMode == OperatingMode.WATCHER ||
            settings.operatingMode == OperatingMode.SPOTTER
        ) {
            watcherEngine.onSoundSpike(rms, settings)
        }
    }
}
