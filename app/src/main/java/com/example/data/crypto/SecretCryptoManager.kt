package com.example.data.crypto

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

class SecretCryptoManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun getOrCreatePublicKeyBase64(uid: String): String {
        val keyPair = getOrCreateKeyPair(uid)
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    private fun getOrCreateKeyPair(uid: String): KeyPair {
        val privStr = prefs.getString("${KEY_PRIV_PREFIX}$uid", null)
        val pubStr = prefs.getString("${KEY_PUB_PREFIX}$uid", null)
        if (!privStr.isNullOrBlank() && !pubStr.isNullOrBlank()) {
            try {
                val kf = KeyFactory.getInstance("EC")
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privStr, Base64.NO_WRAP)))
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubStr, Base64.NO_WRAP)))
                return KeyPair(pub, priv)
            } catch (_: Exception) {
                // Regenerate below if corrupted
            }
        }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        prefs.edit()
            .putString("${KEY_PRIV_PREFIX}$uid", Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .putString("${KEY_PUB_PREFIX}$uid", Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .apply()
        return pair
    }

    private fun deriveSharedAesKey(
        myUid: String,
        chatId: String,
        peerPublicKeyBase64: String?
    ): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        if (!peerPublicKeyBase64.isNullOrBlank()) {
            try {
                val myPair = getOrCreateKeyPair(myUid)
                val kf = KeyFactory.getInstance("EC")
                val peerPubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
                val peerPub = kf.generatePublic(X509EncodedKeySpec(peerPubBytes))
                val agreement = KeyAgreement.getInstance("ECDH")
                agreement.init(myPair.private)
                agreement.doPhase(peerPub, true)
                val sharedSecret = agreement.generateSecret()
                digest.update(sharedSecret)
                digest.update(chatId.toByteArray(Charsets.UTF_8))
                return SecretKeySpec(digest.digest(), "AES")
            } catch (_: Exception) {
                // Fall back to deterministic chat key if peer key invalid
            }
        }
        digest.update("ChatGauravE2E:$chatId".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest.digest(), "AES")
    }

    fun encryptSecretMessage(
        plaintext: String,
        myUid: String,
        chatId: String,
        peerPublicKeyBase64: String?
    ): EncryptedPayload {
        val key = deriveSharedAesKey(myUid, chatId, peerPublicKeyBase64)
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptSecretMessage(
        ciphertextBase64: String,
        ivBase64: String,
        myUid: String,
        chatId: String,
        peerPublicKeyBase64: String?
    ): String {
        return try {
            val key = deriveSharedAesKey(myUid, chatId, peerPublicKeyBase64)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            // Also try deterministic fallback in case sender encrypted before peer key synced
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update("ChatGauravE2E:$chatId".toByteArray(Charsets.UTF_8))
                val fallbackKey = SecretKeySpec(digest.digest(), "AES")
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
                cipher.init(Cipher.DECRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            } catch (_: Exception) {
                "Encrypted message (key rotated)"
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "chat_gaurav_e2e_keys"
        private const val KEY_PRIV_PREFIX = "ec_priv_"
        private const val KEY_PUB_PREFIX = "ec_pub_"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128

        fun sha256Hex(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(input.trim().lowercase().toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
