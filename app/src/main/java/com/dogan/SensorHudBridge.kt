package com.dogan

/** Live jolt / sound levels for the Watchman preview HUD. */
object SensorHudBridge {
    @Volatile
    var joltMps2: Float = 0f
        private set

    @Volatile
    var soundRms: Double = 0.0
        private set

    @Volatile
    var listener: ((joltMps2: Float, soundRms: Double) -> Unit)? = null

    fun publish(joltMps2: Float, soundRms: Double) {
        this.joltMps2 = joltMps2
        this.soundRms = soundRms
        listener?.invoke(joltMps2, soundRms)
    }

    fun clear() {
        joltMps2 = 0f
        soundRms = 0.0
        listener = null
    }
}
