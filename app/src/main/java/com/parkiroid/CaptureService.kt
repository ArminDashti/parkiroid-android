package com.parkiroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.BatteryManager
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
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

/** Foreground service that captures frames, audio, motion events, and uploads telemetry. */
class CaptureService : Service(), LifecycleOwner {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_SWITCH_CAMERA = "switch_camera"
        const val EXTRA_CAMERA_FACING = "camera_facing"

        private const val CHANNEL = "capture_channel"
        private const val MOTION_CHANNEL = "motion_channel"
        private const val NOTIF_ID = 81
        private const val MOTION_NOTIF_ID = 82
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val deviceId by lazy { DeviceIdentity.resolveDeviceId(this) }
    private val apiClient by lazy { ParkiroidApiClient(deviceId = deviceId) }
    private val io = Executors.newSingleThreadExecutor()
    private val audioCapture = AudioCapture()

    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var objectDetector: ParkiroidObjectDetector? = null

    @Volatile private var cachedSettings: AppSettings? = null
    @Volatile private var lastMotionTriggeredCaptureAt = 0L
    @Volatile private var lastScreenWakeAt = 0L

    private lateinit var sensorManager: SensorManager
    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->
        onVehicleBumpDetected(peakMps2)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        acquireWakeLock()

        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        val settings = cachedSettings ?: return
        ParkiroidCamera.bindForMonitoring(
            context = this,
            lifecycleOwner = this,
            cameraFacing = settings.activeCamera,
            jpegQuality = settings.sendingImageQuality.jpegQuality,
        )
        syncObjectDetector(settings)
        vehicleMotionDetector.register(sensorManager)
        audioCapture.start()
        scheduleLoop()
        AppLogger.info("Capture", "Monitoring started (${settings.activeCamera.name.lowercase()} camera)")
    }

    private fun stopCapture() {
        running = false
        ParkiroidCamera.clear()
        vehicleMotionDetector.unregister(sensorManager)
        audioCapture.stop()
        releaseObjectDetector()
        wakeLock?.release()
        wakeLock = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLogger.info("Capture", "Monitoring stopped")
    }

    private fun switchCamera(facing: CameraFacing) {
        val settings = cachedSettings ?: return
        io.execute {
            runBlocking { settingsStore.updateActiveCamera(facing) }
            cachedSettings = settings.copy(activeCamera = facing)
            ParkiroidCamera.switchCamera(
                context = this,
                lifecycleOwner = this,
                cameraFacing = facing,
                jpegQuality = settings.sendingImageQuality.jpegQuality,
            )
            AppLogger.info("Camera", "Switched to ${facing.name.lowercase()} camera")
        }
    }

    private fun scheduleLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings
                    syncObjectDetector(settings)
                    maybeWakeScreen(settings)

                    if (ServerConnectionManager.isConnected()) {
                        val baseUrl = settings.serverBaseUrl
                        if (isValidBaseUrl(baseUrl)) {
                            captureFrame(settings, baseUrl)
                            sendDeviceMetrics(baseUrl, settings.apiKey)
                        }
                    }

                    Thread.sleep(settings.captureIntervalMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (_: Exception) {
                    Thread.sleep(5_000L)
                }
            }
        }
    }

    private fun maybeWakeScreen(settings: AppSettings) {
        if (settings.screenOnIntervalSec <= 0) return
        val now = System.currentTimeMillis()
        val intervalMs = settings.screenOnIntervalSec * 1000L
        if (now - lastScreenWakeAt < intervalMs) return
        lastScreenWakeAt = now
        ScreenWakeHelper.wakeScreen(this)
        AppLogger.info("Screen", "Woke screen (every ${settings.screenOnIntervalSec}s)")
    }

    private fun syncObjectDetector(settings: AppSettings) {
        if (settings.objectDetectionOnDevice) {
            if (objectDetector == null) {
                objectDetector = ParkiroidObjectDetector(this)
            }
        } else {
            releaseObjectDetector()
        }
    }

    private fun releaseObjectDetector() {
        objectDetector?.close()
        objectDetector = null
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
            if (ServerConnectionManager.isConnected() && isValidBaseUrl(settings.serverBaseUrl)) {
                captureFrame(settings, settings.serverBaseUrl)
            }
            showMotionAlert(peakAccelerationMps2)
        }
    }

    private fun captureFrame(settings: AppSettings, baseUrl: String) {
        val capture = ParkiroidCamera.imageCapture ?: return
        val file = File.createTempFile("cap_", ".jpg", cacheDir)
        val capturedAt = Instant.now()
        val out = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(out, io, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val detectionSummary = runOnDeviceObjectDetection(file, settings)
                val uploaded = apiClient.submitFrame(baseUrl, settings.apiKey, file, capturedAt)
                if (uploaded) {
                    AppLogger.info("Upload", "Frame uploaded from ${settings.activeCamera.name.lowercase()} camera")
                } else {
                    AppLogger.warn("Upload", "Frame upload failed")
                }
                updateNotification(settings, detectionSummary)
                file.delete()
            }

            override fun onError(exception: ImageCaptureException) {
                AppLogger.error("Capture", "Photo capture failed: ${exception.message}")
                file.delete()
            }
        })
    }

    private fun runOnDeviceObjectDetection(jpegFile: File, settings: AppSettings): String? {
        if (!settings.objectDetectionOnDevice) return null
        val detector = objectDetector ?: return null
        val bitmap = ParkiroidObjectDetector.decodeJpegForDetection(jpegFile) ?: return null
        return try {
            val result = detector.detect(bitmap, 0.5f)
            ParkiroidObjectDetector.logResult(result)
            ParkiroidObjectDetector.summarize(result)
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun sendDeviceMetrics(baseUrl: String, apiKey: String) {
        if (!isValidBaseUrl(baseUrl)) return
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        apiClient.submitDeviceMetrics(
            baseUrl = baseUrl,
            apiKey = apiKey,
            batteryLevelPercent = level,
            temperatureCelsius = temp,
            recordedAt = Instant.now(),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Capture Service", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(MOTION_CHANNEL, "Motion Alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun showMotionAlert(peakAccelerationMps2: Float) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, MOTION_CHANNEL)
            .setContentTitle(getString(R.string.motion_detected_title))
            .setContentText(getString(R.string.motion_detected_body, peakAccelerationMps2))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(MOTION_NOTIF_ID, notification)
        AppLogger.warn("Motion", "Bump detected (${"%.2f".format(peakAccelerationMps2)} m/s²)")
    }

    private fun buildNotification(detectionSummary: String? = null): Notification {
        val settings = cachedSettings
        val contentText = when {
            detectionSummary != null -> detectionSummary
            settings != null && ServerConnectionManager.isConnected() ->
                getString(R.string.uploading_frames_interval, settings.periodSec)
            else -> getString(R.string.server_not_connected)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.parkiroid_running))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(settings: AppSettings, detectionSummary: String?) {
        cachedSettings = settings
        val summary = if (settings.objectDetectionOnDevice) {
            detectionSummary ?: getString(R.string.object_detection_summary_none)
        } else {
            null
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(summary))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Parkiroid::Capture").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
