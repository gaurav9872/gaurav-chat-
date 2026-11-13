package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserPublicProfile
import com.example.data.model.VoiceCall
import com.example.data.repository.CallRepository
import com.example.notifications.NotificationHelper
import com.example.webrtc.WebRtcVoiceClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

data class ActiveCallUiState(
    val callId: String = "",
    val peerName: String = "",
    val peerPhotoUrl: String = "",
    val isIncoming: Boolean = false,
    val status: String = "idle", // idle, ringing, connecting, connected, ended, declined, missed
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val iceStateLabel: String = "Initializing"
)

class CallViewModel(
    private val appContext: Context,
    private val callRepository: CallRepository,
    val currentUserId: String
) : ViewModel() {

    private val _callState = MutableStateFlow(ActiveCallUiState())
    val callState: StateFlow<ActiveCallUiState> = _callState.asStateFlow()

    private var webRtcClient: WebRtcVoiceClient? = null
    private var callObserverJob: Job? = null
    private var candidatesObserverJob: Job? = null
    private var timerJob: Job? = null

    private var answerApplied = false
    private val appliedCandidateIds = mutableSetOf<String>()

    fun startOutgoingCall(caller: UserPublicProfile, callee: UserPublicProfile) {
        if (_callState.value.status in listOf("ringing", "connecting", "connected")) return
        resetInternalState()
        _callState.value = ActiveCallUiState(
            peerName = callee.displayName,
            peerPhotoUrl = callee.photoUrl,
            isIncoming = false,
            status = "ringing",
            iceStateLabel = "Calling..."
        )

        viewModelScope.launch {
            try {
                val callId = callRepository.createCall(caller, callee)
                val participants = listOf(caller.uid, callee.uid).sorted()
                _callState.value = _callState.value.copy(callId = callId)

                initWebRtc(callId, participants)
                val offerSdp = webRtcClient?.createOfferSdp().orEmpty()
                callRepository.attachOffer(callId, offerSdp)
                observeCallSignaling(callId, isCaller = true)
                observeRemoteCandidates(callId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start outgoing call", e)
                endCall("ended")
            }
        }
    }

    fun prepareIncomingCall(call: VoiceCall) {
        if (_callState.value.callId == call.id && _callState.value.status != "idle") return
        resetInternalState()
        NotificationHelper.startIncomingRingtoneAndVibration(appContext)
        _callState.value = ActiveCallUiState(
            callId = call.id,
            peerName = call.callerName,
            peerPhotoUrl = call.callerPhotoUrl,
            isIncoming = true,
            status = "ringing",
            iceStateLabel = "Incoming voice call"
        )
        observeCallSignaling(call.id, isCaller = false)
    }

    fun acceptIncomingCall(call: VoiceCall) {
        NotificationHelper.stopIncomingRingtoneAndVibration(appContext)
        NotificationHelper.cancelCallNotification(appContext, call.id)
        _callState.value = _callState.value.copy(
            status = "connecting",
            iceStateLabel = "Connecting audio..."
        )
        viewModelScope.launch {
            try {
                initWebRtc(call.id, call.participantIds)
                var offerSdp = call.offerSdp
                if (offerSdp.isBlank()) {
                    // Wait briefly for caller's SDP offer if still attaching
                    repeat(15) {
                        if (offerSdp.isNotBlank()) return@repeat
                        delay(200L)
                    }
                }
                if (offerSdp.isNotBlank()) {
                    val answerSdp = webRtcClient?.handleOfferAndCreateAnswerSdp(offerSdp).orEmpty()
                    callRepository.answerCall(call.id, answerSdp)
                    observeRemoteCandidates(call.id)
                    startDurationTimer()
                    _callState.value = _callState.value.copy(
                        status = "connected",
                        iceStateLabel = "Connected"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to accept incoming call", e)
                endCall("ended")
            }
        }
    }

    fun declineIncomingCall(callId: String) {
        NotificationHelper.stopIncomingRingtoneAndVibration(appContext)
        NotificationHelper.cancelCallNotification(appContext, callId)
        viewModelScope.launch {
            callRepository.endCall(callId, "declined", 0L)
            cleanupCallResources()
        }
    }

    fun endCall(overrideStatus: String? = null) {
        NotificationHelper.stopIncomingRingtoneAndVibration(appContext)
        val current = _callState.value
        val callId = current.callId
        val duration = current.durationSeconds
        val finalStatus = overrideStatus ?: when {
            current.status == "ringing" && !current.isIncoming -> "missed"
            current.status == "ringing" && current.isIncoming -> "declined"
            else -> "ended"
        }
        viewModelScope.launch {
            if (callId.isNotBlank()) {
                NotificationHelper.cancelCallNotification(appContext, callId)
                callRepository.endCall(callId, finalStatus, duration)
            }
            cleanupCallResources()
        }
    }

    fun toggleMute() {
        val next = !_callState.value.isMuted
        webRtcClient?.setMuted(next)
        _callState.value = _callState.value.copy(isMuted = next)
    }

    fun toggleSpeaker() {
        val next = !_callState.value.isSpeakerOn
        webRtcClient?.setSpeakerphone(next)
        _callState.value = _callState.value.copy(isSpeakerOn = next)
    }

    private fun initWebRtc(callId: String, participantIds: List<String>) {
        val client = WebRtcVoiceClient(
            context = appContext,
            onLocalIceCandidate = { candidate ->
                viewModelScope.launch {
                    callRepository.addIceCandidate(
                        callId = callId,
                        participantIds = participantIds,
                        sdpMid = candidate.sdpMid ?: "0",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp
                    )
                }
            },
            onConnectionStateChanged = { iceState ->
                val label = when (iceState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> "HD Voice Connected"
                    PeerConnection.IceConnectionState.CHECKING -> "Securing audio path..."
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Reconnecting..."
                    PeerConnection.IceConnectionState.FAILED -> "Connection lost"
                    else -> iceState.name
                }
                _callState.value = _callState.value.copy(iceStateLabel = label)
            }
        )
        client.initialize()
        webRtcClient = client
    }

    private fun observeCallSignaling(callId: String, isCaller: Boolean) {
        callObserverJob?.cancel()
        callObserverJob = viewModelScope.launch {
            callRepository.observeCall(callId).collectLatest { call ->
                if (call == null) return@collectLatest
                if (isCaller && call.status == "connected" && call.answerSdp.isNotBlank() && !answerApplied) {
                    answerApplied = true
                    webRtcClient?.handleAnswerSdp(call.answerSdp)
                    startDurationTimer()
                    _callState.value = _callState.value.copy(
                        status = "connected",
                        iceStateLabel = "HD Voice Connected"
                    )
                } else if (!isCaller && _callState.value.status == "connecting" && call.offerSdp.isNotBlank() && !answerApplied) {
                    answerApplied = true
                    val answerSdp = webRtcClient?.handleOfferAndCreateAnswerSdp(call.offerSdp).orEmpty()
                    if (answerSdp.isNotBlank()) {
                        callRepository.answerCall(call.id, answerSdp)
                        observeRemoteCandidates(call.id)
                        startDurationTimer()
                        _callState.value = _callState.value.copy(
                            status = "connected",
                            iceStateLabel = "HD Voice Connected"
                        )
                    }
                } else if (call.status in listOf("ended", "declined", "missed")) {
                    NotificationHelper.stopIncomingRingtoneAndVibration(appContext)
                    NotificationHelper.cancelCallNotification(appContext, callId)
                    cleanupCallResources()
                }
            }
        }
    }

    private fun observeRemoteCandidates(callId: String) {
        candidatesObserverJob?.cancel()
        candidatesObserverJob = viewModelScope.launch {
            callRepository.observeIceCandidates(callId, currentUserId).collectLatest { candidates ->
                candidates.forEach { cand ->
                    if (cand.senderId != currentUserId && appliedCandidateIds.add(cand.id)) {
                        webRtcClient?.addRemoteIceCandidate(
                            sdpMid = cand.sdpMid,
                            sdpMLineIndex = cand.sdpMLineIndex.toInt(),
                            sdp = cand.sdp
                        )
                    }
                }
            }
        }
    }

    private fun startDurationTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _callState.value = _callState.value.copy(
                    durationSeconds = _callState.value.durationSeconds + 1L
                )
            }
        }
    }

    private fun resetInternalState() {
        callObserverJob?.cancel()
        candidatesObserverJob?.cancel()
        timerJob?.cancel()
        timerJob = null
        answerApplied = false
        appliedCandidateIds.clear()
        webRtcClient?.dispose()
        webRtcClient = null
    }

    private fun cleanupCallResources() {
        resetInternalState()
        _callState.value = ActiveCallUiState(status = "idle")
    }

    override fun onCleared() {
        super.onCleared()
        NotificationHelper.stopIncomingRingtoneAndVibration(appContext)
        resetInternalState()
    }

    private companion object {
        const val TAG = "CallViewModel"
    }
}
