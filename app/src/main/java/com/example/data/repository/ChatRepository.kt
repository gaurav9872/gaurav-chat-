package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.R
import com.example.data.crypto.SecretCryptoManager
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.MessageSearchResult
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.lastMessageSortTimestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class LocalAttachmentMeta(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val type: String
)

data class UploadedAttachment(
    val url: String,
    val type: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
)

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storageProvider: () -> FirebaseStorage = { FirebaseStorage.getInstance() }
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in before accessing Firestore.")

    suspend fun ensureUserProfile(user: FirebaseUser, publicKeyBase64: String) {
        val uid = user.uid
        val publicRef = db.collection("users").document(uid)
        val privateRef = publicRef.collection("private").document("info")
        val rawEmail = user.email.orEmpty().ifBlank { "$uid@chatgaurav.local" }
        val emailHash = SecretCryptoManager.sha256Hex(rawEmail)
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: rawEmail.substringBefore("@").ifBlank { "Chat User" }
        val handle = displayName.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(30).ifBlank { "user_${uid.take(6)}" }
        val photoUrl = user.photoUrl?.toString().orEmpty()

        try {
            val existingPublic = publicRef.get().await()
            if (!existingPublic.exists()) {
                val payload = mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "handle" to handle,
                    "emailHash" to emailHash,
                    "photoUrl" to photoUrl,
                    "bio" to "Available on Chat Gaurav",
                    "publicKey" to publicKeyBase64,
                    "isOnline" to true,
                    "isDiscoverable" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                publicRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to (existingPublic.getString("displayName")?.takeIf { it.isNotBlank() } ?: displayName),
                    "photoUrl" to photoUrl.ifBlank { existingPublic.getString("photoUrl").orEmpty() },
                    "publicKey" to publicKeyBase64,
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                publicRef.update(updatePayload).await()
            }

            val existingPrivate = privateRef.get().await()
            if (!existingPrivate.exists()) {
                val privPayload = mapOf(
                    "userId" to uid,
                    "email" to rawEmail,
                    "fcmToken" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                privateRef.set(privPayload).await()
            } else {
                privateRef.update(
                    mapOf(
                        "email" to rawEmail,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, publicRef.path)
            throw e
        }
    }

    suspend fun updatePresence(uid: String, isOnline: Boolean) {
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "isOnline" to isOnline,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun updateProfileDetails(uid: String, displayName: String, bio: String, isDiscoverable: Boolean) {
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "displayName" to displayName.trim().take(80),
                    "bio" to bio.trim().take(200),
                    "isDiscoverable" to isDiscoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        val docRef = db.collection("users").document(uid).collection("private").document("info")
        try {
            docRef.update(
                mapOf(
                    "fcmToken" to token,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    fun observeUser(uid: String): Flow<UserPublicProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap -> snap.toObject(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "users/$uid")
                }
                throw error
            }

    fun observePrivateInfo(uid: String): Flow<UserPrivateInfo?> =
        db.collection("users").document(uid).collection("private").document("info")
            .snapshots()
            .map { snap -> snap.toObject(UserPrivateInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "users/$uid/private/info")
                }
                throw error
            }

    fun observeDiscoverableUsers(): Flow<List<UserPublicProfile>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users")
                .whereEqualTo("isDiscoverable", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.uid != uid }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "users")
                    }
                    throw error
                }
        )
    }

    suspend fun getUserById(uid: String): UserPublicProfile? {
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.get().await().toObject(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    suspend fun findUserByEmail(email: String): UserPublicProfile? {
        val hash = SecretCryptoManager.sha256Hex(email)
        return try {
            val snap = db.collection("users")
                .whereEqualTo("isDiscoverable", true)
                .whereEqualTo("emailHash", hash)
                .limit(1)
                .get()
                .await()
            snap.toObjects(UserPublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users")
            null
        }
    }

    fun observeChats(uid: String): Flow<List<ChatThread>> =
        db.collection("chats")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageSortTimestamp() }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "chats")
                }
                throw error
            }

    fun observeChat(chatId: String): Flow<ChatThread?> =
        db.collection("chats").document(chatId)
            .snapshots()
            .map { snap -> snap.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "chats/$chatId")
                }
                throw error
            }

    suspend fun createOrGetChat(currentUser: UserPublicProfile, peer: UserPublicProfile): String {
        val uid = requireUserId()
        require(uid == currentUser.uid) { "Caller UID mismatch" }
        val sortedIds = listOf(currentUser.uid, peer.uid).sorted()
        val chatId = sortedIds.joinToString("_")
        val chatRef = db.collection("chats").document(chatId)
        try {
            val snapshot = chatRef.get().await()
            if (!snapshot.exists()) {
                val payload = mapOf(
                    "participantIds" to sortedIds,
                    "participantNames" to mapOf(currentUser.uid to currentUser.displayName, peer.uid to peer.displayName),
                    "participantPhotos" to mapOf(currentUser.uid to currentUser.photoUrl, peer.uid to peer.photoUrl),
                    "lastMessageText" to "",
                    "lastMessageSenderId" to "",
                    "lastMessageStatus" to "sent",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "secretModeEnabled" to false,
                    "typingUids" to emptyList<String>(),
                    "unreadCounts" to mapOf(currentUser.uid to 0L, peer.uid to 0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                chatRef.set(payload).await()
            } else {
                chatRef.update(
                    mapOf(
                        "participantNames" to mapOf(currentUser.uid to currentUser.displayName, peer.uid to peer.displayName),
                        "participantPhotos" to mapOf(currentUser.uid to currentUser.photoUrl, peer.uid to peer.photoUrl),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            return chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, chatRef.path)
            throw e
        }
    }

    fun observeMessages(chatId: String, currentUserId: String): Flow<List<ChatMessage>> =
        db.collection("chats").document(chatId).collection("messages")
            .whereArrayContains("participantIds", currentUserId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == currentUserId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "chats/$chatId/messages")
                }
                throw error
            }

    suspend fun sendMessage(
        chatId: String,
        receiverId: String,
        participantIds: List<String>,
        plaintext: String,
        isSecret: Boolean,
        cryptoManager: SecretCryptoManager,
        peerPublicKeyBase64: String?
    ): String {
        val senderId = requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val chatRef = db.collection("chats").document(chatId)

        val payload = if (isSecret) {
            val encrypted = cryptoManager.encryptSecretMessage(
                plaintext = plaintext,
                myUid = senderId,
                chatId = chatId,
                peerPublicKeyBase64 = peerPublicKeyBase64
            )
            mapOf(
                "chatId" to chatId,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "participantIds" to participantIds,
                "text" to "",
                "isSecret" to true,
                "ciphertext" to encrypted.ciphertextBase64,
                "iv" to encrypted.ivBase64,
                "status" to "sent",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        } else {
            mapOf(
                "chatId" to chatId,
                "senderId" to senderId,
                "receiverId" to receiverId,
                "participantIds" to participantIds,
                "text" to plaintext,
                "isSecret" to false,
                "ciphertext" to "",
                "iv" to "",
                "status" to "sent",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        }

        try {
            val chatSnap = chatRef.get().await()
            val currentUnread = (chatSnap.get("unreadCounts.$receiverId") as? Number)?.toLong() ?: 0L
            val newUnreadCounts = mapOf(
                senderId to 0L,
                receiverId to (currentUnread + 1L)
            )
            val previewText = if (isSecret) "Encrypted secret message" else plaintext.take(120)

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageSenderId" to senderId,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageStatus" to "sent",
                    "unreadCounts" to newUnreadCounts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            return msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun uploadChatAttachment(
        context: Context,
        chatId: String,
        uri: Uri,
        isImage: Boolean
    ): UploadedAttachment {
        requireUserId()
        val meta = extractAttachmentMetadata(context, uri, isImage)
        if (meta.sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw IllegalArgumentException("File exceeds the 25 MB limit.")
        }

        val sanitizedFileName = meta.name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(120)
            .ifBlank { if (isImage) "image.jpg" else "document" }
        val objectPath = "chats/$chatId/attachments/${UUID.randomUUID()}_$sanitizedFileName"
        val storageRef = storageProvider().reference.child(objectPath)

        return try {
            storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            UploadedAttachment(
                url = downloadUrl,
                type = meta.type,
                name = meta.name,
                sizeBytes = meta.sizeBytes,
                mimeType = meta.mimeType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StorageException) {
            val detail = when (e.errorCode) {
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND,
                StorageException.ERROR_OBJECT_NOT_FOUND ->
                    "Firebase Storage is not activated yet. Enable Storage in the Firebase Console for this project."
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Storage permission denied. Ensure Firebase Storage rules allow authenticated users."
                else -> e.localizedMessage ?: "Firebase Storage upload failed."
            }
            throw IllegalStateException(detail, e)
        }
    }

    suspend fun sendAttachmentMessage(
        chatId: String,
        receiverId: String,
        participantIds: List<String>,
        attachment: UploadedAttachment,
        caption: String = ""
    ): String {
        val senderId = requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val chatRef = db.collection("chats").document(chatId)
        val cleanCaption = caption.trim().take(4096)

        val payload = mapOf(
            "chatId" to chatId,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "participantIds" to participantIds,
            "text" to cleanCaption,
            "isSecret" to false,
            "ciphertext" to "",
            "iv" to "",
            "attachmentUrl" to attachment.url.take(2048),
            "attachmentType" to attachment.type,
            "attachmentName" to attachment.name.take(256),
            "attachmentSizeBytes" to attachment.sizeBytes.coerceIn(0L, MAX_ATTACHMENT_BYTES),
            "attachmentMimeType" to attachment.mimeType.take(128),
            "status" to "sent",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val chatSnap = chatRef.get().await()
            val currentUnread = (chatSnap.get("unreadCounts.$receiverId") as? Number)?.toLong() ?: 0L
            val newUnreadCounts = mapOf(
                senderId to 0L,
                receiverId to (currentUnread + 1L)
            )
            val previewText = when {
                cleanCaption.isNotEmpty() -> cleanCaption.take(120)
                attachment.type == "image" -> "Photo: ${attachment.name}".take(120)
                else -> "Document: ${attachment.name}".take(120)
            }

            val batch = db.batch()
            batch.set(msgRef, payload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageSenderId" to senderId,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageStatus" to "sent",
                    "unreadCounts" to newUnreadCounts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            return msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun markMessageStatus(chatId: String, messageId: String, status: String) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            msgRef.update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
        }
    }

    suspend fun markChatDelivered(chatId: String, currentUserId: String) {
        val chatRef = db.collection("chats").document(chatId)
        try {
            db.runTransaction { tx ->
                val chatSnap = tx.get(chatRef)
                val lastSender = chatSnap.getString("lastMessageSenderId").orEmpty()
                val lastStatus = chatSnap.getString("lastMessageStatus").orEmpty()
                if (lastSender.isNotBlank() && lastSender != currentUserId && lastStatus == "sent") {
                    tx.update(
                        chatRef,
                        mapOf(
                            "lastMessageStatus" to "delivered",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()

            val undelivered = chatRef.collection("messages")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "sent")
                .get()
                .await()
            undelivered.documents.forEach { doc ->
                if (doc.getString("receiverId") == currentUserId && doc.getString("status") == "sent") {
                    try {
                        doc.reference.update(
                            mapOf(
                                "status" to "delivered",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Ignore if receiver concurrently moved message forward to "read"
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
        }
    }

    suspend fun markChatRead(chatId: String, currentUserId: String, peerId: String) {
        val chatRef = db.collection("chats").document(chatId)
        try {
            val snap = chatRef.get().await()
            val lastSender = snap.getString("lastMessageSenderId").orEmpty()
            val currentMap = mapOf(
                currentUserId to 0L,
                peerId to ((snap.get("unreadCounts.$peerId") as? Number)?.toLong() ?: 0L)
            )
            val updateMap = mutableMapOf<String, Any>(
                "unreadCounts" to currentMap,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (lastSender == peerId) {
                updateMap["lastMessageStatus"] = "read"
            }
            chatRef.update(updateMap).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        try {
            msgRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgRef.path)
        }
    }

    suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        val chatRef = db.collection("chats").document(chatId)
        try {
            val snap = chatRef.get().await()
            val existing = (snap.get("typingUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val updated = if (isTyping) {
                (existing + uid).distinct().take(2)
            } else {
                existing.filterNot { it == uid }
            }
            if (updated != existing) {
                chatRef.update(
                    mapOf(
                        "typingUids" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
        }
    }

    suspend fun toggleSecretMode(chatId: String, enabled: Boolean) {
        val chatRef = db.collection("chats").document(chatId)
        try {
            chatRef.update(
                mapOf(
                    "secretModeEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
            throw e
        }
    }

    suspend fun searchMessagesAcrossChats(
        currentUserId: String,
        threads: List<ChatThread>,
        usersById: Map<String, UserPublicProfile>,
        query: String
    ): List<MessageSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || threads.isEmpty()) return emptyList()
        if (auth.currentUser?.uid != currentUserId && auth.currentUser != null) return emptyList()

        return coroutineScope {
            threads.take(25).map { thread ->
                async {
                    val peerId = thread.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                    val peer = usersById[peerId]
                    val peerName = peer?.displayName?.takeIf { it.isNotBlank() }
                        ?: thread.participantNames[peerId].orEmpty().ifBlank { "Chat User" }
                    val peerPhoto = peer?.photoUrl?.takeIf { it.isNotBlank() }
                        ?: thread.participantPhotos[peerId].orEmpty()

                    try {
                        val snap = db.collection("chats")
                            .document(thread.id)
                            .collection("messages")
                            .whereArrayContains("participantIds", currentUserId)
                            .limit(100)
                            .get()
                            .await()
                        snap.toObjects(
                            ChatMessage::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).mapNotNull { msg ->
                            val searchableContent = listOf(msg.text, msg.attachmentName)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                            if (!msg.isSecret && searchableContent.contains(cleanQuery, ignoreCase = true)) {
                                MessageSearchResult(
                                    chatId = thread.id,
                                    peerId = peerId,
                                    peerName = peerName,
                                    peerPhotoUrl = peerPhoto,
                                    messageId = msg.id,
                                    senderId = msg.senderId,
                                    text = searchableContent,
                                    createdAtMillis = msg.createdAt?.toDate()?.time ?: 0L
                                )
                            } else {
                                null
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.LIST, "chats/${thread.id}/messages")
                        emptyList()
                    }
                }
            }.awaitAll()
                .flatten()
                .sortedByDescending { it.createdAtMillis }
        }
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L

        fun extractAttachmentMetadata(
            context: Context,
            uri: Uri,
            isImage: Boolean
        ): LocalAttachmentMeta {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(uri).orEmpty()
            val resolvedType = if (isImage || detectedMime.startsWith("image/")) "image" else "document"
            var displayName = ""
            var sizeBytes = 0L

            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx).orEmpty()
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to URI segment if provider doesn't expose OpenableColumns
            }

            if (displayName.isBlank()) {
                displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: if (resolvedType == "image") "photo_${System.currentTimeMillis()}.jpg" else "document_${System.currentTimeMillis()}"
            }

            val finalMime = detectedMime.ifBlank {
                if (resolvedType == "image") "image/jpeg" else "application/octet-stream"
            }

            return LocalAttachmentMeta(
                name = displayName.take(256),
                sizeBytes = sizeBytes,
                mimeType = finalMime.take(128),
                type = resolvedType
            )
        }
    }
}
