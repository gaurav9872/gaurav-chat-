package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.SecretCryptoManager
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.UiState
import com.example.data.model.UserPublicProfile
import com.example.data.repository.ChatRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisplayChatMessage(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val displayText: String,
    val isSecret: Boolean,
    val status: String,
    val createdAtMillis: Long,
    val burnSecondsRemaining: Int? = null,
    val attachmentUrl: String = "",
    val attachmentType: String = "",
    val attachmentName: String = "",
    val attachmentSizeBytes: Long = 0L,
    val attachmentMimeType: String = ""
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val cryptoManager: SecretCryptoManager,
    val chatId: String,
    val currentUserId: String,
    val peerId: String
) : ViewModel() {

    val chatThread: StateFlow<ChatThread?> = chatRepository.observeChat(chatId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing chat thread", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val peerProfile: StateFlow<UserPublicProfile?> = chatRepository.observeUser(peerId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _ephemeralSecretMessages = MutableStateFlow<List<DisplayChatMessage>>(emptyList())
    private val _ephemeralSecretNotice = MutableStateFlow<String?>(null)
    val ephemeralSecretNotice: StateFlow<String?> = _ephemeralSecretNotice.asStateFlow()

    private val _isUploadingAttachment = MutableStateFlow(false)
    val isUploadingAttachment: StateFlow<Boolean> = _isUploadingAttachment.asStateFlow()

    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError: StateFlow<String?> = _attachmentError.asStateFlow()

    private val processedReceipts = mutableSetOf<String>()
    private val burnedSecretIds = mutableSetOf<String>()
    private val plaintextSentSecrets = mutableMapOf<String, String>()

    private val _isChatVisible = MutableStateFlow(false)
    private var latestRawMessages: List<ChatMessage> = emptyList()

    private val rawMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        chatRepository.observeMessages(chatId, currentUserId)
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { messages ->
                latestRawMessages = messages
                if (_isChatVisible.value) {
                    processIncomingMessages(messages)
                }
                UiState.Success(messages)
            }
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                emit(UiState.Error((fsError ?: error).message ?: "Failed to load messages"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val messagesState: StateFlow<UiState<List<DisplayChatMessage>>> = kotlinx.coroutines.flow.combine(
        rawMessagesState,
        _ephemeralSecretMessages
    ) { state, ephemeralSecrets ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val peerPubKey = peerProfile.value?.publicKey
                val activeMapped = state.data.map { msg ->
                    val resolvedText = if (msg.isSecret) {
                        plaintextSentSecrets[msg.id] ?: cryptoManager.decryptSecretMessage(
                            ciphertextBase64 = msg.ciphertext,
                            ivBase64 = msg.iv,
                            myUid = currentUserId,
                            chatId = chatId,
                            peerPublicKeyBase64 = peerPubKey
                        )
                    } else {
                        msg.text
                    }
                    DisplayChatMessage(
                        id = msg.id,
                        senderId = msg.senderId,
                        receiverId = msg.receiverId,
                        displayText = resolvedText,
                        isSecret = msg.isSecret,
                        status = msg.status,
                        createdAtMillis = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                        burnSecondsRemaining = null,
                        attachmentUrl = msg.attachmentUrl,
                        attachmentType = msg.attachmentType,
                        attachmentName = msg.attachmentName,
                        attachmentSizeBytes = msg.attachmentSizeBytes,
                        attachmentMimeType = msg.attachmentMimeType
                    )
                }
                val activeIds = activeMapped.map { it.id }.toSet()
                val merged = (activeMapped + ephemeralSecrets.filter { it.id !in activeIds })
                    .sortedBy { it.createdAtMillis }
                UiState.Success(merged)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    private var typingDebounceJob: Job? = null

    init {
        startEphemeralBurnTicker()
    }

    fun setChatVisible(visible: Boolean) {
        _isChatVisible.value = visible
        if (visible) {
            viewModelScope.launch {
                chatRepository.markChatRead(chatId, currentUserId, peerId)
            }
            if (latestRawMessages.isNotEmpty()) {
                processIncomingMessages(latestRawMessages)
            }
        }
    }

    private fun processIncomingMessages(messages: List<ChatMessage>) {
        val peerPubKey = peerProfile.value?.publicKey
        var hadUnread = false

        messages.forEach { msg ->
            if (msg.receiverId == currentUserId) {
                if (msg.isSecret) {
                    if (burnedSecretIds.add(msg.id)) {
                        val decrypted = cryptoManager.decryptSecretMessage(
                            ciphertextBase64 = msg.ciphertext,
                            ivBase64 = msg.iv,
                            myUid = currentUserId,
                            chatId = chatId,
                            peerPublicKeyBase64 = peerPubKey
                        )
                        val ephemeral = DisplayChatMessage(
                            id = msg.id,
                            senderId = msg.senderId,
                            receiverId = msg.receiverId,
                            displayText = decrypted,
                            isSecret = true,
                            status = "read",
                            createdAtMillis = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                            burnSecondsRemaining = SECRET_VIEW_SECONDS
                        )
                        _ephemeralSecretMessages.update { list ->
                            (list.filterNot { it.id == msg.id } + ephemeral)
                        }
                        // Delete immediately from Firestore as soon as receiver views/reads it!
                        viewModelScope.launch {
                            chatRepository.markMessageStatus(chatId, msg.id, "read")
                            chatRepository.deleteMessage(chatId, msg.id)
                            _ephemeralSecretNotice.value = "Secret message wiped from server"
                        }
                        hadUnread = true
                    }
                } else if (msg.status != "read" && processedReceipts.add("${msg.id}:read")) {
                    hadUnread = true
                    viewModelScope.launch {
                        chatRepository.markMessageStatus(chatId, msg.id, "read")
                    }
                }
            }
        }

        if (hadUnread) {
            viewModelScope.launch {
                chatRepository.markChatRead(chatId, currentUserId, peerId)
            }
        }
    }

    private fun startEphemeralBurnTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _ephemeralSecretMessages.update { current ->
                    current.mapNotNull { item ->
                        val remaining = (item.burnSecondsRemaining ?: 0) - 1
                        if (remaining <= 0) null else item.copy(burnSecondsRemaining = remaining)
                    }
                }
            }
        }
    }

    fun sendMessage(text: String, isSecret: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        onTypingChanged(false)
        viewModelScope.launch {
            try {
                val participants = listOf(currentUserId, peerId).sorted()
                val peerPubKey = peerProfile.value?.publicKey
                val msgId = chatRepository.sendMessage(
                    chatId = chatId,
                    receiverId = peerId,
                    participantIds = participants,
                    plaintext = clean,
                    isSecret = isSecret,
                    cryptoManager = cryptoManager,
                    peerPublicKeyBase64 = peerPubKey
                )
                if (isSecret) {
                    plaintextSentSecrets[msgId] = clean
                }
            } catch (e: Exception) {
                Log.w(TAG, "Send message error: ${e.message}")
            }
        }
    }

    fun toggleSecretMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.toggleSecretMode(chatId, enabled)
            } catch (e: Exception) {
                Log.w(TAG, "Secret mode toggle failed: ${e.message}")
            }
        }
    }

    fun onTypingChanged(isTypingNow: Boolean) {
        typingDebounceJob?.cancel()
        if (!isTypingNow) {
            viewModelScope.launch {
                chatRepository.setTyping(chatId, currentUserId, false)
            }
            return
        }
        viewModelScope.launch {
            chatRepository.setTyping(chatId, currentUserId, true)
        }
        typingDebounceJob = viewModelScope.launch {
            delay(2500L)
            chatRepository.setTyping(chatId, currentUserId, false)
        }
    }

    fun sendAttachment(context: Context, uri: Uri, isImage: Boolean, caption: String = "") {
        if (_isUploadingAttachment.value) return
        if (chatThread.value?.secretModeEnabled == true) {
            _attachmentError.value = "Turn off Secret Mode to send cloud-stored file attachments."
            return
        }
        onTypingChanged(false)
        viewModelScope.launch {
            _isUploadingAttachment.value = true
            _attachmentError.value = null
            try {
                val uploaded = chatRepository.uploadChatAttachment(
                    context = context.applicationContext,
                    chatId = chatId,
                    uri = uri,
                    isImage = isImage
                )
                val participants = listOf(currentUserId, peerId).sorted()
                chatRepository.sendAttachmentMessage(
                    chatId = chatId,
                    receiverId = peerId,
                    participantIds = participants,
                    attachment = uploaded,
                    caption = caption
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Attachment upload failed: ${e.message}", e)
                _attachmentError.value = e.localizedMessage ?: "Failed to upload attachment."
            } finally {
                _isUploadingAttachment.value = false
            }
        }
    }

    fun clearAttachmentError() {
        _attachmentError.value = null
    }

    fun clearSecretNotice() {
        _ephemeralSecretNotice.value = null
    }

    override fun onCleared() {
        super.onCleared()
        _ephemeralSecretMessages.value = emptyList()
        plaintextSentSecrets.clear()
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "ChatViewModel"
        const val STOP_TIMEOUT_MS = 5000L
        const val SECRET_VIEW_SECONDS = 10
    }
}
