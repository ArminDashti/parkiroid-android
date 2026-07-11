package com.dogan

import android.graphics.RectF

/** Bridges detection tap events from MainActivity to the foreground service Spotter engine. */
object DetectionTapBridge {
    @Volatile
    var handler: ((String, RectF, Int, Int) -> Unit)? = null

    fun onTapped(label: String, bounds: RectF, imageWidth: Int, imageHeight: Int) {
        handler?.invoke(label, bounds, imageWidth, imageHeight)
    }
}
