package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.LocalVideo
import com.example.ui.viewmodel.SeekPreview
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.VlcOrange
import com.example.ui.viewmodel.AspectRatioMode
import com.example.ui.viewmodel.PlayerViewModel
import com.example.utils.SubtitleCue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerScreen(
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Lock orientation to Landscape on player open for standard cinematic view
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        viewModel.initPlayer(context)
    }

    // Reset orientation to portrait on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioMode.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    // Gestures Overlay Values
    val volumeProgress by viewModel.volumeProgress.collectAsState()
    val brightnessProgress by viewModel.brightnessProgress.collectAsState()
    val seekPreview by viewModel.seekPreview.collectAsState()

    // Subtitles Values
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val currentSubtitleText by viewModel.currentSubtitleText.collectAsState()
    val subtitleDelay by viewModel.subtitleDelay.collectAsState()
    val selectedSubtitleTrack by viewModel.selectedSubtitleTrack.collectAsState()

    // Sleep Timer Values
    val sleepTimerMinutes by viewModel.sleepTimerMinutesLeft.collectAsState()

    // HUD controls visibility timer
    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000) // Auto-hide controls after 4 seconds of inactivity
            showControls = false
        }
    }

    // Sheet/Dialog states
    var showSubtitleSyncDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showCustomSrtDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. ExoPlayer Component
        viewModel.player?.let { exoPlayer ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        // Apply Aspect Ratio Resize Modes dynamically
                        view.resizeMode = when (aspectRatioMode) {
                            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioMode.SIXTEEN_NINE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Gesture Detector Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isLocked) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = { offset ->
                                    if (!isLocked) {
                                        val xPos = offset.x
                                        val third = size.width / 3f
                                        if (xPos < third) {
                                            viewModel.skipBackward()
                                        } else if (xPos > third * 2) {
                                            viewModel.skipForward()
                                        } else {
                                            viewModel.togglePlayPause()
                                        }
                                        showControls = true
                                    }
                                }
                            )
                        }
                        .pointerInput(isLocked) {
                            var dragType = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
                            var startX = 0f
                            detectDragGestures(
                                onDragStart = { offset ->
                                    startX = offset.x
                                    dragType = 0
                                },
                                onDrag = { change, dragAmount ->
                                    if (!isLocked) {
                                        change.consume()
                                        // Decide drag intent
                                        if (dragType == 0) {
                                            dragType = if (Math.abs(dragAmount.x) > Math.abs(dragAmount.y)) 1 else 2
                                        }

                                        if (dragType == 1) {
                                            // Horizontal Seek Gesture
                                            val fraction = dragAmount.x / size.width
                                            viewModel.seekGesture(fraction)
                                        } else if (dragType == 2) {
                                            // Vertical Gesture (Left: Brightness, Right: Volume)
                                            val verticalFraction = -dragAmount.y / size.height
                                            val dragX = change.position.x
                                            if (dragX < size.width / 2f) {
                                                // Left side: Brightness
                                                val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1.0f
                                                val newBrightness = viewModel.adjustBrightnessGesture(verticalFraction, currentBrightness)
                                                
                                                activity?.runOnUiThread {
                                                    val layoutParams = activity.window.attributes
                                                    layoutParams.screenBrightness = newBrightness
                                                    activity.window.attributes = layoutParams
                                                }
                                            } else {
                                                // Right side: Volume
                                                viewModel.adjustVolumeGesture(verticalFraction)
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (dragType == 1) {
                                        viewModel.commitSeekGesture()
                                    }
                                    viewModel.cancelSeekGesture()
                                }
                            )
                        }
                )
            }
        }

        // 3. Custom Subtitles Overlay (Scaled & Stylishly positioned)
        if (subtitlesEnabled && currentSubtitleText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 100.dp else 40.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentSubtitleText,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        // 4. Gesture Indicators Overlay (HUD Volume, Brightness, Seek Progress)
        GestureIndicatorOverlay(volumeProgress, brightnessProgress, seekPreview)

        // 5. Custom HUD overlay (Top and Bottom controls panels)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top controls bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onBackClick() },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = currentVideo?.title ?: "Playing Video",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (sleepTimerMinutes > 0) {
                                Text(
                                    text = "Sleep Timer: ${sleepTimerMinutes}m left",
                                    color = VlcOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Top Right buttons (Subtitle selector, Speed selector, Sleep Timer, Favorites)
                    if (!isLocked) {
                        Row {
                            IconButton(onClick = { showSleepTimerDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerMinutes > 0) VlcOrange else Color.White
                                )
                            }
                            IconButton(onClick = { showSubtitleSyncDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitle Options",
                                    tint = if (selectedSubtitleTrack != "None") VlcOrange else Color.White
                                )
                            }
                            IconButton(onClick = { showPlaybackSpeedDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = if (playbackSpeed != 1.0f) VlcOrange else Color.White
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) Color.Red else Color.White
                                )
                            }
                        }
                    }
                }

                // Center Lock Button (Playit / VLC Child lock)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(50.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock Controls",
                            tint = if (isLocked) VlcOrange else Color.White
                        )
                    }
                }

                // Bottom HUD (Slider and media keys)
                if (!isLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        // Slider and times row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentTime),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Slider(
                                value = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f,
                                onValueChange = { fraction ->
                                    viewModel.seekTo((fraction * duration).toLong())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = VlcOrange,
                                    activeTrackColor = VlcOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(
                                text = formatTime(duration),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        // Media playback actions row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cycle Aspect Ratio button
                            IconButton(onClick = {
                                val nextMode = when (aspectRatioMode) {
                                    AspectRatioMode.FIT -> AspectRatioMode.STRETCH
                                    AspectRatioMode.STRETCH -> AspectRatioMode.FILL
                                    AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                    AspectRatioMode.ZOOM -> AspectRatioMode.SIXTEEN_NINE
                                    AspectRatioMode.SIXTEEN_NINE -> AspectRatioMode.FOUR_THREE
                                    AspectRatioMode.FOUR_THREE -> AspectRatioMode.FIT
                                }
                                viewModel.setAspectRatio(nextMode)
                            }) {
                                Icon(
                                    imageVector = when (aspectRatioMode) {
                                        AspectRatioMode.FIT -> Icons.Default.AspectRatio
                                        AspectRatioMode.STRETCH -> Icons.Default.FitScreen
                                        else -> Icons.Default.Fullscreen
                                    },
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White
                                )
                            }

                            // Skip backward 10s
                            IconButton(onClick = { viewModel.skipBackward() }) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Play / Pause Master
                            FloatingActionButton(
                                onClick = { viewModel.togglePlayPause() },
                                containerColor = VlcOrange,
                                contentColor = Color.Black,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Skip forward 10s
                            IconButton(onClick = { viewModel.skipForward() }) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Mute button
                            IconButton(onClick = { viewModel.toggleMute() }) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute Toggle",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Subtitle Synchronization Dialog (VLC style) ---
    if (showSubtitleSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleSyncDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = VlcOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subtitles & Synchronization")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Configure subtitles and manually adjust synchronization delay to align with audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Track selector
                    Text(
                        "Subtitle Track:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.unloadSubtitles() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSubtitleTrack == "None") VlcOrange else SlateSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Off", color = if (selectedSubtitleTrack == "None") Color.Black else Color.White)
                        }
                        Button(
                            onClick = { viewModel.loadDemoSubtitles() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSubtitleTrack == "Demo English SRT") VlcOrange else SlateSurface
                            ),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Demo SRT", color = if (selectedSubtitleTrack == "Demo English SRT") Color.Black else Color.White)
                        }
                        Button(
                            onClick = { showCustomSrtDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedSubtitleTrack.contains("Custom")) VlcOrange else SlateSurface
                            ),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Upload SRT", color = if (selectedSubtitleTrack.contains("Custom")) Color.Black else Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Synchronization delay adjuster (+/- 100ms or 500ms)
                    Text(
                        "Synchronization Delay: ${subtitleDelay} ms",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "If subtitle is too fast, add delay (+). If too slow, subtract (-).",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setSubtitleDelay(subtitleDelay - 500L) },
                            modifier = Modifier.background(SlateSurface, CircleShape)
                        ) {
                            Text("-500", color = Color.White, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { viewModel.setSubtitleDelay(subtitleDelay - 100L) },
                            modifier = Modifier.background(SlateSurface, CircleShape)
                        ) {
                            Text("-100", color = Color.White, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { viewModel.setSubtitleDelay(0L) },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface)
                        ) {
                            Text("Reset", color = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.setSubtitleDelay(subtitleDelay + 100L) },
                            modifier = Modifier.background(SlateSurface, CircleShape)
                        ) {
                            Text("+100", color = Color.White, fontSize = 11.sp)
                        }
                        IconButton(
                            onClick = { viewModel.setSubtitleDelay(subtitleDelay + 500L) },
                            modifier = Modifier.background(SlateSurface, CircleShape)
                        ) {
                            Text("+500", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleSyncDialog = false }) {
                    Text("Close", color = VlcOrange)
                }
            }
        )
    }

    // --- Custom SRT Uploader dialog ---
    if (showCustomSrtDialog) {
        var customSrtText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomSrtDialog = false },
            title = { Text("Paste SRT Subtitles") },
            text = {
                Column {
                    Text(
                        "Paste raw SRT subtitle content below to load custom subtitles.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = customSrtText,
                        onValueChange = { customSrtText = it },
                        placeholder = { Text("1\n00:00:01,000 --> 00:00:05,000\nHello Custom!") },
                        maxLines = 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customSrtText.isNotBlank()) {
                            viewModel.loadCustomSubtitles(customSrtText, "Custom SRT Track")
                        }
                        showCustomSrtDialog = false
                        showSubtitleSyncDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VlcOrange)
                ) {
                    Text("Load Subtitles", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomSrtDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    // --- Playback Speed Dialog ---
    if (showPlaybackSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showPlaybackSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSpeed(speed)
                                    showPlaybackSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "${speed}x", color = Color.White, fontWeight = FontWeight.Medium)
                            if (playbackSpeed == speed) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = VlcOrange)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // --- Playit Sleep Timer Dialog ---
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = VlcOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sleep Timer")
                }
            },
            text = {
                Column {
                    Text(
                        "Automatically pause playback after selected period expires. Ideal for night watching.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    listOf(
                        "Off" to 0,
                        "15 minutes" to 15,
                        "30 minutes" to 30,
                        "45 minutes" to 45,
                        "60 minutes" to 60
                    ).forEach { (label, minutes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (minutes == 0) {
                                        viewModel.stopSleepTimer()
                                    } else {
                                        viewModel.startSleepTimer(minutes)
                                    }
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, color = Color.White, fontWeight = FontWeight.Medium)
                            if ((minutes == 0 && sleepTimerMinutes == 0) || (minutes > 0 && sleepTimerMinutes == minutes)) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = VlcOrange)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun GestureIndicatorOverlay(
    volumeProgress: Float?,
    brightnessProgress: Float?,
    seekPreview: SeekPreview?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Volume overlay
        if (volumeProgress != null) {
            GestureCard(
                icon = if (volumeProgress == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                title = "Volume",
                progress = volumeProgress
            )
        }

        // Brightness overlay
        if (brightnessProgress != null) {
            GestureCard(
                icon = Icons.Default.LightMode,
                title = "Brightness",
                progress = brightnessProgress
            )
        }

        // Seek preview overlay
        if (seekPreview != null) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (seekPreview.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = VlcOrange,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (seekPreview.isForward) {
                            "+${formatTime(seekPreview.deltaMs)}"
                        } else {
                            "-${formatTime(seekPreview.deltaMs)}"
                        },
                        color = VlcOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTime(seekPreview.seekToTimeMs)} / ${formatTime(seekPreview.totalTimeMs)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GestureCard(
    icon: ImageVector,
    title: String,
    progress: Float
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        modifier = Modifier
            .size(width = 180.dp, height = 110.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = VlcOrange, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = VlcOrange,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
