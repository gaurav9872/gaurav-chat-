package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TickBlue
import com.example.ui.theme.TickBlueLight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineBadge: Boolean = false
) {
    Box(modifier = Modifier.size(size)) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            val initials = name.trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "CG" }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (showOnlineBadge && isOnline) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.28f).dp.coerceAtLeast(11.dp))
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun MessageStatusTick(
    status: String,
    tintOverride: Color? = null,
    readTintOverride: Color? = null,
    modifier: Modifier = Modifier
) {
    val isDarkSurface = MaterialTheme.colorScheme.background.red < 0.3f
    val resolvedReadTint = readTintOverride ?: if (isDarkSurface) TickBlue else TickBlueLight
    val targetTint = when {
        status == "read" -> resolvedReadTint
        tintOverride != null -> tintOverride
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animatedTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "statusTickTint"
    )
    val rippleProgress = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    var lastObservedStatus by remember { mutableStateOf(status) }

    LaunchedEffect(status) {
        if (lastObservedStatus != status) {
            lastObservedStatus = status
            launch {
                rippleProgress.snapTo(0f)
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
                rippleProgress.snapTo(0f)
            }
            launch {
                scaleAnim.snapTo(0.85f)
                scaleAnim.animateTo(
                    targetValue = 1.24f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 160)
                )
            }
        }
    }

    val isDoubleTick = status == "delivered" || status == "read"
    val description = when (status) {
        "read" -> "Message read"
        "delivered" -> "Message delivered"
        else -> "Message sent"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(18.dp)
    ) {
        val progress = rippleProgress.value
        if (progress > 0f && progress < 1f) {
            val rippleColor = if (status == "read") resolvedReadTint else targetTint
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = (size.minDimension / 2f) * (0.65f + progress * 0.95f)
                drawCircle(
                    color = rippleColor.copy(alpha = (1f - progress) * 0.55f),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        AnimatedContent(
            targetState = isDoubleTick,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f, animationSpec = tween(200)))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
            },
            label = "statusTickIcon"
        ) { showDouble ->
            Icon(
                imageVector = if (showDouble) Icons.Default.DoneAll else Icons.Default.Done,
                contentDescription = description,
                tint = animatedTint,
                modifier = Modifier
                    .size(16.dp)
                    .scale(scaleAnim.value)
            )
        }
    }
}

fun formatTimeShort(millis: Long): String {
    if (millis <= 0L) return ""
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}

fun formatLastSeen(isOnline: Boolean, lastSeenMillis: Long?): String {
    if (isOnline) return "Online"
    if (lastSeenMillis == null || lastSeenMillis <= 0L) return "Offline"
    val diffMs = System.currentTimeMillis() - lastSeenMillis
    return when {
        diffMs < 60_000L -> "Last seen just now"
        diffMs < 3_600_000L -> "Last seen ${diffMs / 60_000L}m ago"
        diffMs < 86_400_000L -> "Last seen today at ${formatTimeShort(lastSeenMillis)}"
        else -> {
            val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            "Last seen ${dateFmt.format(Date(lastSeenMillis))}"
        }
    }
}
