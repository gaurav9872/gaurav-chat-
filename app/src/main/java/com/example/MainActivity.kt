package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.crypto.SecretCryptoManager
import com.example.data.model.UiState
import com.example.data.model.UserPublicProfile
import com.example.data.preferences.ThemePreferences
import com.example.data.preferences.UserSettings
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.NotificationNavRequest
import com.example.notifications.RealtimeNotificationCoordinator
import com.example.notifications.RealtimeNotificationService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.SplashScreen
import com.example.ui.call.CallOverlayScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.demo.DemoAppRoot
import com.example.ui.home.HomeScreen
import com.example.ui.qr.MyQrScreen
import com.example.ui.qr.QrScannerScreen
import com.example.ui.theme.ChatGauravTheme
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class ChatRoute(val chatId: String, val peerId: String, val highlightMessageId: String? = null)

@Serializable
object MyQrRoute

@Serializable
object QrScannerRoute

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RealtimeNotificationCoordinator.handleIncomingIntent(intent)

        setContent {
            val context = LocalContext.current
            val themePrefs = remember { ThemePreferences(context.applicationContext) }
            val userSettings by themePrefs.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            val scope = rememberCoroutineScope()
            var showSplash by remember { mutableStateOf(true) }

            ChatGauravTheme(
                themeMode = userSettings.themeMode,
                accentPalette = userSettings.accentPalette
            ) {
                when {
                    showSplash -> {
                        SplashScreen(onFinished = { showSplash = false })
                    }

                    !userSettings.onboardingCompleted -> {
                        OnboardingScreen(
                            onComplete = {
                                scope.launch { themePrefs.setOnboardingCompleted(true) }
                            }
                        )
                    }

                    else -> {
                        AppAuthGate(
                            userSettings = userSettings,
                            themePrefs = themePrefs
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RealtimeNotificationCoordinator.handleIncomingIntent(intent)
    }
}

@Composable
private fun AppAuthGate(
    userSettings: UserSettings,
    themePrefs: ThemePreferences
) {
    val context = LocalContext.current
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    var isDemoMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user == null) {
            RealtimeNotificationService.stop(context)
            RealtimeNotificationCoordinator.clearHistory()
        }
    }

    when {
        user != null -> {
            isDemoMode = false
            key(user.uid) {
                AuthenticatedAppRoot(
                    firebaseUser = user,
                    userSettings = userSettings,
                    themePrefs = themePrefs
                )
            }
        }

        isDemoMode -> {
            DemoAppRoot(
                userSettings = userSettings,
                themePrefs = themePrefs,
                onExitDemo = { isDemoMode = false }
            )
        }

        else -> {
            AuthScreen(
                onAuthSuccess = {},
                onGuestDemoLogin = { isDemoMode = true }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppRoot(
    firebaseUser: FirebaseUser,
    userSettings: UserSettings,
    themePrefs: ThemePreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val uid = firebaseUser.uid

    val mainViewModel: MainViewModel = viewModel(
        key = "main_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MainViewModel(
                    chatRepository = ChatRepository(db),
                    callRepository = CallRepository(db),
                    cryptoManager = SecretCryptoManager(app),
                    currentUserId = uid
                )
            }
        }
    )

    val callViewModel: CallViewModel = viewModel(
        key = "call_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                CallViewModel(
                    appContext = app,
                    callRepository = CallRepository(db),
                    currentUserId = uid
                )
            }
        }
    )

    val currentProfile by mainViewModel.currentUserProfile.collectAsStateWithLifecycle()
    val privateInfo by mainViewModel.privateInfo.collectAsStateWithLifecycle()
    val chatsState by mainViewModel.chatsState.collectAsStateWithLifecycle()
    val discoverableUsers by mainViewModel.discoverableUsers.collectAsStateWithLifecycle()
    val callHistoryState by mainViewModel.callHistoryState.collectAsStateWithLifecycle()
    val incomingRingingCall by mainViewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val actionMessage by mainViewModel.actionMessage.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchFilter by mainViewModel.searchFilter.collectAsStateWithLifecycle()
    val messageSearchResults by mainViewModel.messageSearchResults.collectAsStateWithLifecycle()
    val isSearchingMessages by mainViewModel.isSearchingMessages.collectAsStateWithLifecycle()
    val activeCallState by callViewModel.callState.collectAsStateWithLifecycle()

    var pendingCallTarget by remember { mutableStateOf<UserPublicProfile?>(null) }
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCallAction?.invoke()
            pendingCallTarget?.let { target ->
                val me = currentProfile ?: UserPublicProfile(uid = uid, displayName = firebaseUser.displayName ?: "Me")
                callViewModel.startOutgoingCall(me, target)
            }
        }
        pendingCallTarget = null
        pendingCallAction = null
    }

    fun launchWithAudioPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingCallAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Sync profile, ECDH public key, and FCM token
    LaunchedEffect(uid) {
        mainViewModel.syncUserProfile(firebaseUser)
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNotBlank()) {
                mainViewModel.updateFcmToken(token)
            }
        } catch (_: Exception) {
            // FCM optional if not configured in Firebase Console
        }
    }

    // Online / Last Seen lifecycle presence + Foreground notification sync
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uid, userSettings.notificationsEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (userSettings.notificationsEnabled) {
                        RealtimeNotificationService.start(context)
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    RealtimeNotificationCoordinator.setAppInForeground(true)
                    mainViewModel.setOnlineStatus(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    RealtimeNotificationCoordinator.setAppInForeground(false)
                    mainViewModel.setOnlineStatus(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uid, userSettings.notificationsEnabled) {
        if (userSettings.notificationsEnabled) {
            RealtimeNotificationService.start(context)
        } else {
            RealtimeNotificationService.stop(context)
            NotificationHelper.stopIncomingRingtoneAndVibration(context)
        }
    }

    // Track active open chat so we suppress notifications and clear alerts for that conversation
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentOpenChatId = remember(currentBackStackEntry) {
        val entry = currentBackStackEntry
        if (entry != null && entry.destination.hasRoute<ChatRoute>()) {
            entry.toRoute<ChatRoute>().chatId
        } else {
            null
        }
    }
    LaunchedEffect(currentOpenChatId) {
        RealtimeNotificationCoordinator.setActiveOpenChat(currentOpenChatId)
        if (currentOpenChatId != null) {
            NotificationHelper.cancelMessageNotification(context, currentOpenChatId)
        }
    }

    // Consume deep-link navigation requests from notification taps ("Open Chat" / "Accept" Call)
    val pendingNotificationNav by RealtimeNotificationCoordinator.pendingNavigation.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNotificationNav, chatsState, incomingRingingCall, callHistoryState) {
        when (val req = pendingNotificationNav) {
            is NotificationNavRequest.OpenChat -> {
                val threads = (chatsState as? UiState.Success)?.data.orEmpty()
                val resolvedPeerId = req.peerId.ifBlank {
                    threads.firstOrNull { it.id == req.chatId }
                        ?.participantIds
                        ?.firstOrNull { it != uid }
                        .orEmpty()
                }
                if (resolvedPeerId.isNotBlank() || chatsState is UiState.Success) {
                    NotificationHelper.cancelMessageNotification(context, req.chatId)
                    if (resolvedPeerId.isNotBlank()) {
                        navController.navigate(ChatRoute(req.chatId, resolvedPeerId)) {
                            launchSingleTop = true
                        }
                    }
                    RealtimeNotificationCoordinator.consumePendingNavigation()
                }
            }

            is NotificationNavRequest.OpenCall -> {
                val matchingCall = incomingRingingCall?.takeIf { it.id == req.callId }
                if (matchingCall != null) {
                    if (req.autoAccept) {
                        launchWithAudioPermission {
                            callViewModel.acceptIncomingCall(matchingCall)
                        }
                    } else {
                        callViewModel.prepareIncomingCall(matchingCall)
                    }
                    RealtimeNotificationCoordinator.consumePendingNavigation()
                } else if (callHistoryState is UiState.Success) {
                    val finished = (callHistoryState as UiState.Success).data.any {
                        it.id == req.callId && it.status != "ringing"
                    }
                    if (finished) {
                        NotificationHelper.stopIncomingRingtoneAndVibration(context)
                        NotificationHelper.cancelCallNotification(context, req.callId)
                        RealtimeNotificationCoordinator.consumePendingNavigation()
                    }
                }
            }

            null -> {}
        }
    }

    // Mark incoming messages as delivered & post deduplicated WhatsApp-style notifications
    var seededInitialChats by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(chatsState, userSettings.notificationsEnabled) {
        val threads = (chatsState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!seededInitialChats) {
            seededInitialChats = true
            threads.forEach { thread ->
                val ts = thread.lastMessageAt?.toDate()?.time ?: 0L
                RealtimeNotificationCoordinator.seedChatTimestamp(thread.id, ts)
                val unread = (thread.unreadCounts[uid] ?: 0L) > 0L
                if (thread.lastMessageSenderId.isNotBlank() &&
                    thread.lastMessageSenderId != uid &&
                    (thread.lastMessageStatus == "sent" || unread)
                ) {
                    mainViewModel.markThreadDelivered(thread.id)
                }
            }
            return@LaunchedEffect
        }

        threads.forEach { thread ->
            val ts = thread.lastMessageAt?.toDate()?.time ?: 0L
            val unread = (thread.unreadCounts[uid] ?: 0L) > 0L
            val senderId = thread.lastMessageSenderId
            if (senderId.isNotBlank() && senderId != uid) {
                if (thread.lastMessageStatus == "sent" || unread) {
                    mainViewModel.markThreadDelivered(thread.id)
                }
                if (unread &&
                    ts > 0L &&
                    userSettings.notificationsEnabled &&
                    RealtimeNotificationCoordinator.shouldNotifyMessage(thread.id, ts)
                ) {
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
                        context = context,
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

    // Incoming WebRTC voice call trigger + deduplicated notification
    LaunchedEffect(incomingRingingCall?.id, userSettings.notificationsEnabled) {
        val call = incomingRingingCall
        if (call != null) {
            if (userSettings.notificationsEnabled &&
                RealtimeNotificationCoordinator.shouldNotifyCall(call.id)
            ) {
                NotificationHelper.showIncomingCallNotification(context, call.callerName, call.id)
            }
            callViewModel.prepareIncomingCall(call)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    currentUserId = uid,
                    currentUserProfile = currentProfile,
                    privateInfo = privateInfo,
                    chatsState = chatsState,
                    discoverableUsers = discoverableUsers,
                    callHistoryState = callHistoryState,
                    userSettings = userSettings,
                    searchQuery = searchQuery,
                    searchFilter = searchFilter,
                    messageSearchResults = messageSearchResults,
                    isSearchingMessages = isSearchingMessages,
                    actionMessage = actionMessage,
                    onClearActionMessage = mainViewModel::clearActionMessage,
                    onSearchQueryChange = mainViewModel::updateSearchQuery,
                    onSearchFilterChange = mainViewModel::updateSearchFilter,
                    onClearSearch = mainViewModel::clearSearch,
                    onOpenChat = { chatId, peerId, highlightMessageId ->
                        navController.navigate(ChatRoute(chatId, peerId, highlightMessageId))
                    },
                    onStartChatWithPeer = { peer ->
                        mainViewModel.startChatWithPeer(peer) { chatId, peerId ->
                            navController.navigate(ChatRoute(chatId, peerId))
                        }
                    },
                    onFindChatByEmail = { email ->
                        mainViewModel.findAndChatByEmail(email) { chatId, peerId ->
                            navController.navigate(ChatRoute(chatId, peerId))
                        }
                    },
                    onStartVoiceCall = { peer ->
                        launchWithAudioPermission {
                            val me = currentProfile ?: UserPublicProfile(uid = uid, displayName = firebaseUser.displayName ?: "Me")
                            callViewModel.startOutgoingCall(me, peer)
                        }
                    },
                    onOpenMyQr = { navController.navigate(MyQrRoute) },
                    onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                    onThemeModeChanged = { mode -> scope.launch { themePrefs.setThemeMode(mode) } },
                    onAccentPaletteChanged = { palette -> scope.launch { themePrefs.setAccentPalette(palette) } },
                    onNotificationsToggled = { enabled ->
                        scope.launch { themePrefs.setNotificationsEnabled(enabled) }
                        if (enabled) {
                            RealtimeNotificationService.start(context)
                        } else {
                            RealtimeNotificationService.stop(context)
                            NotificationHelper.stopIncomingRingtoneAndVibration(context)
                        }
                    },
                    onUpdateProfile = mainViewModel::updateProfile,
                    onBeforeSignOut = {
                        RealtimeNotificationService.stop(context)
                        RealtimeNotificationCoordinator.clearHistory()
                        mainViewModel.setOnlineStatus(false)
                    }
                )
            }

            composable<ChatRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ChatRoute>()
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat_${uid}_${route.chatId}",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY])
                            val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                            ChatViewModel(
                                chatRepository = ChatRepository(db),
                                cryptoManager = SecretCryptoManager(app),
                                chatId = route.chatId,
                                currentUserId = uid,
                                peerId = route.peerId
                            )
                        }
                    }
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    highlightMessageId = route.highlightMessageId,
                    onBack = { navController.popBackStack() },
                    onStartVoiceCall = { peer ->
                        launchWithAudioPermission {
                            val me = currentProfile ?: UserPublicProfile(uid = uid, displayName = firebaseUser.displayName ?: "Me")
                            callViewModel.startOutgoingCall(me, peer)
                        }
                    }
                )
            }

            composable<MyQrRoute> {
                MyQrScreen(
                    currentUserId = uid,
                    profile = currentProfile,
                    email = privateInfo?.email ?: firebaseUser.email.orEmpty(),
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
                        mainViewModel.connectByQrUid(scannedUid) { chatId, peerId ->
                            navController.popBackStack()
                            navController.navigate(ChatRoute(chatId, peerId))
                        }
                    }
                )
            }
        }

        if (activeCallState.status != "idle") {
            CallOverlayScreen(
                callState = activeCallState,
                onAccept = {
                    val call = incomingRingingCall
                    if (call != null) {
                        launchWithAudioPermission {
                            callViewModel.acceptIncomingCall(call)
                        }
                    }
                },
                onDecline = {
                    val id = incomingRingingCall?.id ?: activeCallState.callId
                    if (id.isNotBlank()) callViewModel.declineIncomingCall(id)
                },
                onEndCall = { callViewModel.endCall() },
                onToggleMute = callViewModel::toggleMute,
                onToggleSpeaker = callViewModel::toggleSpeaker
            )
        }
    }
}
