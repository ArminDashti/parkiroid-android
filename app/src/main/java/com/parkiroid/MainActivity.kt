package com.parkiroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show()
            }
        }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.SEND_SMS
    )

    private lateinit var previewView: PreviewView
    private lateinit var cameraStatus: TextView
    private lateinit var status: TextView
    private var cameraProvider: ProcessCameraProvider? = null
    private var monitoringActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMissingPermissions()

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
                    Toast.makeText(this@MainActivity, R.string.server_url_recommended, Toast.LENGTH_LONG).show()
                }

                startCameraPreview()
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

    override fun onDestroy() {
        if (monitoringActive) {
            stopMonitoringService()
        }
        stopCameraPreview()
        super.onDestroy()
    }

    private fun startCameraPreview() {
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
                    .setJpegQuality(65)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                ParkiroidCamera.imageCapture = imageCapture
                cameraStatus.setText(R.string.camera_ready)
            } catch (_: Exception) {
                ParkiroidCamera.clear()
                cameraStatus.setText(R.string.camera_error)
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraPreview() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        ParkiroidCamera.clear()
        cameraStatus.setText(R.string.camera_idle)
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
}
