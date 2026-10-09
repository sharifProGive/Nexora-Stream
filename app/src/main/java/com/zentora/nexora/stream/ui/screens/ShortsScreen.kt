/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraLinkManager
import com.zentora.nexora.stream.ui.components.FloatingReactionContainer
import com.zentora.nexora.stream.ui.components.StreamCommentsBottomSheet
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    repository: NexoraStreamRepository,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())

    // Vertical Pager with all videos
    val pagerState = rememberPagerState(pageCount = { allVideos.size })

    // Vinyl rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "VinylDisc")
    val vinylAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "Angle"
    )

    var showCommentsForVideoId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("shorts_screen")
    ) {
        if (allVideos.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val video = allVideos[page]
                val isActive = pagerState.currentPage == page
                val authorChannel = allChannels.find { it.channelId == video.authorChannelId }
                val interaction by repository.getInteraction(video.id).collectAsState(initial = null)

                // Dedicated player per page
                val exoPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        playWhenReady = true
                    }
                }

                DisposableEffect(video.localUri) {
                    val mediaItem = MediaItem.fromUri(video.localUri)
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    onDispose { exoPlayer.release() }
                }

                LaunchedEffect(isActive) {
                    if (isActive) {
                        exoPlayer.play()
                        repository.recordView(video.id, video.duration, 0L)
                    } else {
                        exoPlayer.pause()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
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

                    // Bottom subtle gradient shade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.45f)
                            .align(Alignment.BottomCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    // Feature 25: Floating reaction emojis overlay
                    FloatingReactionContainer(modifier = Modifier.fillMaxSize())

                    // Right action column
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Like Button (Feature 22)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { scope.launch { repository.toggleLike(video.id) } },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (interaction?.isLikedByMe == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = "Like",
                                    tint = if (interaction?.isLikedByMe == true) NexoraRed else Color.White
                                )
                            }
                            Text(text = "${interaction?.likesCount ?: 0L}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Dislike Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { scope.launch { repository.toggleDislike(video.id) } },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (interaction?.isDislikedByMe == true) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                    contentDescription = "Dislike",
                                    tint = if (interaction?.isDislikedByMe == true) NexoraRed else Color.White
                                )
                            }
                            Text("Dislike", color = Color.White, fontSize = 11.sp)
                        }

                        // Nexora Sparks Button (Feature 20)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repository.sendSparks(video.id, 25L)
                                        Toast.makeText(context, "+25 Nexora Sparks! ⚡", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Filled.Bolt, contentDescription = "Sparks", tint = NexoraSparkGold)
                            }
                            Text(text = "${interaction?.sparksReceived ?: 0L}", color = NexoraSparkGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Comments Button (Feature 23)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { showCommentsForVideoId = video.id },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Outlined.Comment, contentDescription = "Comments", tint = Color.White)
                            }
                            Text("Chat", color = Color.White, fontSize = 11.sp)
                        }

                        // Share Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val shareText = NexoraLinkManager.buildVideoShareText(
                                        videoTitle = video.title,
                                        videoId = video.id
                                    )
                                    NexoraLinkManager.launchSystemShare(context, shareText)
                                },
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
                            }
                            Text("Share", color = Color.White, fontSize = 11.sp)
                        }

                        // Spinning Vinyl Disc
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .rotate(vinylAngle)
                                .background(Color(0xFF1E1E1E))
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                contentDescription = "Sound Disc",
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Bottom info: Channel, Title, Subscribe
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.82f)
                            .padding(start = 16.dp, bottom = 48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onChannelClick(video.authorChannelId) },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = authorChannel?.channelName ?: "Zentora CLC",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (authorChannel?.hasZentoraBadge == true) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = NexoraVerifiedTick, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { scope.launch { repository.toggleSubscribe(video.authorChannelId) } },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authorChannel?.isSubscribed == true) Color.White.copy(alpha = 0.25f) else NexoraRed
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (authorChannel?.isSubscribed == true) "Subscribed" else "Subscribe",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = video.title, color = Color.White, fontSize = 13.sp, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Original Audio • Zentora Audio Engine", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Comments Sheet
    showCommentsForVideoId?.let { vId ->
        val commentsList by repository.getComments(vId).collectAsState(initial = emptyList())
        StreamCommentsBottomSheet(
            comments = commentsList,
            onDismiss = { showCommentsForVideoId = null },
            onPostComment = { text ->
                var isSafe = true
                scope.launch {
                    isSafe = repository.addComment(vId, "Zentora Member", text)
                }
                isSafe
            },
            onLikeComment = { comment ->
                scope.launch { repository.toggleCommentLike(comment) }
            }
        )
    }
}
