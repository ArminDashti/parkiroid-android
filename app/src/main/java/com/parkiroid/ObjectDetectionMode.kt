package com.parkiroid

enum class ObjectDetectionMode {
    ON_DEVICE,
    SERVER;

    fun toStoredValue(): String = when (this) {
        ON_DEVICE -> STORED_ON_DEVICE
        SERVER -> STORED_SERVER
    }

    companion object {
        const val STORED_ON_DEVICE = "on_device"
        const val STORED_SERVER = "server"

        fun fromStoredValue(value: String?): ObjectDetectionMode = when (value) {
            STORED_SERVER -> SERVER
            else -> ON_DEVICE
        }
    }
}
