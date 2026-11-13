package com.elysium.vanguard.features.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun NativeMediaPlayer(
    filePath: String,
    mimeType: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var aspectRatioMode by remember { mutableIntStateOf(0) } // Start with FIT
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isPlaying by remember { mutableStateOf(true) }

    // Skip feedback state
    var skipFeedbackText by remember { mutableStateOf("") }
    var showSkipFeedback by remember { mutableStateOf(false) }

    // BUG FIX: the auto hide used to be keyed on a Boolean. Pressing the skip button
    // again while the badge was still visible did not restart the timer, so the "+10s"
    // badge could stay on screen. A monotonic token restarts the timer on EVERY press.
    var skipFeedbackToken by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(File(filePath)))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Track play/pause state from ExoPlayer
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // POSITION TRACKER
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSlider) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(300)
        }
    }

    // AUTO-HIDE CONTROLS after 5 seconds
    LaunchedEffect(lastInteractionTime) {
        kotlinx.coroutines.delay(5000)
        showControls = false
    }

    // SKIP FEEDBACK auto-hide: keyed on a token so every press restarts the timer.
    LaunchedEffect(skipFeedbackToken) {
        if (skipFeedbackToken > 0) {
            kotlinx.coroutines.delay(800)
            showSkipFeedback = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
        else "%02d:%02d".format(mins, secs)
    }

    fun seekRelative(deltaMs: Long) {
        val duration = exoPlayer.duration.coerceAtLeast(0L)
        val newPos = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        exoPlayer.seekTo(newPos)
        skipFeedbackText = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
        showSkipFeedback = true
        skipFeedbackToken++          // restarts the auto-hide timer on every press
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (mimeType.startsWith("video/")) {
            // VIDEO SURFACE
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.resizeMode = when (aspectRatioMode) {
                        1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // TAP DETECTION LAYER (invisible, captures all gestures)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 2) {
                                    seekRelative(-10000)
                                } else {
                                    seekRelative(10000)
                                }
                            }
                        )
                    }
            )

            // ═══════════════════════════════════════
            // SKIP FEEDBACK INDICATOR (center of screen)
            // ═══════════════════════════════════════
            AnimatedVisibility(
                visible = showSkipFeedback,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = skipFeedbackText,
                        color = TitanColors.NeonCyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // ═══════════════════════════════════════
            // CONTROLS OVERLAY
            // ═══════════════════════════════════════
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    // ── TOP BAR: Back + File Name + Aspect Ratio ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                            .statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = File(filePath).name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Aspect Ratio Toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable {
                                    aspectRatioMode = (aspectRatioMode + 1) % 3
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (aspectRatioMode) {
                                    1 -> "16:9"
                                    2 -> "FILL"
                                    else -> "FIT"
                                },
                                color = TitanColors.NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // ── CENTER: Play/Pause + Skip Buttons ──
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // REWIND 10s
                        IconButton(
                            onClick = { seekRelative(-10000) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FastRewind,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text("10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // PLAY / PAUSE
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // FORWARD 10s
                        IconButton(
                            onClick = { seekRelative(10000) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FastForward,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text("10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // ── BOTTOM: Seekbar + Time ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding()
                    ) {
                        // Seekbar
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = {
                                isDraggingSlider = true
                                currentPosition = it.toLong()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(currentPosition)
                                isDraggingSlider = false
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = TitanColors.NeonCyan,
                                activeTrackColor = TitanColors.NeonCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time indicators
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            // ═══════════════════════════════════════
            // AUDIO PLAYER
            // ═══════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .premiumGlass(cornerRadius = 24.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = TitanColors.QuantumPink, modifier = Modifier.size(100.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = File(filePath).name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Audio seekbar
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = {
                        isDraggingSlider = true
                        currentPosition = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(currentPosition)
                        isDraggingSlider = false
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = TitanColors.QuantumPink,
                        activeTrackColor = TitanColors.QuantumPink,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Audio controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { seekRelative(-10000) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, contentDescription = null, tint = TitanColors.QuantumPink, modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(TitanColors.QuantumPink.copy(alpha = 0.2f))
                            .border(1.dp, TitanColors.QuantumPink.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TitanColors.QuantumPink,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(
                        onClick = { seekRelative(10000) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = TitanColors.QuantumPink, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Audio Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding()
                    .premiumGlass(cornerRadius = 12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
        }
    }
}
