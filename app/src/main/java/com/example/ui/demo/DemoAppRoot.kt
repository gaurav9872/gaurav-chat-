package com.example.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ChatRoute
import com.example.HomeRoute
import com.example.MyQrRoute
import com.example.QrScannerRoute
import com.example.data.crypto.SecretCryptoManager
import com.example.data.model.ChatSearchFilter
import com.example.data.model.ChatThread
import com.example.data.model.MessageSearchResult
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.VoiceCall
import com.example.data.model.lastMessageSortTimestamp
import com.example.data.preferences.ThemePreferences
import com.example.data.preferences.UserSettings
import com.example.data.repository.ChatRepository
import com.example.ui.call.CallOverlayScreen
import com.example.ui.chat.ChatScreenContent
import com.example.ui.home.HomeScreen
import com.example.ui.qr.MyQrScreen
import com.example.ui.qr.QrScannerScreen
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.DisplayChatMessage
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

@Composable
fun DemoAppRoot(
    userSettings: UserSettings,
    themePrefs: ThemePreferences,
    onExitDemo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val cryptoManager = remember { SecretCryptoManager(context.applicationContext) }
    val demoUid = "demo_guest_uid"
    val myPubKey = remember { cryptoManager.getOrCreatePublicKeyBase64(demoUid) }

    val peerAarav = remember {
        UserPublicProfile(
            id = "demo_aarav_uid",
            uid = "demo_aarav_uid",
            displayName = "Aarav Sharma",
            handle = "aarav_s",
            bio = "Building apps with Chat Gaurav",
            publicKey = myPubKey,
            isOnline = true,
            isDiscoverable = true
        )
    }
    val peerPriya = remember {
        UserPublicProfile(
            id = "demo_priya_uid",
            uid = "demo_priya_uid",
            displayName = "Priya Verma",
            handle = "priya_v",
            bio = "Encrypted mode enthusiast",
            publicKey = myPubKey,
            isOnline = false,
            isDiscoverable = true,
            lastSeen = Timestamp(Date(System.currentTimeMillis() - 18 * 60_000L))
        )
    }

    var currentProfile by remember {
        mutableStateOf(
            UserPublicProfile(
                id = demoUid,
                uid = demoUid,
                displayName = "Gaurav (Demo Guest)",
                handle = "gaurav_demo",
                bio = "Testing Chat Gaurav in Preview Mode",
                publicKey = myPubKey,
                isOnline = true,
                isDiscoverable = true
            )
        )
    }
    val privateInfo = remember {
        UserPrivateInfo(
            id = "info",
            userId = demoUid,
            email = "guest.demo@chatgaurav.local"
        )
    }

    val peers = remember { listOf(peerAarav, peerPriya) }
    val peersById = remember { peers.associateBy { it.uid } }

    var chats by remember {
        val now = System.currentTimeMillis()
        mutableStateOf(
            listOf(
                ChatThread(
                    id = "demo_chat_aarav",
                    participantIds = listOf(demoUid, peerAarav.uid),
                    participantNames = mapOf(demoUid to currentProfile.displayName, peerAarav.uid to peerAarav.displayName),
                    participantPhotos = emptyMap(),
                    lastMessageText = "Try toggling Secret Mode or calling me!",
                    lastMessageSenderId = peerAarav.uid,
                    lastMessageStatus = "read",
                    lastMessageAt = Timestamp(Date(now - 120_000L)),
                    secretModeEnabled = false,
                    unreadCounts = mapOf(demoUid to 1L, peerAarav.uid to 0L)
                ),
                ChatThread(
                    id = "demo_chat_priya",
                    participantIds = listOf(demoUid, peerPriya.uid),
                    participantNames = mapOf(demoUid to currentProfile.displayName, peerPriya.uid to peerPriya.displayName),
                    participantPhotos = emptyMap(),
                    lastMessageText = "QR scanning works from both camera & gallery.",
                    lastMessageSenderId = demoUid,
                    lastMessageStatus = "delivered",
                    lastMessageAt = Timestamp(Date(now - 3_600_000L)),
                    secretModeEnabled = false,
                    unreadCounts = mapOf(demoUid to 0L, peerPriya.uid to 0L)
                )
            )
        )
    }

    val messagesByChat = remember {
        val now = System.currentTimeMillis()
        mutableStateMapOf(
            "demo_chat_aarav" to listOf(
                DisplayChatMessage(
                    id = "demo_m1",
                    senderId = peerAarav.uid,
                    receiverId = demoUid,
                    displayText = "Welcome to Chat Gaurav Demo Mode!",
                    isSecret = false,
                    status = "read",
                    createdAtMillis = now - 300_000L
                ),
                DisplayChatMessage(
                    id = "demo_m2",
                    senderId = demoUid,
                    receiverId = peerAarav.uid,
                    displayText = "Awesome, testing encrypted messaging and search!",
                    isSecret = false,
                    status = "read",
                    createdAtMillis = now - 200_000L
                ),
                DisplayChatMessage(
                    id = "demo_m3",
                    senderId = peerAarav.uid,
                    receiverId = demoUid,
                    displayText = "Try toggling Secret Mode or calling me!",
                    isSecret = false,
                    status = "read",
                    createdAtMillis = now - 120_000L
                )
            ),
            "demo_chat_priya" to listOf(
                DisplayChatMessage(
                    id = "demo_p1",
                    senderId = peerPriya.uid,
                    receiverId = demoUid,
                    displayText = "Hey! Did you check the My QR screen?",
                    isSecret = false,
                    status = "read",
                    createdAtMillis = now - 4_000_000L
                ),
                DisplayChatMessage(
                    id = "demo_p2",
                    senderId = demoUid,
                    receiverId = peerPriya.uid,
                    displayText = "QR scanning works from both camera & gallery.",
                    isSecret = false,
                    status = "delivered",
                    createdAtMillis = now - 3_600_000L
                )
            )
        )
    }

    var callHistory by remember {
        mutableStateOf(
            listOf(
                VoiceCall(
                    id = "demo_call_1",
                    callerId = demoUid,
                    callerName = currentProfile.displayName,
                    calleeId = peerAarav.uid,
                    calleeName = peerAarav.displayName,
                    participantIds = listOf(demoUid, peerAarav.uid),
                    status = "ended",
                    durationSeconds = 48L,
                    createdAt = Timestamp(Date(System.currentTimeMillis() - 7_200_000L))
                )
            )
        )
    }

    var activeCallState by remember { mutableStateOf(ActiveCallUiState()) }
    var activeCallPeerId by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(ChatSearchFilter.ALL) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var secretNotice by remember { mutableStateOf<String?>(null) }

    // Burn countdown for secret messages in demo mode
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            messagesByChat.keys.toList().forEach { chatId ->
                val currentList = messagesByChat[chatId].orEmpty()
                if (currentList.any { it.burnSecondsRemaining != null }) {
                    messagesByChat[chatId] = currentList.mapNotNull { item ->
                        val rem = item.burnSecondsRemaining
                        if (rem == null) item
                        else if (rem <= 1) null
                        else item.copy(burnSecondsRemaining = rem - 1)
                    }
                }
            }
        }
    }

    // Call timer simulation in demo mode
    LaunchedEffect(activeCallState.status) {
        if (activeCallState.status == "ringing" && !activeCallState.isIncoming) {
            delay(1800L)
            if (activeCallState.status == "ringing") {
                activeCallState = activeCallState.copy(
                    status = "connected",
                    durationSeconds = 0L,
                    iceStateLabel = "Preview Audio Simulation"
                )
            }
        } else if (activeCallState.status == "connected") {
            while (activeCallState.status == "connected") {
                delay(1000L)
                activeCallState = activeCallState.copy(durationSeconds = activeCallState.durationSeconds + 1L)
            }
        }
    }

    val messageSearchResults = remember(searchQuery, searchFilter, chats, messagesByChat.toMap()) {
        val clean = searchQuery.trim()
        if (clean.isEmpty() || searchFilter == ChatSearchFilter.CONTACTS) emptyList()
        else {
            chats.flatMap { thread ->
                val peerId = thread.participantIds.firstOrNull { it != demoUid }.orEmpty()
                val peerName = peersById[peerId]?.displayName ?: thread.participantNames[peerId].orEmpty()
                messagesByChat[thread.id].orEmpty().mapNotNull { msg ->
                    val searchable = listOf(msg.displayText, msg.attachmentName)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (!msg.isSecret && searchable.contains(clean, ignoreCase = true)) {
                        MessageSearchResult(
                            chatId = thread.id,
                            peerId = peerId,
                            peerName = peerName,
                            peerPhotoUrl = "",
                            messageId = msg.id,
                            senderId = msg.senderId,
                            text = searchable,
                            createdAtMillis = msg.createdAtMillis
                        )
                    } else null
                }
            }.sortedByDescending { it.createdAtMillis }
        }
    }

    fun startDemoCall(peer: UserPublicProfile) {
        activeCallPeerId = peer.uid
        activeCallState = ActiveCallUiState(
            callId = "demo_call_${UUID.randomUUID().toString().take(8)}",
            peerName = peer.displayName,
            peerPhotoUrl = peer.photoUrl,
            isIncoming = false,
            status = "ringing",
            durationSeconds = 0L,
            iceStateLabel = "Demo Ringing"
        )
    }

    fun endDemoCall() {
        val call = activeCallState
        if (call.status != "idle") {
            callHistory = listOf(
                VoiceCall(
                    id = call.callId,
                    callerId = demoUid,
                    callerName = currentProfile.displayName,
                    calleeId = activeCallPeerId,
                    calleeName = call.peerName,
                    participantIds = listOf(demoUid, activeCallPeerId),
                    status = if (call.status == "connected") "ended" else "missed",
                    durationSeconds = call.durationSeconds,
                    createdAt = Timestamp.now()
                )
            ) + callHistory
            activeCallState = ActiveCallUiState()
            activeCallPeerId = ""
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val activeDemoChatId = remember(navBackStackEntry) {
        runCatching { navBackStackEntry?.toRoute<ChatRoute>()?.chatId }.getOrNull()
    }
    val hasUnreadSentInActiveChat = remember(activeDemoChatId, messagesByChat.toMap()) {
        val cid = activeDemoChatId ?: return@remember false
        messagesByChat[cid].orEmpty().any { it.senderId == demoUid && !it.isSecret && it.status != "read" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Guest Demo Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeDemoChatId != null && hasUnreadSentInActiveChat) {
                        TextButton(
                            onClick = {
                                val cid = activeDemoChatId
                                messagesByChat[cid] = messagesByChat[cid].orEmpty().map { msg ->
                                    if (msg.senderId == demoUid && !msg.isSecret) {
                                        msg.copy(status = "read")
                                    } else msg
                                }
                                chats = chats.map { thread ->
                                    if (thread.id == cid && thread.lastMessageSenderId == demoUid) {
                                        thread.copy(lastMessageStatus = "read")
                                    } else thread
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("demo_simulate_read_button")
                        ) {
                            Text("Simulate Peer Read")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    TextButton(
                        onClick = onExitDemo,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("exit_demo_mode_button")
                    ) {
                        Text("Sign In")
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            currentUserId = demoUid,
                            currentUserProfile = currentProfile,
                            privateInfo = privateInfo,
                            chatsState = UiState.Success(chats),
                            discoverableUsers = peers,
                            callHistoryState = UiState.Success(callHistory),
                            userSettings = userSettings,
                            searchQuery = searchQuery,
                            searchFilter = searchFilter,
                            messageSearchResults = messageSearchResults,
                            isSearchingMessages = false,
                            actionMessage = actionMessage,
                            onClearActionMessage = { actionMessage = null },
                            onSearchQueryChange = { searchQuery = it },
                            onSearchFilterChange = { searchFilter = it },
                            onClearSearch = {
                                searchQuery = ""
                                searchFilter = ChatSearchFilter.ALL
                            },
                            onOpenChat = { chatId, peerId, highlightMessageId ->
                                chats = chats.map {
                                    if (it.id == chatId) it.copy(unreadCounts = it.unreadCounts + (demoUid to 0L)) else it
                                }
                                navController.navigate(ChatRoute(chatId, peerId, highlightMessageId))
                            },
                            onStartChatWithPeer = { peer ->
                                val existing = chats.firstOrNull { it.participantIds.contains(peer.uid) }
                                val chatId = existing?.id ?: "demo_chat_${peer.uid}"
                                if (existing == null) {
                                    val nowTs = Timestamp.now()
                                    chats = (listOf(
                                        ChatThread(
                                            id = chatId,
                                            participantIds = listOf(demoUid, peer.uid),
                                            participantNames = mapOf(demoUid to currentProfile.displayName, peer.uid to peer.displayName),
                                            lastMessageAt = nowTs,
                                            createdAt = nowTs,
                                            updatedAt = nowTs
                                        )
                                    ) + chats).sortedByDescending { it.lastMessageSortTimestamp() }
                                }
                                navController.navigate(ChatRoute(chatId, peer.uid))
                            },
                            onFindChatByEmail = { email ->
                                actionMessage = "Demo mode uses sample contacts; sign in with Google to find $email in Cloud Firestore."
                            },
                            onStartVoiceCall = ::startDemoCall,
                            onOpenMyQr = { navController.navigate(MyQrRoute) },
                            onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                            onThemeModeChanged = { mode -> scope.launch { themePrefs.setThemeMode(mode) } },
                            onAccentPaletteChanged = { palette -> scope.launch { themePrefs.setAccentPalette(palette) } },
                            onNotificationsToggled = { enabled -> scope.launch { themePrefs.setNotificationsEnabled(enabled) } },
                            onUpdateProfile = { name, bio, discoverable ->
                                currentProfile = currentProfile.copy(
                                    displayName = name.ifBlank { currentProfile.displayName },
                                    bio = bio,
                                    isDiscoverable = discoverable
                                )
                                actionMessage = "Demo profile updated"
                            },
                            onBeforeSignOut = {},
                            onSignOutComplete = onExitDemo
                        )
                    }

                    composable<ChatRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ChatRoute>()
                        val thread = chats.firstOrNull { it.id == route.chatId }
                        val peer = peersById[route.peerId]
                        val secretMode = thread?.secretModeEnabled ?: false
                        val list = messagesByChat[route.chatId].orEmpty()

                        ChatScreenContent(
                            currentUserId = demoUid,
                            peerId = route.peerId,
                            peerName = peer?.displayName ?: "Demo Contact",
                            peerPhoto = peer?.photoUrl.orEmpty(),
                            peerProfile = peer,
                            isPeerOnline = peer?.isOnline ?: true,
                            peerLastSeenMillis = peer?.lastSeen?.toDate()?.time,
                            isPeerTyping = thread?.typingUids?.contains(route.peerId) == true,
                            secretModeEnabled = secretMode,
                            messagesState = UiState.Success(list),
                            secretNotice = secretNotice,
                            highlightMessageId = route.highlightMessageId,
                            onClearSecretNotice = { secretNotice = null },
                            onToggleSecretMode = { enabled ->
                                chats = chats.map {
                                    if (it.id == route.chatId) it.copy(secretModeEnabled = enabled) else it
                                }
                            },
                            onTypingChanged = {},
                            onSendMessage = { raw ->
                                val clean = raw.trim()
                                if (clean.isNotEmpty()) {
                                    val msgId = "demo_msg_${UUID.randomUUID().toString().take(8)}"
                                    val now = System.currentTimeMillis()
                                    val encryptedRoundTrip = if (secretMode) {
                                        val enc = cryptoManager.encryptSecretMessage(clean, demoUid, route.chatId, myPubKey)
                                        cryptoManager.decryptSecretMessage(enc.ciphertextBase64, enc.ivBase64, demoUid, route.chatId, myPubKey)
                                    } else {
                                        clean
                                    }
                                    val newMsg = DisplayChatMessage(
                                        id = msgId,
                                        senderId = demoUid,
                                        receiverId = route.peerId,
                                        displayText = encryptedRoundTrip,
                                        isSecret = secretMode,
                                        status = "sent",
                                        createdAtMillis = now,
                                        burnSecondsRemaining = if (secretMode) 10 else null
                                    )
                                    val msgTimestamp = Timestamp.now()
                                    messagesByChat[route.chatId] = messagesByChat[route.chatId].orEmpty() + newMsg
                                    chats = chats.map {
                                        if (it.id == route.chatId) {
                                            it.copy(
                                                lastMessageText = if (secretMode) "Encrypted secret message" else clean,
                                                lastMessageSenderId = demoUid,
                                                lastMessageStatus = "sent",
                                                lastMessageAt = msgTimestamp,
                                                updatedAt = msgTimestamp
                                            )
                                        } else it
                                    }.sortedByDescending { it.lastMessageSortTimestamp() }
                                    if (secretMode) {
                                        secretNotice = "AES-256 encrypted locally • Self-destructing in 10s"
                                    }
                                    if (peer?.isOnline == true && !secretMode) {
                                        scope.launch {
                                            delay(900L)
                                            messagesByChat[route.chatId] = messagesByChat[route.chatId].orEmpty().map {
                                                if (it.id == msgId && it.status == "sent") it.copy(status = "delivered") else it
                                            }
                                            chats = chats.map {
                                                if (it.id == route.chatId && it.lastMessageSenderId == demoUid && it.lastMessageStatus == "sent") {
                                                    it.copy(lastMessageStatus = "delivered")
                                                } else it
                                            }
                                        }
                                    }
                                }
                            },
                            onBack = { navController.popBackStack() },
                            onSendAttachment = { uri, isImage, caption ->
                                val meta = ChatRepository.extractAttachmentMetadata(context, uri, isImage)
                                val msgId = "demo_att_${UUID.randomUUID().toString().take(8)}"
                                val now = System.currentTimeMillis()
                                val cleanCaption = caption.trim()
                                val newMsg = DisplayChatMessage(
                                    id = msgId,
                                    senderId = demoUid,
                                    receiverId = route.peerId,
                                    displayText = cleanCaption,
                                    isSecret = false,
                                    status = "sent",
                                    createdAtMillis = now,
                                    attachmentUrl = uri.toString(),
                                    attachmentType = meta.type,
                                    attachmentName = meta.name,
                                    attachmentSizeBytes = meta.sizeBytes,
                                    attachmentMimeType = meta.mimeType
                                )
                                messagesByChat[route.chatId] = messagesByChat[route.chatId].orEmpty() + newMsg
                                val preview = when {
                                    cleanCaption.isNotEmpty() -> cleanCaption
                                    meta.type == "image" -> "Photo: ${meta.name}"
                                    else -> "Document: ${meta.name}"
                                }
                                val attTimestamp = Timestamp.now()
                                chats = chats.map {
                                    if (it.id == route.chatId) {
                                        it.copy(
                                            lastMessageText = preview,
                                            lastMessageSenderId = demoUid,
                                            lastMessageStatus = "sent",
                                            lastMessageAt = attTimestamp,
                                            updatedAt = attTimestamp
                                        )
                                    } else it
                                }.sortedByDescending { it.lastMessageSortTimestamp() }
                                if (peer?.isOnline == true) {
                                    scope.launch {
                                        delay(900L)
                                        messagesByChat[route.chatId] = messagesByChat[route.chatId].orEmpty().map {
                                            if (it.id == msgId && it.status == "sent") it.copy(status = "delivered") else it
                                        }
                                        chats = chats.map {
                                            if (it.id == route.chatId && it.lastMessageSenderId == demoUid && it.lastMessageStatus == "sent") {
                                                it.copy(lastMessageStatus = "delivered")
                                            } else it
                                        }
                                    }
                                }
                            },
                            onStartVoiceCall = ::startDemoCall
                        )
                    }

                    composable<MyQrRoute> {
                        MyQrScreen(
                            currentUserId = demoUid,
                            profile = currentProfile,
                            email = privateInfo.email,
                            onBack = { navController.popBackStack() },
                            onOpenScanner = {
                                navController.popBackStack()
                                navController.navigate(QrScannerRoute)
                            }
                        )
                    }

                    composable<QrScannerRoute> {
                        QrScannerScreen(
                            onBack = { navController.popBackStack() },
                            onQrScanned = { scannedUid ->
                                val target = peersById[scannedUid] ?: peerAarav
                                actionMessage = "QR scanned ($scannedUid) — opened demo chat with ${target.displayName}"
                                navController.popBackStack()
                                navController.navigate(ChatRoute("demo_chat_${target.uid}", target.uid))
                            }
                        )
                    }
                }
            }
        }

        if (activeCallState.status != "idle") {
            CallOverlayScreen(
                callState = activeCallState,
                onAccept = {
                    activeCallState = activeCallState.copy(status = "connected")
                },
                onDecline = { endDemoCall() },
                onEndCall = { endDemoCall() },
                onToggleMute = { activeCallState = activeCallState.copy(isMuted = !activeCallState.isMuted) },
                onToggleSpeaker = { activeCallState = activeCallState.copy(isSpeakerOn = !activeCallState.isSpeakerOn) }
            )
        }
    }
}
