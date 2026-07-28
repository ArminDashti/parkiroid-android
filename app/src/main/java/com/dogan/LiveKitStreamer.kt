package com.dogan

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** LiveKit publisher for video-only, audio-only, and video+audio modes. */
class LiveKitStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private var room: Room? = null
    private var eventsJob: Job? = null

    fun start(baseUrl: String, streamMode: StreamMode) {
        scope.launch {
            stopInternal()
            val session = withContext(Dispatchers.IO) {
                apiClient.createWebRtcSession(baseUrl)
            } ?: run {
                AppLogger.error("LiveKit", "Failed to create session")
                LiveKitStatusCache.setStreaming(false)
                LiveKitStatusCache.setError("LiveKit session could not be created")
                return@launch
            }
            if (session.token.isBlank() || session.url.isBlank() || session.room.isBlank()) {
                AppLogger.error("LiveKit", "Session missing token, url, or room")
                return@launch
            }

            try {
                val liveKitRoom = LiveKit.create(context.applicationContext)
                eventsJob = launch {
                    liveKitRoom.events.collect { event ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                _streaming.value = true
                                LiveKitStatusCache.setStreaming(true)
                                AppLogger.info("LiveKit", "Connected to room ${session.room}")
                            }
                            is RoomEvent.Disconnected -> {
                                _streaming.value = false
                                LiveKitStatusCache.setStreaming(false)
                                AppLogger.info("LiveKit", "Disconnected from room")
                            }
                            else -> Unit
                        }
                    }
                }

                liveKitRoom.connect(session.url, session.token)
                applyStreamMode(liveKitRoom.localParticipant, streamMode)
                room = liveKitRoom
                AppLogger.info("LiveKit", "Streaming started (${streamMode.displayName})")
            } catch (e: Exception) {
                AppLogger.error("LiveKit", "Connect failed: ${e.message}")
                _streaming.value = false
                LiveKitStatusCache.setStreaming(false)
                LiveKitStatusCache.setError(e.message ?: "LiveKit connect failed")
            }
        }
    }

    private suspend fun applyStreamMode(participant: LocalParticipant, streamMode: StreamMode) {
        when (streamMode) {
            StreamMode.VIDEO_ONLY -> {
                participant.setCameraEnabled(true)
                participant.setMicrophoneEnabled(false)
            }
            StreamMode.AUDIO_ONLY -> {
                participant.setCameraEnabled(false)
                participant.setMicrophoneEnabled(true)
            }
            StreamMode.VIDEO_AUDIO -> {
                participant.setCameraEnabled(true)
                participant.setMicrophoneEnabled(true)
            }
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    fun release() {
        scope.launch { stopInternal() }
        scope.cancel()
    }

    private suspend fun stopInternal() {
        eventsJob?.cancel()
        eventsJob = null
        room?.disconnect()
        room = null
        _streaming.value = false
        LiveKitStatusCache.setStreaming(false)
    }
}
