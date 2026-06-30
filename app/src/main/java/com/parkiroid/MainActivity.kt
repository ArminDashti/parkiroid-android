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

    private fun startMonitoringService() {
        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_START
        }
        startForegroundService(intent)
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
}
