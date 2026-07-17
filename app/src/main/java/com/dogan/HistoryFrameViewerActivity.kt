package com.dogan

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen history frame viewer with previous/next navigation. */
class HistoryFrameViewerActivity : AppCompatActivity() {
    private val formatter = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US)
    private var frames: List<FrameHistoryStore.HistoryFrame> = emptyList()
    private var index: Int = 0

    private lateinit var timestamp: TextView
    private lateinit var image: ImageView
    private lateinit var overlay: DetectionOverlayView
    private lateinit var prevBtn: MaterialButton
    private lateinit var nextBtn: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_frame_viewer)

        findViewById<MaterialToolbar>(R.id.viewerToolbar).apply {
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        timestamp = findViewById(R.id.viewerTimestamp)
        image = findViewById(R.id.viewerImage)
        overlay = findViewById(R.id.viewerOverlay)
        prevBtn = findViewById(R.id.viewerPrevBtn)
        nextBtn = findViewById(R.id.viewerNextBtn)

        val mode = OperatingMode.fromStoredValue(intent.getStringExtra(EXTRA_MODE))
        frames = FrameHistoryStore(this).list(mode)
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, (frames.size - 1).coerceAtLeast(0))

        prevBtn.setOnClickListener {
            if (index > 0) {
                index--
                showCurrent()
            }
        }
        nextBtn.setOnClickListener {
            if (index < frames.lastIndex) {
                index++
                showCurrent()
            }
        }

        if (frames.isEmpty()) {
            finish()
            return
        }
        showCurrent()
    }

    private fun showCurrent() {
        val frame = frames[index]
        timestamp.text = formatter.format(Date(frame.createdAt))
        image.setImageBitmap(BitmapFactory.decodeFile(frame.imagePath))
        overlay.setDetections(frame.detections, frame.imageWidth, frame.imageHeight)
        prevBtn.isEnabled = index > 0
        nextBtn.isEnabled = index < frames.lastIndex
        title = getString(R.string.history_frame_viewer_title) + " (${index + 1}/${frames.size})"
        findViewById<MaterialToolbar>(R.id.viewerToolbar).title =
            getString(R.string.history_frame_viewer_title) + " (${index + 1}/${frames.size})"
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_INDEX = "index"
    }
}
