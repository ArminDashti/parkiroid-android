package com.dogan

/** Dispatches frame analysis to the active operating mode engine(s). */
class ModeController(
    private val watchmanEngine: WatchmanEngine,
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
            OperatingMode.WATCHMAN -> {
                watchmanEngine.onDetections(detections, settings)
            }
            OperatingMode.SPOTTER -> {
                spotterEngine.onDetections(detections, imageWidth, imageHeight, settings)
            }
            OperatingMode.WATCHMAN_SPOTTER -> {
                watchmanEngine.onDetections(detections, settings)
                spotterEngine.onDetections(detections, imageWidth, imageHeight, settings)
            }
            OperatingMode.COPILOT -> {
                copilotEngine.onDetections(detections, imageWidth, imageHeight, settings)
            }
        }
    }

    fun onBump(settings: AppSettings, peakMps2: Float) {
        if (settings.operatingMode == OperatingMode.WATCHMAN ||
            settings.operatingMode == OperatingMode.WATCHMAN_SPOTTER
        ) {
            watchmanEngine.onBump(settings, peakMps2)
        }
    }

    fun onSoundSpike(rms: Double, settings: AppSettings) {
        if (settings.operatingMode == OperatingMode.WATCHMAN ||
            settings.operatingMode == OperatingMode.WATCHMAN_SPOTTER
        ) {
            watchmanEngine.onSoundSpike(rms, settings)
        }
    }
}
