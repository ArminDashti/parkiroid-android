package com.dogan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Live camera preview with detection overlay — the app's "eye" for testing. */
class CameraActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        findViewById<MaterialToolbar>(R.id.cameraToolbar).apply {
            title = getString(R.string.camera_button)
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        previewView = findViewById(R.id.previewView)
        detectionOverlay = findViewById(R.id.detectionOverlay)

        DetectionOverlayBridge.listener = { detections, imageWidth, imageHeight ->
            runOnUiThread {
                if (imageWidth > 0 && imageHeight > 0) {
                    detectionOverlay.setDetections(detections, imageWidth, imageHeight)
                } else {
                    detectionOverlay.clear()
                }
            }
        }

        detectionOverlay.onDetectionTapped = { detection, imageWidth, imageHeight ->
            DetectionTapBridge.onTapped(detection.label, detection.bounds, imageWidth, imageHeight)
            lifecycleScope.launch {
                val settings = settingsStore.settingsFlow.first()
                if (settings.operatingMode == OperatingMode.SPOTTER) {
                    Toast.makeText(
                        this@CameraActivity,
                        getString(R.string.spotter_watching, detection.label),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsStore.settingsFlow.collect { settings ->
                    if (settings.operatingMode == OperatingMode.OFF) {
                        finish()
                        return@collect
                    }
                    detectionOverlay.setTapToWatchEnabled(settings.operatingMode == OperatingMode.SPOTTER)
                    bindPreview(settings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DoganCamera.attachPreviewSurface(previewView)
        DoganCamera.setStatusListener(
            onReady = { runOnUiThread { DoganCamera.attachPreviewSurface(previewView) } },
            onError = { },
        )
    }

    override fun onPause() {
        DoganCamera.detachPreviewSurface()
        super.onPause()
    }

    override fun onDestroy() {
        DetectionOverlayBridge.listener = null
        detectionOverlay.clear()
        DoganCamera.clearStatusListener()
        super.onDestroy()
    }

    private fun bindPreview(settings: AppSettings) {
        val facing = when (settings.activeCamera) {
            CameraFacing.FRONT -> CameraFacing.FRONT
            else -> CameraFacing.REAR
        }
        DoganCamera.bindForPreview(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            cameraFacing = facing,
            jpegQuality = settings.frameQualityForMode(settings.operatingMode).jpegQuality,
            onReady = { DoganCamera.attachPreviewSurface(previewView) },
            onError = {
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
            },
        )
    }
}
