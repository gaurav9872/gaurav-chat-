package com.example.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.data.model.AccentPalette
import com.example.data.model.ChatSearchFilter
import com.example.data.model.ChatThread
import com.example.data.model.MessageSearchResult
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserPrivateInfo
import com.example.data.model.UserPublicProfile
import com.example.data.model.VoiceCall
import com.example.data.model.lastMessageSortTimestamp
import com.example.data.preferences.UserSettings
import com.example.ui.auth.signOutUser
import com.example.ui.components.MessageStatusTick
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallDeclineRed
import com.example.ui.theme.SecretAmber
import com.example.ui.theme.TickBlue
import com.example.ui.theme.TickBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentUserId: String,
    currentUserProfile: UserPublicProfile?,
    privateInfo: UserPrivateInfo?,
    chatsState: UiState<List<ChatThread>>,
    discoverableUsers: List<UserPublicProfile>,
    callHistoryState: UiState<List<VoiceCall>>,
    userSettings: UserSettings,
    searchQuery: String,
    searchFilter: ChatSearchFilter,
    messageSearchResults: List<MessageSearchResult>,
    isSearchingMessages: Boolean,
    actionMessage: String?,
    onClearActionMessage: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFilterChange: (ChatSearchFilter) -> Unit,
    onClearSearch: () -> Unit,
    onOpenChat: (chatId: String, peerId: String, highlightMessageId: String?) -> Unit,
    onStartChatWithPeer: (UserPublicProfile) -> Unit,
    onFindChatByEmail: (String) -> Unit,
    onStartVoiceCall: (UserPublicProfile) -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onAccentPaletteChanged: (AccentPalette) -> Unit,
    onNotificationsToggled: (Boolean) -> Unit,
    onUpdateProfile: (displayName: String, bio: String, isDiscoverable: Boolean) -> Unit,
    onBeforeSignOut: suspend () -> Unit,
    onSignOutComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsToggled(granted)
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasRequestedNotificationPermission = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearActionMessage()
        }
    }

    val totalUnread = (chatsState as? UiState.Success)?.data
        ?.sumOf { (it.unreadCounts[currentUserId] ?: 0L).toInt() } ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                val railItemColors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = railItemColors,
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.AutoMirrored.Filled.Chat else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Chats"
                            )
                        },
                        label = { Text("Chats") },
                        modifier = Modifier.testTag("nav_tab_chats")
                    )
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = railItemColors,
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Default.Call else Icons.Outlined.Call,
                                contentDescription = "Calls"
                            )
                        },
                        label = { Text("Calls") },
                        modifier = Modifier.testTag("nav_tab_calls")
                    )
                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = railItemColors,
                        icon = {
                            Icon(
                                if (selectedTab == 2) Icons.Default.Person else Icons.Outlined.Person,
                                contentDescription = "Profile and QR"
                            )
                        },
                        label = { Text("Profile / QR") },
                        modifier = Modifier.testTag("nav_tab_profile")
                    )
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            title = {
                                Column {
                                    Text(
                                        text = "Chat Gaurav",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (currentUserProfile != null) {
                                        Text(
                                            text = currentUserProfile.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onOpenQrScanner,
                                    modifier = Modifier.testTag("topbar_qr_scan_button")
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan QR code",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = onOpenMyQr,
                                    modifier = Modifier.testTag("topbar_my_qr_button")
                                ) {
                                    Icon(
                                        Icons.Default.QrCode2,
                                        contentDescription = "My QR code",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        val navItemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    colors = navItemColors,
                                    icon = {
                                        Box {
                                            Icon(
                                                if (selectedTab == 0) Icons.AutoMirrored.Filled.Chat else Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = "Chats"
                                            )
                                            if (totalUnread > 0) {
                                                Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                                                    Text(totalUnread.coerceAtMost(99).toString())
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Chats") },
                                    modifier = Modifier.testTag("nav_tab_chats")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    colors = navItemColors,
                                    icon = {
                                        Icon(
                                            if (selectedTab == 1) Icons.Default.Call else Icons.Outlined.Call,
                                            contentDescription = "Calls"
                                        )
                                    },
                                    label = { Text("Calls") },
                                    modifier = Modifier.testTag("nav_tab_calls")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    colors = navItemColors,
                                    icon = {
                                        Icon(
                                            if (selectedTab == 2) Icons.Default.Person else Icons.Outlined.Person,
                                            contentDescription = "Profile and QR"
                                        )
                                    },
                                    label = { Text("Profile / QR") },
                                    modifier = Modifier.testTag("nav_tab_profile")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == 0) {
                        FloatingActionButton(
                            onClick = { showNewChatDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("new_chat_fab")
                        ) {
                            Icon(Icons.Default.AddComment, contentDescription = "Start new chat")
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> ChatsTab(
                            currentUserId = currentUserId,
                            chatsState = chatsState,
                            discoverableUsers = discoverableUsers,
                            searchQuery = searchQuery,
                            searchFilter = searchFilter,
                            messageSearchResults = messageSearchResults,
                            isSearchingMessages = isSearchingMessages,
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchFilterChange = onSearchFilterChange,
                            onClearSearch = onClearSearch,
                            onOpenChat = onOpenChat,
                            onStartNewChat = { showNewChatDialog = true },
                            onScanQr = onOpenQrScanner
                        )

                        1 -> CallsTab(
                            currentUserId = currentUserId,
                            callHistoryState = callHistoryState,
                            discoverableUsers = discoverableUsers,
                            onStartVoiceCall = onStartVoiceCall
                        )

                        2 -> ProfileAndSettingsTab(
                            currentUserProfile = currentUserProfile,
                            privateInfo = privateInfo,
                            userSettings = userSettings,
                            onOpenMyQr = onOpenMyQr,
                            onOpenQrScanner = onOpenQrScanner,
                            onThemeModeChanged = onThemeModeChanged,
                            onAccentPaletteChanged = onAccentPaletteChanged,
                            onRequestNotificationsToggle = { wantEnable ->
                                if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        onNotificationsToggled(true)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    onNotificationsToggled(wantEnable)
                                }
                            },
                            onUpdateProfile = onUpdateProfile,
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = onBeforeSignOut,
                                    onSignOutComplete = onSignOutComplete
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            discoverableUsers = discoverableUsers,
            onDismiss = { showNewChatDialog = false },
            onSelectPeer = { peer ->
                showNewChatDialog = false
                onStartChatWithPeer(peer)
            },
            onFindByEmail = { email ->
                showNewChatDialog = false
                onFindChatByEmail(email)
            },
            onOpenQrScanner = {
                showNewChatDialog = false
                onOpenQrScanner()
            }
        )
    }
}

@Composable
private fun ChatsTab(
    currentUserId: String,
    chatsState: UiState<List<ChatThread>>,
    discoverableUsers: List<UserPublicProfile>,
    searchQuery: String,
    searchFilter: ChatSearchFilter,
    messageSearchResults: List<MessageSearchResult>,
    isSearchingMessages: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchFilterChange: (ChatSearchFilter) -> Unit,
    onClearSearch: () -> Unit,
    onOpenChat: (chatId: String, peerId: String, highlightMessageId: String?) -> Unit,
    onStartNewChat: () -> Unit,
    onScanQr: () -> Unit
) {
    val usersById = remember(discoverableUsers) { discoverableUsers.associateBy { it.uid } }
    val isSearchActive = searchQuery.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search contacts or messages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier.testTag("chats_search_clear_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
                .testTag("chats_search_input")
        )

        AnimatedVisibility(visible = isSearchActive) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    ChatSearchFilter.entries.forEach { filter ->
                        val selected = searchFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSearchFilterChange(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                        )
                    }
                }
                if (isSearchingMessages) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
            }
        }

        when (chatsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = chatsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is UiState.Success -> {
                val cleanQuery = searchQuery.trim()
                val sortedThreads = remember(chatsState.data) {
                    chatsState.data.sortedByDescending { it.lastMessageSortTimestamp() }
                }
                val filteredThreads = remember(sortedThreads, usersById, cleanQuery, searchFilter) {
                    if (cleanQuery.isEmpty()) {
                        sortedThreads
                    } else if (searchFilter == ChatSearchFilter.MESSAGES) {
                        emptyList()
                    } else {
                        sortedThreads.filter { thread ->
                            val peerId = thread.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                            val peer = usersById[peerId]
                            val peerName = peer?.displayName?.takeIf { it.isNotBlank() }
                                ?: thread.participantNames[peerId].orEmpty()
                            val peerHandle = peer?.handle.orEmpty()
                            val matchesContact = peerName.contains(cleanQuery, ignoreCase = true) ||
                                peerHandle.contains(cleanQuery, ignoreCase = true)
                            val matchesLastMessage = searchFilter == ChatSearchFilter.ALL &&
                                thread.lastMessageText.contains(cleanQuery, ignoreCase = true)
                            matchesContact || matchesLastMessage
                        }
                    }
                }

                val visibleMessages = remember(messageSearchResults, cleanQuery, searchFilter) {
                    if (cleanQuery.isEmpty() || searchFilter == ChatSearchFilter.CONTACTS) {
                        emptyList()
                    } else {
                        messageSearchResults
                    }
                }

                if (!isSearchActive && filteredThreads.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No conversations yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start a real-time encrypted chat by scanning a friend's QR code or searching by email.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onStartNewChat, modifier = Modifier.testTag("empty_new_chat_button")) {
                                Text("Find User")
                            }
                            OutlinedButton(
                                onClick = onScanQr,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.testTag("empty_scan_qr_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR")
                            }
                        }
                    }
                } else if (isSearchActive && !isSearchingMessages && filteredThreads.isEmpty() && visibleMessages.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No matches found",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No contacts or messages matched \"$cleanQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onClearSearch,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.testTag("clear_search_empty_button")
                        ) {
                            Text("Clear Search")
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSearchActive && filteredThreads.isNotEmpty()) {
                            item(key = "header_conversations") {
                                Text(
                                    text = "Conversations (${filteredThreads.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }

                        items(filteredThreads, key = { it.id }) { thread ->
                            val peerId = thread.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                            val peer = usersById[peerId]
                            val peerName = peer?.displayName?.takeIf { it.isNotBlank() }
                                ?: thread.participantNames[peerId].orEmpty().ifBlank { "Chat User" }
                            val peerPhoto = peer?.photoUrl?.takeIf { it.isNotBlank() }
                                ?: thread.participantPhotos[peerId].orEmpty()
                            val unread = (thread.unreadCounts[currentUserId] ?: 0L).toInt()
                            val isPeerTyping = thread.typingUids.contains(peerId)

                            ChatThreadRow(
                                thread = thread,
                                currentUserId = currentUserId,
                                peerName = peerName,
                                peerPhoto = peerPhoto,
                                isOnline = peer?.isOnline ?: false,
                                isPeerTyping = isPeerTyping,
                                unreadCount = unread,
                                highlightQuery = cleanQuery,
                                onClick = { onOpenChat(thread.id, peerId, null) }
                            )
                        }

                        if (isSearchActive && visibleMessages.isNotEmpty()) {
                            item(key = "header_messages") {
                                Text(
                                    text = "Messages (${visibleMessages.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                                )
                            }

                            items(visibleMessages, key = { "msg_${it.chatId}_${it.messageId}" }) { match ->
                                MessageSearchResultRow(
                                    result = match,
                                    currentUserId = currentUserId,
                                    highlightQuery = cleanQuery,
                                    onClick = { onOpenChat(match.chatId, match.peerId, match.messageId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun highlightAnnotatedString(
    text: String,
    query: String,
    highlightBackground: Color,
    highlightTextColor: Color
) = remember(text, query, highlightBackground, highlightTextColor) {
    buildAnnotatedString {
        if (query.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var startIndex = 0
        while (startIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
            if (matchIndex == -1) {
                append(text.substring(startIndex))
                break
            }
            if (matchIndex > startIndex) {
                append(text.substring(startIndex, matchIndex))
            }
            withStyle(
                SpanStyle(
                    background = highlightBackground,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(matchIndex, matchIndex + query.length))
            }
            startIndex = matchIndex + query.length
        }
    }
}

@Composable
private fun MessageSearchResultRow(
    result: MessageSearchResult,
    currentUserId: String,
    highlightQuery: String,
    onClick: () -> Unit
) {
    val senderPrefix = if (result.senderId == currentUserId) "You: " else ""
    val highlightedSnippet = highlightAnnotatedString(
        text = result.text,
        query = highlightQuery,
        highlightBackground = MaterialTheme.colorScheme.primaryContainer,
        highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("message_search_result_${result.messageId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            UserAvatar(
                name = result.peerName,
                photoUrl = result.peerPhotoUrl,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.peerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTimeShort(result.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = buildAnnotatedString {
                        if (senderPrefix.isNotEmpty()) {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) {
                                append(senderPrefix)
                            }
                        }
                        append(highlightedSnippet)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    currentUserId: String,
    peerName: String,
    peerPhoto: String,
    isOnline: Boolean,
    isPeerTyping: Boolean,
    unreadCount: Int,
    highlightQuery: String,
    onClick: () -> Unit
) {
    val annotatedName = highlightAnnotatedString(
        text = peerName,
        query = highlightQuery,
        highlightBackground = MaterialTheme.colorScheme.primaryContainer,
        highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    val previewRaw = when {
        isPeerTyping -> "typing..."
        thread.lastMessageText.isBlank() -> "Tap to start messaging"
        else -> thread.lastMessageText
    }
    val annotatedPreview = if (!isPeerTyping && thread.lastMessageText.isNotBlank()) {
        highlightAnnotatedString(
            text = previewRaw,
            query = highlightQuery,
            highlightBackground = MaterialTheme.colorScheme.primaryContainer,
            highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        buildAnnotatedString { append(previewRaw) }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_row_${thread.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            UserAvatar(
                name = peerName,
                photoUrl = peerPhoto,
                size = 52.dp,
                isOnline = isOnline,
                showOnlineBadge = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            text = annotatedName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (thread.secretModeEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secret mode active",
                                tint = SecretAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = formatTimeShort(thread.lastMessageAt?.toDate()?.time ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val isDarkSurface = MaterialTheme.colorScheme.background.red < 0.3f
                        if (!isPeerTyping && thread.lastMessageSenderId == currentUserId && thread.lastMessageText.isNotBlank()) {
                            MessageStatusTick(
                                status = thread.lastMessageStatus,
                                tintOverride = MaterialTheme.colorScheme.onSurfaceVariant,
                                readTintOverride = if (isDarkSurface) TickBlue else TickBlueLight
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = annotatedPreview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPeerTyping) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsTab(
    currentUserId: String,
    callHistoryState: UiState<List<VoiceCall>>,
    discoverableUsers: List<UserPublicProfile>,
    onStartVoiceCall: (UserPublicProfile) -> Unit
) {
    val usersById = remember(discoverableUsers) { discoverableUsers.associateBy { it.uid } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 720.dp)
            .padding(horizontal = 16.dp)
    ) {
        when (callHistoryState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(callHistoryState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                if (callHistoryState.data.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No voice calls yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open any chat and tap the Call button for encrypted peer-to-peer WebRTC voice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(callHistoryState.data, key = { it.id }) { call ->
                            val isOutgoing = call.callerId == currentUserId
                            val peerId = if (isOutgoing) call.calleeId else call.callerId
                            val peerName = if (isOutgoing) call.calleeName else call.callerName
                            val peerPhoto = if (isOutgoing) call.calleePhotoUrl else call.callerPhotoUrl
                            val isMissed = call.status in listOf("missed", "declined")

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                tonalElevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    UserAvatar(name = peerName, photoUrl = peerPhoto, size = 46.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = peerName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isMissed && !isOutgoing) CallDeclineRed else MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when {
                                                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                                                    isOutgoing -> Icons.AutoMirrored.Filled.CallMade
                                                    else -> Icons.AutoMirrored.Filled.CallReceived
                                                },
                                                contentDescription = null,
                                                tint = if (isMissed) CallDeclineRed else CallAcceptGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val durationText = if (call.durationSeconds > 0) " • ${call.durationSeconds}s" else ""
                                            Text(
                                                text = "${if (isOutgoing) "Outgoing" else "Incoming"} (${call.status})$durationText • ${formatTimeShort(call.createdAt?.toDate()?.time ?: 0L)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val target = usersById[peerId] ?: UserPublicProfile(
                                                uid = peerId,
                                                displayName = peerName,
                                                photoUrl = peerPhoto
                                            )
                                            onStartVoiceCall(target)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Call,
                                            contentDescription = "Call $peerName",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAndSettingsTab(
    currentUserProfile: UserPublicProfile?,
    privateInfo: UserPrivateInfo?,
    userSettings: UserSettings,
    onOpenMyQr: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onAccentPaletteChanged: (AccentPalette) -> Unit,
    onRequestNotificationsToggle: (Boolean) -> Unit,
    onUpdateProfile: (displayName: String, bio: String, isDiscoverable: Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var editingName by remember(currentUserProfile?.displayName) {
        mutableStateOf(currentUserProfile?.displayName.orEmpty())
    }
    var editingBio by remember(currentUserProfile?.bio) {
        mutableStateOf(currentUserProfile?.bio.orEmpty())
    }
    var isDiscoverable by remember(currentUserProfile?.isDiscoverable) {
        mutableStateOf(currentUserProfile?.isDiscoverable ?: true)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Profile Header & QR Quick Actions
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = currentUserProfile?.displayName ?: "User",
                        photoUrl = currentUserProfile?.photoUrl.orEmpty(),
                        size = 64.dp,
                        isOnline = true,
                        showOnlineBadge = true
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUserProfile?.displayName?.ifBlank { "Chat Gaurav User" } ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = privateInfo?.email.orEmpty().ifBlank { "@${currentUserProfile?.handle.orEmpty()}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onOpenMyQr,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_my_qr_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("My QR Code")
                    }
                    OutlinedButton(
                        onClick = onOpenQrScanner,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_scan_qr_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR")
                    }
                }
            }
        }

        // Appearance: Light/Dark Mode + Custom Accent Themes
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "Appearance & Accent Theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = userSettings.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onThemeModeChanged(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Accent Palette: ${userSettings.accentPalette.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(AccentPalette.entries) { palette ->
                        val selected = userSettings.accentPalette == palette
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(palette.swatchColor)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onAccentPaletteChanged(palette) }
                                .testTag("accent_swatch_${palette.name.lowercase()}")
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = palette.label, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Profile Details & Privacy
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Text(
                    text = "Profile & Discovery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val profileFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = profileFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editingBio,
                    onValueChange = { editingBio = it },
                    label = { Text("Status / Bio") },
                    singleLine = true,
                    colors = profileFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Discoverable in User Search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "When off, people can still add you via QR code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isDiscoverable, onCheckedChange = { isDiscoverable = it })
                }
                Button(
                    onClick = { onUpdateProfile(editingName, editingBio, isDiscoverable) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Profile")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Message & Call Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Local & FCM notifications",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = userSettings.notificationsEnabled,
                        onCheckedChange = onRequestNotificationsToggle
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun NewChatDialog(
    discoverableUsers: List<UserPublicProfile>,
    onDismiss: () -> Unit,
    onSelectPeer: (UserPublicProfile) -> Unit,
    onFindByEmail: (String) -> Unit,
    onOpenQrScanner: () -> Unit
) {
    var emailInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Start New Chat",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Find by exact Google email") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onFindByEmail(emailInput) },
                        enabled = emailInput.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Find Email")
                    }
                    OutlinedButton(
                        onClick = onOpenQrScanner,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan QR")
                    }
                }

                if (discoverableUsers.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "People on Chat Gaurav",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyColumn(modifier = Modifier.height(180.dp)) {
                        items(discoverableUsers, key = { it.uid }) { user ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPeer(user) }
                                    .padding(vertical = 8.dp)
                            ) {
                                UserAvatar(
                                    name = user.displayName,
                                    photoUrl = user.photoUrl,
                                    size = 38.dp,
                                    isOnline = user.isOnline,
                                    showOnlineBadge = true
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = user.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(user.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
