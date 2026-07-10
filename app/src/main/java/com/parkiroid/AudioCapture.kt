package com.parkiroid

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/** Samples microphone amplitude during monitoring for sound awareness. */
class AudioCapture {
    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        val sampleRate = 16_000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            AppLogger.warn("Audio", "Microphone buffer size unavailable")
            return
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.warn("Audio", "Microphone not initialized")
            recorder.release()
            return
        }

        audioRecord = recorder
        running = true
        recorder.startRecording()
        AppLogger.info("Audio", "Sound monitoring started")

        thread = Thread {
            val buffer = ShortArray(bufferSize)
            var lastLogAt = 0L
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = computeRms(buffer, read)
                    val now = System.currentTimeMillis()
                    if (rms > LOUD_THRESHOLD && now - lastLogAt > 5_000L) {
                        lastLogAt = now
                        AppLogger.info("Audio", "Sound level spike detected (rms=%.2f)".format(rms))
                    }
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        audioRecord?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        audioRecord = null
        AppLogger.info("Audio", "Sound monitoring stopped")
    }

    private fun computeRms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }

    companion object {
        private const val LOUD_THRESHOLD = 2500.0
    }
}
