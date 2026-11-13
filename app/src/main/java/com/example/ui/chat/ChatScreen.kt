package com.example.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.UiState
import com.example.data.model.UserPublicProfile
import com.example.ui.components.MessageStatusTick
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatLastSeen
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.SecretAmber
import com.example.ui.theme.SecretAmberLight
import com.example.ui.theme.SecretSurfaceDark
import com.example.ui.theme.SecretSurfaceLight
import com.example.ui.theme.TickBlue
import com.example.ui.theme.TickBlueOnPrimary
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.DisplayChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    highlightMessageId: String? = null,
    onBack: () -> Unit,
    onStartVoiceCall: (UserPublicProfile) -> Unit
) {
    val context = LocalContext.current
    val chatThread by viewModel.chatThread.collectAsStateWithLifecycle()
    val peerProfile by viewModel.peerProfile.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val secretNotice by viewModel.ephemeralSecretNotice.collectAsStateWithLifecycle()
    val isUploadingAttachment by viewModel.isUploadingAttachment.collectAsStateWithLifecycle()
    val attachmentError by viewModel.attachmentError.collectAsStateWithLifecycle()

    val secretModeEnabled = chatThread?.secretModeEnabled ?: false
    val peerId = viewModel.peerId
    val peerName = peerProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: chatThread?.participantNames?.get(peerId).orEmpty().ifBlank { "Chat User" }
    val peerPhoto = peerProfile?.photoUrl?.takeIf { it.isNotBlank() }
        ?: chatThread?.participantPhotos?.get(peerId).orEmpty()
    val isPeerTyping = chatThread?.typingUids?.contains(peerId) == true

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.setChatVisible(true)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setChatVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setChatVisible(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setChatVisible(false)
            viewModel.onTypingChanged(false)
        }
    }

    ChatScreenContent(
        currentUserId = viewModel.currentUserId,
        peerId = peerId,
        peerName = peerName,
        peerPhoto = peerPhoto,
        peerProfile = peerProfile,
        isPeerOnline = peerProfile?.isOnline ?: false,
        peerLastSeenMillis = peerProfile?.lastSeen?.toDate()?.time,
        isPeerTyping = isPeerTyping,
        secretModeEnabled = secretModeEnabled,
        messagesState = messagesState,
        secretNotice = secretNotice,
        isUploadingAttachment = isUploadingAttachment,
        attachmentError = attachmentError,
        highlightMessageId = highlightMessageId,
        onClearSecretNotice = viewModel::clearSecretNotice,
        onClearAttachmentError = viewModel::clearAttachmentError,
        onToggleSecretMode = viewModel::toggleSecretMode,
        onTypingChanged = viewModel::onTypingChanged,
        onSendMessage = { text -> viewModel.sendMessage(text, secretModeEnabled) },
        onSendAttachment = { uri, isImage, caption ->
            viewModel.sendAttachment(context, uri, isImage, caption)
        },
        onBack = onBack,
        onStartVoiceCall = onStartVoiceCall
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    currentUserId: String,
    peerId: String,
    peerName: String,
    peerPhoto: String,
    peerProfile: UserPublicProfile?,
    isPeerOnline: Boolean,
    peerLastSeenMillis: Long?,
    isPeerTyping: Boolean,
    secretModeEnabled: Boolean,
    messagesState: UiState<List<DisplayChatMessage>>,
    secretNotice: String?,
    isUploadingAttachment: Boolean = false,
    attachmentError: String? = null,
    highlightMessageId: String? = null,
    onClearSecretNotice: () -> Unit,
    onClearAttachmentError: () -> Unit = {},
    onToggleSecretMode: (Boolean) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onSendAttachment: (Uri, Boolean, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onStartVoiceCall: (UserPublicProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by rememberSaveable { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var previewImageMessage by remember { mutableStateOf<DisplayChatMessage?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeHighlightId by remember(highlightMessageId) { mutableStateOf(highlightMessageId) }
    var didScrollToHighlight by remember(highlightMessageId) { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val caption = inputText.trim()
            inputText = ""
            onTypingChanged(false)
            onSendAttachment(uri, true, caption)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Transient permission is sufficient if provider refuses persistence
            }
            val caption = inputText.trim()
            inputText = ""
            onTypingChanged(false)
            onSendAttachment(uri, false, caption)
        }
    }

    LaunchedEffect(secretNotice) {
        if (!secretNotice.isNullOrBlank()) {
            snackbarHostState.showSnackbar(secretNotice)
            onClearSecretNotice()
        }
    }

    LaunchedEffect(attachmentError) {
        if (!attachmentError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(attachmentError)
            onClearAttachmentError()
        }
    }

    val messageList = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messageList, highlightMessageId) {
        if (messageList.isNotEmpty()) {
            if (!didScrollToHighlight && !highlightMessageId.isNullOrBlank()) {
                val targetIndex = messageList.indexOfFirst { it.id == highlightMessageId }
                if (targetIndex >= 0) {
                    didScrollToHighlight = true
                    listState.animateScrollToItem(targetIndex)
                    kotlinx.coroutines.delay(2200L)
                    activeHighlightId = null
                    return@LaunchedEffect
                }
            }
            if (didScrollToHighlight || highlightMessageId.isNullOrBlank()) {
                listState.animateScrollToItem(messageList.lastIndex)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (secretModeEnabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                name = peerName,
                                photoUrl = peerPhoto,
                                size = 40.dp,
                                isOnline = isPeerOnline,
                                showOnlineBadge = true
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        isPeerTyping -> "typing..."
                                        else -> formatLastSeen(
                                            isOnline = isPeerOnline,
                                            lastSeenMillis = peerLastSeenMillis
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isPeerTyping) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val target = peerProfile ?: UserPublicProfile(
                                    uid = peerId,
                                    displayName = peerName,
                                    photoUrl = peerPhoto
                                )
                                onStartVoiceCall(target)
                            },
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Start voice call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Prominent Secret Mode Toggle Strip inside every chat screen
            val isDarkBg = MaterialTheme.colorScheme.background.red < 0.3f
            val secretAccentColor = if (isDarkBg) SecretAmber else SecretAmberLight
            val secretBannerBg = if (secretModeEnabled) {
                if (isDarkBg) SecretSurfaceDark else SecretSurfaceLight
            } else {
                MaterialTheme.colorScheme.surface
            }

            Surface(
                color = secretBannerBg,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (secretModeEnabled) secretAccentColor else MaterialTheme.colorScheme.outlineVariant
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (secretModeEnabled) Icons.Default.EnhancedEncryption else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (secretModeEnabled) secretAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Secret Mode (Auto-Delete & Encrypted)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (secretModeEnabled) secretAccentColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (secretModeEnabled)
                                    "AES-256 E2E active • Wiped from server on read"
                                else
                                    "Enable for disappearing AES-256 encrypted messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = secretModeEnabled,
                        onCheckedChange = onToggleSecretMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = SecretAmber
                        ),
                        modifier = Modifier.testTag("secret_mode_switch")
                    )
                }
            }

            // Messages Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = messagesState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = if (secretModeEnabled)
                                        "Secret Mode is ON. Messages sent here are AES-256 encrypted and self-destruct once read."
                                    else
                                        "Say hello to $peerName!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(state.data, key = { it.id }) { msg ->
                                    MessageBubble(
                                        message = msg,
                                        isMine = msg.senderId == currentUserId,
                                        isHighlighted = msg.id == activeHighlightId,
                                        onImageClick = { previewImageMessage = it },
                                        onDocumentClick = { docMsg ->
                                            try {
                                                val parsedUri = Uri.parse(docMsg.attachmentUrl)
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    if (docMsg.attachmentMimeType.isNotBlank()) {
                                                        setDataAndType(parsedUri, docMsg.attachmentMimeType)
                                                    } else {
                                                        data = parsedUri
                                                    }
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: ActivityNotFoundException) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("No app installed to open ${docMsg.attachmentName.ifBlank { "this document" }}.")
                                                }
                                            } catch (e: Exception) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Unable to open attachment.")
                                                }
                                            }
                                        }
                                    )
                                }
                                if (isPeerTyping) {
                                    item(key = "typing_indicator") {
                                        TypingIndicatorBubble()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isUploadingAttachment) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Uploading attachment to Firebase Storage...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Composer
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        enabled = !isUploadingAttachment,
                        onClick = {
                            if (secretModeEnabled) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Turn off Secret Mode to send stored file attachments.")
                                }
                            } else {
                                showAttachmentSheet = true
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach image or document",
                            tint = if (secretModeEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { next ->
                            inputText = next
                            onTypingChanged(next.isNotBlank())
                        },
                        placeholder = {
                            Text(
                                if (secretModeEnabled) "Send secret AES-256 message..."
                                else "Message..."
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (secretModeEnabled) SecretAmber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                    onTypingChanged(false)
                                }
                            },
                            modifier = Modifier.testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (secretModeEnabled) Color.Black else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
            modifier = Modifier.testTag("attachment_picker_sheet")
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Share attachment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AttachmentPickerOptionRow(
                    icon = Icons.Default.Image,
                    title = "Photo or Image",
                    subtitle = "Pick from gallery via Android Photo Picker",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tag = "attach_image_option",
                    onClick = {
                        showAttachmentSheet = false
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                AttachmentPickerOptionRow(
                    icon = Icons.Default.InsertDriveFile,
                    title = "Document or File",
                    subtitle = "PDF, Docs, Sheets, or files up to 25 MB",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    tag = "attach_document_option",
                    onClick = {
                        showAttachmentSheet = false
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/*",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.*",
                                "*/*"
                            )
                        )
                    }
                )
            }
        }
    }

    val fullScreenMsg = previewImageMessage
    if (fullScreenMsg != null && fullScreenMsg.attachmentUrl.isNotBlank()) {
        Dialog(
            onDismissRequest = { previewImageMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fullScreenMsg.attachmentUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = fullScreenMsg.attachmentName.ifBlank { "Image attachment" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = fullScreenMsg.attachmentName.ifBlank { "Photo preview" },
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { previewImageMessage = null },
                            modifier = Modifier.testTag("image_preview_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close preview",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPickerOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = containerColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: DisplayChatMessage,
    isMine: Boolean,
    isHighlighted: Boolean = false,
    onImageClick: (DisplayChatMessage) -> Unit = {},
    onDocumentClick: (DisplayChatMessage) -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.3f
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val secretAccentColor = if (isDarkTheme) SecretAmber else SecretAmberLight
    val baseBubbleColor = when {
        message.isSecret && isMine -> if (isDarkTheme) SecretSurfaceDark else SecretSurfaceLight
        message.isSecret && !isMine -> if (isDarkTheme) SecretSurfaceDark else SecretSurfaceLight
        isMine -> MaterialTheme.colorScheme.primary
        isDarkTheme -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }

    val statusPulse = remember(message.id) { Animatable(0f) }
    var lastStatus by remember(message.id) { mutableStateOf(message.status) }
    LaunchedEffect(message.status) {
        if (isMine && !message.isSecret && lastStatus != message.status) {
            lastStatus = message.status
            statusPulse.snapTo(0f)
            statusPulse.animateTo(1f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            statusPulse.animateTo(0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }
    }

    val targetBubbleColor = when {
        isMine && !message.isSecret -> MaterialTheme.colorScheme.primary
        else -> baseBubbleColor
    }
    val animatedBubbleColor by animateColorAsState(
        targetValue = targetBubbleColor,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "bubbleStatusColor"
    )

    val textColor = when {
        message.isSecret -> MaterialTheme.colorScheme.onSurface
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val metaColor = when {
        message.isSecret -> MaterialTheme.colorScheme.onSurfaceVariant
        isMine -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    val pulseAlpha = statusPulse.value
    val pulseBorderColor = if (message.status == "read") {
        TickBlue.copy(alpha = pulseAlpha * 0.85f)
    } else {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = pulseAlpha * 0.55f)
    }
    val bubbleBorder = when {
        isHighlighted -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.tertiary)
        message.isSecret -> BorderStroke(1.dp, secretAccentColor)
        pulseAlpha > 0.01f -> BorderStroke(1.5.dp, pulseBorderColor)
        !isMine && !isDarkTheme -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = animatedBubbleColor,
            shape = shape,
            border = bubbleBorder,
            shadowElevation = if (!isMine && !isDarkTheme) 1.dp else 0.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.isSecret) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = "AES-256 encrypted",
                            tint = secretAccentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (message.burnSecondsRemaining != null)
                                "E2E Secret • Vanishing in ${message.burnSecondsRemaining}s"
                            else
                                "E2E Secret • Deletes on read",
                            style = MaterialTheme.typography.labelSmall,
                            color = secretAccentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (message.attachmentType == "image" && message.attachmentUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(textColor.copy(alpha = 0.08f))
                            .clickable { onImageClick(message) }
                            .testTag("message_image_preview_${message.id}")
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(message.attachmentUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = message.attachmentName.ifBlank { "Image attachment" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (message.attachmentName.isNotBlank()) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(topEnd = 8.dp),
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(
                                    text = message.attachmentName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (message.displayText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else if (message.attachmentType == "document" && message.attachmentUrl.isNotBlank()) {
                    val isPdf = message.attachmentMimeType.contains("pdf", ignoreCase = true) ||
                        message.attachmentName.endsWith(".pdf", ignoreCase = true)
                    Surface(
                        onClick = { onDocumentClick(message) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_document_preview_${message.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPdf) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isPdf) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.attachmentName.ifBlank { "Document" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (message.attachmentSizeBytes > 0L) {
                                            append(formatAttachmentSize(message.attachmentSizeBytes))
                                            append(" • ")
                                        }
                                        append(
                                            when {
                                                isPdf -> "PDF Document"
                                                message.attachmentMimeType.isNotBlank() ->
                                                    message.attachmentMimeType.substringAfter('/').uppercase()
                                                else -> "File"
                                            }
                                        )
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metaColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open document",
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (message.displayText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.displayText.isNotBlank()) {
                    Text(
                        text = message.displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (message.burnSecondsRemaining != null) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Self-destruct countdown",
                            tint = secretAccentColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatTimeShort(message.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor
                    )
                    if (isMine && !message.isSecret) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusTick(
                            status = message.status,
                            tintOverride = metaColor,
                            readTintOverride = TickBlueOnPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun formatAttachmentSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typingDots")
    val dot1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 120), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 240), RepeatMode.Reverse),
        label = "dot3"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            listOf(dot1, dot2, dot3).forEach { y ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .offset(y = y.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
