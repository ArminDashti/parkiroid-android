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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Main screen with camera preview, server status, camera switching, settings, and logs. */
class MainActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var serverStatusTxt: TextView
    private lateinit var cameraStatusTxt: TextView
    private lateinit var fpsStatusTxt: TextView

    private var monitoringActive = false
    private var activeCamera = CameraFacing.REAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestMissingPermissions()

        previewView = findViewById(R.id.previewView)
        detectionOverlay = findViewById(R.id.detectionOverlay)
        serverStatusTxt = findViewById(R.id.serverStatusTxt)
        cameraStatusTxt = findViewById(R.id.cameraStatusTxt)
        fpsStatusTxt = findViewById(R.id.fpsStatusTxt)

        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.logsBtn).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        findViewById<Button>(R.id.rearCameraBtn).setOnClickListener {
            selectCamera(CameraFacing.REAR)
        }
        findViewById<Button>(R.id.frontCameraBtn).setOnClickListener {
            selectCamera(CameraFacing.FRONT)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { status ->
                    serverStatusTxt.text = connectionStatusLabel(status)
                }
            }
        }

        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            activeCamera = settings.activeCamera
            updateFpsLabel(settings.realtimeFps)
        }

        registerCameraStatusListener()
        selectCamera(CameraFacing.REAR)
    }

    override fun onResume() {
        super.onResume()
        if (monitoringActive) {
            registerCameraStatusListener()
            ParkiroidCamera.attachPreviewSurface(previewView)
            cameraStatusTxt.setText(R.string.camera_ready)
        } else {
            lifecycleScope.launch {
                val settings = settingsStore.settingsFlow.first()
                bindPreview(settings)
            }
        }
    }

    override fun onPause() {
        if (monitoringActive) {
            ParkiroidCamera.detachPreviewSurface()
            cameraStatusTxt.setText(R.string.camera_background)
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (!monitoringActive) {
            ParkiroidCamera.clear()
        }
        ParkiroidCamera.clearStatusListener()
        super.onDestroy()
    }

    private fun selectCamera(facing: CameraFacing) {
        lifecycleScope.launch {
            if (!hasRequiredPermissions()) {
                requestMissingPermissions()
                Toast.makeText(this@MainActivity, R.string.permissions_required, Toast.LENGTH_LONG).show()
                return@launch
            }

            val settings = settingsStore.settingsFlow.first()
            activeCamera = facing
            settingsStore.updateActiveCamera(facing)
            AppLogger.info("Camera", "Selected ${facing.name.lowercase()} camera")

            if (monitoringActive) {
                val intent = Intent(this@MainActivity, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_SWITCH_CAMERA
                    putExtra(CaptureService.EXTRA_CAMERA_FACING, facing.toStoredValue())
                }
                startService(intent)
                ParkiroidCamera.attachPreviewSurface(previewView)
                cameraStatusTxt.text = getString(R.string.camera_active, facingLabel(facing))
            } else {
                bindPreview(settings.copy(activeCamera = facing))
            }

            if (ServerConnectionManager.isConnected() && !monitoringActive) {
                startMonitoringService()
                monitoringActive = true
            }
        }
    }

    private fun bindPreview(settings: AppSettings) {
        cameraStatusTxt.setText(R.string.camera_opening)
        ParkiroidCamera.bindForPreview(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            cameraFacing = settings.activeCamera,
            jpegQuality = settings.sendingImageQuality.jpegQuality,
            onReady = {
                cameraStatusTxt.text = getString(R.string.camera_active, facingLabel(settings.activeCamera))
            },
            onError = {
                cameraStatusTxt.setText(R.string.camera_error)
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
            },
        )
    }

    private fun registerCameraStatusListener() {
        ParkiroidCamera.setStatusListener(
            onReady = {
                runOnUiThread {
                    ParkiroidCamera.attachPreviewSurface(previewView)
                    cameraStatusTxt.text = getString(R.string.camera_active, facingLabel(activeCamera))
                }
            },
            onError = {
                runOnUiThread {
                    cameraStatusTxt.setText(R.string.camera_error)
                }
            },
        )
    }

    private fun startMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        startForegroundService(intent)
        monitoringActive = true
        AppLogger.info("Capture", "Background monitoring started")
    }

    private fun connectionStatusLabel(status: ConnectionStatus): String = when (status) {
        ConnectionStatus.CONNECTED -> getString(R.string.server_status_connected)
        ConnectionStatus.CONNECTING -> getString(R.string.server_status_connecting)
        ConnectionStatus.FAILED -> getString(R.string.server_status_failed)
        ConnectionStatus.DISCONNECTED -> getString(R.string.server_status_disconnected)
    }

    private fun facingLabel(facing: CameraFacing): String = when (facing) {
        CameraFacing.REAR -> getString(R.string.rear_camera)
        CameraFacing.FRONT -> getString(R.string.front_camera)
    }

    private fun updateFpsLabel(fps: Int) {
        fpsStatusTxt.text = getString(R.string.fps_active, fps)
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

    companion object {
        private val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
