package com.dogan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Draws detection bounding boxes and handles tap-to-watch in Spotter mode. */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val carBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GREEN
    }
    private val personBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.RED
    }
    private val carTextBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 128, 0)
    }
    private val personTextBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 160, 0, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
    }

    private var detections: List<VehicleDetection> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var tapToWatchEnabled = false

    var onDetectionTapped: ((VehicleDetection, Int, Int) -> Unit)? = null

    fun setTapToWatchEnabled(enabled: Boolean) {
        tapToWatchEnabled = enabled
    }

    fun setDetections(detections: List<VehicleDetection>, imageWidth: Int, imageHeight: Int) {
        this.detections = detections
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        imageWidth = 0
        imageHeight = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && detections.isNotEmpty()) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return

        for (detection in detections) {
            val mapped = mapRectToView(detection.bounds, width, height)
            val isPerson = detection.label.equals("person", ignoreCase = true)
            val boxPaint = if (isPerson) personBoxPaint else carBoxPaint
            val textBgPaint = if (isPerson) personTextBackgroundPaint else carTextBackgroundPaint
            canvas.drawRect(mapped, boxPaint)

            val label = "${detection.label} ${"%.0f".format(detection.confidence * 100)}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            val textTop = (mapped.top - textHeight - 8f).coerceAtLeast(0f)
            val textBackground = RectF(
                mapped.left,
                textTop,
                mapped.left + textWidth + 12f,
                textTop + textHeight + 8f,
            )
            canvas.drawRect(textBackground, textBgPaint)
            canvas.drawText(label, mapped.left + 6f, textTop + textHeight, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!tapToWatchEnabled || event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        if (detections.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return false

        val tapX = event.x
        val tapY = event.y
        for (detection in detections) {
            val mapped = mapRectToView(detection.bounds, width, height)
            if (mapped.contains(tapX, tapY)) {
                onDetectionTapped?.invoke(detection, imageWidth, imageHeight)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun mapRectToView(bounds: RectF, viewWidth: Int, viewHeight: Int): RectF {
        val scale = max(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f
        return RectF(
            bounds.left * scale + offsetX,
            bounds.top * scale + offsetY,
            bounds.right * scale + offsetX,
            bounds.bottom * scale + offsetY,
        )
    }
}
