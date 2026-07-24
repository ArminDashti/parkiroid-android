package com.dogan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /**
     * When true, still captures rebind continuous monitoring afterward (preview / recording).
     * When false, the camera stays unbound after each still (duty-cycle / battery mode).
     */
    @Volatile
    var preferContinuousBind: Boolean = false

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
        preferContinuousBind = true
        if (boundOwner === lifecycleOwner && imageCapture != null && imageAnalysis != null) {
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
        if (preferContinuousBind) {
            bindForMonitoring(context, lifecycleOwner, cameraFacing, jpegQuality, analysisExecutor, realtimeFps)
        }
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
        preferContinuousBind = true
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
                imageAnalysis = null
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

    /**
     * Opens the camera briefly, captures one still as a [Bitmap], then unbinds.
     * Does not keep the sensor powered between calls unless [preferContinuousBind] is true.
     */
    fun captureBitmap(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        facing: CameraFacing,
        jpegQuality: Int,
        onComplete: (Bitmap?) -> Unit,
        executor: Executor,
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
                markUnbound()
                provider.bindToLifecycle(lifecycleOwner, facing.toCameraSelector(), capture)
                activeFacing = facing
                val file = java.io.File.createTempFile("dogan_analysis_", ".jpg", context.cacheDir)
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        file.delete()
                        finishStillCapture(context, lifecycleOwner, provider)
                        onComplete(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        finishStillCapture(context, lifecycleOwner, provider)
                        onComplete(null)
                    }
                })
            } catch (e: Exception) {
                onComplete(null)
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
                markUnbound()
                provider.bindToLifecycle(lifecycleOwner, facing.toCameraSelector(), capture)
                activeFacing = facing
                val file = java.io.File.createTempFile("dogan_${facing.name.lowercase()}_", ".jpg", context.cacheDir)
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        finishStillCapture(context, lifecycleOwner, provider)
                        onComplete(file)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        finishStillCapture(context, lifecycleOwner, provider)
                        onComplete(null)
                    }
                })
            } catch (e: Exception) {
                onComplete(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun finishStillCapture(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        provider: ProcessCameraProvider,
    ) {
        provider.unbindAll()
        markUnbound()
        if (preferContinuousBind) {
            bindForMonitoring(context, lifecycleOwner, activeFacing)
        }
    }

    private fun markUnbound() {
        preview = null
        imageAnalysis = null
        captureRef.set(null)
        boundOwner = null
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
        preferContinuousBind = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageAnalysis = null
        captureRef.set(null)
        boundOwner = null
        analysisListener = null
    }
}
