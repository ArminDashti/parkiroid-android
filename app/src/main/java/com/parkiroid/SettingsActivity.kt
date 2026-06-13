package com.parkiroid

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var selectedFrameUploadIntervalSec = SettingsStore.DEFAULT_PERIOD_SEC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        val serverInput = findViewById<TextInputEditText>(R.id.serverInput)
        val apiKeyInput = findViewById<TextInputEditText>(R.id.apiKeyInput)
        val objectDetectionModeGroup = findViewById<RadioGroup>(R.id.objectDetectionModeGroup)
        val frameUploadIntervalLayout = findViewById<TextInputLayout>(R.id.frameUploadIntervalLayout)
        val frameUploadIntervalInput = findViewById<AutoCompleteTextView>(R.id.frameUploadIntervalInput)
        val saveBtn = findViewById<Button>(R.id.saveBtn)

        val allowedIntervals = SettingsStore.ALLOWED_FRAME_UPLOAD_INTERVALS_SEC
        val intervalLabels = allowedIntervals.map { seconds ->
            getString(R.string.frame_upload_interval_option, seconds)
        }
        frameUploadIntervalInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, intervalLabels)
        )
        frameUploadIntervalInput.setOnItemClickListener { _, _, position, _ ->
            selectedFrameUploadIntervalSec = allowedIntervals[position]
        }

        fun setSelectedFrameUploadInterval(seconds: Int) {
            val normalizedSeconds = SettingsStore.normalizeFrameUploadInterval(seconds)
            selectedFrameUploadIntervalSec = normalizedSeconds
            val labelIndex = allowedIntervals.indexOf(normalizedSeconds).coerceAtLeast(0)
            frameUploadIntervalInput.setText(intervalLabels[labelIndex], false)
        }

        fun updateFrameUploadIntervalVisibility() {
            val isServerMode = objectDetectionModeGroup.checkedRadioButtonId == R.id.objectDetectionServer
            frameUploadIntervalLayout.visibility = if (isServerMode) View.VISIBLE else View.GONE
        }

        objectDetectionModeGroup.setOnCheckedChangeListener { _, _ ->
            updateFrameUploadIntervalVisibility()
        }

        scope.launch {
            val settings = settingsStore.settingsFlow.first()
            serverInput.setText(settings.serverBaseUrl)
            apiKeyInput.setText(settings.apiKey)
            setSelectedFrameUploadInterval(settings.periodSec)
            val selectedModeViewId = when (settings.objectDetectionMode) {
                ObjectDetectionMode.ON_DEVICE -> R.id.objectDetectionOnDevice
                ObjectDetectionMode.SERVER -> R.id.objectDetectionServer
            }
            objectDetectionModeGroup.check(selectedModeViewId)
            updateFrameUploadIntervalVisibility()
        }

        saveBtn.setOnClickListener {
            val objectDetectionMode = when (objectDetectionModeGroup.checkedRadioButtonId) {
                R.id.objectDetectionOnDevice -> ObjectDetectionMode.ON_DEVICE
                else -> ObjectDetectionMode.SERVER
            }

            val periodSec = if (objectDetectionMode == ObjectDetectionMode.SERVER) {
                selectedFrameUploadIntervalSec
            } else {
                SettingsStore.DEFAULT_PERIOD_SEC
            }

            scope.launch {
                settingsStore.save(
                    serverUrl = serverInput.text?.toString().orEmpty(),
                    apiKey = apiKeyInput.text?.toString().orEmpty(),
                    objectDetectionMode = objectDetectionMode,
                    periodSec = periodSec
                )
                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
