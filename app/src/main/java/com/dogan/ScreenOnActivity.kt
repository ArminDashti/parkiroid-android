package com.dogan

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/** Transparent activity that turns the screen on briefly, then exits. */
class ScreenOnActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finish()
    }
}
