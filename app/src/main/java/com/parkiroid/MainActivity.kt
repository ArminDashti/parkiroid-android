package com.parkiroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.SEND_SMS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestMissingPermissions()

        val serverInput = findViewById<TextInputEditText>(R.id.serverInput)
        val periodInput = findViewById<TextInputEditText>(R.id.periodInput)
        val alertPhonesInput = findViewById<TextInputEditText>(R.id.alertPhonesInput)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)
        val status = findViewById<TextView>(R.id.statusTxt)

        scope.launch {
            val st = settingsStore.settingsFlow.first()
            serverInput.setText(st.serverBaseUrl)
            periodInput.setText(st.periodSec.toString())
            alertPhonesInput.setText(st.alertPhoneNumbers)
        }

        saveBtn.setOnClickListener {
            scope.launch {
                settingsStore.save(
                    serverInput.text?.toString().orEmpty(),
                    periodInput.text?.toString()?.toIntOrNull() ?: 15,
                    alertPhonesInput.text?.toString().orEmpty()
                )
            }
        }

        startBtn.setOnClickListener {
            val intent = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            status.setText(R.string.status_running)
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_STOP
            }
            startService(intent)
            status.setText(R.string.status_idle)
        }
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
