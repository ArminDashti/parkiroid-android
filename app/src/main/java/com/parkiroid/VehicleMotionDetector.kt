package com.parkiroid

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects vehicle bumps and jolts from phone motion sensors.
 * Tuned for high sensitivity — catches gentle parking-lot contacts and light collisions.
 */
class VehicleMotionDetector(
    private val onBumpDetected: (peakAccelerationMps2: Float) -> Unit
) : SensorEventListener {

    private var usesLinearAcceleration = true
    private val gravityEstimate = FloatArray(3)
    private var baselineMagnitude = 0f
    private var previousMagnitude = 0f
    private var previousTimestampNanos = 0L
    private var lastTriggerAtMs = 0L
    private var hasBaseline = false

    fun register(sensorManager: SensorManager): Boolean {
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linearSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        usesLinearAcceleration = linearSensor != null
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return true
    }

    fun unregister(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
        resetState()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val magnitude = if (usesLinearAcceleration) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            sqrt(x * x + y * y + z * z)
        } else {
            resolveLinearMagnitude(event.values)
        }

        if (!hasBaseline) {
            baselineMagnitude = magnitude
            hasBaseline = true
            previousMagnitude = magnitude
            previousTimestampNanos = event.timestamp
            return
        }

        baselineMagnitude = baselineMagnitude * BASELINE_SMOOTHING + magnitude * (1f - BASELINE_SMOOTHING)
        val deviation = abs(magnitude - baselineMagnitude)

        val deltaSeconds = ((event.timestamp - previousTimestampNanos) / 1_000_000_000f).coerceAtLeast(0.001f)
        val jerk = abs(magnitude - previousMagnitude) / deltaSeconds

        previousMagnitude = magnitude
        previousTimestampNanos = event.timestamp

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTriggerAtMs < COOLDOWN_MS) return

        val isBump = magnitude >= PEAK_ACCELERATION_THRESHOLD_MPS2 ||
            deviation >= DEVIATION_THRESHOLD_MPS2 ||
            jerk >= JERK_THRESHOLD_MPS3

        if (!isBump) return

        lastTriggerAtMs = nowMs
        baselineMagnitude = magnitude
        onBumpDetected(magnitude)
    }

    private fun resolveLinearMagnitude(rawValues: FloatArray): Float {
        for (axis in 0..2) {
            gravityEstimate[axis] =
                GRAVITY_LOW_PASS_ALPHA * gravityEstimate[axis] +
                    (1f - GRAVITY_LOW_PASS_ALPHA) * rawValues[axis]
        }
        val linearX = rawValues[0] - gravityEstimate[0]
        val linearY = rawValues[1] - gravityEstimate[1]
        val linearZ = rawValues[2] - gravityEstimate[2]
        return sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
    }

    private fun resetState() {
        hasBaseline = false
        baselineMagnitude = 0f
        previousMagnitude = 0f
        previousTimestampNanos = 0L
        gravityEstimate.fill(0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val COOLDOWN_MS = 2_000L

        private const val GRAVITY_LOW_PASS_ALPHA = 0.9f
        /** Peak linear acceleration indicating contact — tuned for gentle bumps. */
        private const val PEAK_ACCELERATION_THRESHOLD_MPS2 = 0.10f
        /** Deviation from the resting baseline — catches force transferred through the car body. */
        private const val DEVIATION_THRESHOLD_MPS2 = 0.07f
        /** Sudden rate-of-change catches brief impulses that stay below peak thresholds. */
        private const val JERK_THRESHOLD_MPS3 = 1.2f
        private const val BASELINE_SMOOTHING = 0.992f
    }
}
