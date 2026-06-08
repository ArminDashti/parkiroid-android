package com.parkiroid

import androidx.camera.core.ImageCapture
import java.util.concurrent.atomic.AtomicReference

object ParkiroidCamera {
    private val captureRef = AtomicReference<ImageCapture?>(null)

    var imageCapture: ImageCapture?
        get() = captureRef.get()
        set(value) = captureRef.set(value)

    fun clear() {
        captureRef.set(null)
    }
}
