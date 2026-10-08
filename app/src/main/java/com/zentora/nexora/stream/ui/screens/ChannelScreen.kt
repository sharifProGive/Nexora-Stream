/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.ChannelEntity
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.launch

enum class ChannelSubTab(val label: String) {
    HOME("Home"),
    VIDEOS("Videos"),
    SHORTS("Shorts"),
    LIVE("Live"),
    PLAYLISTS("Playlists")
}

/**
 * Full Channel Screen (Feature 3):
 * Header banner, channel avatar with cyan border, title, verified handle,
 * live subscriber counter, bio string, action button row (Analytics, Edit channel, Community),
 * and scrollable sub-tabs [Home, Videos, Shorts, Live, Playlists] querying real database channels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    repository: NexoraStreamRepository,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val channelFlow = remember(channelId) { repository.getChannel(channelId) }
    val channel by channelFlow.collectAsState(initial = null)

    val channelVideosFlow = remember(channelId) { repository.getVideosByAuthor(channelId) }
    val channelVideos by channelVideosFlow.collectAsState(initial = emptyList())

    val userSession = remember { NexoraIdManager.getSession() }
    val isMyChannel = channelId == "ch_zentora_core"

    var selectedTab by remember { mutableStateOf(ChannelSubTab.HOME) }
    var showAnalyticsModal by remember { mutableStateOf(false) }

    val fallbackChannel = remember(channelId) {
        ChannelEntity(
            channelId = channelId,
            channelName = "Zentora CLC",
            avatarUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80",
            bannerUri = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
            subscriberCount = 0L,
            isSubscribed = false,
            creatorLevel = "Rising Creator",
            hasZentoraBadge = true,
            description = "Official channel of Zentora CLC. Next-generation media systems, distributed software platforms, and audio-visual engineering."
        )
    }

    val currentChannel = channel ?: fallbackChannel

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentChannel.channelName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search channel")
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ==================== 1. HEADER BANNER ====================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1B0A12), Color(0xFF2E0916), Color(0xFF0F1E28))
                            )
                        )
                ) {
                    if (currentChannel.bannerUri.isNotBlank()) {
                        AsyncImage(
                            model = currentChannel.bannerUri,
                            contentDescription = "Channel Banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // ==================== 2. CHANNEL METADATA & CYAN BORDER AVATAR ====================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Channel Avatar with Cyan Border
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, NexoraZentoraBlue, CircleShape)
                        ) {
                            AsyncImage(
                                model = currentChannel.avatarUri,
                                contentDescription = currentChannel.channelName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentChannel.channelName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentChannel.hasZentoraBadge) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = "Zentora Verified",
                                        tint = NexoraVerifiedTick,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "@${currentChannel.channelName.lowercase().replace(" ", "_")} • ${currentChannel.subscriberCount} subscribers • ${channelVideos.size} videos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentChannel.description,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ==================== 3. OFFICIAL YOUTUBE ACTION PILLS ====================
                    if (isMyChannel) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(19.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF272727),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("Manage videos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            FilledTonalButton(
                                onClick = { showAnalyticsModal = true },
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF272727),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Analytics, contentDescription = "Analytics", modifier = Modifier.size(18.dp))
                            }

                            FilledTonalButton(
                                onClick = {},
                                modifier = Modifier.size(38.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF272727),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit channel", modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        // Subscribe Button if viewing an external channel
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.toggleSubscribe(currentChannel.channelId)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentChannel.isSubscribed) MaterialTheme.colorScheme.surfaceVariant else NexoraRed
                            )
                        ) {
                            Text(
                                text = if (currentChannel.isSubscribed) "Subscribed" else "Subscribe",
                                color = if (currentChannel.isSubscribed) MaterialTheme.colorScheme.onSurface else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==================== 4. SCROLLABLE SUB-TAB ROW ====================
            // [Home, Videos, Shorts, Live, Playlists]
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = NexoraRed,
                            height = 2.5.dp
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                ) {
                    ChannelSubTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == tab) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            // ==================== 5. SUB-TAB CONTENT ====================
            when (selectedTab) {
                ChannelSubTab.HOME, ChannelSubTab.VIDEOS -> {
                    if (channelVideos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No videos uploaded yet", fontWeight = FontWeight.Bold)
                                    Text("Upload videos in Studio to see them here", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(channelVideos, key = { it.id }) { video ->
                            ChannelVideoItem(video = video, onClick = { onVideoClick(video.id) })
                        }
                    }
                }

                ChannelSubTab.SHORTS -> {
                    val shortsList = channelVideos.filter { it.duration <= 65000L }
                    if (shortsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No Shorts published yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(shortsList, key = { it.id }) { video ->
                            ChannelVideoItem(video = video, onClick = { onVideoClick(video.id) })
                        }
                    }
                }

                ChannelSubTab.LIVE -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No scheduled live streams", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                ChannelSubTab.PLAYLISTS -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NexoraRedDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Uploaded Streams", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${channelVideos.size} items • Created by channel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Channel Analytics BottomSheet Modal
    if (showAnalyticsModal) {
        ModalBottomSheet(
            onDismissRequest = { showAnalyticsModal = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Channel Analytics",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsStatCard(
                        title = "Views",
                        value = "${channelVideos.sumOf { it.viewCount }}",
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = "Subscribers",
                        value = "${currentChannel.subscriberCount}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsStatCard(
                        title = "Creator Status",
                        value = currentChannel.creatorLevel,
                        modifier = Modifier.weight(1f)
                    )
                    AnalyticsStatCard(
                        title = "Storage Status",
                        value = "Cloud Synced",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showAnalyticsModal = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
                ) {
                    Text("Close Analytics", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChannelVideoItem(
    video: VideoEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222226))
        ) {
            if (video.thumbnailUri.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUri,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = formatMsToTime(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${video.viewCount} views • ${video.category}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AnalyticsStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
