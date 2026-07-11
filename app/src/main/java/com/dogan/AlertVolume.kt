package com.dogan

/** Alert playback volume levels. */
enum class AlertVolume(val displayName: String, val volumeFraction: Float) {
    OFF("Off", 0f),
    VERY_LOW("Very Low", 0.2f),
    LOW("Low", 0.4f),
    BALANCED("Balanced", 0.6f),
    HIGH("High", 0.8f),
    VERY_HIGH("Very High", 1.0f);

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): AlertVolume =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase() }
                ?: BALANCED
    }
}
