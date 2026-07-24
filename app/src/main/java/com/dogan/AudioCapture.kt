package com.dogan

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/** Samples microphone amplitude and feeds cabin noise archive. */
class AudioCapture(
    private val onSpike: ((Double) -> Unit)? = null,
    private val cabinNoiseArchive: CabinNoiseArchive? = null,
) {
    private var audioRecord: AudioRecord? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    @Volatile
    var currentRms: Double = 0.0
        private set

    @Volatile
    var soundSensitivity: SensitivityLevel = SensitivityLevel.MEDIUM

    @Volatile
    var customSoundThreshold: Double = SettingsStore.DEFAULT_CUSTOM_SOUND_THRESHOLD

    fun start() {
        if (running) return
        val sampleRate = CabinNoiseArchive.SAMPLE_RATE
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
        cabinNoiseArchive?.startNewSegment()
        AppLogger.info("Audio", "Sound monitoring started")

        thread = Thread {
            val buffer = ShortArray(bufferSize)
            var lastSpikeAt = 0L
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = computeRms(buffer, read)
                    currentRms = rms
                    cabinNoiseArchive?.writePcmSamples(buffer, read, rms)
                    val now = System.currentTimeMillis()
                    if (rms > loudThreshold() && now - lastSpikeAt > 5_000L) {
                        lastSpikeAt = now
                        onSpike?.invoke(rms)
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
        cabinNoiseArchive?.stop()
        audioRecord?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        audioRecord = null
        currentRms = 0.0
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

    private fun loudThreshold(): Double = when (soundSensitivity) {
        SensitivityLevel.LOW -> 4000.0
        SensitivityLevel.MEDIUM -> 2500.0
        SensitivityLevel.HIGH -> 1500.0
        SensitivityLevel.CUSTOM -> customSoundThreshold
    }

    companion object {
        private const val DEFAULT_LOUD_THRESHOLD = 2500.0
    }
}
