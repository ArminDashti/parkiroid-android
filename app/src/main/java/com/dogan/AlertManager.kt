package com.dogan

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Plays server-downloaded alert sounds and shows notifications. */
class AlertManager(
    private val context: Context,
    private val soundDownloadManager: SoundDownloadManager,
) {
    private var mediaPlayer: MediaPlayer? = null
    private val lastAlertAt = mutableMapOf<AlertType, Long>()

    fun trigger(
        alertType: AlertType,
        settings: AppSettings,
        title: String,
        body: String,
        channelId: String = "dogan_alerts",
        cooldownMs: Long = 5_000L,
    ) {
        val now = System.currentTimeMillis()
        val last = lastAlertAt[alertType] ?: 0L
        if (now - last < cooldownMs) return
        lastAlertAt[alertType] = now

        showNotification(title, body, channelId)

        if (settings.alertVolume == AlertVolume.OFF) return

        val soundFile = soundDownloadManager.getSoundFile(alertType)
        if (soundFile == null || !soundFile.exists()) {
            AppLogger.warn("Alert", "No sound file for ${alertType.soundId}")
            return
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(soundFile.absolutePath)
                setVolume(settings.alertVolume.volumeFraction, settings.alertVolume.volumeFraction)
                prepare()
                start()
            }
            val durationMs = settings.alertDuration.seconds * 1000L
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                } catch (_: Exception) {
                }
            }, durationMs)
        } catch (e: Exception) {
            AppLogger.error("Alert", "Failed to play sound: ${e.message}")
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun showNotification(title: String, body: String, channelId: String) {
        try {
            val notification = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(alertTypeHash(title), notification)
        } catch (_: SecurityException) {
            AppLogger.warn("Alert", "Notification permission not granted")
        }
    }

    private fun alertTypeHash(title: String): Int = title.hashCode() and 0xFFFF
}
