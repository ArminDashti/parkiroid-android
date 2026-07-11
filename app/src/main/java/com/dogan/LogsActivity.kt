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

        val logsTxt = findViewById<TextView>(R.id.logsTxt)
        val logsScroll = findViewById<ScrollView>(R.id.logsScroll)

        findViewById<Button>(R.id.clearLogsBtn).setOnClickListener {
            AppLogger.clear()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.lines.collect { lines ->
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
}
