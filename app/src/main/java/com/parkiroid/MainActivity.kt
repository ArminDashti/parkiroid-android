package com.parkiroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Main screen for camera preview, monitoring controls, and navigation to settings. */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val KEY_MONITORING_ACTIVE = "monitoring_active"
    }

    private val settingsStore by lazy { SettingsStore(this) }
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            }
        }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA
    )

    private lateinit var previewView: PreviewView
    private lateinit var cameraStatus: TextView
    private lateinit var status: TextView
    private var monitoringActive = false

    /** Wires UI actions, requests permissions, and restores monitoring on resume when active. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMissingPermissions()

        monitoringActive = savedInstanceState?.getBoolean(KEY_MONITORING_ACTIVE, false) ?: false

        previewView = findViewById(R.id.previewView)
        cameraStatus = findViewById(R.id.cameraStatusTxt)
        status = findViewById(R.id.statusTxt)
        val settingsBtn = findViewById<Button>(R.id.settingsBtn)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        startBtn.setOnClickListener {
            lifecycleScope.launch {
                if (!hasRequiredPermissions()) {
                    requestMissingPermissions()
                    Toast.makeText(this@MainActivity, R.string.permissions_required, Toast.LENGTH_LONG).show()
                    return@launch
                }

                val settings = settingsStore.settingsFlow.first()
                if (settings.serverBaseUrl.isBlank()) {
                    Toast.makeText(this@MainActivity, R.string.server_url_required, Toast.LENGTH_LONG).show()
                    return@launch
                }

                registerCameraStatusListener()
                cameraStatus.setText(R.string.camera_opening)
                startMonitoringService()
                monitoringActive = true
                status.setText(R.string.status_running)
            }
        }

        stopBtn.setOnClickListener {
            stopMonitoring()
        }

        if (monitoringActive) {
            status.setText(R.string.status_running)
            registerCameraStatusListener()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_MONITORING_ACTIVE, monitoringActive)
    }

    /** Re-binds the camera when returning to the app while monitoring is active. */
    override fun onResume() {
        super.onResume()
        if (monitoringActive) {
            registerCameraStatusListener()
            attachPreviewIfReady()
        }
    }

    override fun onPause() {
        if (monitoringActive) {
            ParkiroidCamera.detachPreviewSurface()
            cameraStatus.setText(R.string.camera_background)
        }
        super.onPause()
    }

    /** Stops monitoring and releases camera resources when the activity is destroyed. */
    override fun onDestroy() {
        if (monitoringActive && !isChangingConfigurations) {
            stopMonitoringService()
        }
        ParkiroidCamera.clearStatusListener()
        super.onDestroy()
    }

    private fun registerCameraStatusListener() {
        ParkiroidCamera.setStatusListener(
            onReady = {
                runOnUiThread {
                    attachPreviewIfReady()
                    cameraStatus.setText(R.string.camera_ready)
                }
            },
            onError = {
                runOnUiThread {
                    cameraStatus.setText(R.string.camera_error)
                    status.setText(R.string.status_error)
                }
            }
        )
    }

    /** Binds preview, capture, and optional analysis use cases to the rear camera lifecycle. */
    private fun bindCamera(settings: AppSettings) {
        cameraStatus.setText(R.string.camera_opening)
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setJpegQuality(80)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysis = if (shouldShowBoundingBoxes(settings)) {
                    ImageAnalysis.Builder().build().also { analysisUseCase ->
                        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(this)) { image ->
                            analyzePreviewFrame(image, settings)
                        }
                    }
                } else null

                val useCases = mutableListOf<UseCase>(preview, capture)
                analysis?.let { useCases.add(it) }

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray()
                )

                previewView.implementationMode = PreviewView.ImplementationMode.SURFACE_VIEW
                ParkiroidCamera.imageCapture = capture
                cameraProvider = provider
                cameraStatus.setText(R.string.camera_ready)
            } catch (e: Exception) {
                cameraStatus.setText(R.string.camera_error)
                status.setText(R.string.status_error)
            }
        }, ContextCompat.getMainExecutor(this))
    }
                }
            },
            onError = {
                runOnUiThread {
                    cameraStatus.setText(R.string.camera_error)
                    Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun attachPreviewIfReady() {
        if (ParkiroidCamera.isBound) {
            ParkiroidCamera.attachPreviewSurface(previewView)
            cameraStatus.setText(R.string.camera_ready)
        }
    }

    private fun stopMonitoring() {
        stopMonitoringService()
        ParkiroidCamera.clearStatusListener()
        monitoringActive = false
        cameraStatus.setText(R.string.camera_idle)
        status.setText(R.string.status_idle)
    }

    /** Returns true when on-device detection with bounding box overlay is enabled. */
    private fun shouldShowBoundingBoxes(settings: AppSettings): Boolean =
        settings.objectDetectionMode == ObjectDetectionMode.ON_DEVICE && settings.showBoundingBoxes

    /** Lazily creates or releases the preview-only ONNX detector based on settings. */
    private fun syncPreviewDetector(settings: AppSettings) {
        if (shouldShowBoundingBoxes(settings)) {
            if (previewObjectDetector == null) {
                previewObjectDetector = ParkiroidObjectDetector(this)
            }
        } else {
            releasePreviewDetector()
        }
    }

    /** Throttled preview-frame analysis that updates the detection overlay on the UI thread. */
    private fun analyzePreviewFrame(image: ImageProxy, settings: AppSettings) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < ANALYSIS_INTERVAL_MS) {
            image.close()
            return
        }
        lastAnalysisAt = now

        val detector = previewObjectDetector
        if (detector == null || !shouldShowBoundingBoxes(settings)) {
            image.close()
            return
        }

        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            bitmap = imageProxyToBitmap(image)
            rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees)
            val result = detector.detect(rotated, settings.confidenceThreshold)
            val imageWidth = rotated.width
            val imageHeight = rotated.height
            runOnUiThread {
                detectionOverlay.setDetections(result.detections, imageWidth, imageHeight)
            }
        } catch (_: Exception) {
            runOnUiThread { detectionOverlay.clear() }
        } finally {
            if (rotated != null && rotated !== bitmap) rotated.recycle()
            bitmap?.recycle()
            image.close()
        }
    }

    /** Unbinds camera use cases and clears shared capture and overlay state. */
    private fun stopCameraPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        ParkiroidCamera.clear()
        releasePreviewDetector()
        detectionOverlay.clear()
        cameraStatus.setText(R.string.camera_idle)
    }

    /** Closes and drops the preview ONNX detector instance. */
    private fun releasePreviewDetector() {
        previewObjectDetector?.close()
        previewObjectDetector = null
    }

    /** Starts the foreground capture service via startForegroundService on Android O+. */
    private fun startMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        startForegroundService(intent)
    }

    /** Sends a stop action to the foreground capture service. */
    private fun stopMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        startService(intent)
    }

    /** Prompts for any runtime permissions that are not yet granted. */
    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** Returns true when all required runtime permissions are granted. */
    private fun hasRequiredPermissions(): Boolean = missingPermissions().isEmpty()

    /** Lists required permissions that the app does not currently hold. */
    private fun missingPermissions(): List<String> =
        requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

    /** Converts an RGBA8888 [ImageProxy] plane into an ARGB bitmap. */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        buffer.rewind()
        val pixels = IntArray(image.width * image.height)
        var offset = 0
        for (i in pixels.indices) {
            val pixel = buffer.getInt(offset)
            pixels[i] = pixel
            offset += 4
        }
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    /** Rotates a bitmap by the camera-reported orientation degrees, recycling the source when needed. */
    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        private const val ANALYSIS_INTERVAL_MS = 300L
    }
}
