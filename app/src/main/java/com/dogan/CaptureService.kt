package com.dogan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.ImageProxy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Foreground service orchestrating modes, telemetry, detection, and streaming. */
class CaptureService : Service(), LifecycleOwner {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_SWITCH_CAMERA = "switch_camera"
        const val ACTION_MODE_ACTIVATED = "mode_activated"
        const val EXTRA_CAMERA_FACING = "camera_facing"

        private const val CHANNEL = "dogan_capture_channel"
        private const val MOTION_CHANNEL = "dogan_motion_channel"
        private const val NOTIF_ID = 81
        private const val PREVIEW_ANALYSIS_FPS_FLOOR = 2f
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val deviceId by lazy { DeviceIdentity.resolveDeviceId(this) }
    private val apiClient by lazy { DoganApiClient(deviceId = deviceId) }
    private val io = Executors.newSingleThreadExecutor()
    private val uploadExecutor = Executors.newSingleThreadExecutor()
    private val keepAliveExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val captureCallbackExecutor = Executors.newSingleThreadExecutor()

    private val telemetryDatabase by lazy { TelemetryDatabase(this) }
    private val frameHistoryStore by lazy { FrameHistoryStore(this) }
    private val cabinNoiseArchive by lazy { CabinNoiseArchive(this) }
    private val locationTracker by lazy { LocationTracker(this) }
    private val ambientLightSensor by lazy { AmbientLightSensor(this) }
    private val modelAssetManager by lazy { ModelAssetManager(this) }
    private val soundDownloadManager by lazy { SoundDownloadManager(this, apiClient) }
    private val alertManager by lazy { AlertManager(this, soundDownloadManager) }
    private val watchedVehicleStore by lazy { WatchedVehicleStore() }
    private val watcherEngine by lazy { WatcherEngine(alertManager) }
    private val spotterEngine by lazy { SpotterEngine(watchedVehicleStore, alertManager) }
    private val suddenIntrusionDetector by lazy { SuddenIntrusionDetector() }
    private val signOcrDetector by lazy { SignOcrDetector(ncnnObjectDetector) }
    private val copilotEngine by lazy {
        CopilotEngine(suddenIntrusionDetector, signOcrDetector, locationTracker, alertManager)
    }
    private val modeController by lazy {
        ModeController(watcherEngine, spotterEngine, copilotEngine)
    }
    private val telemetryUploader by lazy { TelemetryUploader(apiClient, telemetryDatabase) }
    private val liveKitStreamer by lazy { LiveKitStreamer(this, apiClient) }
    private val resourceMonitor by lazy { DeviceResourceMonitor(this) }
    private val detectionMediaArchive by lazy { DetectionMediaArchive(this) }
    private val detectionVideoRecorder by lazy { DetectionVideoRecorder(this) }

    private lateinit var audioCapture: AudioCapture
    private lateinit var telemetryCollector: TelemetryCollector
    private lateinit var ncnnObjectDetector: NcnnObjectDetector

    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var lastMotionTriggeredCaptureAt = 0L
    @Volatile private var lastScreenWakeAt = 0L
    @Volatile private var lastAnalysisAt = 0L
    @Volatile private var continuousCameraActive = false
    private val latestDetections = AtomicReference<List<VehicleDetection>>(emptyList())

    private lateinit var sensorManager: SensorManager
    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->
        onVehicleBumpDetected(peakMps2)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ncnnObjectDetector = NcnnObjectDetector(modelAssetManager)
        audioCapture = AudioCapture(
            onSpike = { rms ->
                cachedSettings?.let { modeController.onSoundSpike(rms, it) }
            },
            cabinNoiseArchive = cabinNoiseArchive,
        )
        telemetryCollector = TelemetryCollector(
            this, locationTracker, ambientLightSensor, audioCapture, deviceId, resourceMonitor,
        )
        AppLogger.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_SWITCH_CAMERA -> {
                val facing = CameraFacing.fromStoredValue(intent.getStringExtra(EXTRA_CAMERA_FACING))
                switchCamera(facing)
            }
            ACTION_MODE_ACTIVATED -> {
                if (running) {
                    refreshForModeActivation()
                } else {
                    startCapture()
                }
            }
            else -> {
                if (running) {
                    refreshForModeActivation()
                } else {
                    startCapture()
                }
            }
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (running) return
        running = true
        createChannels()
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        acquireWakeLock()

        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        val settings = cachedSettings ?: return
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)

        downloadAssets(settings)
        vehicleMotionDetector.register(sensorManager)
        locationTracker.start(settings.telemetryIntervalMs)
        ambientLightSensor.start()
        audioCapture.start()
        scheduleAnalysisLoop()
        scheduleTelemetryLoop()
        scheduleKeepAliveLoop()
        scheduleUploadLoop()
        scheduleDiagnosticUploadLoop()

        if (ServerConnectionManager.isConnected()) {
            ServerSettingsSync.start(this)
            liveKitStreamer.start(settings.serverBaseUrl, settings.streamMode)
        }

        applySensitivity(settings)
        logModeActivated(settings.operatingMode)

        DetectionTapBridge.handler = { label, bounds, w, h ->
            spotterEngine.addWatchedVehicle(label, bounds, w, h)
        }
    }

    /** Refresh settings and force the next analysis frame after a mode switch. */
    private fun refreshForModeActivation() {
        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        val settings = cachedSettings ?: return
        SessionCredentials.updateFrom(settings)
        AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
        applySensitivity(settings)
        lastAnalysisAt = 0L
        logModeActivated(settings.operatingMode)
        startForeground(NOTIF_ID, buildNotification())
        // Mode may already be running before Connect; start LiveKit once the API is up.
        if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
            ServerSettingsSync.start(this)
            liveKitStreamer.start(settings.serverBaseUrl, settings.streamMode)
        }
    }

    private fun applySensitivity(settings: AppSettings) {
        vehicleMotionDetector.joltSensitivity = settings.joltSensitivity
        vehicleMotionDetector.customJoltScale = settings.customJoltScale
        audioCapture.soundSensitivity = settings.soundSensitivity
        audioCapture.customSoundThreshold = settings.customSoundThreshold
    }

    private fun logModeActivated(mode: OperatingMode) {
        when (mode) {
            OperatingMode.SPOTTER ->
                AppLogger.info(LogSection.SPOTTER, "Spotter", "Spotter activated — history and detection started")
            OperatingMode.WATCHER ->
                AppLogger.info(LogSection.WATCHMAN, "Watchman", "Watchman activated — history and detection started")
            OperatingMode.COPILOT ->
                AppLogger.info("Capture", "Copilot activated — monitoring started")
            OperatingMode.OFF ->
                AppLogger.info("Capture", "Monitoring stopped")
        }
    }

    private fun stopCapture() {
        running = false
        AppLogger.activeModeSection = null
        continuousCameraActive = false
        DoganCamera.clear()
        vehicleMotionDetector.unregister(sensorManager)
        locationTracker.stop()
        ambientLightSensor.stop()
        audioCapture.stop()
        liveKitStreamer.stop()
        detectionVideoRecorder.stop()
        alertManager.release()
        DetectionTapBridge.handler = null
        DetectionOverlayBridge.clear()
        SensorHudBridge.clear()
        ncnnObjectDetector.close()
        wakeLock?.release()
        wakeLock = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.info("Capture", "Monitoring stopped")
    }

    private fun downloadAssets(settings: AppSettings) {
        io.execute {
            modelAssetManager.ensureSelectedModelReady(settings.aiModel)
            if (ServerConnectionManager.isConnected()) {
                soundDownloadManager.fetchAndDownloadAll(settings.serverBaseUrl)
            }
        }
    }

    /**
     * Opens the camera only when a frame is needed (duty-cycle), unless Preview or recording
     * requires a continuous bind.
     */
    private fun scheduleAnalysisLoop() {
        analysisExecutor.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings
                    if (settings.operatingMode == OperatingMode.OFF) {
                        releaseContinuousCamera()
                        DetectionOverlayBridge.clear()
                        Thread.sleep(1_000L)
                        continue
                    }

                    applySensitivity(settings)
                    SensorHudBridge.publish(vehicleMotionDetector.lastMagnitudeMps2, audioCapture.currentRms)

                    if (needsContinuousCamera(settings)) {
                        ensureContinuousCamera(settings)
                        Thread.sleep(200L)
                        continue
                    }

                    releaseContinuousCamera()
                    val now = System.currentTimeMillis()
                    val fps = settings.fpsForMode(settings.operatingMode).coerceAtLeast(0.001f)
                    val minInterval = (1000f / fps).toLong().coerceAtLeast(1L)
                    val waitMs = (minInterval - (now - lastAnalysisAt)).coerceAtLeast(0L)
                    if (waitMs > 0L) {
                        Thread.sleep(waitMs.coerceAtMost(1_000L))
                        continue
                    }
                    captureAndAnalyzeOnce(settings)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    AppLogger.error("Detection", "Analysis loop error: ${e.message}")
                    Thread.sleep(2_000L)
                }
            }
        }
    }

    private fun needsContinuousCamera(settings: AppSettings): Boolean {
        return DetectionOverlayBridge.listener != null || settings.recordingEnabled
    }

    private fun ensureContinuousCamera(settings: AppSettings) {
        if (continuousCameraActive && DoganCamera.isBound) return
        DoganCamera.setAnalysisListener { proxy ->
            processAnalysisFrame(proxy, settings)
        }
        val previewFacing = when (settings.activeCamera) {
            CameraFacing.FRONT -> CameraFacing.FRONT
            else -> CameraFacing.REAR
        }
        DoganCamera.bindForMonitoring(
            context = this,
            lifecycleOwner = this,
            cameraFacing = previewFacing,
            jpegQuality = settings.frameQualityForMode(settings.operatingMode).jpegQuality,
            analysisExecutor = analysisExecutor,
            realtimeFps = settings.fpsForMode(settings.operatingMode).toInt().coerceAtLeast(1),
        )
        continuousCameraActive = true
        AppLogger.info(
            "Detection",
            "Continuous camera bound (preview/recording; mode=${settings.operatingMode.displayName})",
        )
    }

    private fun releaseContinuousCamera() {
        if (!continuousCameraActive && !DoganCamera.isBound) return
        DoganCamera.clear()
        continuousCameraActive = false
    }

    private fun captureAndAnalyzeOnce(settings: AppSettings) {
        val facing = when (settings.activeCamera) {
            CameraFacing.FRONT -> CameraFacing.FRONT
            else -> CameraFacing.REAR
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        var bitmap: Bitmap? = null
        DoganCamera.preferContinuousBind = false
        DoganCamera.captureBitmap(
            context = this,
            lifecycleOwner = this,
            facing = facing,
            jpegQuality = settings.frameQualityForMode(settings.operatingMode).jpegQuality,
            onComplete = { captured ->
                bitmap = captured
                latch.countDown()
            },
            // Must not be analysisExecutor — this method awaits on that thread.
            executor = ContextCompat.getMainExecutor(this),
        )
        if (!latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
            AppLogger.error("Detection", "Timed out waiting for duty-cycle capture")
            return
        }
        val frame = bitmap
        if (frame == null) {
            AppLogger.error("Detection", "Duty-cycle capture returned no bitmap")
            return
        }
        lastAnalysisAt = System.currentTimeMillis()
        processBitmapFrame(frame, settings, recycleBitmap = true)
    }

    private fun processAnalysisFrame(proxy: ImageProxy, settings: AppSettings) {
        try {
            val now = System.currentTimeMillis()
            val currentSettings = cachedSettings ?: settings
            if (currentSettings.operatingMode == OperatingMode.OFF) {
                DetectionOverlayBridge.clear()
                return
            }
            applySensitivity(currentSettings)
            SensorHudBridge.publish(vehicleMotionDetector.lastMagnitudeMps2, audioCapture.currentRms)
            var fps = currentSettings.fpsForMode(currentSettings.operatingMode).coerceAtLeast(0.001f)
            // Preview testing: floor to ~2 FPS while overlay listener is attached.
            if (DetectionOverlayBridge.listener != null) {
                fps = maxOf(fps, PREVIEW_ANALYSIS_FPS_FLOOR)
            }
            val minInterval = (1000f / fps).toLong().coerceAtLeast(1L)
            if (now - lastAnalysisAt < minInterval) return
            lastAnalysisAt = now

            val bitmap = proxyToBitmap(proxy)
            if (bitmap == null) {
                AppLogger.error("Detection", "Failed to convert camera frame to bitmap")
                return
            }
            processBitmapFrame(bitmap, currentSettings, recycleBitmap = true)
        } finally {
            proxy.close()
        }
    }

    private fun processBitmapFrame(bitmap: Bitmap, settings: AppSettings, recycleBitmap: Boolean) {
        val result = ncnnObjectDetector.detect(
            bitmap,
            settings.confidenceForMode(settings.operatingMode),
            settings.aiModel,
        )
        val frameWidth = bitmap.width
        val frameHeight = bitmap.height
        latestDetections.set(result.detections)
        DetectionOverlayBridge.publish(result.detections, frameWidth, frameHeight)
        modeController.processFrame(
            result.detections,
            frameWidth,
            frameHeight,
            settings,
        )
        frameHistoryStore.append(
            mode = settings.operatingMode,
            bitmap = bitmap,
            detections = result.detections,
            maxFrames = settings.historyRetentionForMode(settings.operatingMode),
        )
        if (recycleBitmap) bitmap.recycle()
        maybeRecordMedia(settings)
        NcnnObjectDetector.logResult(result)
    }

    /**
     * Converts CameraX YUV_420_888 to a Bitmap, respecting plane strides and applying
     * [ImageProxy.imageInfo.rotationDegrees] so inference matches the upright preview.
     */
    private fun proxyToBitmap(proxy: ImageProxy): Bitmap? {
        val nv21 = yuv420888ToNv21(proxy) ?: return null
        val yuv = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream()
        if (!yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 90, out)) {
            AppLogger.error("Detection", "Failed to compress analysis frame to JPEG")
            return null
        }
        val decoded = android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            ?: return null
        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation == 0) return decoded
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    /** Packs YUV_420_888 planes into NV21 (YYYY… VUVU…), handling rowStride and pixelStride. */
    private fun yuv420888ToNv21(proxy: ImageProxy): ByteArray? {
        val width = proxy.width
        val height = proxy.height
        if (proxy.planes.size < 3) {
            AppLogger.error("Detection", "ImageProxy has fewer than 3 planes")
            return null
        }
        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, width, height, nv21, 0)

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val vBuffer = vPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vBase = vBuffer.position()
        val uBase = uBuffer.position()

        var outputOffset = ySize
        // NV21 interleaves V then U per chroma sample.
        for (row in 0 until chromaHeight) {
            val vRowStart = vBase + row * vRowStride
            val uRowStart = uBase + row * uRowStride
            for (col in 0 until chromaWidth) {
                nv21[outputOffset++] = vBuffer.get(vRowStart + col * vPixelStride)
                nv21[outputOffset++] = uBuffer.get(uRowStart + col * uPixelStride)
            }
        }
        return nv21
    }

    private fun copyPlane(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int,
    ) {
        val buffer = source.duplicate()
        val base = buffer.position()
        var outputPos = outOffset
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(out, outOffset, width * height)
            return
        }
        for (row in 0 until height) {
            val rowStart = base + row * rowStride
            for (col in 0 until width) {
                out[outputPos++] = buffer.get(rowStart + col * pixelStride)
            }
        }
    }

    private fun switchCamera(facing: CameraFacing) {
        val settings = cachedSettings ?: return
        io.execute {
            runBlocking { settingsStore.updateActiveCamera(facing) }
            cachedSettings = settings.copy(activeCamera = facing)
            if (needsContinuousCamera(cachedSettings ?: settings)) {
                continuousCameraActive = false
                DoganCamera.switchCamera(
                    context = this,
                    lifecycleOwner = this,
                    cameraFacing = when (facing) {
                        CameraFacing.BOTH -> CameraFacing.REAR
                        else -> facing
                    },
                    jpegQuality = settings.frameQualityForMode(settings.operatingMode).jpegQuality,
                    analysisExecutor = analysisExecutor,
                    realtimeFps = settings.fpsForMode(settings.operatingMode).toInt().coerceAtLeast(1),
                )
                continuousCameraActive = true
            }
            AppLogger.info("Camera", "Switched to ${facing.name.lowercase()} camera")
        }
    }

    private fun scheduleTelemetryLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings
                    SessionCredentials.updateFrom(settings)
                    AppLogger.activeModeSection = LogSection.forOperatingMode(settings.operatingMode)
                    applySensitivity(settings)

                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        collectAndEnqueueTelemetry(settings)
                        telemetryDatabase.purgeOlderThan(settings.telemetryRetentionHours)
                        AppLogger.purgeOlderThan(this@CaptureService, settings.logRetentionDays)
                    }

                    Thread.sleep(settings.telemetryIntervalMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(5_000L)
                }
            }
        }
    }

    private fun collectAndEnqueueTelemetry(settings: AppSettings) {
        val includeFrames = shouldIncludeFrames(settings)
        val pendingActionIds = pendingCaptureActionIds(settings)
        val forceCapture = pendingActionIds.isNotEmpty()
        val latch = java.util.concurrent.CountDownLatch(
            when (settings.activeCamera) {
                CameraFacing.BOTH -> 2
                else -> 1
            },
        )
        var rearFile: File? = null
        var frontFile: File? = null

        when (settings.activeCamera) {
            CameraFacing.FRONT -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality, { file ->
                    frontFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
            CameraFacing.REAR -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality, { file ->
                    rearFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
            CameraFacing.BOTH -> {
                DoganCamera.captureFromFacing(this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality, { file ->
                    rearFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
                DoganCamera.captureFromFacing(this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality, { file ->
                    frontFile = file
                    latch.countDown()
                }, captureCallbackExecutor)
            }
        }

        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)

        val health = apiClient.pingHealthWithLatency(settings.serverBaseUrl)
        val latency = health?.latencyMs ?: -1L

        val attachFrames = includeFrames || forceCapture
        val payload = telemetryCollector.collectSnapshot(rearFile, frontFile, latency, attachFrames)
        telemetryDatabase.enqueue(payload)
        if (forceCapture && telemetryHasAttachedFrames(payload)) {
            acknowledgePendingActions(settings.serverBaseUrl, pendingActionIds)
        }
        rearFile?.delete()
        frontFile?.delete()
    }

    private fun shouldIncludeFrames(settings: AppSettings): Boolean {
        return settings.imageUploadPolicyForMode(settings.operatingMode) == ImageUploadPolicy.AUTO
    }

    private fun pendingCaptureActionIds(settings: AppSettings): List<Long> {
        val actions = apiClient.fetchPendingActions(settings.serverBaseUrl) ?: return emptyList()
        val actionIds = ArrayList<Long>(actions.length())
        for (index in 0 until actions.length()) {
            val action = actions.optJSONObject(index) ?: continue
            val actionId = action.optLong("id", 0L)
            if (actionId > 0L) {
                actionIds.add(actionId)
            }
        }
        return actionIds
    }

    private fun telemetryHasAttachedFrames(payload: JSONObject): Boolean {
        return payload.optString("rear_camera_frame_base64").isNotBlank() ||
            payload.optString("front_camera_frame_base64").isNotBlank()
    }

    private fun acknowledgePendingActions(baseUrl: String, actionIds: List<Long>) {
        for (actionId in actionIds) {
            if (!apiClient.acknowledgeAction(baseUrl, actionId)) {
                AppLogger.warn("Actions", "Failed to acknowledge action $actionId")
            }
        }
    }

    private fun maybeRecordMedia(settings: AppSettings) {
        if (settings.operatingMode == OperatingMode.OFF || !settings.recordingEnabled) {
            if (detectionVideoRecorder.isRecording()) detectionVideoRecorder.stop()
            return
        }
        val modeSettings = settings.modeSettings(settings.operatingMode)
        detectionMediaArchive.enforceRetention(
            settings.operatingMode,
            modeSettings.imageRetentionHours,
            settings.recordingRetentionHours,
        )
        val audioMode = if (settings.recordingSoundEnabled) {
            VideoAudioMode.VIDEO_AND_SOUND
        } else {
            VideoAudioMode.VIDEO_ONLY
        }
        val chunkMinutes = settings.recordingChunkMinutes
        if (!detectionVideoRecorder.isRecording()) {
            detectionVideoRecorder.start(settings.operatingMode, audioMode, chunkMinutes)
        } else {
            detectionVideoRecorder.maybeRotateChunk(chunkMinutes, settings.operatingMode, audioMode)
        }
    }

    private fun scheduleUploadLoop() {
        uploadExecutor.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        val stats = telemetryUploader.uploadPending(settings.serverBaseUrl)
                        if (stats.uploaded > 0) {
                            AppLogger.info("Telemetry", "Uploaded ${stats.uploaded}, remaining ${stats.remaining}")
                        }
                    }
                    Thread.sleep(2_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(5_000L)
                }
            }
        }
    }

    private fun scheduleDiagnosticUploadLoop() {
        uploadExecutor.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: continue
                    if (!ServerConnectionManager.isConnected()) continue
                    for (segment in cabinNoiseArchive.getPendingUploads()) {
                        if (!segment.file.exists()) continue
                        val metadata = JSONObject()
                            .put("segment_id", segment.id)
                            .put("start_ms", segment.startMs)
                            .put("end_ms", segment.endMs)
                            .put("rms_peak", segment.rmsPeak)
                            .put("linked_alert_id", segment.linkedAlertId)
                            .put("mode", segment.mode)
                            .put("device_id", deviceId)
                        val ok = apiClient.submitDiagnosticAudio(
                            settings.serverBaseUrl,
                            segment.file,
                            metadata,
                        )
                        if (ok) cabinNoiseArchive.markUploaded(segment.id)
                    }
                    Thread.sleep(30_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(30_000L)
                }
            }
        }
    }

    private fun scheduleKeepAliveLoop() {
        keepAliveExecutor.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    maybeWakeScreen(settings)
                    val checkMs = if (settings.screenOnIntervalMin > 0) {
                        minOf(30_000L, settings.screenOnIntervalMin * 60_000L / 2)
                    } else {
                        30_000L
                    }
                    Thread.sleep(checkMs.coerceAtLeast(5_000L))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(30_000L)
                }
            }
        }
    }

    private fun maybeWakeScreen(settings: AppSettings) {
        if (settings.screenOnIntervalMin <= 0) return
        val now = System.currentTimeMillis()
        val intervalMs = settings.screenOnIntervalMin * 60_000L
        if (now - lastScreenWakeAt < intervalMs) return
        lastScreenWakeAt = now
        ScreenWakeHelper.wakeScreen(this)
        AppLogger.info("KeepAlive", "Screen wake pulse (${settings.screenOnIntervalMin} min interval)")
    }

    private fun isValidBaseUrl(baseUrl: String): Boolean {
        val trimmed = baseUrl.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    private fun onVehicleBumpDetected(peakAccelerationMps2: Float) {
        io.execute {
            val now = System.currentTimeMillis()
            if (now - lastMotionTriggeredCaptureAt < VehicleMotionDetector.COOLDOWN_MS) return@execute
            lastMotionTriggeredCaptureAt = now
            val settings = cachedSettings ?: return@execute
            modeController.onBump(settings, peakAccelerationMps2)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Dogan Capture", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(MOTION_CHANNEL, "Motion Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
            nm.createNotificationChannel(
                NotificationChannel("dogan_alerts", "Dogan Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun buildNotification(detectionSummary: String? = null): Notification {
        val settings = cachedSettings
        val modeLabel = settings?.operatingMode?.displayName ?: ""
        val contentText = detectionSummary ?: modeLabel.ifBlank { getString(R.string.dogan_running) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.dogan_running))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dogan::Capture").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun getLatestDetections(): List<VehicleDetection> = latestDetections.get()

    override fun onBind(intent: Intent?): IBinder? = null
}
