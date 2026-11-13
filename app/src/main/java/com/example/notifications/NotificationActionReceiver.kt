package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.R
import com.example.data.crypto.SecretCryptoManager
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_INLINE_REPLY -> handleInlineReply(context, intent)
            NotificationHelper.ACTION_DECLINE_CALL -> handleDeclineCall(context, intent)
        }
    }

    private fun handleInlineReply(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(RealtimeNotificationCoordinator.EXTRA_CHAT_ID).orEmpty()
        val peerId = intent.getStringExtra(RealtimeNotificationCoordinator.EXTRA_PEER_ID).orEmpty()
        val isSecret = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_SECRET, false)
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (chatId.isNotBlank()) {
            NotificationHelper.cancelMessageNotification(context, chatId)
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (replyText.isBlank() || chatId.isBlank() || peerId.isBlank() || uid == null) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                val chatRepository = ChatRepository(db)
                val peerPublicKey = if (isSecret) {
                    chatRepository.getUserById(peerId)?.publicKey
                } else {
                    null
                }
                chatRepository.sendMessage(
                    chatId = chatId,
                    receiverId = peerId,
                    participantIds = listOf(uid, peerId).sorted(),
                    plaintext = replyText,
                    isSecret = isSecret,
                    cryptoManager = SecretCryptoManager(context),
                    peerPublicKeyBase64 = peerPublicKey
                )
                RealtimeNotificationCoordinator.seedChatTimestamp(chatId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Inline notification reply failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDeclineCall(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ID).orEmpty()
        NotificationHelper.stopIncomingRingtoneAndVibration(context)
        if (callId.isNotBlank()) {
            NotificationHelper.cancelCallNotification(context, callId)
            RealtimeNotificationCoordinator.clearCallNotified(callId)
        }

        if (callId.isBlank() || FirebaseAuth.getInstance().currentUser == null) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                CallRepository(db).endCall(callId, "declined", 0L)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decline call from notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotifActionReceiver"
    }
}
