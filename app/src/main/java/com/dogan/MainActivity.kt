package com.dogan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

/** Main hub: mode selection and navigation to camera / settings / logs. */
class MainActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var cameraBtn: MaterialButton
    private lateinit var modeCopilotBtn: MaterialButton
    private lateinit var modeSpotterBtn: MaterialButton
    private lateinit var modeWatchmanBtn: MaterialButton
    private lateinit var modeOffBtn: MaterialButton

    private var monitoringActive = false
    private var currentMode: OperatingMode = OperatingMode.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppLogger.init(this)
        requestMissingPermissions()

        cameraBtn = findViewById(R.id.cameraBtn)
        modeCopilotBtn = findViewById(R.id.modeCopilotBtn)
        modeSpotterBtn = findViewById(R.id.modeSpotterBtn)
        modeWatchmanBtn = findViewById(R.id.modeWatchmanBtn)
        modeOffBtn = findViewById(R.id.modeOffBtn)

        cameraBtn.setOnClickListener {
            if (currentMode == OperatingMode.OFF) {
                Toast.makeText(this, R.string.camera_disabled_off, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, CameraActivity::class.java))
        }
        modeCopilotBtn.setOnClickListener { selectMode(OperatingMode.COPILOT) }
        modeSpotterBtn.setOnClickListener { selectMode(OperatingMode.SPOTTER) }
        modeWatchmanBtn.setOnClickListener { selectMode(OperatingMode.WATCHER) }
        modeOffBtn.setOnClickListener { selectMode(OperatingMode.OFF) }

        findViewById<MaterialButton>(R.id.copilotSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_COPILOT)
        }
        findViewById<MaterialButton>(R.id.recordingSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_RECORDING)
        }
        findViewById<MaterialButton>(R.id.spotterSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_SPOTTER)
        }
        findViewById<MaterialButton>(R.id.watchmanSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_WATCHMAN)
        }
        findViewById<MaterialButton>(R.id.connectivityBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_CONNECTIVITY)
        }
        findViewById<MaterialButton>(R.id.settingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_GENERAL)
        }
        findViewById<MaterialButton>(R.id.logsBtn).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.exitBtn).setOnClickListener { exitApp() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { status ->
                    when (status) {
                        ConnectionStatus.CONNECTED -> {
                            ServerSettingsSync.start(this@MainActivity)
                            if (currentMode != OperatingMode.OFF) {
                                ensureMonitoringStarted()
                            }
                        }
                        ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> {
                            ServerSettingsSync.stop()
                        }
                        else -> Unit
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsStore.settingsFlow.collect { settings ->
                    currentMode = settings.operatingMode
                    updateModeButtons(settings.operatingMode)
                    updateCameraEnabled(settings.operatingMode)
                    if (settings.operatingMode == OperatingMode.OFF) {
                        stopMonitoringIfNeeded()
                    } else if (ServerConnectionManager.isConnected() || settings.objectDetectionOnDevice) {
                        ensureMonitoringStarted()
                    }
                }
            }
        }
    }

    private fun selectMode(mode: OperatingMode) {
        lifecycleScope.launch {
            SettingsPublisher.pushOperatingMode(this@MainActivity, mode)
            currentMode = mode
            updateModeButtons(mode)
            updateCameraEnabled(mode)
            if (mode == OperatingMode.OFF) {
                stopMonitoringIfNeeded()
            } else {
                ensureMonitoringStarted()
            }
        }
    }

    private fun updateModeButtons(mode: OperatingMode) {
        styleModeButton(modeCopilotBtn, mode == OperatingMode.COPILOT)
        styleModeButton(modeSpotterBtn, mode == OperatingMode.SPOTTER)
        styleModeButton(modeWatchmanBtn, mode == OperatingMode.WATCHER)
        styleModeButton(modeOffBtn, mode == OperatingMode.OFF)
    }

    private fun styleModeButton(button: MaterialButton, selected: Boolean) {
        button.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.dogan_mode_selected else R.color.dogan_mode_unselected,
            ),
        )
    }

    private fun updateCameraEnabled(mode: OperatingMode) {
        val enabled = mode != OperatingMode.OFF
        cameraBtn.isEnabled = enabled
        cameraBtn.alpha = if (enabled) 1f else 0.45f
    }

    private fun openSettings(section: String) {
        startActivity(
            Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, section),
        )
    }

    private fun exitApp() {
        lifecycleScope.launch {
            if (ServerConnectionManager.isConnected()) {
                ServerConnectionManager.disconnect()
            }
            stopMonitoringIfNeeded()
            finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            currentMode = settings.operatingMode
            updateModeButtons(settings.operatingMode)
            updateCameraEnabled(settings.operatingMode)
            if (settings.operatingMode != OperatingMode.OFF) {
                ensureMonitoringStarted()
            }
        }
    }

    private fun ensureMonitoringStarted() {
        if (monitoringActive) return
        if (!hasRequiredPermissions()) return
        if (currentMode == OperatingMode.OFF) return
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        startForegroundService(intent)
        monitoringActive = true
    }

    private fun stopMonitoringIfNeeded() {
        if (!monitoringActive) return
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        }
        startService(intent)
        monitoringActive = false
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
