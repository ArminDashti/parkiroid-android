package com.dogan

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** CameraX binding for monitoring, preview, analysis, and dual-camera capture. */
object DoganCamera {
    private val captureRef = AtomicReference<ImageCapture?>(null)
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var boundOwner: LifecycleOwner? = null

    @Volatile var activeFacing: CameraFacing = CameraFacing.REAR
        private set

    @Volatile
    private var readyListener: (() -> Unit)? = null

    @Volatile
    private var errorListener: ((Exception) -> Unit)? = null

    @Volatile
    private var analysisListener: ((ImageProxy) -> Unit)? = null

    var imageCapture: ImageCapture?
        get() = captureRef.get()
        set(value) = captureRef.set(value)

    val isBound: Boolean get() = imageCapture != null

    fun setStatusListener(onReady: () -> Unit, onError: (Exception) -> Unit) {
        readyListener = onReady
        errorListener = onError
        if (imageCapture != null) onReady()
    }

    fun clearStatusListener() {
        readyListener = null
        errorListener = null
    }

    fun setAnalysisListener(listener: ((ImageProxy) -> Unit)?) {
        analysisListener = listener
    }

    fun bindForMonitoring(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cameraFacing: CameraFacing = activeFacing,
        jpegQuality: Int = ImageQuality.BALANCED.jpegQuality,
        analysisExecutor: Executor? = null,
        realtimeFps: Int = 5,
    ) {
        activeFacing = cameraFacing
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
                    .setJpegQuality(jpegQuality)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        val executor = analysisExecutor ?: ContextCompat.getMainExecutor(context)
                        analysis.setAnalyzer(executor) { proxy ->
                            analysisListener?.invoke(proxy) ?: proxy.close()
                        }
                    }

                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraFacing.toCameraSelector(),
                    previewUseCase,
                    captureUseCase,
                    analysisUseCase,
                )

                preview = previewUseCase
                imageAnalysis = analysisUseCase
                captureRef.set(captureUseCase)
                cameraProvider = provider
                boundOwner = lifecycleOwner
                AppLogger.info("Camera", "Bound ${cameraFacing.name.lowercase()} camera for monitoring")
                readyListener?.invoke()
            } catch (exception: Exception) {
                clear()
                errorListener?.invoke(exception)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cameraFacing: CameraFacing,
        jpegQuality: Int,
        analysisExecutor: Executor? = null,
        realtimeFps: Int = 5,
    ) {
        activeFacing = cameraFacing
        boundOwner = null
        bindForMonitoring(context, lifecycleOwner, cameraFacing, jpegQuality, analysisExecutor, realtimeFps)
    }

    fun bindForPreview(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraFacing: CameraFacing,
        jpegQuality: Int = ImageQuality.BALANCED.jpegQuality,
        onReady: () -> Unit = {},
        onError: (Exception) -> Unit = {},
    ) {
        activeFacing = cameraFacing
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                provider.unbindAll()

                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val captureUseCase = ImageCapture.Builder()
                    .setJpegQuality(jpegQuality)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraFacing.toCameraSelector(),
                    previewUseCase,
                    captureUseCase,
                )

                preview = previewUseCase
                captureRef.set(captureUseCase)
                cameraProvider = provider
                boundOwner = lifecycleOwner
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onReady()
            } catch (exception: Exception) {
                clear()
                onError(exception)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun captureFromFacing(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        facing: CameraFacing,
        jpegQuality: Int,
        onComplete: (java.io.File?) -> Unit,
        executor: java.util.concurrent.Executor,
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val capture = ImageCapture.Builder()
                    .setJpegQuality(jpegQuality)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, facing.toCameraSelector(), capture)
                val file = java.io.File.createTempFile("dogan_${facing.name.lowercase()}_", ".jpg", context.cacheDir)
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        provider.unbindAll()
                        rebindAfterSnapshot(context, lifecycleOwner)
                        onComplete(file)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        provider.unbindAll()
                        rebindAfterSnapshot(context, lifecycleOwner)
                        onComplete(null)
                    }
                })
            } catch (e: Exception) {
                onComplete(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun rebindAfterSnapshot(context: Context, lifecycleOwner: LifecycleOwner) {
        if (boundOwner === lifecycleOwner && captureRef.get() != null) {
            bindForMonitoring(context, lifecycleOwner, activeFacing)
        }
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
        imageAnalysis = null
        captureRef.set(null)
        boundOwner = null
        analysisListener = null
    }
}
