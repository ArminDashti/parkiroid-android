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
        val apiUrl = findViewById<TextInputEditText>(R.id.apiUrlInput)
        val streamUrl = findViewById<TextInputEditText>(R.id.streamUrlInput)

        username.setText(settings.username)
        password.setText(settings.password)
        apiUrl.setText(formatHostPort(settings.apiEndpoint, settings.apiPort))
        streamUrl.setText(formatHostPort(settings.streamEndpoint, settings.streamPort))

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
        bindText(apiUrl) { text ->
            val parsed = parseHostPort(text, SettingsStore.DEFAULT_API_ENDPOINT, SettingsStore.DEFAULT_API_PORT)
            persist(
                mapOf("api_endpoint" to parsed.host, "api_port" to parsed.port),
            ) { it.copy(apiEndpoint = parsed.host, apiPort = parsed.port) }
        }
        bindText(streamUrl) { text ->
            val parsed = parseHostPort(text, SettingsStore.DEFAULT_STREAM_ENDPOINT, SettingsStore.DEFAULT_STREAM_PORT)
            persist(
                mapOf("stream_endpoint" to parsed.host, "stream_port" to parsed.port),
            ) { it.copy(streamEndpoint = parsed.host, streamPort = parsed.port) }
        }

        bindLogRetention(settings)
        bindLogsButton(LogSection.CONNECTIVITY)
        findViewById<MaterialButton>(R.id.flushHistoryBtn).setOnClickListener {
            confirmFlush(getString(R.string.flush_history)) {
                FrameHistoryStore(this).flush()
                diskUsageManager.flushSpotter()
                diskUsageManager.flushWatcher()
                diskUsageManager.flushCopilot()
            }
        }
        bindFlushLogsButton()
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
        val enabled = findViewById<SwitchMaterial>(R.id.recordingEnabledSwitch)
        val sound = findViewById<SwitchMaterial>(R.id.recordingSoundSwitch)

        chunk.setText(settings.recordingChunkMinutes.toString())
        setupQualityDropdown(quality, settings.recordingQuality) { q ->
            persist(mapOf("recording_quality" to q.toStoredValue())) { it.copy(recordingQuality = q) }
        }
        enabled.isChecked = settings.recordingEnabled
        sound.isChecked = settings.recordingSoundEnabled

        bindText(chunk) { text ->
            val value = SettingsStore.normalizeVideoChunkMinutes(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_COPILOT_VIDEO_CHUNK_MINUTES,
            )
            persist(mapOf("recording_chunk_minutes" to value)) { it.copy(recordingChunkMinutes = value) }
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("recording_enabled" to checked)) { it.copy(recordingEnabled = checked) }
        }
        sound.setOnCheckedChangeListener { _, checked ->
            if (suppressAutoSave) return@setOnCheckedChangeListener
            persist(mapOf("recording_sound_enabled" to checked)) { it.copy(recordingSoundEnabled = checked) }
        }

        bindLogRetention(settings)
        findViewById<MaterialButton>(R.id.previewBtn).setOnClickListener {
            openPreview(preferMode = null, showSensors = false)
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            lifecycleScope.launch {
                val mode = settingsStore.settingsFlow.first().operatingMode
                openHistory(if (mode == OperatingMode.OFF) OperatingMode.COPILOT else mode)
            }
        }
        bindLogsButton(LogSection.RECORDING)
        findViewById<MaterialButton>(R.id.flushHistoryBtn).setOnClickListener {
            confirmFlush(getString(R.string.flush_history)) {
                FrameHistoryStore(this).flush()
            }
        }
        bindFlushLogsButton()
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
        val frameQuality = findViewById<AutoCompleteTextView>(R.id.frameQualityInput)
        val confidence = findViewById<TextInputEditText>(R.id.minConfidenceInput)
        val keepFrames = findViewById<TextInputEditText>(R.id.keepFramesInput)

        setupDropdown(aiModel, AiModel.all.map { it.displayName })
        aiModel.setText(settings.aiModel.displayName, false)
        setupQualityDropdown(frameQuality, mode.frameQuality) { q ->
            persistMode("spotter", mapOf("spotter_frame_quality" to q.toStoredValue())) { m -> m.copy(frameQuality = q) }
        }
        confidence.setText(mode.minConfidence.toString())
        keepFrames.setText(mode.imageRetentionHours.toString())

        aiModel.setOnItemClickListener { _, _, position, _ ->
            val model = AiModel.all.getOrElse(position) { AiModel.YOLO26_NANO }
            persist(mapOf("ai_model" to model.toStoredValue())) { it.copy(aiModel = model) }
        }
        bindText(confidence) { text ->
            val value = SettingsStore.normalizeConfidence(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_MIN_CONFIDENCE,
            )
            persistMode("spotter", mapOf("spotter_min_confidence" to value)) { it.copy(minConfidence = value) }
        }
        bindText(keepFrames) { text ->
            val value = SettingsStore.normalizeRetentionHours(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_MEDIA_RETENTION_HOURS,
            )
            persistMode("spotter", mapOf("spotter_image_retention_hours" to value)) {
                it.copy(imageRetentionHours = value)
            }
        }

        bindLogRetention(settings)
        findViewById<MaterialButton>(R.id.previewBtn).setOnClickListener {
            openPreview(preferMode = OperatingMode.SPOTTER, showSensors = false)
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            openHistory(OperatingMode.SPOTTER)
        }
        bindLogsButton(LogSection.SPOTTER)
        findViewById<MaterialButton>(R.id.flushHistoryBtn).setOnClickListener {
            confirmFlush(OperatingMode.SPOTTER.displayName) {
                diskUsageManager.flushSpotter()
                FrameHistoryStore(this).flush(OperatingMode.SPOTTER)
            }
        }
        bindFlushLogsButton()
    }

    private fun bindWatchman(settings: AppSettings) {
        val mode = settings.watcherSettings
        currentFps = mode.fps
        setupFpsStepper(currentFps) { fps ->
            persistMode("watcher", mapOf("watcher_fps" to fps)) { m -> m.copy(fps = fps) }
        }
        val aiModel = findViewById<AutoCompleteTextView>(R.id.aiModelInput)
        val jolt = findViewById<AutoCompleteTextView>(R.id.joltSensitivityInput)
        val sound = findViewById<AutoCompleteTextView>(R.id.soundSensitivityInput)
        val customJoltLayout = findViewById<View>(R.id.customJoltScaleLayout)
        val customSoundLayout = findViewById<View>(R.id.customSoundThresholdLayout)
        val customJoltInput = findViewById<TextInputEditText>(R.id.customJoltScaleInput)
        val customSoundInput = findViewById<TextInputEditText>(R.id.customSoundThresholdInput)
        val frameQuality = findViewById<AutoCompleteTextView>(R.id.frameQualityInput)
        val confidence = findViewById<TextInputEditText>(R.id.minConfidenceInput)
        val keepFrames = findViewById<TextInputEditText>(R.id.keepFramesInput)

        setupDropdown(aiModel, AiModel.all.map { it.displayName })
        aiModel.setText(settings.aiModel.displayName, false)
        setupDropdown(jolt, SensitivityLevel.all.map { it.displayName })
        jolt.setText(settings.joltSensitivity.displayName, false)
        setupDropdown(sound, SensitivityLevel.all.map { it.displayName })
        sound.setText(settings.soundSensitivity.displayName, false)
        customJoltInput.setText(settings.customJoltScale.toString())
        customSoundInput.setText(settings.customSoundThreshold.toString())
        fun refreshCustomVisibility() {
            customJoltLayout.visibility =
                if (settings.joltSensitivity == SensitivityLevel.CUSTOM ||
                    jolt.text.toString() == SensitivityLevel.CUSTOM.displayName
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            customSoundLayout.visibility =
                if (settings.soundSensitivity == SensitivityLevel.CUSTOM ||
                    sound.text.toString() == SensitivityLevel.CUSTOM.displayName
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
        refreshCustomVisibility()
        setupQualityDropdown(frameQuality, mode.frameQuality) { q ->
            persistMode("watcher", mapOf("watcher_frame_quality" to q.toStoredValue())) { m -> m.copy(frameQuality = q) }
        }
        confidence.setText(mode.minConfidence.toString())
        keepFrames.setText(mode.imageRetentionHours.toString())

        aiModel.setOnItemClickListener { _, _, position, _ ->
            val model = AiModel.all.getOrElse(position) { AiModel.YOLO26_NANO }
            persist(mapOf("ai_model" to model.toStoredValue())) { it.copy(aiModel = model) }
        }
        jolt.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            customJoltLayout.visibility = if (level == SensitivityLevel.CUSTOM) View.VISIBLE else View.GONE
            persist(mapOf("jolt_sensitivity" to level.toStoredValue())) { it.copy(joltSensitivity = level) }
        }
        sound.setOnItemClickListener { _, _, position, _ ->
            val level = SensitivityLevel.all.getOrElse(position) { SensitivityLevel.MEDIUM }
            customSoundLayout.visibility = if (level == SensitivityLevel.CUSTOM) View.VISIBLE else View.GONE
            persist(mapOf("sound_sensitivity" to level.toStoredValue())) { it.copy(soundSensitivity = level) }
        }
        bindText(customJoltInput) { text ->
            val value = SettingsStore.normalizeCustomJoltScale(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_CUSTOM_JOLT_SCALE,
            )
            persist(mapOf("custom_jolt_scale" to value)) { it.copy(customJoltScale = value) }
        }
        bindText(customSoundInput) { text ->
            val value = SettingsStore.normalizeCustomSoundThreshold(
                text.toDoubleOrNull() ?: SettingsStore.DEFAULT_CUSTOM_SOUND_THRESHOLD,
            )
            persist(mapOf("custom_sound_threshold" to value)) { it.copy(customSoundThreshold = value) }
        }
        bindText(confidence) { text ->
            val value = SettingsStore.normalizeConfidence(
                text.toFloatOrNull() ?: SettingsStore.DEFAULT_MIN_CONFIDENCE,
            )
            persistMode("watcher", mapOf("watcher_min_confidence" to value)) { it.copy(minConfidence = value) }
        }
        bindText(keepFrames) { text ->
            val value = SettingsStore.normalizeRetentionHours(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_MEDIA_RETENTION_HOURS,
            )
            persistMode("watcher", mapOf("watcher_image_retention_hours" to value)) {
                it.copy(imageRetentionHours = value)
            }
        }

        bindLogRetention(settings)
        findViewById<MaterialButton>(R.id.previewBtn).setOnClickListener {
            openPreview(preferMode = OperatingMode.WATCHER, showSensors = true)
        }
        findViewById<MaterialButton>(R.id.historyFramesBtn).setOnClickListener {
            openHistory(OperatingMode.WATCHER)
        }
        bindLogsButton(LogSection.WATCHMAN)
        findViewById<MaterialButton>(R.id.flushHistoryBtn).setOnClickListener {
            confirmFlush(OperatingMode.WATCHER.displayName) {
                diskUsageManager.flushWatcher()
                FrameHistoryStore(this).flush(OperatingMode.WATCHER)
            }
        }
        bindFlushLogsButton()
    }

    private fun bindGeneral(settings: AppSettings) {
        val sync = findViewById<TextInputEditText>(R.id.syncIntervalInput)
        val keepAlive = findViewById<TextInputEditText>(R.id.screenOnIntervalInput)

        sync.setText(settings.settingsSyncIntervalSec.toString())
        keepAlive.setText(settings.screenOnIntervalMin.toString())

        bindText(sync) { text ->
            val value = SettingsStore.normalizeSettingsSyncIntervalSec(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SETTINGS_SYNC_INTERVAL_SEC,
            )
            persist(mapOf("settings_sync_interval_sec" to value)) { it.copy(settingsSyncIntervalSec = value) }
        }
        bindText(keepAlive) { text ->
            val value = SettingsStore.normalizeScreenOnIntervalMin(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_SCREEN_ON_INTERVAL_MIN,
            )
            persist(mapOf("screen_on_interval_min" to value)) { it.copy(screenOnIntervalMin = value) }
        }

        bindLogRetention(settings)
        bindLogsButton()
    }

    private fun bindLogRetention(settings: AppSettings) {
        val logRetention = findViewById<TextInputEditText>(R.id.logRetentionInput) ?: return
        logRetention.setText(settings.logRetentionDays.toString())
        bindText(logRetention) { text ->
            val value = SettingsStore.normalizeLogRetentionDays(
                text.toIntOrNull() ?: SettingsStore.DEFAULT_LOG_RETENTION_DAYS,
            )
            persist(mapOf("log_retention_days" to value)) { it.copy(logRetentionDays = value) }
        }
    }

    private fun bindLogsButton(section: LogSection? = null) {
        findViewById<MaterialButton>(R.id.logsBtn)?.setOnClickListener {
            val intent = Intent(this, LogsActivity::class.java)
            if (section != null) {
                intent.putExtra(LogsActivity.EXTRA_LOG_SECTION, section.storedValue)
            }
            startActivity(intent)
        }
    }

    private fun bindFlushLogsButton() {
        findViewById<MaterialButton>(R.id.flushLogsBtn)?.setOnClickListener {
            confirmFlush("Logs") {
                diskUsageManager.flushLogs()
            }
        }
    }

    private fun openPreview(preferMode: OperatingMode?, showSensors: Boolean) {
        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            if (settings.operatingMode == OperatingMode.OFF) {
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.preview_requires_active_mode,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            // preferMode is only used for HUD defaults; never auto-activates a mode.
            val showSensorHud = showSensors ||
                settings.operatingMode == OperatingMode.WATCHER ||
                preferMode == OperatingMode.WATCHER
            startForegroundService(
                Intent(this@SettingsActivity, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                },
            )
            startActivity(
                Intent(this@SettingsActivity, CameraActivity::class.java)
                    .putExtra(CameraActivity.EXTRA_SHOW_SENSORS, showSensorHud),
            )
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

    private fun formatHostPort(host: String, port: Int): String = "$host:$port"

    private fun parseHostPort(raw: String, defaultHost: String, defaultPort: Int): HostPort {
        val trimmed = raw.trim().removePrefix("http://").removePrefix("https://")
        if (trimmed.isEmpty()) return HostPort(defaultHost, defaultPort)
        val slash = trimmed.indexOf('/')
        val hostPortPart = if (slash >= 0) trimmed.substring(0, slash) else trimmed
        val colon = hostPortPart.lastIndexOf(':')
        if (colon <= 0) {
            return HostPort(hostPortPart.ifBlank { defaultHost }, defaultPort)
        }
        val host = hostPortPart.substring(0, colon).ifBlank { defaultHost }
        val port = hostPortPart.substring(colon + 1).toIntOrNull() ?: defaultPort
        return HostPort(host, port)
    }

    private data class HostPort(val host: String, val port: Int)

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
