package com.dogan

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var sensorHud: LinearLayout
    private lateinit var joltValueTxt: TextView
    private lateinit var soundValueTxt: TextView
    private var showSensors = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        showSensors = intent.getBooleanExtra(EXTRA_SHOW_SENSORS, false)

        findViewById<MaterialToolbar>(R.id.cameraToolbar).apply {
            title = getString(R.string.preview_title)
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        previewView = findViewById(R.id.previewView)
        detectionOverlay = findViewById(R.id.detectionOverlay)
        sensorHud = findViewById(R.id.sensorHud)
        joltValueTxt = findViewById(R.id.joltValueTxt)
        soundValueTxt = findViewById(R.id.soundValueTxt)
        sensorHud.visibility = if (showSensors) View.VISIBLE else View.GONE

        DetectionOverlayBridge.listener = { detections, imageWidth, imageHeight ->
            runOnUiThread {
                if (imageWidth > 0 && imageHeight > 0) {
                    detectionOverlay.setDetections(detections, imageWidth, imageHeight)
                } else {
                    detectionOverlay.clear()
                }
            }
        }

        SensorHudBridge.listener = { jolt, sound ->
            runOnUiThread {
                joltValueTxt.text = getString(R.string.preview_jolt_value, jolt)
                soundValueTxt.text = getString(R.string.preview_sound_value, sound)
            }
        }
        joltValueTxt.text = getString(R.string.preview_jolt_value, SensorHudBridge.joltMps2)
        soundValueTxt.text = getString(R.string.preview_sound_value, SensorHudBridge.soundRms)

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
                    val sensorsVisible = showSensors || settings.operatingMode == OperatingMode.WATCHER
                    sensorHud.visibility = if (sensorsVisible) View.VISIBLE else View.GONE
                    // Keep CaptureService's monitoring bind (Preview + ImageAnalysis); only attach the surface.
                    DoganCamera.attachPreviewSurface(previewView)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DoganCamera.attachPreviewSurface(previewView)
        DoganCamera.setStatusListener(
            onReady = { runOnUiThread { DoganCamera.attachPreviewSurface(previewView) } },
            onError = {
                runOnUiThread {
                    Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    override fun onPause() {
        DoganCamera.detachPreviewSurface()
        super.onPause()
    }

    override fun onDestroy() {
        DetectionOverlayBridge.listener = null
        SensorHudBridge.listener = null
        detectionOverlay.clear()
        DoganCamera.clearStatusListener()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SHOW_SENSORS = "show_sensors"
    }
}
