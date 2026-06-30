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



class SettingsActivity : AppCompatActivity() {

    private val settingsStore by lazy { SettingsStore(this) }

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var selectedIntervalSec = SettingsStore.DEFAULT_INTERVAL_MS / 1000f



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



        fun setSelectedIntervalSec(intervalSec: Float) {

            val normalizedSeconds = SettingsStore.normalizeFrameUploadIntervalSec(intervalSec)

            selectedIntervalSec = normalizedSeconds

            val labelIndex = allowedIntervals.indexOf(normalizedSeconds.toInt()).coerceAtLeast(0)

            frameUploadIntervalInput.setText(intervalLabels[labelIndex], false)

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

