package com.parkiroid

import androidx.camera.core.ImageCapture
import java.util.concurrent.atomic.AtomicReference

/** Thread-safe holder for the shared [ImageCapture] instance used by the foreground service. */
object ParkiroidCamera {
    private val captureRef = AtomicReference<ImageCapture?>(null)

    /** The active rear-camera capture use case bound by [MainActivity], or null when idle. */
    var imageCapture: ImageCapture?
        get() = captureRef.get()
        set(value) = captureRef.set(value)

    /** Clears the shared capture reference when the camera preview is stopped. */
    fun clear() {
        captureRef.set(null)
    }
}
