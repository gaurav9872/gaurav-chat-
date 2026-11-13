package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_MESSAGES = "messages_channel"
    const val CHANNEL_CALLS = "calls_channel"
    const val CHANNEL_SYNC = "sync_status_channel"

    const val ACTION_INLINE_REPLY = "com.example.notifications.ACTION_INLINE_REPLY"
    const val ACTION_DECLINE_CALL = "com.example.notifications.ACTION_DECLINE_CALL"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_IS_SECRET = "extra_is_secret"
    const val SYNC_NOTIFICATION_ID = 99001

    private var activeRingtone: Ringtone? = null
    private var isVibrating = false

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val msgSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val msgAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val msgChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time 1-on-1 message alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 120, 220)
                setSound(msgSound, msgAudioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val callAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                context.getString(R.string.notification_channel_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming WebRTC voice call alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 800)
                setSound(ringtoneUri, callAudioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notification_channel_sync),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps real-time message and call notifications active"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            manager.createNotificationChannel(msgChannel)
            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(syncChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildSyncForegroundNotification(context: Context): Notification {
        createNotificationChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            SYNC_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Connected for real-time messages & calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .build()
    }

    fun showMessageNotification(
        context: Context,
        title: String,
        body: String,
        chatId: String,
        peerId: String = "",
        isSecret: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val safeTitle = title.ifBlank { "Chat Gaurav" }
        val displayBody = if (isSecret) {
            "Encrypted Secret Message"
        } else {
            body.ifBlank { "Sent a message" }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RealtimeNotificationCoordinator.EXTRA_CHAT_ID, chatId)
            putExtra(RealtimeNotificationCoordinator.EXTRA_PEER_ID, peerId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            chatNotificationId(chatId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mePerson = Person.Builder().setName("You").build()
        val senderPerson = Person.Builder().setName(safeTitle).build()
        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .addMessage(displayBody, System.currentTimeMillis(), senderPerson)

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(safeTitle)
            .setContentText(displayBody)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open Chat", openPendingIntent)

        if (peerId.isNotBlank()) {
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Reply to $safeTitle")
                .build()
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_INLINE_REPLY
                putExtra(RealtimeNotificationCoordinator.EXTRA_CHAT_ID, chatId)
                putExtra(RealtimeNotificationCoordinator.EXTRA_PEER_ID, peerId)
                putExtra(EXTRA_IS_SECRET, isSecret)
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                chatNotificationId(chatId) + 17,
                replyIntent,
                replyFlags
            )
            val replyAction = NotificationCompat.Action.Builder(
                0,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
            builder.addAction(replyAction)
        }

        try {
            NotificationManagerCompat.from(context).notify(chatNotificationId(chatId), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked dynamically
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        callerName: String,
        callId: String
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val safeCallerName = callerName.ifBlank { "Incoming Caller" }
        val baseId = callNotificationId(callId)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ID, callId)
            putExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ACTION, RealtimeNotificationCoordinator.CALL_ACTION_OPEN)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            baseId + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ID, callId)
            putExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ACTION, RealtimeNotificationCoordinator.CALL_ACTION_ACCEPT)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            baseId + 2,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(RealtimeNotificationCoordinator.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            baseId + 3,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(safeCallerName)
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            acceptPendingIntent
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Voice Call")
            .setContentText("$safeCallerName is calling on Chat Gaurav")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .setStyle(callStyle)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(baseId, notification)
        } catch (_: SecurityException) {
            // Permission revoked dynamically
        }
    }

    fun cancelMessageNotification(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatNotificationId(chatId))
    }

    fun cancelCallNotification(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(callNotificationId(callId))
    }

    private fun chatNotificationId(chatId: String): Int =
        10000 + (chatId.hashCode() and 0x0FFF_FFFF) % 40000

    private fun callNotificationId(callId: String): Int =
        55000 + (callId.hashCode() and 0x0FFF_FFFF) % 40000

    fun startIncomingRingtoneAndVibration(context: Context) {
        try {
            if (activeRingtone?.isPlaying != true) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                activeRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
            }
            val vibrator = getVibrator(context)
            if (vibrator != null && !isVibrating) {
                isVibrating = true
                val pattern = longArrayOf(0, 600, 400, 600, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {
            // Ignore ringtone errors on devices without audio hardware
        }
    }

    fun stopIncomingRingtoneAndVibration(context: Context) {
        try {
            activeRingtone?.stop()
            activeRingtone = null
            if (isVibrating) {
                getVibrator(context)?.cancel()
                isVibrating = false
            }
        } catch (_: Exception) {
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
