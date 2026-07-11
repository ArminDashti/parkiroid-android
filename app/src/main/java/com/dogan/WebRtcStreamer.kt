package com.dogan

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

/** WebRTC streaming with video-only, audio-only, and video+audio modes. */
class WebRtcStreamer(
    private val context: Context,
    private val apiClient: DoganApiClient,
    private val deviceId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private val initialized = AtomicBoolean(false)

    fun initialize() {
        if (initialized.getAndSet(true)) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun start(baseUrl: String, apiKey: String, streamMode: StreamMode) {
        scope.launch {
            initialize()
            val session = apiClient.createWebRtcSession(baseUrl, apiKey) ?: run {
                AppLogger.error("WebRTC", "Failed to create session")
                return@launch
            }
            val iceServers = mutableListOf<PeerConnection.IceServer>()
            val iceArray = session.iceServers
            if (iceArray != null) {
                for (i in 0 until iceArray.length()) {
                    val ice = iceArray.getJSONObject(i)
                    val urls = ice.optJSONArray("urls")
                    if (urls != null) {
                        for (j in 0 until urls.length()) {
                            iceServers.add(
                                PeerConnection.IceServer.builder(urls.getString(j))
                                    .setUsername(ice.optString("username"))
                                    .setPassword(ice.optString("credential"))
                                    .createIceServer(),
                            )
                        }
                    }
                }
            }
            if (iceServers.isEmpty()) {
                iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            }

            val config = PeerConnection.RTCConfiguration(iceServers)
            peerConnection = peerConnectionFactory?.createPeerConnection(config, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    _streaming.value = state == PeerConnection.IceConnectionState.CONNECTED
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidate(candidate: IceCandidate?) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
            })

            setupTracks(streamMode)
            createOffer()
            _streaming.value = true
            AppLogger.info("WebRTC", "Streaming started (${streamMode.displayName})")
        }
    }

    fun stop() {
        videoTrack?.dispose()
        audioTrack?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        peerConnection?.close()
        peerConnection = null
        _streaming.value = false
        AppLogger.info("WebRTC", "Streaming stopped")
    }

    fun release() {
        stop()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        initialized.set(false)
    }

    fun getVideoSource(): VideoSource? = videoSource

    private fun setupTracks(streamMode: StreamMode) {
        val factory = peerConnectionFactory ?: return
        val pc = peerConnection ?: return

        if (streamMode == StreamMode.VIDEO_ONLY || streamMode == StreamMode.VIDEO_AUDIO) {
            videoSource = factory.createVideoSource(false)
            videoTrack = factory.createVideoTrack("dogan_video", videoSource)
            pc.addTrack(videoTrack)
        }
        if (streamMode == StreamMode.AUDIO_ONLY || streamMode == StreamMode.VIDEO_AUDIO) {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("dogan_audio", audioSource)
            pc.addTrack(audioTrack)
        }
    }

    private fun createOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) = Unit
                        override fun onSetSuccess() = Unit
                        override fun onCreateFailure(p0: String?) = Unit
                        override fun onSetFailure(p0: String?) = Unit
                    }, sdp)
                }
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String?) {
                AppLogger.error("WebRTC", "Offer failed: $error")
            }
            override fun onSetFailure(error: String?) = Unit
        }, constraints)
    }
}
