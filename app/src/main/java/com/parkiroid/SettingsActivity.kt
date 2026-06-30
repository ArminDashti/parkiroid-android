package com.parkiroid



import android.os.Bundle

import android.widget.ArrayAdapter

import android.widget.AutoCompleteTextView

import android.widget.Button

import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity

import com.google.android.material.appbar.MaterialToolbar

import com.google.android.material.textfield.TextInputEditText

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.launch



/** Settings screen for server URL, API key, detection mode, and capture preferences. */
class SettingsActivity : AppCompatActivity() {

    private val settingsStore by lazy { SettingsStore(this) }

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var selectedIntervalSec = SettingsStore.DEFAULT_INTERVAL_MS / 1000f



    /** Binds form controls, loads persisted settings, and handles save actions. */
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }



        val serverInput = findViewById<TextInputEditText>(R.id.serverInput)

        val apiKeyInput = findViewById<TextInputEditText>(R.id.apiKeyInput)

        val frameUploadIntervalInput = findViewById<AutoCompleteTextView>(R.id.frameUploadIntervalInput)

        val saveBtn = findViewById<Button>(R.id.saveBtn)



        val allowedIntervals = SettingsStore.ALLOWED_INTERVALS_SEC

        val intervalLabels = allowedIntervals.map { seconds ->

            getString(R.string.frame_upload_interval_option, seconds)

        }

        frameUploadIntervalInput.setAdapter(

            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, intervalLabels)

        )

        frameUploadIntervalInput.setOnItemClickListener { _, _, position, _ ->

            selectedIntervalSec = allowedIntervals[position].toFloat()

        }

        /** Applies a normalized frame upload interval to the dropdown selection. */
        fun setSelectedIntervalSec(intervalSec: Float) {

            val normalizedSeconds = SettingsStore.normalizeFrameUploadIntervalSec(intervalSec)

            selectedIntervalSec = normalizedSeconds

            val labelIndex = allowedIntervals.indexOf(normalizedSeconds.toInt()).coerceAtLeast(0)

            frameUploadIntervalInput.setText(intervalLabels[labelIndex], false)

        }

        /** Updates the confidence threshold label to show the current percentage. */
        fun updateConfidenceThresholdLabel(threshold: Float) {
            val percent = (threshold * 100).roundToInt()
            confidenceThresholdValue.text = getString(R.string.confidence_threshold_value, percent)
        }

        /** Syncs the slider and label to a normalized confidence threshold value. */
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

        /** Shows server-only or on-device-only controls based on the selected detection mode. */
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

            setSelectedIntervalSec(settings.intervalSec)

        }



        saveBtn.setOnClickListener {

            scope.launch {

                settingsStore.save(

                    serverUrl = serverInput.text?.toString().orEmpty(),

                    apiKey = apiKeyInput.text?.toString().orEmpty(),

                    intervalSec = selectedIntervalSec,

                )

                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()

                finish()

            }

        }

    }

}

