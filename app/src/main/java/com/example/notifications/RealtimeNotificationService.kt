package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RealtimeNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var chatsListenerJob: Job? = null
    private var callsListenerJob: Job? = null
    private var settingsListenerJob: Job? = null
    private var activeUid: String? = null
    private var notificationsEnabled = true
    private var hasSeededChats = false
    private val activeRingingCallIds = mutableSetOf<String>()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val uid = auth.currentUser?.uid
        if (uid == null) {
            stopListeners()
            stopSelf()
        } else if (uid != activeUid) {
            startListenersForUser(uid)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationHelper.SYNC_NOTIFICATION_ID,
            NotificationHelper.buildSyncForegroundNotification(this),
            fgType
        )

        settingsListenerJob = serviceScope.launch {
            ThemePreferences(applicationContext).settings.collectLatest { settings ->
                notificationsEnabled = settings.notificationsEnabled
                if (!notificationsEnabled) {
                    NotificationHelper.stopIncomingRingtoneAndVibration(applicationContext)
                    stopListeners()
                    stopSelf()
                }
            }
        }

        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (uid != activeUid) {
            startListenersForUser(uid)
        }
        return START_STICKY
    }

    private fun startListenersForUser(uid: String) {
        stopListeners()
        activeUid = uid
        hasSeededChats = false

        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        val chatRepository = ChatRepository(db)
        val callRepository = CallRepository(db)

        chatsListenerJob = serviceScope.launch {
            chatRepository.observeChats(uid)
                .catch { error ->
                    Log.e(TAG, "Chat notification listener error", error)
                }
                .collectLatest { threads ->
                    if (!hasSeededChats) {
                        hasSeededChats = true
                        threads.forEach { thread ->
                            val ts = thread.lastMessageAt?.toDate()?.time ?: 0L
                            RealtimeNotificationCoordinator.seedChatTimestamp(thread.id, ts)
                        }
                        return@collectLatest
                    }

                    if (!notificationsEnabled) return@collectLatest

                    threads.forEach { thread ->
                        val senderId = thread.lastMessageSenderId
                        if (senderId.isBlank() || senderId == uid) return@forEach

                        val ts = thread.lastMessageAt?.toDate()?.time ?: 0L
                        val unread = (thread.unreadCounts[uid] ?: 0L) > 0L

                        if (thread.lastMessageStatus == "sent" || unread) {
                            launch {
                                chatRepository.markChatDelivered(thread.id, uid)
                            }
                        }

                        if (unread && ts > 0L && RealtimeNotificationCoordinator.shouldNotifyMessage(thread.id, ts)) {
                            val peerId = thread.participantIds.firstOrNull { it != uid } ?: senderId
                            val senderName = thread.participantNames[senderId]?.takeIf { it.isNotBlank() }
                                ?: "New message"
                            val isSecret = thread.secretModeEnabled ||
                                thread.lastMessageText.equals("Encrypted secret message", ignoreCase = true)
                            val preview = if (isSecret) {
                                "Encrypted Secret Message"
                            } else {
                                thread.lastMessageText.ifBlank { "Sent a message" }
                            }
                            NotificationHelper.showMessageNotification(
                                context = applicationContext,
                                title = senderName,
                                body = preview,
                                chatId = thread.id,
                                peerId = peerId,
                                isSecret = isSecret
                            )
                        }
                    }
                }
        }

        callsListenerJob = serviceScope.launch {
            callRepository.observeIncomingRingingCalls(uid)
                .catch { error ->
                    Log.e(TAG, "Call notification listener error", error)
                }
                .collectLatest { ringingCalls ->
                    val currentIds = ringingCalls.map { it.id }.toSet()
                    val endedOrAnswered = activeRingingCallIds - currentIds
                    endedOrAnswered.forEach { endedCallId ->
                        NotificationHelper.stopIncomingRingtoneAndVibration(applicationContext)
                        NotificationHelper.cancelCallNotification(applicationContext, endedCallId)
                        RealtimeNotificationCoordinator.clearCallNotified(endedCallId)
                    }
                    activeRingingCallIds.clear()
                    activeRingingCallIds.addAll(currentIds)

                    if (!notificationsEnabled) return@collectLatest

                    ringingCalls.forEach { call ->
                        if (RealtimeNotificationCoordinator.shouldNotifyCall(call.id)) {
                            NotificationHelper.startIncomingRingtoneAndVibration(applicationContext)
                            NotificationHelper.showIncomingCallNotification(
                                context = applicationContext,
                                callerName = call.callerName.ifBlank { "Someone" },
                                callId = call.id
                            )
                        }
                    }
                }
        }
    }

    private fun stopListeners() {
        activeUid = null
        chatsListenerJob?.cancel()
        chatsListenerJob = null
        callsListenerJob?.cancel()
        callsListenerJob = null
        activeRingingCallIds.forEach { callId ->
            NotificationHelper.cancelCallNotification(applicationContext, callId)
            RealtimeNotificationCoordinator.clearCallNotified(callId)
        }
        activeRingingCallIds.clear()
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        stopListeners()
        settingsListenerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RealtimeNotifService"

        fun start(context: Context) {
            if (!NotificationHelper.hasNotificationPermission(context)) return
            try {
                val intent = Intent(context, RealtimeNotificationService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start foreground sync service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RealtimeNotificationService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
