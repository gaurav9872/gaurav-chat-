package com.example.notifications

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NotificationNavRequest {
    data class OpenChat(val chatId: String, val peerId: String) : NotificationNavRequest
    data class OpenCall(val callId: String, val autoAccept: Boolean) : NotificationNavRequest
}

object RealtimeNotificationCoordinator {
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_PEER_ID = "extra_peer_id"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALL_ACTION = "extra_call_action"
    const val CALL_ACTION_ACCEPT = "accept"
    const val CALL_ACTION_OPEN = "open"

    private val lock = Any()
    private val lastNotifiedChatTimestamps = mutableMapOf<String, Long>()
    private val notifiedCallIds = mutableSetOf<String>()

    private val _activeOpenChatId = MutableStateFlow<String?>(null)
    val activeOpenChatId: StateFlow<String?> = _activeOpenChatId.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _pendingNavigation = MutableStateFlow<NotificationNavRequest?>(null)
    val pendingNavigation: StateFlow<NotificationNavRequest?> = _pendingNavigation.asStateFlow()

    fun setActiveOpenChat(chatId: String?) {
        _activeOpenChatId.value = chatId
    }

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun isChatCurrentlyVisible(chatId: String): Boolean =
        _isAppInForeground.value && _activeOpenChatId.value == chatId

    fun shouldNotifyMessage(chatId: String, timestampMillis: Long): Boolean {
        if (isChatCurrentlyVisible(chatId)) return false
        synchronized(lock) {
            val prev = lastNotifiedChatTimestamps[chatId] ?: 0L
            if (timestampMillis > 0L && timestampMillis <= prev) {
                return false
            }
            lastNotifiedChatTimestamps[chatId] = if (timestampMillis > 0L) timestampMillis else System.currentTimeMillis()
            return true
        }
    }

    fun seedChatTimestamp(chatId: String, timestampMillis: Long) {
        if (timestampMillis <= 0L) return
        synchronized(lock) {
            val prev = lastNotifiedChatTimestamps[chatId] ?: 0L
            if (timestampMillis > prev) {
                lastNotifiedChatTimestamps[chatId] = timestampMillis
            }
        }
    }

    fun shouldNotifyCall(callId: String): Boolean {
        synchronized(lock) {
            return notifiedCallIds.add(callId)
        }
    }

    fun clearCallNotified(callId: String) {
        synchronized(lock) {
            notifiedCallIds.remove(callId)
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        val parsed = parseIntent(intent) ?: return
        _pendingNavigation.value = parsed
    }

    fun consumePendingNavigation() {
        _pendingNavigation.value = null
    }

    fun clearHistory() {
        synchronized(lock) {
            lastNotifiedChatTimestamps.clear()
            notifiedCallIds.clear()
        }
        _activeOpenChatId.value = null
        _pendingNavigation.value = null
    }

    private fun parseIntent(intent: Intent?): NotificationNavRequest? {
        if (intent == null) return null
        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty().ifBlank {
            intent.getStringExtra("callId").orEmpty()
        }
        if (callId.isNotBlank()) {
            val action = intent.getStringExtra(EXTRA_CALL_ACTION).orEmpty()
            return NotificationNavRequest.OpenCall(
                callId = callId,
                autoAccept = action == CALL_ACTION_ACCEPT
            )
        }

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty().ifBlank {
            intent.getStringExtra("chatId").orEmpty()
        }
        if (chatId.isNotBlank()) {
            val peerId = intent.getStringExtra(EXTRA_PEER_ID).orEmpty().ifBlank {
                intent.getStringExtra("peerId").orEmpty()
            }
            return NotificationNavRequest.OpenChat(chatId = chatId, peerId = peerId)
        }
        return null
    }
}
