package com.parkiroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
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
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var cameraStatus: TextView
    private lateinit var status: TextView
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewObjectDetector: ParkiroidObjectDetector? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var monitoringActive = false
    private var lastAnalysisAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMissingPermissions()

        previewView = findViewById(R.id.previewView)
        detectionOverlay = findViewById(R.id.detectionOverlay)
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
                    Toast.makeText(this@MainActivity, R.string.server_url_recommended, Toast.LENGTH_LONG).show()
                }

                bindCamera(settings)
                startMonitoringService()
                monitoringActive = true
                status.setText(R.string.status_running)
            }
        }

        stopBtn.setOnClickListener {
            stopMonitoringService()
            stopCameraPreview()
            monitoringActive = false
            status.setText(R.string.status_idle)
        }
    }

    override fun onResume() {
        super.onResume()
        if (monitoringActive) {
            lifecycleScope.launch {
                bindCamera(settingsStore.settingsFlow.first())
            }
        }
    }

    override fun onDestroy() {
        if (monitoringActive) {
            stopMonitoringService()
        }
        stopCameraPreview()
        super.onDestroy()
    }

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
                val imageCapture = ImageCapture.Builder()
                    .setJpegQuality(80)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                syncPreviewDetector(settings)

                val useCases = mutableListOf<UseCase>(preview, imageCapture)
                if (shouldShowBoundingBoxes(settings)) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    imageAnalysis.setAnalyzer(analysisExecutor) { image ->
                        analyzePreviewFrame(image, settings)
                    }
                    useCases.add(imageAnalysis)
                } else {
                    runOnUiThread { detectionOverlay.clear() }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray()
                )
                ParkiroidCamera.imageCapture = imageCapture
                cameraStatus.setText(R.string.camera_ready)
            } catch (_: Exception) {
                ParkiroidCamera.clear()
                releasePreviewDetector()
                cameraStatus.setText(R.string.camera_error)
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun shouldShowBoundingBoxes(settings: AppSettings): Boolean =
        settings.objectDetectionMode == ObjectDetectionMode.ON_DEVICE && settings.showBoundingBoxes

    private fun syncPreviewDetector(settings: AppSettings) {
        if (shouldShowBoundingBoxes(settings)) {
            if (previewObjectDetector == null) {
                previewObjectDetector = ParkiroidObjectDetector(this)
            }
        } else {
            releasePreviewDetector()
        }
    }

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

    private fun stopCameraPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        ParkiroidCamera.clear()
        releasePreviewDetector()
        detectionOverlay.clear()
        cameraStatus.setText(R.string.camera_idle)
    }

    private fun releasePreviewDetector() {
        previewObjectDetector?.close()
        previewObjectDetector = null
    }

    private fun startMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        startService(intent)
    }

    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasRequiredPermissions(): Boolean = missingPermissions().isEmpty()

    private fun missingPermissions(): List<String> =
        requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

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
