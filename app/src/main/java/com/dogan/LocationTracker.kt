package com.dogan

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Tracks GPS location and speed via Fused Location Provider. */
class LocationTracker(context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @Volatile
    var lastLocation: Location? = null
        private set

    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long) {
        stop()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .build()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                lastLocation = result.lastLocation ?: result.locations.lastOrNull()
            }
        }
        fusedClient.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
        AppLogger.info("Location", "GPS tracking started (${intervalMs}ms)")
    }

    fun stop() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
    }

    fun gpsSignalQuality(): String {
        val loc = lastLocation ?: return "poor"
        val accuracy = loc.accuracy
        return when {
            accuracy <= 5f -> "excellent"
            accuracy <= 15f -> "good"
            accuracy <= 50f -> "fair"
            else -> "poor"
        }
    }

    fun speedKmh(): Float {
        val loc = lastLocation ?: return 0f
        return (loc.speed * 3.6f).coerceAtLeast(0f)
    }
}
