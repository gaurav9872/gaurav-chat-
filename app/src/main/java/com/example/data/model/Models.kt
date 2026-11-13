package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserPublicProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val handle: String = "",
    val emailHash: String = "",
    val photoUrl: String = "",
    val bio: String = "Available on Chat Gaurav",
    val publicKey: String = "",
    @get:PropertyName("isOnline") @field:PropertyName("isOnline") val isOnline: Boolean = false,
    @get:PropertyName("isDiscoverable") @field:PropertyName("isDiscoverable") val isDiscoverable: Boolean = true,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPrivateInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageStatus: String = "sent",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val secretModeEnabled: Boolean = false,
    val typingUids: List<String> = emptyList(),
    val unreadCounts: Map<String, Long> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun ChatThread.lastMessageSortTimestamp(): Timestamp =
    lastMessageAt ?: updatedAt ?: createdAt ?: Timestamp(0, 0)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val participantIds: List<String> = emptyList(),
    val text: String = "",
    @get:PropertyName("isSecret") @field:PropertyName("isSecret") val isSecret: Boolean = false,
    val ciphertext: String = "",
    val iv: String = "",
    val attachmentUrl: String = "",
    val attachmentType: String = "",
    val attachmentName: String = "",
    val attachmentSizeBytes: Long = 0L,
    val attachmentMimeType: String = "",
    val status: String = "sent",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VoiceCall(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val calleePhotoUrl: String = "",
    val participantIds: List<String> = emptyList(),
    val status: String = "ringing",
    val offerSdp: String = "",
    val answerSdp: String = "",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IceCandidateDoc(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val sdp: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AccentPalette(
    val label: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondaryLight: Color,
    val secondaryDark: Color,
    val swatchColor: Color
) {
    EMERALD(
        label = "Emerald Flow",
        primaryLight = Color(0xFF006B5E),
        primaryDark = Color(0xFF53DBC8),
        secondaryLight = Color(0xFF4A635E),
        secondaryDark = Color(0xFFB1CCC6),
        swatchColor = Color(0xFF00897B)
    ),
    COBALT(
        label = "Midnight Cobalt",
        primaryLight = Color(0xFF1558B0),
        primaryDark = Color(0xFFA8C7FA),
        secondaryLight = Color(0xFF535F70),
        secondaryDark = Color(0xFFBBC7DB),
        swatchColor = Color(0xFF1E88E5)
    ),
    CRIMSON(
        label = "Crimson Ember",
        primaryLight = Color(0xFFB3261E),
        primaryDark = Color(0xFFF2B8B5),
        secondaryLight = Color(0xFF775654),
        secondaryDark = Color(0xFFE7BDB9),
        swatchColor = Color(0xFFE53935)
    ),
    VIOLET(
        label = "Royal Violet",
        primaryLight = Color(0xFF6750A4),
        primaryDark = Color(0xFFD0BCFF),
        secondaryLight = Color(0xFF625B71),
        secondaryDark = Color(0xFFCCC2DC),
        swatchColor = Color(0xFF8E24AA)
    ),
    AMBER(
        label = "Sunset Amber",
        primaryLight = Color(0xFF8F4E00),
        primaryDark = Color(0xFFFFB870),
        secondaryLight = Color(0xFF735A42),
        secondaryDark = Color(0xFFE2C0A4),
        swatchColor = Color(0xFFFB8C00)
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ChatSearchFilter(val label: String) {
    ALL("All"),
    CONTACTS("Contacts"),
    MESSAGES("Messages")
}

data class MessageSearchResult(
    val chatId: String,
    val peerId: String,
    val peerName: String,
    val peerPhotoUrl: String,
    val messageId: String,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long
)
