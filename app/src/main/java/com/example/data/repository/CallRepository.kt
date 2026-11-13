package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.IceCandidateDoc
import com.example.data.model.UserPublicProfile
import com.example.data.model.VoiceCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CallRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in before accessing calls.")

    fun observeCallsForUser(uid: String): Flow<List<VoiceCall>> =
        db.collection("calls")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(VoiceCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "calls")
                }
                throw error
            }

    fun observeIncomingRingingCalls(uid: String): Flow<List<VoiceCall>> =
        db.collection("calls")
            .whereArrayContains("participantIds", uid)
            .whereEqualTo("status", "ringing")
            .snapshots()
            .map { snap ->
                snap.toObjects(VoiceCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.calleeId == uid }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "calls")
                }
                throw error
            }

    fun observeCall(callId: String): Flow<VoiceCall?> =
        db.collection("calls").document(callId)
            .snapshots()
            .map { snap -> snap.toObject(VoiceCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "calls/$callId")
                }
                throw error
            }

    suspend fun createCall(caller: UserPublicProfile, callee: UserPublicProfile): String {
        val uid = requireUserId()
        require(uid == caller.uid) { "Caller mismatch" }
        val callRef = db.collection("calls").document()
        val payload = mapOf(
            "callerId" to caller.uid,
            "callerName" to caller.displayName,
            "callerPhotoUrl" to caller.photoUrl,
            "calleeId" to callee.uid,
            "calleeName" to callee.displayName,
            "calleePhotoUrl" to callee.photoUrl,
            "participantIds" to listOf(caller.uid, callee.uid).sorted(),
            "status" to "ringing",
            "offerSdp" to "",
            "answerSdp" to "",
            "durationSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            callRef.set(payload).await()
            return callRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, callRef.path)
            throw e
        }
    }

    suspend fun attachOffer(callId: String, offerSdp: String) {
        val callRef = db.collection("calls").document(callId)
        try {
            callRef.update(
                mapOf(
                    "offerSdp" to offerSdp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            throw e
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String) {
        val callRef = db.collection("calls").document(callId)
        try {
            callRef.update(
                mapOf(
                    "answerSdp" to answerSdp,
                    "status" to "connected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            throw e
        }
    }

    suspend fun endCall(callId: String, finalStatus: String, durationSeconds: Long) {
        val callRef = db.collection("calls").document(callId)
        try {
            val snap = callRef.get().await()
            val currentStatus = snap.getString("status").orEmpty()
            if (currentStatus == "ringing" || currentStatus == "connected") {
                callRef.update(
                    mapOf(
                        "status" to finalStatus,
                        "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        participantIds: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdp: String
    ) {
        val uid = requireUserId()
        val candRef = db.collection("calls").document(callId).collection("candidates").document()
        val payload = mapOf(
            "senderId" to uid,
            "participantIds" to participantIds,
            "sdpMid" to sdpMid,
            "sdpMLineIndex" to sdpMLineIndex.toLong(),
            "sdp" to sdp,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            candRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, candRef.path)
        }
    }

    fun observeIceCandidates(callId: String, currentUserId: String): Flow<List<IceCandidateDoc>> =
        db.collection("calls").document(callId).collection("candidates")
            .whereArrayContains("participantIds", currentUserId)
            .snapshots()
            .map { snap -> snap.toObjects(IceCandidateDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == currentUserId) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "calls/$callId/candidates")
                }
                throw error
            }
}
