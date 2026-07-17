package com.dogan

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Collects the unified telemetry snapshot. */
class TelemetryCollector(
    private val context: Context,
    private val locationTracker: LocationTracker,
    private val ambientLightSensor: AmbientLightSensor,
    private val audioCapture: AudioCapture,
    private val deviceId: String,
    private val resourceMonitor: DeviceResourceMonitor,
) {
    fun collectSnapshot(
        rearFrameFile: File?,
        frontFrameFile: File?,
        serverLatencyMs: Long,
        includeFrames: Boolean,
    ): JSONObject {
        val loc = locationTracker.lastLocation
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f

        val gpsLocation = JSONObject()
        if (loc != null) {
            gpsLocation.put("latitude", loc.latitude)
            gpsLocation.put("longitude", loc.longitude)
        } else {
            gpsLocation.put("latitude", 0.0)
            gpsLocation.put("longitude", 0.0)
        }

        return JSONObject()
            .put("device_id", deviceId)
            .put("recorded_at", Instant.now().toString())
            .put("gps_location", gpsLocation)
            .put("gps_signal_quality", locationTracker.gpsSignalQuality())
            .put("speed_kmh", locationTracker.speedKmh().toDouble())
            .put("network_signal_strength_dbm", NetworkInfoCollector.getSignalStrengthDbm(context))
            .put("network_type", NetworkInfoCollector.getNetworkType(context))
            .put("cabin_noise_rms", audioCapture.currentRms)
            .put("battery_temperature_celsius", batteryTemp.toDouble())
            .put("battery_percentage", batteryPct)
            .put("rear_camera_frame_base64", if (includeFrames) encodeFile(rearFrameFile) else "")
            .put("front_camera_frame_base64", if (includeFrames) encodeFile(frontFrameFile) else "")
            .put("ambient_light_lux", ambientLightSensor.lux.toDouble())
            .put("server_latency_ms", serverLatencyMs)
            .put("device_ip_address", NetworkInfoCollector.getLocalIpAddress())
            .put("cpu_usage_percent", resourceMonitor.cpuUsagePercent())
            .put("ram_usage_percent", resourceMonitor.ramUsagePercent())
    }

    private fun encodeFile(file: File?): String {
        if (file == null || !file.exists()) return ""
        return Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }
}
