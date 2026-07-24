package com.dogan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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

/** Main hub: mode selection and navigation to settings / logs. */
class MainActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var modeCopilotBtn: MaterialButton
    private lateinit var modeSpotterBtn: MaterialButton
    private lateinit var modeWatchmanBtn: MaterialButton
    private lateinit var connectivityBtn: MaterialButton

    private var monitoringActive = false
    private var currentMode: OperatingMode = OperatingMode.OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppLogger.init(this)
        requestMissingPermissions()

        modeCopilotBtn = findViewById(R.id.modeCopilotBtn)
        modeSpotterBtn = findViewById(R.id.modeSpotterBtn)
        modeWatchmanBtn = findViewById(R.id.modeWatchmanBtn)
        connectivityBtn = findViewById(R.id.connectivityBtn)

        modeCopilotBtn.setOnClickListener { toggleOrSelectMode(OperatingMode.COPILOT) }
        modeSpotterBtn.setOnClickListener { toggleOrSelectMode(OperatingMode.SPOTTER) }
        modeWatchmanBtn.setOnClickListener { toggleOrSelectMode(OperatingMode.WATCHER) }

        findViewById<MaterialButton>(R.id.copilotSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_COPILOT)
        }
        findViewById<MaterialButton>(R.id.spotterSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_SPOTTER)
        }
        findViewById<MaterialButton>(R.id.watchmanSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_WATCHMAN)
        }
        connectivityBtn.setOnClickListener {
            openSettings(SettingsActivity.SECTION_CONNECTIVITY)
        }
        findViewById<MaterialButton>(R.id.connectivitySettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_CONNECTIVITY)
        }
        findViewById<MaterialButton>(R.id.recordingSettingsBtn).setOnClickListener {
            openSettings(SettingsActivity.SECTION_RECORDING)
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
                    updateConnectivityButton(status)
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
                    if (settings.operatingMode == OperatingMode.OFF) {
                        stopMonitoringIfNeeded()
                    } else if (ServerConnectionManager.isConnected() || settings.objectDetectionOnDevice) {
                        ensureMonitoringStarted()
                    }
                }
            }
        }
    }

    /** Selects [mode], or turns OFF if that mode is already active. */
    private fun toggleOrSelectMode(mode: OperatingMode) {
        if (currentMode == mode) {
            selectMode(OperatingMode.OFF)
        } else {
            selectMode(mode)
        }
    }

    private fun selectMode(mode: OperatingMode) {
        lifecycleScope.launch {
            SettingsPublisher.pushOperatingMode(this@MainActivity, mode)
            currentMode = mode
            updateModeButtons(mode)
            if (mode == OperatingMode.OFF) {
                stopMonitoringIfNeeded()
            } else {
                ensureMonitoringStarted(forceModeRefresh = true)
                if (!hasRequiredPermissions()) {
                    val section = LogSection.forOperatingMode(mode)
                    AppLogger.error(
                        section,
                        mode.displayName,
                        "Cannot start ${mode.displayName}: required permissions missing",
                    )
                }
            }
        }
    }

    private fun updateModeButtons(mode: OperatingMode) {
        styleModeButton(modeCopilotBtn, mode == OperatingMode.COPILOT)
        styleModeButton(modeSpotterBtn, mode == OperatingMode.SPOTTER)
        styleModeButton(modeWatchmanBtn, mode == OperatingMode.WATCHER)
    }

    private fun styleModeButton(button: MaterialButton, selected: Boolean) {
        button.setBackgroundColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.dogan_mode_selected else R.color.dogan_mode_unselected,
            ),
        )
    }

    private fun updateConnectivityButton(status: ConnectionStatus) {
        val connected = status == ConnectionStatus.CONNECTED
        connectivityBtn.text = getString(
            if (connected) R.string.connected_label else R.string.disconnected_label,
        )
        connectivityBtn.setBackgroundColor(
            Color.parseColor(if (connected) "#2E7D32" else "#C62828"),
        )
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
            updateConnectivityButton(ServerConnectionManager.status.value)
            if (settings.operatingMode != OperatingMode.OFF) {
                ensureMonitoringStarted()
            }
        }
    }

    private fun ensureMonitoringStarted(forceModeRefresh: Boolean = false) {
        if (currentMode == OperatingMode.OFF) return
        if (!hasRequiredPermissions()) return
        val intent = Intent(this, CaptureService::class.java).apply {
            action = if (forceModeRefresh || monitoringActive) {
                CaptureService.ACTION_MODE_ACTIVATED
            } else {
                CaptureService.ACTION_START
            }
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
