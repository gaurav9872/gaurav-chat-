package com.example.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcVoiceClient(
    private val context: Context,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerState: Boolean = false

    fun initialize() {
        if (peerConnection != null) return

        previousAudioMode = audioManager.mode
        @Suppress("DEPRECATION")
        previousSpeakerState = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val egl = EglBase.create()
        eglBase = egl
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, false, false))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state != null) {
                    onConnectionStateChanged(state)
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    onLocalIceCandidate(candidate)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val source = factory.createAudioSource(audioConstraints)
        audioSource = source
        val track = factory.createAudioTrack("ARDAMSa0", source).apply {
            setEnabled(true)
        }
        localAudioTrack = track
        peerConnection?.addTrack(track, listOf("ARDAMS"))
    }

    suspend fun createOfferSdp(): String {
        val pc = checkNotNull(peerConnection) { "PeerConnection not initialized" }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val offer = suspendCancellableCoroutine { cont ->
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null && cont.isActive) cont.resume(desc)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP offer"))
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create offer failed"))
                }
            }, constraints)
        }
        setLocalDescription(pc, offer)
        return offer.description
    }

    suspend fun handleOfferAndCreateAnswerSdp(offerSdp: String): String {
        val pc = checkNotNull(peerConnection) { "PeerConnection not initialized" }
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val answer = suspendCancellableCoroutine { cont ->
            pc.createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null && cont.isActive) cont.resume(desc)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP answer"))
                }

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create answer failed"))
                }
            }, constraints)
        }
        setLocalDescription(pc, answer)
        return answer.description
    }

    suspend fun handleAnswerSdp(answerSdp: String) {
        val pc = peerConnection ?: return
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        try {
            peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp))
        } catch (e: Exception) {
            Log.w("WebRtcVoiceClient", "Failed to add ICE candidate: ${e.message}")
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setSpeakerphone(enabled: Boolean) {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
    }

    fun dispose() {
        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnectionFactory?.dispose()
            eglBase?.release()
        } catch (e: Exception) {
            Log.w("WebRtcVoiceClient", "Dispose warning: ${e.message}")
        } finally {
            localAudioTrack = null
            audioSource = null
            peerConnection = null
            peerConnectionFactory = null
            eglBase = null
            try {
                audioManager.mode = previousAudioMode
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = previousSpeakerState
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun setLocalDescription(pc: PeerConnection, desc: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            pc.setLocalDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set local SDP failed"))
                }
            }, desc)
        }
    }

    private suspend fun setRemoteDescription(pc: PeerConnection, desc: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set remote SDP failed"))
                }
            }, desc)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
