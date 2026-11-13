package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.SecretCryptoManager
import com.example.data.model.ChatSearchFilter
import com.example.data.model.ChatThread
import com.example.data.model.MessageSearchResult
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.VoiceCall
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseUser
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
import kotlinx.coroutines.launch

class MainViewModel(
    private val chatRepository: ChatRepository,
    private val callRepository: CallRepository,
    private val cryptoManager: SecretCryptoManager,
    val currentUserId: String
) : ViewModel() {

    val currentUserProfile: StateFlow<UserPublicProfile?> = chatRepository.observeUser(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing current profile", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val privateInfo: StateFlow<UserPrivateInfo?> = chatRepository.observePrivateInfo(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing private info", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val chatsState: StateFlow<UiState<List<ChatThread>>> = chatRepository.observeChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load chats"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val discoverableUsers: StateFlow<List<UserPublicProfile>> = chatRepository.observeDiscoverableUsers()
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing discoverable users", error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val callHistoryState: StateFlow<UiState<List<VoiceCall>>> = callRepository.observeCallsForUser(currentUserId)
        .map<List<VoiceCall>, UiState<List<VoiceCall>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "Failed to load call history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val incomingRingingCall: StateFlow<VoiceCall?> = callRepository.observeIncomingRingingCalls(currentUserId)
        .map { calls -> calls.firstOrNull() }
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(ChatSearchFilter.ALL)
    val searchFilter: StateFlow<ChatSearchFilter> = _searchFilter.asStateFlow()

    private val _messageSearchResults = MutableStateFlow<List<MessageSearchResult>>(emptyList())
    val messageSearchResults: StateFlow<List<MessageSearchResult>> = _messageSearchResults.asStateFlow()

    private val _isSearchingMessages = MutableStateFlow(false)
    val isSearchingMessages: StateFlow<Boolean> = _isSearchingMessages.asStateFlow()

    private var messageSearchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        triggerMessageSearch(query, _searchFilter.value)
    }

    fun updateSearchFilter(filter: ChatSearchFilter) {
        _searchFilter.value = filter
        triggerMessageSearch(_searchQuery.value, filter)
    }

    fun clearSearch() {
        messageSearchJob?.cancel()
        _searchQuery.value = ""
        _searchFilter.value = ChatSearchFilter.ALL
        _messageSearchResults.value = emptyList()
        _isSearchingMessages.value = false
    }

    private fun triggerMessageSearch(query: String, filter: ChatSearchFilter) {
        messageSearchJob?.cancel()
        val clean = query.trim()
        if (clean.isEmpty() || filter == ChatSearchFilter.CONTACTS) {
            _messageSearchResults.value = emptyList()
            _isSearchingMessages.value = false
            return
        }
        val threads = (chatsState.value as? UiState.Success)?.data.orEmpty()
        if (threads.isEmpty()) {
            _messageSearchResults.value = emptyList()
            _isSearchingMessages.value = false
            return
        }
        messageSearchJob = viewModelScope.launch {
            _isSearchingMessages.value = true
            delay(250L)
            val usersById = discoverableUsers.value.associateBy { it.uid }
            val results = chatRepository.searchMessagesAcrossChats(
                currentUserId = currentUserId,
                threads = threads,
                usersById = usersById,
                query = clean
            )
            _messageSearchResults.value = results
            _isSearchingMessages.value = false
        }
    }

    fun syncUserProfile(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            try {
                val pubKey = cryptoManager.getOrCreatePublicKeyBase64(firebaseUser.uid)
                chatRepository.ensureUserProfile(firebaseUser, pubKey)
            } catch (e: Exception) {
                Log.w(TAG, "Profile sync warning: ${e.message}")
            }
        }
    }

    fun setOnlineStatus(isOnline: Boolean) {
        viewModelScope.launch {
            chatRepository.updatePresence(currentUserId, isOnline)
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            chatRepository.updateFcmToken(currentUserId, token)
        }
    }

    fun markThreadDelivered(chatId: String) {
        viewModelScope.launch {
            chatRepository.markChatDelivered(chatId, currentUserId)
        }
    }

    fun updateProfile(displayName: String, bio: String, isDiscoverable: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.updateProfileDetails(currentUserId, displayName, bio, isDiscoverable)
                _actionMessage.value = "Profile updated"
            } catch (e: Exception) {
                _actionMessage.value = e.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    fun startChatWithPeer(peer: UserPublicProfile, onChatReady: (chatId: String, peerId: String) -> Unit) {
        viewModelScope.launch {
            try {
                val me = currentUserProfile.value ?: chatRepository.getUserById(currentUserId)
                    ?: UserPublicProfile(uid = currentUserId, displayName = "Me")
                val chatId = chatRepository.createOrGetChat(me, peer)
                onChatReady(chatId, peer.uid)
            } catch (e: Exception) {
                _actionMessage.value = e.localizedMessage ?: "Could not open chat"
            }
        }
    }

    fun connectByQrUid(peerUid: String, onChatReady: (chatId: String, peerId: String) -> Unit) {
        if (peerUid == currentUserId) {
            _actionMessage.value = "That is your own QR code!"
            return
        }
        viewModelScope.launch {
            val peer = chatRepository.getUserById(peerUid)
            if (peer == null) {
                _actionMessage.value = "User not found. Make sure they have signed into Chat Gaurav."
                return@launch
            }
            startChatWithPeer(peer, onChatReady)
        }
    }

    fun findAndChatByEmail(email: String, onChatReady: (chatId: String, peerId: String) -> Unit) {
        val clean = email.trim()
        if (clean.isEmpty()) {
            _actionMessage.value = "Please enter an email address"
            return
        }
        viewModelScope.launch {
            val peer = chatRepository.findUserByEmail(clean)
            if (peer == null) {
                _actionMessage.value = "No discoverable Chat Gaurav user found with that email"
                return@launch
            }
            if (peer.uid == currentUserId) {
                _actionMessage.value = "Cannot start a chat with yourself"
                return@launch
            }
            startChatWithPeer(peer, onChatReady)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
