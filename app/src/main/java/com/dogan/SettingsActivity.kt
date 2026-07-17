package com.dogan

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Focused settings screen for one section; auto-saves and pushes to server. */
class SettingsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private val diskUsageManager by lazy { DiskUsageManager(this) }

    private lateinit var section: String
    private var suppressAutoSave = false
    private var currentFps: Float = SettingsStore.DEFAULT_FPS
    private var fpsValueTxt: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        section = intent.getStringExtra(EXTRA_SECTION) ?: SECTION_GENERAL

        findViewById<MaterialToolbar>(R.id.settingsToolbar).apply {
            title = sectionTitle(section)
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        val content = findViewById<FrameLayout>(R.id.settingsContent)
        val layoutId = when (section) {
            SECTION_CONNECTIVITY -> R.layout.section_connectivity
            SECTION_RECORDING -> R.layout.section_recording
            SECTION_COPILOT -> R.layout.section_copilot
            SECTION_SPOTTER -> R.layout.section_spotter
            SECTION_WATCHMAN -> R.layout.section_watchman
            else -> R.layout.section_general
        }
        LayoutInflater.from(this).inflate(layoutId, content, true)

        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            suppressAutoSave = true
            bindSection(settings)
            suppressAutoSave = false
        }
    }

    private fun sectionTitle(section: String): String = when (section) {
        SECTION_CONNECTIVITY -> getString(R.string.settings_section_connectivity)
        SECTION_RECORDING -> getString(R.string.settings_section_recording)
        SECTION_COPILOT -> getString(R.string.settings_section_copilot)
        SECTION_SPOTTER -> getString(R.string.settings_section_spotter)
        SECTION_WATCHMAN -> getString(R.string.settings_section_watchman)
        else -> getString(R.string.settings_title)
    }

    private fun bindSection(settings: AppSettings) {
        when (section) {
            SECTION_CONNECTIVITY -> bindConnectivity(settings)
            SECTION_RECORDING -> bindRecording(settings)
            SECTION_COPILOT -> bindCopilot(settings)
            SECTION_SPOTTER -> bindSpotter(settings)
            SECTION_WATCHMAN -> bindWatchman(settings)
            else -> bindGeneral(settings)
        }
    }

    private fun bindConnectivity(settings: AppSettings) {
        val connectionBtn = findViewById<MaterialButton>(R.id.connectionBtn)
        val pingStatusTxt = findViewById<TextView>(R.id.pingStatusTxt)
        val apiStatusTxt = findViewById<TextView>(R.id.apiStatusTxt)
        val liveKitStatusTxt = findViewById<TextView>(R.id.liveKitStatusTxt)
        val connectionErrorTxt = findViewById<TextView>(R.id.connectionErrorTxt)
        val username = findViewById<TextInputEditText>(R.id.usernameInput)
        val password = findViewById<TextInputEditText>(R.id.passwordInput)
        val apiEndpoint = findViewById<TextInputEditText>(R.id.apiEndpointInput)
        val apiPort = findViewById<TextInputEditText>(R.id.apiPortInput)
        val streamEndpoint = findViewById<TextInputEditText>(R.id.streamEndpointInput)
        val streamPort = findViewById<TextInputEditText>(R.id.streamPortInput)
        val telemetryInterval = findViewById<TextInputEditText>(R.id.telemetryIntervalInput)

        username.setText(settings.username)
        password.setText(settings.password)
        apiEndpoint.setText(settings.apiEndpoint)
        apiPort.setText(settings.apiPort.toString())
        streamEndpoint.setText(settings.streamEndpoint)
        streamPort.setText(settings.streamPort.toString())
        telemetryInterval.setText(settings.telemetryIntervalSec.toString())

        fun refreshStatusUi() {
            updateConnectionButton(connectionBtn, ServerConnectionManager.status.value)
            updateConnectivityStatusPanel(pingStatusTxt, apiStatusTxt, liveKitStatusTxt, connectionErrorTxt)
        }

        connectionBtn.setOnClickListener { toggleConnection() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServerConnectionManager.status.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PingLatencyCache.latencyMs.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LiveKitStatusCache.streaming.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LiveKitStatusCache.activeSessionCount.collect { refreshStatusUi() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (ServerConnectionManager.isConnected()) {
                        val current = settingsStore.settingsFlow.first()
                        ServerConnectionManager.refreshPing(this@SettingsActivity, current)
                        ServerConnectionManager.refreshLiveKitConnections(this@SettingsActivity, current)
                    }
                    delay(30_000L)
                }
            }
        }
        refreshStatusUi()

        bindText(username) { text ->
            persist(mapOf("username" to text)) { it.copy(username = text) }
        }
        bindText(password) { text ->
            persist(mapOf("password" to text)) { it.copy(password = text) }
        }
        bindText(apiEndpoint) { text ->
            persist(mapOf("api_endpoint" to text)) { it.copy(apiEndpoint = text) }
        }
        bindText(apiPort) { text ->
            val port = text.toIntOrNull() ?: SettingsStore.DEFAULT_API_PORT
            persist(mapOf("api_port" to port)) { it.copy(apiPort = port) }
        }
        bindText(streamEndpoint) { text ->
            persist(mapOf("stream_endpoint" to text)) { it.copy(streamEndpoint = text) }
        }
        bindText(streamPort) { text ->
            val port = text.toIntOrNull() ?: SettingsStore.DEFAULT_STREAM_PORT
            persist(mapOf("stream_port" to port)) { it.copy(streamPort = port) }
        }
        bindText(telemetryInterval) { text ->
            val value = SettingsStore.normalizeTelemetryIntervalSec(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_TELEMETRY_INTERVAL_SEC,
            )
            persist(mapOf("telemetry_interval_sec" to value)) { it.copy(telemetryIntervalSec = value) }
        }
    }

    private fun updateConnectivityStatusPanel(
        pingStatusTxt: TextView,
        apiStatusTxt: TextView,
        liveKitStatusTxt: TextView,
        connectionErrorTxt: TextView,
    ) {
        val latency = PingLatencyCache.latencyMs.value
        pingStatusTxt.text = if (latency >= 0) {
            getString(R.string.ping_status_ms, latency)
        } else {
            getString(R.string.ping_status_unknown)
        }
        val apiConnected = ServerConnectionManager.isConnected()
        apiStatusTxt.text = getString(
            R.string.api_connection_status,
            getString(if (apiConnected) R.string.connected_label else R.string.disconnected_label),
        )
        val liveKitConnected = LiveKitStatusCache.isConnected
        val sessions = LiveKitStatusCache.activeSessionCount.value
        liveKitStatusTxt.text = if (sessions > 0) {
            getString(
                R.string.livekit_connection_status_sessions,
                getString(if (liveKitConnected) R.string.connected_label else R.string.disconnected_label),
                sessions,
            )
        } else {
            getString(
                R.string.livekit_connection_status,
                getString(if (liveKitConnected) R.string.connected_label else R.string.disconnected_label),
            )
        }
        val error = ServerConnectionManager.lastError ?: LiveKitStatusCache.lastError.value
        if (error.isNullOrBlank()) {
            connectionErrorTxt.visibility = View.GONE
            connectionErrorTxt.text = ""
        } else {
            connectionErrorTxt.visibility = View.VISIBLE
            connectionErrorTxt.text = error
        }
    }

    private fun bindRecording(settings: AppSettings) {
        currentFps = settings.recordingFps
        setupFpsStepper(currentFps) { fps ->
            persist(mapOf("recording_fps" to fps)) { it.copy(recordingFps = fps) }
        }
        val chunk = findViewById<TextInputEditText>(R.id.recordingChunkInput)
        val quality = findViewById<AutoCompleteTextView>(R.id.recordingQualityInput)
        val sound = findViewById<SwitchMaterial>(R.id.recordingSoundSwitch)
        val retention = findViewById<TextInputEditText>(R.id.recordingRetentionInput)

        chunk.setText(settings.recordingChunkMinutes.toString())
        setupQualityDropdown(quality, settings.recordingQuality) { q ->
            persist(mapOf("recording_quality" to q.toStoredValue())) { it.copy(recordingQuality = q) }
        }
        sound.isChecked = settings.recordingSoundEnabled
        retention.setText(settings.recordingRetentionHours.toString())

        bindText(chunk) { text ->
            val value = SettingsStore.normalizeVideoChunkMinutes(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_COPILOT_VIDEO_CHUNK_MINUTES,
            )
            persist(mapOf("recording_chunk_minutes" to value)) { it.copy(recordingChunkMinutes = value) }
        }
        sound.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("recording_sound_enabled" to checked)) { it.copy(recordingSoundEnabled = checked) }
        }
        bindText(retention) { text ->
            val value = SettingsStore.normalizeRecordingRetentionHours(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_RECORDING_RETENTION_HOURS,
            )
            persist(mapOf("recording_retention_hours" to value)) { it.copy(recordingRetentionHours = value) }
        }
    }

    private fun bindCopilot(settings: AppSettings) {
        val mode = settings.copilotSettings
        currentFps = mode.fps
        setupFpsStepper(currentFps) { fps ->
            persistMode("copilot", mapOf("copilot_fps" to fps)) { m -> m.copy(fps = fps) }
        }
        val aiModel = findViewById<AutoCompleteTextView>(R.id.aiModelInput)
        val alert = findViewById<SwitchMaterial>(R.id.copilotAlertSwitch)
        val distance = findViewById<SwitchMaterial>(R.id.copilotDistanceSwitch)
        val history = findViewById<TextInputEditText>(R.id.historyRetentionInput)
        val alertDuration = findViewById<AutoCompleteTextView>(R.id.alertDurationInput)
        val frameQuality = findViewById<AutoCompleteTextView>(R.id.frameQualityInput)
        val confidence = findViewById<TextInputEditText>(R.id.minConfidenceInput)

        setupDropdown(aiModel, AiModel.all.map { it.displayName })
        aiModel.setText(settings.aiModel.displayName, false)
        alert.isChecked = settings.copilotAlertsEnabled
        distance.isChecked = settings.copilotDistanceControlEnabled
        history.setText(mode.historyRetentionFrames.toString())
        setupDropdown(alertDuration, AlertDuration.all.map { it.displayName })
        alertDuration.setText(settings.alertDuration.displayName, false)
        setupQualityDropdown(frameQuality, mode.frameQuality) { q ->
            persistMode("copilot", mapOf("copilot_frame_quality" to q.toStoredValue())) { m -> m.copy(frameQuality = q) }
        }
        confidence.setText(mode.minConfidence.toString())

        aiModel.setOnItemClickListener { _, _, position, _ ->
            val model = AiModel.all.getOrElse(position) { AiModel.YOLO26_NANO }
            persist(mapOf("ai_model" to model.toStoredValue())) { it.copy(aiModel = model) }
        }
        alert.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("copilot_alerts_enabled" to checked)) { it.copy(copilotAlertsEnabled = checked) }
        }
        distance.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("copilot_distance_control_enabled" to checked)) {
                it.copy(copilotDistanceControlEnabled = checked)
            }
        }
        bindText(history) { text ->
            val value = SettingsStore.normalizeHistoryFrames(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_HISTORY_RETENTION_FRAMES,
            )
            persistMode("copilot", mapOf("copilot_history_retention_frames" to value)) {
                it.copy(historyRetentionFrames = value)
            }
        }
        alertDuration.setOnItemClickListener { _, _, position, _ ->
            val duration = AlertDuration.all.getOrElse(position) { AlertDuration.THREE }
            persist(mapOf("alert_duration" to duration.toStoredValue())) { it.copy(alertDuration = duration) }
        }
        bindText(confidence) { text ->
            val value = SettingsStore.normalizeConfidence(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_MIN_CONFIDENCE,
            )
            persistMode("copilot", mapOf("copilot_min_confidence" to value)) { it.copy(minConfidence = value) }
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            openHistory(OperatingMode.COPILOT)
        }
    }

    private fun bindSpotter(settings: AppSettings) {
        val mode = settings.spotterSettings
        currentFps = mode.fps
        setupFpsStepper(currentFps) { fps ->
            persistMode("spotter", mapOf("spotter_fps" to fps)) { m -> m.copy(fps = fps) }
        }
        val aiModel = findViewById<AutoCompleteTextView>(R.id.aiModelInput)
        val history = findViewById<TextInputEditText>(R.id.historyRetentionInput)
        val frameQuality = findViewById<AutoCompleteTextView>(R.id.frameQualityInput)
        val confidence = findViewById<TextInputEditText>(R.id.minConfidenceInput)
        setupDropdown(aiModel, AiModel.all.map { it.displayName })
        aiModel.setText(settings.aiModel.displayName, false)
        history.setText(mode.historyRetentionFrames.toString())
        setupQualityDropdown(frameQuality, mode.frameQuality) { q ->
            persistMode("spotter", mapOf("spotter_frame_quality" to q.toStoredValue())) { m -> m.copy(frameQuality = q) }
        }
        confidence.setText(mode.minConfidence.toString())
        aiModel.setOnItemClickListener { _, _, position, _ ->
            val model = AiModel.all.getOrElse(position) { AiModel.YOLO26_NANO }
            persist(mapOf("ai_model" to model.toStoredValue())) { it.copy(aiModel = model) }
        }
        bindText(history) { text ->
            val value = SettingsStore.normalizeHistoryFrames(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_HISTORY_RETENTION_FRAMES,
            )
            persistMode("spotter", mapOf("spotter_history_retention_frames" to value)) {
                it.copy(historyRetentionFrames = value)
            }
        }
        bindText(confidence) { text ->
            val value = SettingsStore.normalizeConfidence(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_MIN_CONFIDENCE,
            )
            persistMode("spotter", mapOf("spotter_min_confidence" to value)) { it.copy(minConfidence = value) }
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            openHistory(OperatingMode.SPOTTER)
        }
    }

    private fun bindWatchman(settings: AppSettings) {
        val mode = settings.watcherSettings
        currentFps = mode.fps
        setupFpsStepper(currentFps) { fps ->
            persistMode("watcher", mapOf("watcher_fps" to fps)) { m -> m.copy(fps = fps) }
        }
        val aiModel = findViewById<AutoCompleteTextView>(R.id.aiModelInput)
        val history = findViewById<TextInputEditText>(R.id.historyRetentionInput)
        val jolt = findViewById<AutoCompleteTextView>(R.id.joltSensitivityInput)
        val sound = findViewById<AutoCompleteTextView>(R.id.soundSensitivityInput)
        val frameQuality = findViewById<AutoCompleteTextView>(R.id.frameQualityInput)
        val confidence = findViewById<TextInputEditText>(R.id.minConfidenceInput)

        setupDropdown(aiModel, AiModel.all.map { it.displayName })
        aiModel.setText(settings.aiModel.displayName, false)
        history.setText(mode.historyRetentionFrames.toString())
        setupDropdown(jolt, SensitivityLevel.all.map { it.displayName })
        jolt.setText(settings.joltSensitivity.displayName, false)
        setupDropdown(sound, SensitivityLevel.all.map { it.displayName })
        sound.setText(settings.soundSensitivity.displayName, false)
        setupQualityDropdown(frameQuality, mode.frameQuality) { q ->
            persistMode("watcher", mapOf("watcher_frame_quality" to q.toStoredValue())) { m -> m.copy(frameQuality = q) }
        }
        confidence.setText(mode.minConfidence.toString())

        aiModel.setOnItemClickListener { _, _, position, _ ->
            val model = AiModel.all.getOrElse(position) { AiModel.YOLO26_NANO }
            persist(mapOf("ai_model" to model.toStoredValue())) { it.copy(aiModel = model) }
        }
        bindText(history) { text ->
            val value = SettingsStore.normalizeHistoryFrames(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_HISTORY_RETENTION_FRAMES,
            )
            persistMode("watcher", mapOf("watcher_history_retention_frames" to value)) {
                it.copy(historyRetentionFrames = value)
            }
        }
        jolt.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            persist(mapOf("jolt_sensitivity" to level.toStoredValue())) { it.copy(joltSensitivity = level) }
        }
        sound.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            persist(mapOf("sound_sensitivity" to level.toStoredValue())) { it.copy(soundSensitivity = level) }
        }
        bindText(confidence) { text ->
            val value = SettingsStore.normalizeConfidence(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_MIN_CONFIDENCE,
            )
            persistMode("watcher", mapOf("watcher_min_confidence" to value)) { it.copy(minConfidence = value) }
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            openHistory(OperatingMode.WATCHER)
        }
    }

    private fun bindGeneral(settings: AppSettings) {
        val sync = findViewById<TextInputEditText>(R.id.syncIntervalInput)
        val camera = findViewById<AutoCompleteTextView>(R.id.cameraInput)
        val keepAlive = findViewById<TextInputEditText>(R.id.screenOnIntervalInput)
        val logRetention = findViewById<TextInputEditText>(R.id.logRetentionInput)
        val boxes = findViewById<SwitchMaterial>(R.id.showBoundingBoxesSwitch)

        sync.setText(settings.settingsSyncIntervalSec.toString())
        setupDropdown(
            camera,
            listOf(getString(R.string.camera_rear), getString(R.string.camera_front), getString(R.string.camera_both)),
        )
        camera.setText(cameraLabel(settings.activeCamera), false)
        keepAlive.setText(settings.screenOnIntervalMin.toString())
        logRetention.setText(settings.logRetentionDays.toString())
        boxes.isChecked = settings.showBoundingBoxes

        bindText(sync) { text ->
            val value = SettingsStore.normalizeSettingsSyncIntervalSec(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SETTINGS_SYNC_INTERVAL_SEC,
            )
            persist(mapOf("settings_sync_interval_sec" to value)) { it.copy(settingsSyncIntervalSec = value) }
        }
        camera.setOnItemClickListener { _, _, position, _ ->
            val facing = when (position) {
                1 -> CameraFacing.FRONT
                2 -> CameraFacing.BOTH
                else -> CameraFacing.REAR
            }
            persist(mapOf("active_camera" to facing.toStoredValue())) { it.copy(activeCamera = facing) }
        }
        bindText(keepAlive) { text ->
            val value = SettingsStore.normalizeScreenOnIntervalMin(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SCREEN_ON_INTERVAL_MIN,
            )
            persist(mapOf("screen_on_interval_min" to value)) { it.copy(screenOnIntervalMin = value) }
        }
        bindText(logRetention) { text ->
            val value = SettingsStore.normalizeLogRetentionDays(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_LOG_RETENTION_DAYS,
            )
            persist(mapOf("log_retention_days" to value)) { it.copy(logRetentionDays = value) }
        }
        boxes.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("show_bounding_boxes" to checked)) { it.copy(showBoundingBoxes = checked) }
        }

        refreshDiskUsage()
        findViewById<MaterialButton>(R.id.flushSpotterBtn).setOnClickListener {
            confirmFlush(OperatingMode.SPOTTER.displayName) {
                diskUsageManager.flushSpotter()
                FrameHistoryStore(this).flush(OperatingMode.SPOTTER)
                refreshDiskUsage()
            }
        }
        findViewById<MaterialButton>(R.id.flushWatcherBtn).setOnClickListener {
            confirmFlush(OperatingMode.WATCHER.displayName) {
                diskUsageManager.flushWatcher()
                FrameHistoryStore(this).flush(OperatingMode.WATCHER)
                refreshDiskUsage()
            }
        }
        findViewById<MaterialButton>(R.id.flushCopilotBtn).setOnClickListener {
            confirmFlush(OperatingMode.COPILOT.displayName) {
                diskUsageManager.flushCopilot()
                FrameHistoryStore(this).flush(OperatingMode.COPILOT)
                refreshDiskUsage()
            }
        }
        findViewById<MaterialButton>(R.id.flushLogsBtn).setOnClickListener {
            confirmFlush("Logs") {
                diskUsageManager.flushLogs()
                refreshDiskUsage()
            }
        }
    }

    private fun setupFpsStepper(initial: Float, onChange: (Float) -> Unit) {
        val root = findViewById<View>(R.id.fpsStepper)
        fpsValueTxt = root.findViewById(R.id.fpsValueTxt)
        currentFps = SettingsStore.normalizeFps(initial)
        fpsValueTxt?.text = SettingsStore.formatFps(currentFps)
        root.findViewById<ImageButton>(R.id.fpsMinusBtn).setOnClickListener {
            currentFps = SettingsStore.stepFpsDown(currentFps)
            fpsValueTxt?.text = SettingsStore.formatFps(currentFps)
            onChange(currentFps)
        }
        root.findViewById<ImageButton>(R.id.fpsPlusBtn).setOnClickListener {
            currentFps = SettingsStore.stepFpsUp(currentFps)
            fpsValueTxt?.text = SettingsStore.formatFps(currentFps)
            onChange(currentFps)
        }
    }

    private fun setupQualityDropdown(
        view: AutoCompleteTextView,
        selected: ImageQuality,
        onChange: (ImageQuality) -> Unit,
    ) {
        val labels = ImageQuality.uiOptions.map { it.label }
        setupDropdown(view, labels)
        view.setText(selected.label, false)
        view.setOnItemClickListener { _, _, position, _ ->
            onChange(ImageQuality.uiOptions.getOrElse(position) { ImageQuality.BALANCED })
        }
    }

    private fun setupDropdown(view: AutoCompleteTextView, labels: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
    }

    private var textJob: Job? = null

    private fun bindText(edit: TextInputEditText, onCommit: (String) -> Unit) {
        edit.doAfterTextChanged { editable ->
            if (suppressAutoSave) return@doAfterTextChanged
            textJob?.cancel()
            textJob = lifecycleScope.launch {
                delay(400)
                onCommit(editable?.toString().orEmpty())
            }
        }
    }

    private fun persist(keys: Map<String, Any?>, mutate: (AppSettings) -> AppSettings) {
        if (suppressAutoSave) return
        lifecycleScope.launch {
            SettingsPublisher.saveAndPush(this@SettingsActivity, mutate, keys)
        }
    }

    private fun persistMode(
        modePrefix: String,
        keys: Map<String, Any?>,
        mutateMode: (ModeSettings) -> ModeSettings,
    ) {
        persist(keys) { settings ->
            when (modePrefix) {
                "spotter" -> settings.copy(spotterSettings = mutateMode(settings.spotterSettings))
                "copilot" -> settings.copy(copilotSettings = mutateMode(settings.copilotSettings))
                else -> settings.copy(watcherSettings = mutateMode(settings.watcherSettings))
            }
        }
    }

    private fun openHistory(mode: OperatingMode) {
        startActivity(
            Intent(this, HistoryFramesActivity::class.java)
                .putExtra(HistoryFramesActivity.EXTRA_MODE, mode.toStoredValue()),
        )
    }

    private fun toggleConnection() {
        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            if (ServerConnectionManager.isConnected()) {
                ServerConnectionManager.disconnect()
                return@launch
            }
            val connected = withContext(Dispatchers.IO) {
                ServerConnectionManager.connect(this@SettingsActivity, settings)
            }
            val error = ServerConnectionManager.lastError
            if (connected) {
                val message = if (error.isNullOrBlank()) {
                    getString(R.string.server_connected)
                } else {
                    getString(R.string.server_connected_with_warning, error)
                }
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    error ?: getString(R.string.server_connect_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun updateConnectionButton(button: MaterialButton, status: ConnectionStatus) {
        val connected = status == ConnectionStatus.CONNECTED
        button.text = getString(if (connected) R.string.connected_label else R.string.disconnected_label)
        button.setBackgroundColor(
            Color.parseColor(if (connected) "#2E7D32" else "#C62828"),
        )
    }

    private fun confirmFlush(label: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.flush_confirm, label))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onConfirm()
                Toast.makeText(this, getString(R.string.storage_flushed, label), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshDiskUsage() {
        val usage = diskUsageManager.summarize()
        findViewById<TextView>(R.id.spotterUsageTxt).text =
            getString(R.string.disk_usage_spotter, DiskUsageManager.formatBytes(usage.spotterBytes))
        findViewById<TextView>(R.id.watcherUsageTxt).text =
            getString(R.string.disk_usage_watchman, DiskUsageManager.formatBytes(usage.watcherBytes))
        findViewById<TextView>(R.id.copilotUsageTxt).text =
            getString(R.string.disk_usage_copilot, DiskUsageManager.formatBytes(usage.copilotBytes))
        findViewById<TextView>(R.id.logsUsageTxt).text =
            getString(R.string.disk_usage_logs, DiskUsageManager.formatBytes(usage.logsBytes))
    }

    private fun cameraLabel(facing: CameraFacing): String = when (facing) {
        CameraFacing.REAR -> getString(R.string.camera_rear)
        CameraFacing.FRONT -> getString(R.string.camera_front)
        CameraFacing.BOTH -> getString(R.string.camera_both)
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_CONNECTIVITY = "connectivity"
        const val SECTION_RECORDING = "recording"
        const val SECTION_COPILOT = "copilot"
        const val SECTION_SPOTTER = "spotter"
        const val SECTION_WATCHMAN = "watchman"
        const val SECTION_GENERAL = "general"
    }
}
