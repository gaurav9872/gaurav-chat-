package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.crypto.SecretCryptoManager
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun authenticatedUsers_canCreateProfileChatAndSecretMessage_andDeleteOnRead() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = SecretCryptoManager(context)

        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceUser = checkNotNull(auth.currentUser)
        val aliceRepo = ChatRepository(firestore, auth)
        val alicePubKey = crypto.getOrCreatePublicKeyBase64(aliceUid)
        withTimeout(TIMEOUT_MS) { aliceRepo.ensureUserProfile(aliceUser, alicePubKey) }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobUser = checkNotNull(auth.currentUser)
        val bobRepo = ChatRepository(firestore, auth)
        val bobPubKey = crypto.getOrCreatePublicKeyBase64(bobUid)
        withTimeout(TIMEOUT_MS) { bobRepo.ensureUserProfile(bobUser, bobPubKey) }

        val aliceProfile = checkNotNull(withTimeout(TIMEOUT_MS) { bobRepo.getUserById(aliceUid) })
        val bobProfile = checkNotNull(withTimeout(TIMEOUT_MS) { bobRepo.getUserById(bobUid) })

        val chatId = withTimeout(TIMEOUT_MS) { bobRepo.createOrGetChat(bobProfile, aliceProfile) }
        val participants = listOf(aliceUid, bobUid).sorted()

        val msgId = withTimeout(TIMEOUT_MS) {
            bobRepo.sendMessage(
                chatId = chatId,
                receiverId = aliceUid,
                participantIds = participants,
                plaintext = "Top secret payload",
                isSecret = true,
                cryptoManager = crypto,
                peerPublicKeyBase64 = aliceProfile.publicKey
            )
        }

        // Switch back to Alice, read, decrypt, and auto-delete
        signInTestUser(ALICE_EMAIL)
        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list -> list.any { it.id == msgId } }
        }
        val secretMsg = messages.first { it.id == msgId }
        assertTrue(secretMsg.isSecret)
        assertEquals("", secretMsg.text)

        val decrypted = crypto.decryptSecretMessage(
            ciphertextBase64 = secretMsg.ciphertext,
            ivBase64 = secretMsg.iv,
            myUid = aliceUid,
            chatId = chatId,
            peerPublicKeyBase64 = bobPubKey
        )
        assertEquals("Top secret payload", decrypted)

        withTimeout(TIMEOUT_MS) { aliceRepo.deleteMessage(chatId, msgId) }
        val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list -> list.none { it.id == msgId } }
        }
        assertTrue(afterDelete.none { it.id == msgId })

        // Switch to Bob, send normal message, then Alice marks delivered and read
        signInTestUser(BOB_EMAIL)
        val normalMsgId = withTimeout(TIMEOUT_MS) {
            bobRepo.sendMessage(
                chatId = chatId,
                receiverId = aliceUid,
                participantIds = participants,
                plaintext = "Normal hello!",
                isSecret = false,
                cryptoManager = crypto,
                peerPublicKeyBase64 = aliceProfile.publicKey
            )
        }
        signInTestUser(ALICE_EMAIL)
        withTimeout(TIMEOUT_MS) { aliceRepo.markChatDelivered(chatId, aliceUid) }
        val deliveredList = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list ->
                list.any { it.id == normalMsgId && it.status == "delivered" }
            }
        }
        assertEquals("delivered", deliveredList.first { it.id == normalMsgId }.status)

        withTimeout(TIMEOUT_MS) {
            aliceRepo.markMessageStatus(chatId, normalMsgId, "read")
            aliceRepo.markChatRead(chatId, aliceUid, bobUid)
        }
        val readList = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list ->
                list.any { it.id == normalMsgId && it.status == "read" }
            }
        }
        assertEquals("read", readList.first { it.id == normalMsgId }.status)

        // Calling markChatDelivered after read must preserve "read" (no regression)
        withTimeout(TIMEOUT_MS) { aliceRepo.markChatDelivered(chatId, aliceUid) }

        val aliceChats = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeChats(aliceUid).first { it.any { c -> c.id == chatId } }
        }
        assertEquals("read", aliceChats.first { it.id == chatId }.lastMessageStatus)
        val searchHits = withTimeout(TIMEOUT_MS) {
            aliceRepo.searchMessagesAcrossChats(
                currentUserId = aliceUid,
                threads = aliceChats,
                usersById = mapOf(bobUid to bobProfile),
                query = "hello"
            )
        }
        assertTrue(searchHits.any { it.messageId == normalMsgId && it.text == "Normal hello!" })

        // Verify attachment message persistence and deserialization
        signInTestUser(BOB_EMAIL)
        val docMsgId = withTimeout(TIMEOUT_MS) {
            bobRepo.sendAttachmentMessage(
                chatId = chatId,
                receiverId = aliceUid,
                participantIds = participants,
                attachment = UploadedAttachment(
                    url = "https://firebasestorage.googleapis.com/v0/b/test/o/spec.pdf",
                    type = "document",
                    name = "spec.pdf",
                    sizeBytes = 4096L,
                    mimeType = "application/pdf"
                ),
                caption = "Project spec"
            )
        }
        signInTestUser(ALICE_EMAIL)
        val withAttachment = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeMessages(chatId, aliceUid).first { list ->
                list.any { it.id == docMsgId }
            }
        }
        val docMsg = withAttachment.first { it.id == docMsgId }
        assertEquals("document", docMsg.attachmentType)
        assertEquals("spec.pdf", docMsg.attachmentName)
        assertEquals(4096L, docMsg.attachmentSizeBytes)
        assertEquals("Project spec", docMsg.text)
    }

    @Test
    fun unauthenticatedUser_observeChats_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ChatRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChats("nonexistent_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val fsError = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError.code)
    }

    @Test
    fun thirdPartyUser_cannotReadAnotherPairChats() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceUser = checkNotNull(auth.currentUser)
        val repo = ChatRepository(firestore, auth)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = SecretCryptoManager(context)
        withTimeout(TIMEOUT_MS) { repo.ensureUserProfile(aliceUser, crypto.getOrCreatePublicKeyBase64(aliceUid)) }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobUser = checkNotNull(auth.currentUser)
        withTimeout(TIMEOUT_MS) { repo.ensureUserProfile(bobUser, crypto.getOrCreatePublicKeyBase64(bobUid)) }

        val aliceProfile = withTimeout(TIMEOUT_MS) { repo.getUserById(aliceUid)!! }
        val bobProfile = withTimeout(TIMEOUT_MS) { repo.getUserById(bobUid)!! }
        val chatId = withTimeout(TIMEOUT_MS) { repo.createOrGetChat(bobProfile, aliceProfile) }

        val charlieUid = signInTestUser(CHARLIE_EMAIL)
        var chatCaught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChat(chatId).first()
            }
        } catch (e: Exception) {
            chatCaught = e
        }
        val chatFsError = checkNotNull(chatCaught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for third party observeChat, got $chatCaught (charlie=$charlieUid)"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, chatFsError.code)

        var listCaught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("chats").document(chatId).collection("messages").snapshots().first()
            }
        } catch (e: Exception) {
            listCaught = e
        }
        val listFsError = checkNotNull(listCaught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for third party unfiltered messages, got $listCaught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, listFsError.code)
    }

    @Test
    fun observeChats_ordersNewestSentAndReceivedChatFirst() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = SecretCryptoManager(context)
        val repo = ChatRepository(firestore, auth)

        val aliceUid = signInTestUser(ALICE_EMAIL)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(checkNotNull(auth.currentUser), crypto.getOrCreatePublicKeyBase64(aliceUid))
        }
        val bobUid = signInTestUser(BOB_EMAIL)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(checkNotNull(auth.currentUser), crypto.getOrCreatePublicKeyBase64(bobUid))
        }
        val charlieUid = signInTestUser(CHARLIE_EMAIL)
        withTimeout(TIMEOUT_MS) {
            repo.ensureUserProfile(checkNotNull(auth.currentUser), crypto.getOrCreatePublicKeyBase64(charlieUid))
        }

        val aliceProfile = checkNotNull(withTimeout(TIMEOUT_MS) { repo.getUserById(aliceUid) })
        val bobProfile = checkNotNull(withTimeout(TIMEOUT_MS) { repo.getUserById(bobUid) })
        val charlieProfile = checkNotNull(withTimeout(TIMEOUT_MS) { repo.getUserById(charlieUid) })

        // Alice creates chat with Bob first, then chat with Charlie
        signInTestUser(ALICE_EMAIL)
        val chatBobId = withTimeout(TIMEOUT_MS) { repo.createOrGetChat(aliceProfile, bobProfile) }
        val chatCharlieId = withTimeout(TIMEOUT_MS) { repo.createOrGetChat(aliceProfile, charlieProfile) }

        // Alice sends message to Bob (makes Bob's chat newer than Charlie's)
        withTimeout(TIMEOUT_MS) {
            repo.sendMessage(
                chatId = chatBobId,
                receiverId = bobUid,
                participantIds = listOf(aliceUid, bobUid).sorted(),
                plaintext = "Hi Bob, making your chat newest",
                isSecret = false,
                cryptoManager = crypto,
                peerPublicKeyBase64 = bobProfile.publicKey
            )
        }
        val afterAliceToBob = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats(aliceUid).first { list ->
                list.size >= 2 && list.first().id == chatBobId
            }
        }
        assertEquals(chatBobId, afterAliceToBob.first().id)

        // Charlie sends message to Alice -> Alice's list must automatically move Charlie's chat to the top
        signInTestUser(CHARLIE_EMAIL)
        withTimeout(TIMEOUT_MS) {
            repo.sendMessage(
                chatId = chatCharlieId,
                receiverId = aliceUid,
                participantIds = listOf(aliceUid, charlieUid).sorted(),
                plaintext = "Hi Alice, I should be at the top now!",
                isSecret = false,
                cryptoManager = crypto,
                peerPublicKeyBase64 = aliceProfile.publicKey
            )
        }
        signInTestUser(ALICE_EMAIL)
        val afterCharlieToAlice = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats(aliceUid).first { list ->
                list.size >= 2 && list.first().id == chatCharlieId
            }
        }
        assertEquals(chatCharlieId, afterCharlieToAlice.first().id)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_kt@test.com"
        const val BOB_EMAIL = "bob_kt@test.com"
        const val CHARLIE_EMAIL = "charlie_kt@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
