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



class CaptureService : Service(), LifecycleOwner {

    companion object {

        const val ACTION_START = "start"

        const val ACTION_STOP = "stop"

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

    private var running = false

    private var wakeLock: PowerManager.WakeLock? = null



    @Volatile private var cachedSettings: AppSettings? = null

    private lateinit var sensorManager: SensorManager

    private val vehicleMotionDetector = VehicleMotionDetector { peakMps2 ->

        onVehicleBumpDetected(peakMps2)

    }

    @Volatile private var lastMotionTriggeredCaptureAt = 0L



    override fun onCreate() {

        super.onCreate()

        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_STOP -> stopCapture()

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

        ParkiroidCamera.bindForMonitoring(this, this)

        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }

        vehicleMotionDetector.register(sensorManager)

        scheduleLoop()

    }



    private fun stopCapture() {

        running = false

        ParkiroidCamera.clear()

        vehicleMotionDetector.unregister(sensorManager)

        wakeLock?.release()

        wakeLock = null

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()

    }



    private fun scheduleLoop() {

        io.execute {

            while (running) {

                try {

                    val settings = runBlocking { settingsStore.settingsFlow.first() }

                    cachedSettings = settings

                    val baseUrl = settings.serverBaseUrl

                    if (isValidBaseUrl(baseUrl)) {

                        captureFrame(settings, baseUrl)

                        sendDeviceMetrics(baseUrl, settings.apiKey)

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

            val baseUrl = settings.serverBaseUrl

            if (isValidBaseUrl(baseUrl)) {

                captureFrame(settings, baseUrl)

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

                apiClient.submitFrame(baseUrl, settings.apiKey, file, capturedAt)

                updateNotification(settings)

                file.delete()

            }



            override fun onError(exception: ImageCaptureException) {

                file.delete()

            }

        })

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

            recordedAt = Instant.now()

        )

    }



    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(

                NotificationChannel(CHANNEL, "Capture Service", NotificationManager.IMPORTANCE_LOW)

            )

            nm.createNotificationChannel(

                NotificationChannel(MOTION_CHANNEL, "Motion Alerts", NotificationManager.IMPORTANCE_HIGH)

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

    }



    private fun buildNotification(): Notification {

        val settings = cachedSettings

        val contentText = if (settings != null && isValidBaseUrl(settings.serverBaseUrl)) {

            getString(R.string.uploading_frames_interval, settings.periodSec)

        } else {

            getString(R.string.server_not_configured)

        }

        return NotificationCompat.Builder(this, CHANNEL)

            .setContentTitle(getString(R.string.parkiroid_running))

            .setContentText(contentText)

            .setSmallIcon(android.R.drawable.ic_menu_camera)

            .setOngoing(true)

            .build()

    }



    private fun updateNotification(settings: AppSettings) {

        cachedSettings = settings

        getSystemService(NotificationManager::class.java)

            .notify(NOTIF_ID, buildNotification())

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

