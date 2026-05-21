package com.parkiroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.sqrt

class CaptureService : Service(), LifecycleOwner, SensorEventListener {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "capture_channel"
        private const val NOTIF_ID = 81
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val settingsStore by lazy { SettingsStore(this) }
    private val io = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private var imageCapture: ImageCapture? = null
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var lastAlarmAt = 0L

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
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
        bindCamera()
        accel?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    private fun stopCapture() {
        running = false
        sensorManager.unregisterListener(this)
        wakeLock?.release()
        wakeLock = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            imageCapture = ImageCapture.Builder()
                .setJpegQuality(65)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
            scheduleLoop()
        }, ContextCompatExecutor.main)
    }

    private fun scheduleLoop() {
        io.execute {
            while (running) {
                val settings = kotlinx.coroutines.runBlocking { settingsStore.settingsFlow.first() }
                takePhotoAndSend(settings.serverBaseUrl)
                sendBatteryInfo(settings.serverBaseUrl)
                Thread.sleep(settings.periodSec * 1000L)
            }
        }
    }

    private fun takePhotoAndSend(baseUrl: String) {
        val capture = imageCapture ?: return
        val file = File.createTempFile("cap_", ".jpg", cacheDir)
        val out = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(out, io, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val req = Request.Builder().url("$baseUrl/samroid/api/v1/img").post(body).build()
                client.newCall(req).execute().close()
                file.delete()
            }

            override fun onError(exception: ImageCaptureException) {}
        })
    }

    private fun sendBatteryInfo(baseUrl: String) {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        val payload = JSONObject()
            .put("batteryPercent", level)
            .put("batteryTempC", temp)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/samroid/api/v1/battery/info").post(payload).build()
        client.newCall(req).execute().close()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        val mag = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        val now = System.currentTimeMillis()
        if (now - lastAlarmAt < 5000) return

        if (mag > 30) {
            postAlarm("/samroid/api/v1/alarm/violent-jolt")
            lastAlarmAt = now
        } else if (mag > 18) {
            postAlarm("/samroid/api/v1/alarm/jarring-noise")
            lastAlarmAt = now
        }
    }

    private fun postAlarm(path: String) {
        val baseUrl = kotlinx.coroutines.runBlocking { settingsStore.settingsFlow.first().serverBaseUrl }
        if (baseUrl.isBlank()) return
        val req = Request.Builder().url("$baseUrl$path")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().close()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Capture Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle("Parkiroid running")
        .setContentText("Capturing in background")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .build()

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Parkiroid::Capture").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object ContextCompatExecutor : java.util.concurrent.Executor {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    override fun execute(command: Runnable) = main.post(command)
}
