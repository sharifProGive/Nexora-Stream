/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraDrm
import com.zentora.nexora.stream.engine.NexoraLinkManager
import com.zentora.nexora.stream.ui.components.*
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NexoraAdData(
    val adId: String,
    val title: String,
    val sponsorName: String,
    val adMediaUrl: String,
    val clickThroughUrl: String,
    val isSkippable: Boolean,
    val skippableAfterSeconds: Int,
    val totalAdDurationSeconds: Int
)

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
    val currentVideo = remember(allVideos, videoId) {
        allVideos.find { it.id == videoId } ?: allVideos.firstOrNull()
    }
    val interaction by repository.getInteraction(videoId).collectAsState(initial = null)
    val comments by repository.getComments(videoId).collectAsState(initial = emptyList())
    val notes by repository.getNotes(videoId).collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())
    val authorChannel = allChannels.find { it.channelId == currentVideo?.authorChannelId }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(1L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    var isAdActive by remember { mutableStateOf(false) }
    var currentAd by remember { mutableStateOf<NexoraAdData?>(null) }
    var adCountdownRemaining by remember { mutableIntStateOf(9) }
    var isAdSkippable by remember { mutableStateOf(false) }

    var isTouchLocked by remember { mutableStateOf(false) }

    var isLoopActive by remember { mutableStateOf(false) }
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    var brightnessOverlay by remember { mutableStateOf<Float?>(null) }
    var volumeOverlay by remember { mutableStateOf<Int?>(null) }
    var doubleTapFeedback by remember { mutableStateOf<String?>(null) }

    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var currentAudioTrack by remember { mutableStateOf("en") }
    var showNotesModal by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { maxOf(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }

    val chapters = remember(currentVideo?.chapterMarkersJson) {
        parseChapterMarkers(currentVideo?.chapterMarkersJson ?: "[]")
    }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    val playMainVideo: () -> Unit = {
        isAdActive = false
        currentAd = null
        isAdSkippable = false
        currentPositionMs = 0L
        if (currentVideo != null) {
            try {
                val maskedUri = NexoraDrm.maskUri(currentVideo.localUri)
                val physicalUri = NexoraDrm.resolveUri(maskedUri)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.setMediaItem(MediaItem.fromUri(physicalUri))
                exoPlayer.prepare()
                exoPlayer.play()
                scope.launch {
                    repository.recordView(currentVideo.id, currentVideo.duration, 0L)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to load video stream", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val latestIsAdActive by rememberUpdatedState(isAdActive)
    val latestPlayMainVideo by rememberUpdatedState(playMainVideo)

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0L) {
                        totalDurationMs = dur
                    }
                } else if (state == Player.STATE_ENDED) {
                    if (latestIsAdActive) {
                        latestPlayMainVideo()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (latestIsAdActive) {
                    latestPlayMainVideo()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(currentVideo?.id) {
        if (currentVideo == null) return@LaunchedEffect

        isLoopActive = false
        loopStartMs = null
        loopEndMs = null

        var fetchedAd: NexoraAdData? = null
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://nexora-backend-fq54.onrender.com/api/v1/ads/pre-roll")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3500
                    readTimeout = 3500
                }
                if (connection.responseCode == 200) {
                    val respText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(respText)
                    if (json.optBoolean("hasAd", false) && json.has("ad")) {
                        val adObj = json.getJSONObject("ad")
                        val rawMediaUrl = adObj.optString("adMediaUrl", "").trim()
                        if (rawMediaUrl.isNotEmpty()) {
                            val totalDur = adObj.optInt("totalAdDurationSeconds", 15).coerceIn(5, 90)
                            val skipAfter = adObj.optInt("skippableAfterSeconds", 9).coerceIn(5, 10)
                            val skippable = if (totalDur <= 10) false else adObj.optBoolean("isSkippable", true)

                            fetchedAd = NexoraAdData(
                                adId = adObj.optString("adId", "ZT-AD-LIVE"),
                                title = adObj.optString("title", "Sponsored"),
                                sponsorName = adObj.optString("sponsorName", "Official Partner"),
                                adMediaUrl = rawMediaUrl,
                                clickThroughUrl = adObj.optString("clickThroughUrl", "").trim(),
                                isSkippable = skippable,
                                skippableAfterSeconds = if (skippable) skipAfter else totalDur,
                                totalAdDurationSeconds = totalDur
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                fetchedAd = null
            } finally {
                connection?.disconnect()
            }
        }

        val finalAd = fetchedAd ?: NexoraAdData(
            adId = "ZT-AD-FALLBACK",
            title = "Official Partner Promotion",
            sponsorName = "Zentora Partner Network",
            adMediaUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            clickThroughUrl = "https://play.google.com/store/apps/details?id=com.zentora.nexora.stream",
            isSkippable = true,
            skippableAfterSeconds = 9,
            totalAdDurationSeconds = 15
        )

        currentAd = finalAd
        isAdActive = true
        adCountdownRemaining = finalAd.skippableAfterSeconds
        isAdSkippable = false

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(finalAd.adMediaUrl))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    LaunchedEffect(isAdActive, adCountdownRemaining, currentAd) {
        if (isAdActive && currentAd != null) {
            if (adCountdownRemaining > 0) {
                delay(1000L)
                adCountdownRemaining -= 1
            }
            if (adCountdownRemaining <= 0) {
                if (currentAd?.isSkippable == true) {
                    isAdSkippable = true
                } else {
                    playMainVideo()
                }
            }
        }
    }

    LaunchedEffect(isPlaying, isAdActive, isLoopActive, loopStartMs, loopEndMs) {
        while (isPlaying) {
            val pos = maxOf(0L, exoPlayer.currentPosition)
            val dur = exoPlayer.duration
            currentPositionMs = pos
            if (dur > 0L) {
                totalDurationMs = dur
            }

            if (isAdActive) {
                val maxAdMs = (currentAd?.totalAdDurationSeconds ?: 90).coerceAtMost(90) * 1000L
                if (pos >= maxAdMs) {
                    playMainVideo()
                }
            } else if (isLoopActive && loopStartMs != null && loopEndMs != null) {
                if (pos >= loopEndMs!!) {
                    exoPlayer.seekTo(loopStartMs!!)
                }
            }
            delay(400L)
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isTouchLocked, isAdActive) {
        if (isControlsVisible && isPlaying && !isTouchLocked && !isAdActive) {
            delay(3500L)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                    .background(Color.Black)
                    .pointerInput(isTouchLocked, isAdActive) {
                        if (!isTouchLocked && !isAdActive) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    brightnessOverlay = null
                                    volumeOverlay = null
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    if (size.height <= 0) return@detectVerticalDragGestures
                                    val isLeft = change.position.x < (size.width / 2)
                                    val delta = -dragAmount / size.height.toFloat()

                                    if (isLeft) {
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
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
                    .pointerInput(isTouchLocked, isAdActive) {
                        detectTapGestures(
                            onTap = {
                                if (!isAdActive) {
                                    isControlsVisible = !isControlsVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!isTouchLocked && !isAdActive) {
                                    val safeDur = if (exoPlayer.duration > 0L) exoPlayer.duration else totalDurationMs
                                    val isLeft = offset.x < (size.width / 2)
                                    if (isLeft) {
                                        val p = maxOf(0L, exoPlayer.currentPosition - 10_000L)
                                        exoPlayer.seekTo(p)
                                        doubleTapFeedback = "-10s"
                                    } else {
                                        val p = minOf(safeDur, exoPlayer.currentPosition + 10_000L)
                                        exoPlayer.seekTo(p)
                                        doubleTapFeedback = "+10s"
                                    }
                                    scope.launch {
                                        delay(700L)
                                        doubleTapFeedback = null
                                    }
                                }
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isAdActive && currentAd != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clickable {
                                    val rawUrl = currentAd?.clickThroughUrl?.trim().orEmpty()
                                    if (rawUrl.isNotEmpty()) {
                                        val validUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                                            rawUrl
                                        } else {
                                            "https://$rawUrl"
                                        }
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Cannot open sponsor link", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFFFCC00))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentAd?.sponsorName ?: "Sponsored",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = "Open Sponsor",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Surface(
                            color = if (isAdSkippable) NexoraRed else Color.Black.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 20.dp)
                                .clickable(enabled = isAdSkippable) {
                                    playMainVideo()
                                }
                        ) {
                            val labelText = when {
                                currentAd?.isSkippable == false -> "Ad ends in ${adCountdownRemaining}s"
                                isAdSkippable -> "Skip Ad ⏭"
                                else -> "Skip in ${adCountdownRemaining}s"
                            }
                            Text(
                                text = labelText,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (!isAdActive) {
                    // FloatingReactionContainer removed successfully here.

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
                }

                if (isBuffering) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NexoraRed)
                    }
                }

                if (isTouchLocked && !isAdActive) {
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

                // Explicit receiver fix for AnimatedVisibility inside Box scope
                androidx.compose.animation.AnimatedVisibility(
                    visible = isControlsVisible && !isTouchLocked && !isAdActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    ) {
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
                                IconButton(onClick = {
                                    isTouchLocked = true
                                    Toast.makeText(context, "Screen Touch Locked", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White)
                                }

                                IconButton(onClick = { showAudioTrackDialog = true }) {
                                    Icon(Icons.Filled.Audiotrack, contentDescription = "Audio track", tint = Color.White)
                                }

                                IconButton(onClick = { showNotesModal = true }) {
                                    Icon(Icons.Filled.NoteAlt, contentDescription = "Notes", tint = Color.White)
                                }

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

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            IconButton(onClick = {
                                exoPlayer.seekTo(maxOf(0L, exoPlayer.currentPosition - 10_000L))
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
                                val safeDur = if (exoPlayer.duration > 0L) exoPlayer.duration else totalDurationMs
                                exoPlayer.seekTo(minOf(safeDur, exoPlayer.currentPosition + 10_000L))
                            }) {
                                Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (chapters.isNotEmpty()) {
                                ChapterMarkersBar(
                                    markers = chapters,
                                    currentPositionMs = currentPositionMs,
                                    totalDurationMs = totalDurationMs,
                                    onSeekTo = { pos -> exoPlayer.seekTo(pos) }
                                )
                            }

                            ReplayedHeatmapCanvas(
                                videoDurationMs = totalDurationMs,
                                seed = currentVideo.id.hashCode()
                            )

                            val safeTotalDurationFloat = maxOf(1f, totalDurationMs.toFloat())
                            val safeCurrentPosFloat = currentPositionMs.toFloat().coerceIn(0f, safeTotalDurationFloat)

                            Slider(
                                value = safeCurrentPosFloat,
                                onValueChange = { newPos ->
                                    currentPositionMs = newPos.toLong()
                                    exoPlayer.seekTo(newPos.toLong())
                                },
                                valueRange = 0f..safeTotalDurationFloat,
                                colors = SliderDefaults.colors(thumbColor = NexoraRed, activeTrackColor = NexoraRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
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
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isLoopActive) NexoraRed else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.clickable {
                                            if (!isLoopActive) {
                                                loopStartMs = currentPositionMs
                                                loopEndMs = minOf(totalDurationMs, currentPositionMs + 15_000L)
                                                isLoopActive = true
                                                Toast.makeText(
                                                    context,
                                                    "A-B Loop Active: ${formatMsToTime(loopStartMs!!)} -> ${formatMsToTime(loopEndMs!!)}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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

                                    IconButton(
                                        onClick = {
                                            val newFs = !isFullscreen
                                            isFullscreen = newFs
                                            activity?.requestedOrientation = if (newFs) {
                                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                            contentDescription = "Fullscreen",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onChannelClick(currentVideo.authorChannelId) }
                            ) {
                                Box {
                                    AsyncImage(
                                        model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                        contentDescription = "Channel",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (authorChannel?.hasZentoraBadge == true) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(NexoraVerifiedTick)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(authorChannel?.channelName ?: "Zentora CLC", fontWeight = FontWeight.Bold)
                                        if (authorChannel?.hasZentoraBadge == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = NexoraVerifiedTick,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${authorChannel?.subscriberCount ?: 0L} subscribers • ${authorChannel?.creatorLevel ?: "Rising Creator"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NexoraRed
                                    )
                                }
                            }

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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
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
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(16.dp)
                                            .background(Color.Gray.copy(alpha = 0.4f))
                                    )
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

                            IconButton(
                                onClick = { scope.launch { repository.toggleWatchLater(currentVideo.id) } },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = if (interaction?.savedToWatchLater == true) Icons.Filled.BookmarkAdded else Icons.Outlined.WatchLater,
                                    contentDescription = "Watch Later",
                                    tint = if (interaction?.savedToWatchLater == true) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    val shareText = NexoraLinkManager.buildVideoShareText(
                                        videoTitle = currentVideo.title,
                                        videoId = currentVideo.id
                                    )
                                    NexoraLinkManager.launchSystemShare(context, shareText)
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share Video",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Text(
                            text = "Next on Nexora Stream",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

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
                                Text(
                                    relVideo.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${relVideo.viewCount} views • ${relVideo.category}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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

    if (showCommentsSheet) {
        val commentsVm = remember(currentVideo.id) { CommentsViewModel(repository, currentVideo.id) }
        CommentsSheet(
            viewModel = commentsVm,
            onDismiss = { showCommentsSheet = false }
        )
    }
}
