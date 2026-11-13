package com.example.ui.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallDeclineRed
import com.example.ui.viewmodel.ActiveCallUiState
import java.util.Locale

@Composable
fun CallOverlayScreen(
    callState: ActiveCallUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "callPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val formattedDuration = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        callState.durationSeconds / 60,
        callState.durationSeconds % 60
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF062321),
                        Color(0xFF0A3834),
                        Color(0xFF091414)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 32.dp)
        ) {
            Text(
                text = "CHAT GAURAV VOICE",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF6EE7B7)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .scale(if (callState.status == "ringing") pulseScale else 1.05f)
                        .background(Color(0xFF10B981).copy(alpha = 0.18f), CircleShape)
                )
                UserAvatar(
                    name = callState.peerName.ifBlank { "Caller" },
                    photoUrl = callState.peerPhotoUrl,
                    size = 116.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callState.peerName.ifBlank { "Chat User" },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (callState.status) {
                    "connected" -> "$formattedDuration • ${callState.iceStateLabel}"
                    "connecting" -> "Connecting secure audio..."
                    else -> callState.iceStateLabel
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD1FAE5)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (callState.isIncoming && callState.status == "ringing") {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = CallDeclineRed,
                            modifier = Modifier.size(72.dp)
                        ) {
                            IconButton(
                                onClick = onDecline,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("call_decline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = CallAcceptGreen,
                            modifier = Modifier.size(72.dp)
                        ) {
                            IconButton(
                                onClick = onAccept,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("call_accept_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Accept call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CallControlButton(
                        icon = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (callState.isMuted) "Muted" else "Mute",
                        isActive = callState.isMuted,
                        onClick = onToggleMute,
                        tag = "call_mute_button"
                    )

                    Surface(
                        shape = CircleShape,
                        color = CallDeclineRed,
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("call_end_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    CallControlButton(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = if (callState.isSpeakerOn) "Speaker On" else "Speaker",
                        isActive = callState.isSpeakerOn,
                        onClick = onToggleSpeaker,
                        tag = "call_speaker_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(58.dp)
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(tag)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color(0xFF064E4A) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}
