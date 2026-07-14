package com.dogan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Foreground service orchestrating modes, telemetry, detection, and streaming. */
class CaptureService : Service(), LifecycleOwner {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_SWITCH_CAMERA = "switch_camera"
        const val EXTRA_CAMERA_FACING = "camera_facing"

        private const val CHANNEL = "dogan_capture_channel"
        private const val MOTION_CHANNEL = "dogan_motion_channel"
        private const val NOTIF_ID = 81
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val deviceId by lazy { DeviceIdentity.resolveDeviceId(this) }
    private val apiClient by lazy { DoganApiClient(deviceId = deviceId) }
    private val io = Executors.newSingleThreadExecutor()
    private val keepAliveExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val telemetryDatabase by lazy { TelemetryDatabase(this) }
    private val cabinNoiseArchive by lazy { CabinNoiseArchive(this) }
    private val locationTracker by lazy { LocationTracker(this) }
    private val ambientLightSensor by lazy { AmbientLightSensor(this) }
    private val modelDownloadManager by lazy { ModelDownloadManager(this, apiClient) }
    private val soundDownloadManager by lazy { SoundDownloadManager(this, apiClient) }
    private val alertManager by lazy { AlertManager(this, soundDownloadManager) }
    private val watchedVehicleStore by lazy { WatchedVehicleStore() }
    private val watchmanEngine by lazy { WatchmanEngine(alertManager) }
    private val spotterEngine by lazy { SpotterEngine(watchedVehicleStore, alertManager) }
    private val suddenIntrusionDetector by lazy { SuddenIntrusionDetector() }
    private val signOcrDetector by lazy { SignOcrDetector(ncnnObjectDetector) }
    private val copilotEngine by lazy {
        CopilotEngine(suddenIntrusionDetector, signOcrDetector, locationTracker, alertManager)
    }
    private val modeController by lazy {
        ModeController(watchmanEngine, spotterEngine, copilotEngine)
    }
    private val telemetryUploader by lazy { TelemetryUploader(apiClient, telemetryDatabase) }
    private val liveKitStreamer by lazy { LiveKitStreamer(this, apiClient) }

    private lateinit var audioCapture: AudioCapture
    private lateinit var telemetryCollector: TelemetryCollector
    private lateinit var ncnnObjectDetector: NcnnObjectDetector

    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var lastMotionTriggeredCaptureAt = 0L
    @Volatile private var lastScreenWakeAt = 0L
    @Volatile private var lastAnalysisAt = 0L
    private val latestDetections = AtomicReference<List<VehicleDetection>>(emptyList())

    private lateinit var sensorManager: SensorManager
    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->
        onVehicleBumpDetected(peakMps2)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ncnnObjectDetector = NcnnObjectDetector(this, modelDownloadManager)
        audioCapture = AudioCapture(
            onSpike = { rms ->
                cachedSettings?.let { modeController.onSoundSpike(rms, it) }
            },
            cabinNoiseArchive = cabinNoiseArchive,
        )
        telemetryCollector = TelemetryCollector(this, locationTracker, ambientLightSensor, audioCapture, deviceId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopCapture()
            ACTION_SWITCH_CAMERA -> {
                val facing = CameraFacing.fromStoredValue(intent.getStringExtra(EXTRA_CAMERA_FACING))
                switchCamera(facing)
            }
            else -> startCapture()
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

        downloadAssets(settings)
        bindCamera(settings)
        vehicleMotionDetector.register(sensorManager)
        locationTracker.start(settings.telemetryIntervalMs)
        ambientLightSensor.start()
        audioCapture.start()
        scheduleTelemetryLoop()
        scheduleKeepAliveLoop()
        scheduleUploadLoop()
        scheduleDiagnosticUploadLoop()

        if (ServerConnectionManager.isConnected()) {
            ServerSettingsSync.start(this)
            liveKitStreamer.start(settings.serverBaseUrl, settings.apiKey, settings.streamMode)
        }

        AppLogger.info("Capture", "Monitoring started — ${settings.operatingMode.displayName}")

        DetectionTapBridge.handler = { label, bounds, w, h ->
            spotterEngine.addWatchedVehicle(label, bounds, w, h)
        }
    }

    private fun stopCapture() {
        running = false
        DoganCamera.clear()
        vehicleMotionDetector.unregister(sensorManager)
        locationTracker.stop()
        ambientLightSensor.stop()
        audioCapture.stop()
        liveKitStreamer.stop()
        alertManager.release()
        DetectionTapBridge.handler = null
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
            if (ServerConnectionManager.isConnected()) {
                modelDownloadManager.fetchAndDownloadModel(
                    settings.serverBaseUrl,
                    settings.apiKey,
                    settings.aiModel.toStoredValue(),
                    settings.wifiOnlyDownloads,
                )
                modelDownloadManager.fetchAndDownloadAll(settings.serverBaseUrl, settings.apiKey, settings.wifiOnlyDownloads)
                soundDownloadManager.fetchAndDownloadAll(settings.serverBaseUrl, settings.apiKey, settings.wifiOnlyDownloads)
            }
        }
    }

    private fun bindCamera(settings: AppSettings) {
        DoganCamera.setAnalysisListener { proxy ->
            processAnalysisFrame(proxy, settings)
        }
        DoganCamera.bindForMonitoring(
            context = this,
            lifecycleOwner = this,
            cameraFacing = settings.activeCamera,
            jpegQuality = settings.sendingImageQuality.jpegQuality,
            analysisExecutor = analysisExecutor,
            realtimeFps = settings.realtimeFps,
        )
    }

    private fun processAnalysisFrame(proxy: androidx.camera.core.ImageProxy, settings: AppSettings) {
        try {
            val now = System.currentTimeMillis()
            val minInterval = 1000L / settings.realtimeFps.coerceAtLeast(1)
            if (now - lastAnalysisAt < minInterval) return
            lastAnalysisAt = now

            val currentSettings = cachedSettings ?: settings
            if (!currentSettings.objectDetectionOnDevice) return

            val bitmap = proxyToBitmap(proxy) ?: return
            val result = ncnnObjectDetector.detect(
                bitmap,
                currentSettings.minDetectionConfidence,
                currentSettings.aiModel,
            )
            bitmap.recycle()
            latestDetections.set(result.detections)
            modeController.processFrame(
                result.detections,
                proxy.width,
                proxy.height,
                currentSettings,
            )
            NcnnObjectDetector.logResult(result)
        } finally {
            proxy.close()
        }
    }

    private fun proxyToBitmap(proxy: androidx.camera.core.ImageProxy): Bitmap? {
        val yBuffer = proxy.planes[0].buffer
        val uBuffer = proxy.planes[1].buffer
        val vBuffer = proxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 80, out)
        val jpegBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun switchCamera(facing: CameraFacing) {
        val settings = cachedSettings ?: return
        io.execute {
            runBlocking { settingsStore.updateActiveCamera(facing) }
            cachedSettings = settings.copy(activeCamera = facing)
            DoganCamera.switchCamera(
                context = this,
                lifecycleOwner = this,
                cameraFacing = facing,
                jpegQuality = settings.sendingImageQuality.jpegQuality,
                analysisExecutor = analysisExecutor,
                realtimeFps = settings.realtimeFps,
            )
            AppLogger.info("Camera", "Switched to ${facing.name.lowercase()} camera")
        }
    }

    private fun scheduleTelemetryLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings

                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        collectAndEnqueueTelemetry(settings)
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
        val latch = java.util.concurrent.CountDownLatch(2)
        var rearFile: File? = null
        var frontFile: File? = null

        DoganCamera.captureFromFacing(this, this, CameraFacing.REAR, settings.sendingImageQuality.jpegQuality, { file ->
            rearFile = file
            latch.countDown()
        }, io)

        DoganCamera.captureFromFacing(this, this, CameraFacing.FRONT, settings.sendingImageQuality.jpegQuality, { file ->
            frontFile = file
            latch.countDown()
        }, io)

        latch.await(15, java.util.concurrent.TimeUnit.SECONDS)

        val health = apiClient.pingHealthWithLatency(settings.serverBaseUrl)
        val latency = health?.latencyMs ?: -1L

        val payload = telemetryCollector.collectSnapshot(rearFile, frontFile, latency)
        telemetryDatabase.enqueue(payload)
        rearFile?.delete()
        frontFile?.delete()
    }

    private fun scheduleUploadLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
                    if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                        val stats = telemetryUploader.uploadPending(settings.serverBaseUrl, settings.apiKey)
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
        io.execute {
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
                            settings.apiKey,
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
