package com.parkiroid

import android.os.Bundle
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
    private var loadedPeriodSec = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        val maxShakeInput = findViewById<TextInputEditText>(R.id.maxShakeInput)
        val serverInput = findViewById<TextInputEditText>(R.id.serverInput)
        val smsNumberInput = findViewById<TextInputEditText>(R.id.smsNumberInput)
        val saveBtn = findViewById<Button>(R.id.saveBtn)

        scope.launch {
            val settings = settingsStore.settingsFlow.first()
            loadedPeriodSec = settings.periodSec
            maxShakeInput.setText(settings.maxShakeMagnitude.toString())
            serverInput.setText(settings.serverBaseUrl)
            smsNumberInput.setText(settings.alertPhoneNumbers)
        }

        saveBtn.setOnClickListener {
            val maxShake = maxShakeInput.text?.toString()?.toFloatOrNull()
            if (maxShake == null) {
                Toast.makeText(this, R.string.invalid_shake_magnitude, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch {
                settingsStore.save(
                    serverUrl = serverInput.text?.toString().orEmpty(),
                    maxShakeMagnitude = maxShake,
                    alertPhoneNumbers = smsNumberInput.text?.toString().orEmpty(),
                    periodSec = loadedPeriodSec
                )
                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

}
