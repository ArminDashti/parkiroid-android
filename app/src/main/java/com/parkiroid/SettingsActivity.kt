package com.parkiroid

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Settings screen for server, AI models, capture timing, quality, and detection options. */
class SettingsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }

    private lateinit var connectionStatusTxt: TextView
    private lateinit var serverInput: TextInputEditText
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var captureIntervalInput: TextInputEditText
    private lateinit var screenOnIntervalInput: TextInputEditText
    private lateinit var onDeviceDetectionSwitch: SwitchMaterial
    private lateinit var aiModelInput: AutoCompleteTextView
    private lateinit var detectionQualityInput: AutoCompleteTextView
    private lateinit var sendingQualityInput: AutoCompleteTextView
    private lateinit var realtimeFpsInput: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        connectionStatusTxt = findViewById(R.id.connectionStatusTxt)
        serverInput = findViewById(R.id.serverInput)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        captureIntervalInput = findViewById(R.id.captureIntervalInput)
        screenOnIntervalInput = findViewById(R.id.screenOnIntervalInput)
        onDeviceDetectionSwitch = findViewById(R.id.onDeviceDetectionSwitch)
        aiModelInput = findViewById(R.id.aiModelInput)
        detectionQualityInput = findViewById(R.id.detectionQualityInput)
        sendingQualityInput = findViewById(R.id.sendingQualityInput)
        realtimeFpsInput = findViewById(R.id.realtimeFpsInput)

        setupDropdown(aiModelInput, AiModel.all.map { it.displayName }) { index ->
            AiModel.all[index]
        }
        setupDropdown(detectionQualityInput, ImageQuality.entries.map { it.label }) { index ->
            ImageQuality.entries[index]
        }
        setupDropdown(sendingQualityInput, ImageQuality.entries.map { it.label }) { index ->
            ImageQuality.entries[index]
        }
        setupDropdown(
            realtimeFpsInput,
            SettingsStore.ALLOWED_REALTIME_FPS.map { getString(R.string.fps_option, it) },
        ) { index ->
            SettingsStore.ALLOWED_REALTIME_FPS[index]
        }

        findViewById<Button>(R.id.connectBtn).setOnClickListener { connectToServer() }
        findViewById<Button>(R.id.disconnectBtn).setOnClickListener {
            ServerConnectionManager.disconnect()
        }
        findViewById<Button>(R.id.saveBtn).setOnClickListener { saveSettings() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { status ->
                    connectionStatusTxt.text = connectionStatusLabel(status)
                }
            }
        }

        lifecycleScope.launch {
            loadSettings(settingsStore.settingsFlow.first())
        }
    }

    private fun connectToServer() {
        lifecycleScope.launch {
            val current = settingsStore.settingsFlow.first()
            val draft = readDraftSettings().copy(activeCamera = current.activeCamera)
            settingsStore.save(draft)
            val connected = ServerConnectionManager.connect(this@SettingsActivity, draft)
            val message = if (connected) R.string.server_connected else R.string.server_connect_failed
            Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            val current = settingsStore.settingsFlow.first()
            settingsStore.save(readDraftSettings().copy(activeCamera = current.activeCamera))
            Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private suspend fun loadSettings(settings: AppSettings) {
        serverInput.setText(settings.serverBaseUrl)
        apiKeyInput.setText(settings.apiKey)
        captureIntervalInput.setText(settings.captureIntervalMs.toString())
        screenOnIntervalInput.setText(settings.screenOnIntervalSec.toString())
        onDeviceDetectionSwitch.isChecked = settings.objectDetectionOnDevice
        setDropdownSelection(aiModelInput, settings.aiModel.displayName)
        setDropdownSelection(detectionQualityInput, settings.detectionImageQuality.label)
        setDropdownSelection(sendingQualityInput, settings.sendingImageQuality.label)
        setDropdownSelection(realtimeFpsInput, getString(R.string.fps_option, settings.realtimeFps))
    }

    private fun readDraftSettings(): AppSettings {
        val intervalMs = captureIntervalInput.text?.toString()?.toLongOrNull()
            ?: SettingsStore.DEFAULT_INTERVAL_MS
        val screenOnSec = screenOnIntervalInput.text?.toString()?.toIntOrNull()
            ?: SettingsStore.DEFAULT_SCREEN_ON_INTERVAL_SEC
        val aiModel = AiModel.all.getOrElse(aiModelInput.listSelection) { AiModel.YOLOV8_NANO }
        val detectionQuality = ImageQuality.entries.getOrElse(detectionQualityInput.listSelection) { ImageQuality.BALANCED }
        val sendingQuality = ImageQuality.entries.getOrElse(sendingQualityInput.listSelection) { ImageQuality.BALANCED }
        val fps = SettingsStore.ALLOWED_REALTIME_FPS.getOrElse(realtimeFpsInput.listSelection) {
            SettingsStore.DEFAULT_REALTIME_FPS
        }

        return AppSettings(
            serverBaseUrl = serverInput.text?.toString().orEmpty(),
            apiKey = apiKeyInput.text?.toString().orEmpty(),
            captureIntervalMs = intervalMs,
            activeCamera = CameraFacing.REAR,
            aiModel = aiModel,
            objectDetectionOnDevice = onDeviceDetectionSwitch.isChecked,
            screenOnIntervalSec = screenOnSec,
            detectionImageQuality = detectionQuality,
            sendingImageQuality = sendingQuality,
            realtimeFps = fps,
        )
    }

    private fun <T> setupDropdown(
        view: AutoCompleteTextView,
        labels: List<String>,
        onSelect: ((Int) -> T)? = null,
    ) {
        view.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels),
        )
        view.setOnItemClickListener { _, _, position, _ ->
            onSelect?.invoke(position)
        }
    }

    private fun setDropdownSelection(view: AutoCompleteTextView, label: String) {
        view.setText(label, false)
        val index = (view.adapter as? ArrayAdapter<*>)?.let { adapter ->
            (0 until adapter.count).firstOrNull { adapter.getItem(it)?.toString() == label }
        } ?: 0
        view.listSelection = index
    }

    private val AutoCompleteTextView.listSelection: Int
        get() {
            val current = text?.toString().orEmpty()
            val adapter = adapter as? ArrayAdapter<*> ?: return 0
            return (0 until adapter.count).firstOrNull { adapter.getItem(it)?.toString() == current } ?: 0
        }

    private fun connectionStatusLabel(status: ConnectionStatus): String = when (status) {
        ConnectionStatus.CONNECTED -> getString(R.string.server_status_connected)
        ConnectionStatus.CONNECTING -> getString(R.string.server_status_connecting)
        ConnectionStatus.FAILED -> getString(R.string.server_status_failed)
        ConnectionStatus.DISCONNECTED -> getString(R.string.server_status_disconnected)
    }
}
