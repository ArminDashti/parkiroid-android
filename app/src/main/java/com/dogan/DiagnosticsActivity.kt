package com.dogan

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Diagnostics screen for internet, server API, and WebRTC connectivity tests. */
class DiagnosticsActivity : AppCompatActivity() {
    private val settingsStore by lazy { SettingsStore(this) }
    private lateinit var resultsContainer: LinearLayout
    private lateinit var statusTxt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        findViewById<MaterialToolbar>(R.id.diagnosticsToolbar).setNavigationOnClickListener { finish() }

        resultsContainer = findViewById(R.id.testResultsContainer)
        statusTxt = findViewById(R.id.diagnosticsStatusTxt)

        findViewById<Button>(R.id.testInternetBtn).setOnClickListener { runTest(TestType.INTERNET) }
        findViewById<Button>(R.id.testServerApiBtn).setOnClickListener { runTest(TestType.SERVER_API) }
        findViewById<Button>(R.id.testWebRtcBtn).setOnClickListener { runTest(TestType.WEBRTC) }
        findViewById<Button>(R.id.runAllTestsBtn).setOnClickListener { runTest(TestType.ALL) }
    }

    private enum class TestType { INTERNET, SERVER_API, WEBRTC, ALL }

    private fun runTest(type: TestType) {
        statusTxt.setText(R.string.diagnostics_running)
        resultsContainer.removeAllViews()

        lifecycleScope.launch {
            val settings = settingsStore.settingsFlow.first()
            SessionCredentials.updateFrom(settings)
            val deviceId = DeviceIdentity.resolveDeviceId(this@DiagnosticsActivity)
            val apiClient = DoganApiClient(deviceId = deviceId)
            val tester = ConnectivityTester(apiClient)

            val results = when (type) {
                TestType.INTERNET -> listOf(tester.testInternet())
                TestType.SERVER_API -> listOf(tester.testServerApi(settings.serverBaseUrl))
                TestType.WEBRTC -> listOf(tester.testLiveKit(settings.serverBaseUrl))
                TestType.ALL -> tester.runAll(settings.serverBaseUrl)
            }

            resultsContainer.removeAllViews()
            for (result in results) {
                addResultCard(result)
                AppLogger.info("Diagnostics", "${result.name}: ${if (result.passed) "PASS" else "FAIL"} — ${result.detail}")
            }

            val allPassed = results.all { it.passed }
            statusTxt.text = if (allPassed) {
                getString(R.string.diagnostics_all_passed)
            } else {
                getString(R.string.diagnostics_some_failed)
            }
        }
    }

    private fun addResultCard(result: ConnectivityTester.TestResult) {
        val card = layoutInflater.inflate(R.layout.item_diagnostic_result, resultsContainer, false)
        card.findViewById<TextView>(R.id.testNameTxt).text = result.name
        card.findViewById<TextView>(R.id.testStatusTxt).text =
            if (result.passed) getString(R.string.diagnostics_pass) else getString(R.string.diagnostics_fail)
        card.findViewById<TextView>(R.id.testDetailTxt).text =
            "${result.detail}\nLatency: ${result.latencyMs} ms"
        card.findViewById<View>(R.id.testStatusIndicator).setBackgroundResource(
            if (result.passed) android.R.color.holo_green_dark else android.R.color.holo_red_dark,
        )
        resultsContainer.addView(card)
    }
}
