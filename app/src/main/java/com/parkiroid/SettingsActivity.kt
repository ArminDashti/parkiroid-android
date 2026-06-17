package com.parkiroid

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var selectedFrameUploadIntervalSec = SettingsStore.DEFAULT_PERIOD_SEC
    private var selectedConfidenceThreshold = SettingsStore.DEFAULT_CONFIDENCE_THRESHOLD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        val serverInput = findViewById<TextInputEditText>(R.id.serverInput)
        val apiKeyInput = findViewById<TextInputEditText>(R.id.apiKeyInput)
        val objectDetectionModeGroup = findViewById<RadioGroup>(R.id.objectDetectionModeGroup)
        val frameUploadIntervalLayout = findViewById<TextInputLayout>(R.id.frameUploadIntervalLayout)
        val frameUploadIntervalInput = findViewById<AutoCompleteTextView>(R.id.frameUploadIntervalInput)
        val onDeviceDetectionLayout = findViewById<View>(R.id.onDeviceDetectionLayout)
        val confidenceThresholdValue = findViewById<TextView>(R.id.confidenceThresholdValue)
        val confidenceThresholdSlider = findViewById<Slider>(R.id.confidenceThresholdSlider)
        val showBoundingBoxesSwitch = findViewById<MaterialSwitch>(R.id.showBoundingBoxesSwitch)
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

        fun updateConfidenceThresholdLabel(threshold: Float) {
            val percent = (threshold * 100).roundToInt()
            confidenceThresholdValue.text = getString(R.string.confidence_threshold_value, percent)
        }

        fun setSelectedConfidenceThreshold(threshold: Float) {
            val normalized = SettingsStore.normalizeConfidenceThreshold(threshold)
            selectedConfidenceThreshold = normalized
            confidenceThresholdSlider.value = normalized
            updateConfidenceThresholdLabel(normalized)
        }

        confidenceThresholdSlider.addOnChangeListener { _, value, _ ->
            selectedConfidenceThreshold = SettingsStore.normalizeConfidenceThreshold(value)
            updateConfidenceThresholdLabel(selectedConfidenceThreshold)
        }

        fun updateModeSpecificVisibility() {
            val isServerMode = objectDetectionModeGroup.checkedRadioButtonId == R.id.objectDetectionServer
            val isOnDeviceMode = objectDetectionModeGroup.checkedRadioButtonId == R.id.objectDetectionOnDevice
            frameUploadIntervalLayout.visibility = if (isServerMode) View.VISIBLE else View.GONE
            onDeviceDetectionLayout.visibility = if (isOnDeviceMode) View.VISIBLE else View.GONE
        }

        objectDetectionModeGroup.setOnCheckedChangeListener { _, _ ->
            updateModeSpecificVisibility()
        }

        scope.launch {
            val settings = settingsStore.settingsFlow.first()
            serverInput.setText(settings.serverBaseUrl)
            apiKeyInput.setText(settings.apiKey)
            setSelectedFrameUploadInterval(settings.periodSec)
            setSelectedConfidenceThreshold(settings.confidenceThreshold)
            showBoundingBoxesSwitch.isChecked = settings.showBoundingBoxes
            val selectedModeViewId = when (settings.objectDetectionMode) {
                ObjectDetectionMode.ON_DEVICE -> R.id.objectDetectionOnDevice
                ObjectDetectionMode.SERVER -> R.id.objectDetectionServer
            }
            objectDetectionModeGroup.check(selectedModeViewId)
            updateModeSpecificVisibility()
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
                    periodSec = periodSec,
                    confidenceThreshold = selectedConfidenceThreshold,
                    showBoundingBoxes = showBoundingBoxesSwitch.isChecked
                )
                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
