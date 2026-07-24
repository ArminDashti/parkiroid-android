package com.dogan

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/** Scrollable view of recent in-app log lines. */
class LogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        findViewById<MaterialToolbar>(R.id.logsToolbar).setNavigationOnClickListener { finish() }

        val filterSection = intent.getStringExtra(EXTRA_LOG_SECTION)
            ?.let { LogSection.fromStoredValue(it) }

        val logsTxt = findViewById<TextView>(R.id.logsTxt)
        val logsScroll = findViewById<ScrollView>(R.id.logsScroll)

        findViewById<Button>(R.id.clearLogsBtn).setOnClickListener {
            AppLogger.clear()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.entries.collect { entries ->
                    val lines = if (filterSection == null) {
                        entries.map { it.displayLine }
                    } else {
                        entries.filter { it.section == filterSection }.map { it.displayLine }
                    }
                    logsTxt.text = if (lines.isEmpty()) {
                        getString(R.string.logs_empty)
                    } else {
                        lines.joinToString("\n")
                    }
                    logsScroll.post { logsScroll.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOG_SECTION = "log_section"
    }
}
