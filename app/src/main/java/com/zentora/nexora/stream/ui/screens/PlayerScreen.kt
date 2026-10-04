/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraDrm
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.*
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    repository: NexoraStreamRepository,
    onBack: () -> Unit,
    onNavigateVideo: (String) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val currentVideo = allVideos.find { it.id == videoId } ?: allVideos.firstOrNull()
    val interaction by repository.getInteraction(videoId).collectAsState(initial = null)
    val comments by repository.getComments(videoId).collectAsState(initial = emptyList())
    val notes by repository.getNotes(videoId).collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())
    val authorChannel = allChannels.find { it.channelId == currentVideo?.authorChannelId }

    // ExoPlayer state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(1L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Feature 9: One-Tap Screen Touch Lock
    var isTouchLocked by remember { mutableStateOf(false) }

    // Feature 8: A-B Repeat Loop tool
    var isLoopActive by remember { mutableStateOf(false) }
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    // Feature 10: Gestures HUD
    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    var volumeOverlay by remember { mutableStateOf<Int?>(null) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    // Modals
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var currentAudioTrack by remember { mutableStateOf("en") }
    var showNotesModal by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // Chapter markers (Feature 11)
    val chapters = remember(currentVideo?.chapterMarkersJson) {
        parseChapterMarkers(currentVideo?.chapterMarkersJson ?: "[]")
    }

    // Media3 ExoPlayer instance with optimized buffer management (Feature 4)
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    totalDurationMs = maxOf(1L, exoPlayer.duration)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Load video with DRM masking applied (Feature 26)
    LaunchedEffect(currentVideo?.id) {
        if (currentVideo != null) {
            val maskedUri = NexoraDrm.maskUri(currentVideo.localUri)
            val physicalUri = NexoraDrm.resolveUri(maskedUri)
            val mediaItem = MediaItem.fromUri(physicalUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            // Increment view count in Room database
            repository.recordView(currentVideo.id, currentVideo.duration, 0L)
        }
    }

    // Periodic time loop + A-B Loop checking (Feature 8)
    LaunchedEffect(isPlaying, isLoopActive, loopStartMs, loopEndMs) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            totalDurationMs = maxOf(1L, exoPlayer.duration)

            if (isLoopActive && loopStartMs != null && loopEndMs != null) {
                if (currentPositionMs >= loopEndMs!!) {
                    exoPlayer.seekTo(loopStartMs!!)
                }
            }
            delay(400)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible, isPlaying, isTouchLocked) {
        if (isControlsVisible && isPlaying && !isTouchLocked) {
            delay(3500)
            isControlsVisible = false
        }
    }

    BackHandler {
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isFullscreen = false
        } else {
            onBack()
        }
    }

    if (currentVideo == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NexoraRed)
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Player Surface + Gestures Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                    .background(Color.Black)
                    // Gestures: Vertical swipe for Brightness & Volume (Feature 10)
                    .pointerInput(isTouchLocked) {
                        if (!isTouchLocked) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    brightnessOverlay = null
                                    volumeOverlay = null
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val isLeft = change.position.x < (size.width / 2)
                                    val delta = -dragAmount / size.height

                                    if (isLeft) {
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                            val n = (cur + delta).coerceIn(0.05f, 1.0f)
                                            lp.screenBrightness = n
                                            act.window.attributes = lp
                                            brightnessOverlay = n
                                        }
                                    } else {
                                        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val d = (delta * maxVolume).toInt()
                                        val n = (cur + d).coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0)
                                        volumeOverlay = (n * 100 / maxVolume)
                                    }
                                }
                            )
                        }
                    }
                    // Tap gestures: Single tap toggle controls, Double tap seek 10s (Feature 10)
                    .pointerInput(isTouchLocked) {
                        detectTapGestures(
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (!isTouchLocked) {
                                    val isLeft = offset.x < (size.width / 2)
                                    if (isLeft) {
                                        val p = maxOf(0L, exoPlayer.currentPosition - 10_000)
                                        exoPlayer.seekTo(p)
                                        doubleTapFeedback = "-10s"
                                    } else {
                                        val p = minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000)
                                        exoPlayer.seekTo(p)
                                        doubleTapFeedback = "+10s"
                                    }
                                    scope.launch {
                                        delay(700)
                                        doubleTapFeedback = null
                                    }
                                }
                            }
                        )
                    }
            ) {
                // ExoPlayer Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Feature 25: Floating Live Reaction Emojis
                FloatingReactionContainer(
                    modifier = Modifier.fillMaxSize(),
                    onReactionSent = { em ->
                        Toast.makeText(context, "Reaction $em broadcasted!", Toast.LENGTH_SHORT).show()
                    }
                )

                // Double-Tap Feedback
                doubleTapFeedback?.let { fb ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(14.dp)
                    ) {
                        Text(text = fb, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }

                // Brightness & Volume HUD overlays
                brightnessOverlay?.let { b ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BrightnessMedium, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Brightness: ${(b * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                volumeOverlay?.let { v ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Volume: $v%", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Buffering Indicator
                if (isBuffering) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexoraRed)
                    }
                }

                // Feature 9: Touch Lock Status Floating Button
                if (isTouchLocked) {
                    IconButton(
                        onClick = {
                            isTouchLocked = false
                            Toast.makeText(context, "Screen Touch Unlocked", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .clip(CircleShape)
                            .background(NexoraRed)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                }

                // Full Controls Overlay (Hidden when screen is touch-locked)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isTouchLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
                        // Top Action Icons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back", tint = Color.White)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Touch Lock Toggle (Feature 9)
                                IconButton(onClick = {
                                    isTouchLocked = true
                                    Toast.makeText(context, "Screen Touch Locked", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White)
                                }

                                // Audio Track Switcher (Feature 6)
                                IconButton(onClick = { showAudioTrackDialog = true }) {
                                    Icon(Icons.Filled.Audiotrack, contentDescription = "Audio track", tint = Color.White)
                                }

                                // Timestamped Notes & Bookmarks (Feature 7)
                                IconButton(onClick = { showNotesModal = true }) {
                                    Icon(Icons.Filled.NoteAlt, contentDescription = "Notes", tint = Color.White)
                                }

                                // Picture-in-Picture (Feature 12)
                                IconButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    }
                                }) {
                                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                                }
                            }
                        }

                        // Center Play / Pause
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            IconButton(onClick = {
                                exoPlayer.seekTo(maxOf(0L, exoPlayer.currentPosition - 10_000))
                            }) {
                                Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            IconButton(
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Toggle Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = {
                                exoPlayer.seekTo(minOf(exoPlayer.duration, exoPlayer.currentPosition + 10_000))
                            }) {
                                Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }

                        // Bottom Scrub Bar, Heatmap & Chapter Markers
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            // Feature 11: Chapter Markers Bar
                            if (chapters.isNotEmpty()) {
                                ChapterMarkersBar(
                                    markers = chapters,
                                    currentPositionMs = currentPositionMs,
                                    totalDurationMs = totalDurationMs,
                                    onSeekTo = { pos -> exoPlayer.seekTo(pos) }
                                )
                            }

                            // Feature 5: Most Replayed Heatmap Canvas
                            ReplayedHeatmapCanvas(
                                videoDurationMs = totalDurationMs,
                                seed = currentVideo.id.hashCode()
                            )

                            // Scrub Slider
                            Slider(
                                value = currentPositionMs.toFloat(),
                                onValueChange = { newPos ->
                                    currentPositionMs = newPos.toLong()
                                    exoPlayer.seekTo(newPos.toLong())
                                },
                                valueRange = 0f..totalDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = NexoraRed, activeTrackColor = NexoraRed),
                                modifier = Modifier.fillMaxWidth().height(20.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatMsToTime(currentPositionMs)} / ${formatMsToTime(totalDurationMs)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Feature 8: A-B Repeat Loop button
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isLoopActive) NexoraRed else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.clickable {
                                            if (!isLoopActive) {
                                                // Start loop from current to +15s
                                                loopStartMs = currentPositionMs
                                                loopEndMs = minOf(totalDurationMs, currentPositionMs + 15000L)
                                                isLoopActive = true
                                                Toast.makeText(context, "A-B Loop Active: ${formatMsToTime(loopStartMs!!)} -> ${formatMsToTime(loopEndMs!!)}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isLoopActive = false
                                                loopStartMs = null
                                                loopEndMs = null
                                                Toast.makeText(context, "A-B Loop Disabled", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (isLoopActive) "LOOP ON" else "A-B LOOP",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Fullscreen Toggle
                                    IconButton(
                                        onClick = {
                                            val newFs = !isFullscreen
                                            isFullscreen = newFs
                                            activity?.requestedOrientation = if (newFs) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Below Player Scrollable Content
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = currentVideo.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${currentVideo.viewCount} views • ${currentVideo.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Channel info & Subscribe button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).clickable { onChannelClick(currentVideo.authorChannelId) }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                        contentDescription = "Channel",
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (authorChannel?.hasZentoraBadge == true) {
                                        Box(
                                            modifier = Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(NexoraVerifiedTick)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(authorChannel?.channelName ?: "Zentora CLC", fontWeight = FontWeight.Bold)
                                        if (authorChannel?.hasZentoraBadge == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = NexoraVerifiedTick, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Text(
                                        text = "${authorChannel?.subscriberCount ?: 0L} subscribers • ${authorChannel?.creatorLevel ?: "Rising Creator"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexoraRed
                                    )
                                }
                            }

                            // Subscribe Button (Feature 24)
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.toggleSubscribe(currentVideo.authorChannelId)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authorChannel?.isSubscribed == true) MaterialTheme.colorScheme.surfaceVariant else NexoraRed
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = if (authorChannel?.isSubscribed == true) "Subscribed" else "Subscribe",
                                    color = if (authorChannel?.isSubscribed == true) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Action Bar: Reactive Like/Dislike, Nexora Spark, Watch Later, Comments
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Like/Dislike pill (Feature 22)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { scope.launch { repository.toggleLike(currentVideo.id) } },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (interaction?.isLikedByMe == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                            contentDescription = "Like",
                                            tint = if (interaction?.isLikedByMe == true) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "${interaction?.likesCount ?: 0L}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.Gray.copy(alpha = 0.4f)))
                                    IconButton(
                                        onClick = { scope.launch { repository.toggleDislike(currentVideo.id) } },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (interaction?.isDislikedByMe == true) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                            contentDescription = "Dislike",
                                            tint = if (interaction?.isDislikedByMe == true) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Nexora Spark / Super Thanks (Feature 20)
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.sendSparks(currentVideo.id, 50L)
                                        Toast.makeText(context, "Sent 50 Nexora Sparks to Creator! ⚡", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NexoraSparkGold),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Spark (${interaction?.sparksReceived ?: 0L})", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                            }

                            // Watch Later Toggle
                            IconButton(
                                onClick = { scope.launch { repository.toggleWatchLater(currentVideo.id) } },
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = if (interaction?.savedToWatchLater == true) Icons.Filled.BookmarkAdded else Icons.Outlined.WatchLater,
                                    contentDescription = "Watch Later",
                                    tint = if (interaction?.savedToWatchLater == true) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Comments Preview Bar (Feature 23)
                        Card(
                            onClick = { showCommentsSheet = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Comments (${comments.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    val top = comments.firstOrNull()
                                    Text(
                                        text = top?.commentText ?: "Tap to join the discussion…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Next on Nexora Stream",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Up Next Video List
                    val related = allVideos.filter { it.id != currentVideo.id }
                    items(related, key = { it.id }) { relVideo ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateVideo(relVideo.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = relVideo.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(formatMsToTime(relVideo.duration), color = Color.White, fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(relVideo.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${relVideo.viewCount} views • ${relVideo.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Audio Track Switcher Dialog (Feature 6)
    if (showAudioTrackDialog) {
        AudioTrackDialog(
            currentTrackId = currentAudioTrack,
            onDismiss = { showAudioTrackDialog = false },
            onSelectTrack = { track ->
                currentAudioTrack = track.id
                showAudioTrackDialog = false
                Toast.makeText(context, "Switched to: ${track.languageName}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Video Notes & Bookmarks Modal (Feature 7)
    if (showNotesModal) {
        VideoNotesModal(
            notes = notes,
            currentVideoPositionMs = currentPositionMs,
            onDismiss = { showNotesModal = false },
            onSeekTo = { seekMs ->
                exoPlayer.seekTo(seekMs)
                showNotesModal = false
            },
            onAddNote = { timeMs, text ->
                scope.launch { repository.addNote(currentVideo.id, timeMs, text) }
            },
            onDeleteNote = { noteId ->
                scope.launch { repository.deleteNote(noteId) }
            }
        )
    }

    // Comments BottomSheet (Feature 23 & 27)
    if (showCommentsSheet) {
        StreamCommentsBottomSheet(
            comments = comments,
            onDismiss = { showCommentsSheet = false },
            onPostComment = { text ->
                var isSafe = true
                scope.launch {
                    val user = NexoraIdManager.getSession()
                    isSafe = repository.addComment(currentVideo.id, user.username, text)
                }
                isSafe
            },
            onLikeComment = { comment ->
                scope.launch { repository.toggleCommentLike(comment) }
            }
        )
    }
}
