package com.parkiroid

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraX binding tied to the foreground [CaptureService] lifecycle so capture continues
 * when the app is backgrounded (required on Android 9+ / API 28+).
 */
object ParkiroidCamera {
    private val captureRef = AtomicReference<ImageCapture?>(null)
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundOwner: LifecycleOwner? = null

    @Volatile
    private var readyListener: (() -> Unit)? = null

    @Volatile
    private var errorListener: ((Exception) -> Unit)? = null

    val imageCapture: ImageCapture? get() = captureRef.get()

    val isBound: Boolean get() = imageCapture != null

    fun setStatusListener(onReady: () -> Unit, onError: (Exception) -> Unit) {
        readyListener = onReady
        errorListener = onError
        if (imageCapture != null) {
            onReady()
        }
    }

    fun clearStatusListener() {
        readyListener = null
        errorListener = null
    }

    fun bindForMonitoring(context: Context, lifecycleOwner: LifecycleOwner) {
        if (boundOwner === lifecycleOwner && imageCapture != null) {
            readyListener?.invoke()
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                provider.unbindAll()

                val previewUseCase = Preview.Builder().build()
                val captureUseCase = ImageCapture.Builder()
                    .setJpegQuality(80)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase,
                    captureUseCase,
                )

                preview = previewUseCase
                captureRef.set(captureUseCase)
                cameraProvider = provider
                boundOwner = lifecycleOwner
                readyListener?.invoke()
            } catch (exception: Exception) {
                clear()
                errorListener?.invoke(exception)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun attachPreviewSurface(previewView: PreviewView) {
        preview?.setSurfaceProvider(previewView.surfaceProvider)
    }

    fun detachPreviewSurface() {
        preview?.setSurfaceProvider { request ->
            request.willNotProvideSurface()
        }
    }

    fun clear() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        captureRef.set(null)
        boundOwner = null
    }
}
