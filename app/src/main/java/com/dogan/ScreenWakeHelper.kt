package com.dogan

import android.content.Context
import android.content.Intent
import android.os.PowerManager

/** Briefly turns the screen on from a background service on a timer. */
object ScreenWakeHelper {
    fun wakeScreen(context: Context) {
        val intent = Intent(context, ScreenOnActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "Dogan::ScreenWake",
        )
        wakeLock.acquire(3_000L)
    }
}
