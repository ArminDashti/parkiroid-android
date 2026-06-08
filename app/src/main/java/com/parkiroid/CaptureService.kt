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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var lastAlarmAt = 0L
    @Volatile private var cachedSettings: AppSettings? = null

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
        cachedSettings = runBlocking { settingsStore.settingsFlow.first() }
        scheduleLoop()
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

    private fun scheduleLoop() {
        io.execute {
            while (running) {
                try {
                    val settings = runBlocking { settingsStore.settingsFlow.first() }
                    cachedSettings = settings
                    val baseUrl = settings.serverBaseUrl
                    if (isValidBaseUrl(baseUrl)) {
                        takePhotoAndSend(baseUrl)
                        sendBatteryInfo(baseUrl)
                    }
                    Thread.sleep(settings.periodSec * 1000L)
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

    private fun takePhotoAndSend(baseUrl: String) {
        if (!isValidBaseUrl(baseUrl)) return
        val capture = ParkiroidCamera.imageCapture ?: return
        val file = File.createTempFile("cap_", ".jpg", cacheDir)
        val out = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(out, io, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val req = Request.Builder().url("$baseUrl/parkiroid/api/v1/img").post(body).build()
                client.newCall(req).execute().close()
                file.delete()
            }

            override fun onError(exception: ImageCaptureException) {}
        })
    }

    private fun sendBatteryInfo(baseUrl: String) {
        if (!isValidBaseUrl(baseUrl)) return
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temp = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        val payload = JSONObject()
            .put("batteryPercent", level)
            .put("batteryTempC", temp)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$baseUrl/parkiroid/api/v1/battery/info").post(payload).build()
        client.newCall(req).execute().close()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        val mag = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        val now = System.currentTimeMillis()
        if (now - lastAlarmAt < 5000) return

        val settings = cachedSettings ?: runBlocking { settingsStore.settingsFlow.first() }
        val violentThreshold = settings.maxShakeMagnitude.toDouble()
        val jarringThreshold = settings.jarringShakeMagnitude.toDouble()

        if (mag > violentThreshold) {
            postAlarm("/parkiroid/api/v1/alarm/violent-jolt", "Parkiroid: violent jolt detected")
            lastAlarmAt = now
        } else if (mag > jarringThreshold) {
            postAlarm("/parkiroid/api/v1/alarm/jarring-noise", "Parkiroid: jarring noise detected")
            lastAlarmAt = now
        }
    }

    private fun postAlarm(path: String, smsMessage: String) {
        val settings = runBlocking { settingsStore.settingsFlow.first() }
        val baseUrl = settings.serverBaseUrl
        if (!isValidBaseUrl(baseUrl)) {
            sendAlarmSms(settings, smsMessage)
            return
        }
        val req = Request.Builder().url("$baseUrl$path")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().close()
        sendAlarmSms(settings, smsMessage)
    }

    private fun sendAlarmSms(settings: AppSettings, message: String) {
        val numbers = settingsStore.parsePhoneNumbers(settings.alertPhoneNumbers)
        if (numbers.isEmpty()) return
        SmsSender.sendToAll(this, numbers, message)
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
