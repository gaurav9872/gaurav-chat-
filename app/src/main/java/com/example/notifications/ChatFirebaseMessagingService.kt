package com.example.notifications

import android.util.Log
import com.example.R
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        val repo = ChatRepository(db)
        serviceScope.launch {
            repo.updateFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: "message"
        val title = message.notification?.title ?: data["senderName"] ?: data["title"] ?: "Chat Gaurav"
        val body = message.notification?.body ?: data["body"] ?: "New activity"
        val chatId = data["chatId"].orEmpty()
        val peerId = data["peerId"] ?: data["senderId"].orEmpty()
        val callId = data["callId"].orEmpty()
        val isSecret = data["isSecret"]?.equals("true", ignoreCase = true) == true
        val timestampMillis = data["timestampMillis"]?.toLongOrNull() ?: System.currentTimeMillis()

        serviceScope.launch {
            val enabled = try {
                ThemePreferences(applicationContext).settings.first().notificationsEnabled
            } catch (_: Exception) {
                true
            }
            if (!enabled) return@launch

            if (type == "call" && callId.isNotBlank()) {
                if (RealtimeNotificationCoordinator.shouldNotifyCall(callId)) {
                    NotificationHelper.startIncomingRingtoneAndVibration(applicationContext)
                    NotificationHelper.showIncomingCallNotification(
                        context = applicationContext,
                        callerName = title,
                        callId = callId
                    )
                }
            } else if (chatId.isNotBlank()) {
                if (RealtimeNotificationCoordinator.shouldNotifyMessage(chatId, timestampMillis)) {
                    NotificationHelper.showMessageNotification(
                        context = applicationContext,
                        title = title,
                        body = body,
                        chatId = chatId,
                        peerId = peerId,
                        isSecret = isSecret
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "ChatFCM"
    }
}
