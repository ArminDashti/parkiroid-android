package com.parkiroid

import androidx.camera.core.CameraSelector

/** Active camera lens used for preview and capture. */
enum class CameraFacing {
    REAR,
    FRONT;

    fun toCameraSelector(): CameraSelector = when (this) {
        REAR -> CameraSelector.DEFAULT_BACK_CAMERA
        FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun toStoredValue(): String = name.lowercase()

    companion object {
        fun fromStoredValue(value: String?): CameraFacing =
            if (value.equals(FRONT.name, ignoreCase = true)) FRONT else REAR
    }
}
