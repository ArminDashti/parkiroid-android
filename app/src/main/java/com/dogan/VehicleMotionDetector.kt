package com.dogan

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/** Detects vehicle bumps and jolts from phone motion sensors, tuned for parking-lot sensitivity. */
class VehicleMotionDetector(
    private val onBumpDetected: (peakAccelerationMps2: Float) -> Unit
) : SensorEventListener {

    @Volatile
    var joltSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM

    @Volatile
    var customJoltScale: Float = SettingsStore.DEFAULT_CUSTOM_JOLT_SCALE

    @Volatile
    var lastMagnitudeMps2: Float = 0f
        private set

    private var usesLinearAcceleration = true
    private val gravityEstimate = FloatArray(3)
    private var baselineMagnitude = 0f
    private var previousMagnitude = 0f
    private var previousTimestampNanos = 0L
    private var lastTriggerAtMs = 0L
    private var hasBaseline = false

    /** Subscribes to linear acceleration or accelerometer updates at game rate. */
    fun register(sensorManager: SensorManager): Boolean {
        val linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linearSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        usesLinearAcceleration = linearSensor != null
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return true
    }

    /** Stops listening and clears internal motion state. */
    fun unregister(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
        resetState()
    }

    /** Evaluates each sensor sample for peak, deviation, and jerk bump signatures. */
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
        lastMagnitudeMps2 = magnitude

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastTriggerAtMs < COOLDOWN_MS) return

        val scale = sensitivityScale(joltSensitivity)
        val isBump = magnitude >= PEAK_ACCELERATION_THRESHOLD_MPS2 * scale ||
            deviation >= DEVIATION_THRESHOLD_MPS2 * scale ||
            jerk >= JERK_SENSITIVITY_MPS3 * scale

        if (!isBump) return

        lastTriggerAtMs = nowMs
        baselineMagnitude = magnitude
        onBumpDetected(magnitude)
    }

    /** Estimates linear acceleration from raw accelerometer readings by subtracting gravity. */
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

    /** Clears baseline tracking so the next sample re-establishes resting motion. */
    private fun resetState() {
        hasBaseline = false
        baselineMagnitude = 0f
        previousMagnitude = 0f
        previousTimestampNanos = 0L
        lastMagnitudeMps2 = 0f
        gravityEstimate.fill(0f)
    }

    /** No-op; sensor accuracy changes do not affect bump detection. */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sensitivityScale(level: SensitivityLevel): Float = when (level) {
        SensitivityLevel.LOW -> 1.8f
        SensitivityLevel.MEDIUM -> 1.0f
        SensitivityLevel.HIGH -> 0.6f
        SensitivityLevel.CUSTOM -> customJoltScale
    }

    companion object {
        const val COOLDOWN_MS = 2_000L

        private const val GRAVITY_LOW_PASS_ALPHA = 0.9f
        /** Peak linear acceleration indicating contact — tuned for gentle bumps. */
        private const val PEAK_ACCELERATION_THRESHOLD_MPS2 = 0.10f
        /** Deviation from the resting baseline — catches force transferred through the car body. */
        private const val DEVIATION_THRESHOLD_MPS2 = 0.07f
        /** Jerk sensitivity threshold (rate-of-change of acceleration) to detect brief impulses. */
        private const val JERK_SENSITIVITY_MPS3 = 1.2f
        private const val BASELINE_SMOOTHING = 0.992f
    }
}
