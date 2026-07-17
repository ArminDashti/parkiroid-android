package com.dogan

import androidx.camera.core.CameraSelector

/** Active camera lens used for preview and capture. */
enum class CameraFacing {
    REAR,
    FRONT,
    BOTH;

    fun toCameraSelector(): CameraSelector = when (this) {
        REAR, BOTH -> CameraSelector.DEFAULT_BACK_CAMERA
        FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): CameraFacing =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase() }
                ?: REAR
    }
}
