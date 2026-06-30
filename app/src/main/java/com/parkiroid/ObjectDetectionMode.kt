package com.parkiroid

/** Where vehicle object detection runs: locally on the device or on the Parkiroid server. */
enum class ObjectDetectionMode {
    ON_DEVICE,
    SERVER;

    /** Serializes this mode to the string stored in DataStore preferences. */
    fun toStoredValue(): String = when (this) {
        ON_DEVICE -> STORED_ON_DEVICE
        SERVER -> STORED_SERVER
    }

    companion object {
        const val STORED_ON_DEVICE = "on_device"
        const val STORED_SERVER = "server"

        /** Parses a stored preference value, defaulting to on-device detection when unknown. */
        fun fromStoredValue(value: String?): ObjectDetectionMode = when (value) {
            STORED_SERVER -> SERVER
            else -> ON_DEVICE
        }
    }
}
