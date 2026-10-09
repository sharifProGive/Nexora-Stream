/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraLinkManager
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.launch

/**
 * SubscriptionsScreen (Module 2):
 * - Circular avatar row at top displaying all subscribed/available channels with real-time badges.
 * - Filter chips rail: [All | Today | Videos | Shorts | Live | Posts] directly above the main video feed.
 * - Rich feed of videos matching native YouTube Subscriptions experience.
 */
@Composable
fun SubscriptionsScreen(
    repository: NexoraStreamRepository,
    onChannelClick: (String) -> Unit,
    onVideoClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channels by repository.allChannels.collectAsState(initial = emptyList())
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val communityPosts by repository.allCommunityPosts.collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = remember { listOf("All", "Today", "Videos", "Shorts", "Live", "Posts") }

    // Filter videos and content based on chip selection
    val displayedVideos = remember(allVideos, selectedFilter) {
        when (selectedFilter) {
            "All" -> allVideos
            "Today" -> allVideos.take(4)
            "Videos" -> allVideos.filter { it.duration > 65000L }
            "Shorts" -> allVideos.filter { it.duration <= 65000L }
            "Live" -> allVideos.filter { it.isPremiere }
            "Posts" -> emptyList()
            else -> allVideos
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("subscriptions_screen")
    ) {
        // ==================== 1. CIRCULAR AVATARS ROW AT TOP ====================
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(channels, key = { it.channelId }) { channel ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(62.dp)
                            .clickable { onChannelClick(channel.channelId) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (channel.isSubscribed) 2.dp else 1.dp,
                                    color = if (channel.isSubscribed) NexoraCyanGlow else Color(0xFF333333),
                                    shape = CircleShape
                                )
                        ) {
                            AsyncImage(
                                model = channel.avatarUri,
                                contentDescription = channel.channelName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (channel.hasZentoraBadge) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(NexoraVerifiedTick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = Color.Black,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = channel.channelName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        // ==================== 2. FILTER CHIPS RAIL ====================
        // [All | Today | Videos | Shorts | Live | Posts]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { chip ->
                val isSelected = selectedFilter == chip
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = chip },
                    label = {
                        Text(
                            text = chip,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.background,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        // ==================== 3. FEED CONTENT ====================
        if (selectedFilter == "Posts") {
            // Community Posts Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (communityPosts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No community posts from your subscriptions yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(communityPosts, key = { it.postId }) { post ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    post.contentText,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (post.imageUri != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AsyncImage(
                                        model = post.imageUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Video Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (displayedVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No $selectedFilter content available right now.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(displayedVideos, key = { it.id }) { video ->
                        val authorChannel = channels.find { it.channelId == video.authorChannelId }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVideoClick(video.id) }
                                .padding(bottom = 12.dp)
                        ) {
                            // 16:9 Thumbnail Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (video.thumbnailUri.isNotBlank()) {
                                    AsyncImage(
                                        model = video.thumbnailUri,
                                        contentDescription = video.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                    ) {
                                    Text(
                                        text = formatMsToTime(video.duration),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                if (video.isPremiere) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = NexoraRed,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "PREMIERE",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Info Row below thumbnail
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable { onChannelClick(video.authorChannelId) }
                                ) {
                                    AsyncImage(
                                        model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                        contentDescription = "Channel Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 20.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = "${authorChannel?.channelName ?: "Zentora CLC"} • ${video.viewCount} views",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                var showVideoMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showVideoMenu = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = "More",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showVideoMenu,
                                        onDismissRequest = { showVideoMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            onClick = {
                                                showVideoMenu = false
                                                val shareText = NexoraLinkManager.buildVideoShareText(
                                                    videoTitle = video.title,
                                                    videoId = video.id
                                                )
                                                NexoraLinkManager.launchSystemShare(context, shareText)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Share, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Save to Watch later") },
                                            onClick = {
                                                showVideoMenu = false
                                                scope.launch {
                                                    repository.toggleWatchLater(video.id)
                                                    Toast.makeText(context, "Saved to Watch later", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.WatchLater, contentDescription = null)
                                            }
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
