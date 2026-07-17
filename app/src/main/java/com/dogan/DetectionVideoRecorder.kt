package com.dogan

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records detection video chunks per operating mode. */
class DetectionVideoRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var chunkStartedAt = 0L

    @Volatile
    private var activeMode: OperatingMode? = null

    fun start(mode: OperatingMode, audioMode: VideoAudioMode, chunkMinutes: Int) {
        stop()
        activeMode = mode
        val dir = DetectionMediaArchive(context).directoryForMode(mode)
        currentFile = File(dir, "video_${System.currentTimeMillis()}.mp4")
        chunkStartedAt = System.currentTimeMillis()
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        if (audioMode == VideoAudioMode.VIDEO_AND_SOUND) {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (audioMode == VideoAudioMode.VIDEO_AND_SOUND) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        recorder.setOutputFile(currentFile!!.absolutePath)
        recorder.prepare()
        recorder.start()
        mediaRecorder = recorder
        AppLogger.info("VideoRecorder", "Started ${mode.displayName} recording")
    }

    fun maybeRotateChunk(chunkMinutes: Int, mode: OperatingMode, audioMode: VideoAudioMode) {
        if (mediaRecorder == null) return
        val elapsed = System.currentTimeMillis() - chunkStartedAt
        if (elapsed >= chunkMinutes * 60_000L) {
            start(mode, audioMode, chunkMinutes)
        }
    }

    fun stop() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        currentFile = null
        activeMode = null
    }

    fun isRecording(): Boolean = mediaRecorder != null
}
