package com.dogan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main screen with mode picker, camera preview, detection overlay, and navigation. */
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
    private lateinit var modeStatusTxt: TextView
    private lateinit var modeInput: AutoCompleteTextView

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
        modeStatusTxt = findViewById(R.id.modeStatusTxt)
        modeInput = findViewById(R.id.modeInput)

        setupModeDropdown()

        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.logsBtn).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        findViewById<Button>(R.id.diagnosticsBtn).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.rearCameraBtn).setOnClickListener {
            selectCamera(CameraFacing.REAR)
        }
        findViewById<Button>(R.id.frontCameraBtn).setOnClickListener {
            selectCamera(CameraFacing.FRONT)
        }

        detectionOverlay.onDetectionTapped = { detection, imageWidth, imageHeight ->
            DetectionTapBridge.onTapped(detection.label, detection.bounds, imageWidth, imageHeight)
            lifecycleScope.launch {
                val settings = settingsStore.settingsFlow.first()
                if (settings.operatingMode == OperatingMode.SPOTTER ||
                    settings.operatingMode == OperatingMode.WATCHMAN_SPOTTER
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.spotter_watching, detection.label),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { status ->
                    serverStatusTxt.text = connectionStatusLabel(status)
                    when (status) {
                        ConnectionStatus.CONNECTED -> ServerSettingsSync.start(this@MainActivity)
                        ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> ServerSettingsSync.stop()
                        else -> Unit
                    }
                }
            }
        }

        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            activeCamera = settings.activeCamera
            updateFpsLabel(settings.realtimeFps)
            updateModeLabel(settings.operatingMode)
            setDropdownSelection(modeInput, settings.operatingMode.displayName)
            detectionOverlay.setTapToWatchEnabled(
                settings.operatingMode == OperatingMode.SPOTTER ||
                    settings.operatingMode == OperatingMode.WATCHMAN_SPOTTER,
            )
        }

        lifecycleScope.launch {
            while (isActive) {
                if (monitoringActive) {
                    // Detection overlay updates via broadcast from service would be ideal;
                    // for now refresh from DoganCamera analysis in service logs.
                }
                delay(500)
            }
        }

        registerCameraStatusListener()
        selectCamera(CameraFacing.REAR)
    }

    private fun setupModeDropdown() {
        val labels = OperatingMode.all.map { it.displayName }
        modeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels),
        )
        modeInput.setOnItemClickListener { _, _, position, _ ->
            val mode = OperatingMode.all[position]
            lifecycleScope.launch {
                settingsStore.updateOperatingMode(mode)
                updateModeLabel(mode)
                detectionOverlay.setTapToWatchEnabled(
                    mode == OperatingMode.SPOTTER || mode == OperatingMode.WATCHMAN_SPOTTER,
                )
                AppLogger.info("Mode", "Selected ${mode.displayName}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (monitoringActive) {
            registerCameraStatusListener()
            DoganCamera.attachPreviewSurface(previewView)
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
            DoganCamera.detachPreviewSurface()
            cameraStatusTxt.setText(R.string.camera_background)
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (!monitoringActive) {
            DoganCamera.clear()
        }
        DoganCamera.clearStatusListener()
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
                DoganCamera.attachPreviewSurface(previewView)
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
        DoganCamera.bindForPreview(
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
        DoganCamera.setStatusListener(
            onReady = {
                runOnUiThread {
                    DoganCamera.attachPreviewSurface(previewView)
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

    private fun updateModeLabel(mode: OperatingMode) {
        modeStatusTxt.text = getString(R.string.mode_active, mode.displayName)
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

    private fun setDropdownSelection(view: AutoCompleteTextView, label: String) {
        view.setText(label, false)
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
        private val requiredPermissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
