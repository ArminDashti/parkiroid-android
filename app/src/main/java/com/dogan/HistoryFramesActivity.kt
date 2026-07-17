package com.dogan

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gallery of retained detection frames with bounding boxes for a mode. */
class HistoryFramesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_frames)

        val mode = OperatingMode.fromStoredValue(intent.getStringExtra(EXTRA_MODE))
        findViewById<MaterialToolbar>(R.id.historyToolbar).apply {
            title = getString(R.string.history_frames_title, mode.displayName)
            setNavigationOnClickListener { finish() }
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        }

        val frames = FrameHistoryStore(this).list(mode)
        val empty = findViewById<TextView>(R.id.historyEmptyTxt)
        val recycler = findViewById<RecyclerView>(R.id.historyRecycler)
        if (frames.isEmpty()) {
            empty.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            empty.visibility = View.GONE
            recycler.layoutManager = GridLayoutManager(this, 2)
            recycler.adapter = HistoryAdapter(frames) { index ->
                startActivity(
                    Intent(this, HistoryFrameViewerActivity::class.java)
                        .putExtra(HistoryFrameViewerActivity.EXTRA_MODE, mode.toStoredValue())
                        .putExtra(HistoryFrameViewerActivity.EXTRA_INDEX, index),
                )
            }
        }
    }

    private class HistoryAdapter(
        private val frames: List<FrameHistoryStore.HistoryFrame>,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        private val formatter = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_frame, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val frame = frames[position]
            val bitmap = BitmapFactory.decodeFile(frame.imagePath)
            holder.image.setImageBitmap(bitmap)
            holder.overlay.setDetections(frame.detections, frame.imageWidth, frame.imageHeight)
            holder.timestamp.text = formatter.format(Date(frame.createdAt))
            holder.itemView.setOnClickListener { onClick(position) }
        }

        override fun getItemCount(): Int = frames.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.historyImage)
            val overlay: DetectionOverlayView = view.findViewById(R.id.historyOverlay)
            val timestamp: TextView = view.findViewById(R.id.historyTimestamp)
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}
