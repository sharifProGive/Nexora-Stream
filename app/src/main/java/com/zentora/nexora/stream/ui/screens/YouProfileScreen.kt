/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.HistoryEntity
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.*

/**
 * YouTube-style 'You' Screen matching exact screenshots (14-21-44 & 14-21-57):
 * - Top bar: "Accounts v" pill + Cast, Notifications, Search, Settings icons.
 * - Header: Large circular avatar with gold/cyan border, Channel Name, @handle,
 *   white pill "View channel", dark pill "Switch account".
 * - "History >" horizontal rail with duration badges, red progress bar, title, channel name, and 3-dots.
 * - "Library" section with filter pills [Recent v, Playlists, Music].
 * - Stacked playlist cards: Liked videos, Downloads, Your videos, Watch later, Sounds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouProfileScreen(
    repository: NexoraStreamRepository,
    onVideoClick: (String) -> Unit,
    onViewChannel: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val userSession = remember { NexoraIdManager.getSession() }
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val allHistory by repository.allHistory.collectAsState(initial = emptyList())
    val watchLaterList by repository.watchLaterInteractions.collectAsState(initial = emptyList())
    val likedList by repository.likedInteractions.collectAsState(initial = emptyList())

    var libraryFilter by remember { mutableStateOf("Recent") }

    // Real history items from Room DB
    val historyVideos = remember(allHistory, allVideos) {
        allHistory.mapNotNull { hist ->
            allVideos.find { it.id == hist.videoId }?.let { video ->
                Pair(video, hist)
            }
        }
    }

    // User's own videos in Room DB
    val myVideos = remember(allVideos) {
        allVideos.filter { it.authorChannelId == "ch_zentora_core" }
    }

    val channelDisplayName = if (userSession.username.isNotBlank()) userSession.username else "Skyline Pro gamer"
    val channelHandle = if (userSession.handle.isNotBlank()) userSession.handle else "@SkylineProgamer"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // "Accounts v" pill button on left (screenshot 14-21-44)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF222222),
                        modifier = Modifier.clickable { /* Account switcher */ }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accounts", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                actions = {
                    // Cast Icon
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Cast, contentDescription = "Cast", tint = Color.White)
                    }
                    // Notifications Icon
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Color.White)
                    }
                    // Search Icon
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White)
                    }
                    // Settings Gear Icon
                    IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("settings_gear_button")) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.White)
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ==================== 1. USER PROFILE HEADER ====================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Large Circular Avatar with Gold/Cyan Gradient Border Ring (screenshot 14-21-44)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(NexoraZentoraBlue, NexoraSparkGold, Color(0xFF00E5FF), NexoraZentoraBlue)
                                ),
                                shape = CircleShape
                            )
                            .clickable { onViewChannel("ch_zentora_core") }
                    ) {
                        if (userSession.avatarUri.isNotBlank()) {
                            AsyncImage(
                                model = userSession.avatarUri,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF1A1A24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = channelDisplayName.take(2).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channelDisplayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = channelHandle,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Two Pill Buttons Row (matching screenshot 14-21-44):
                // [View channel] (White) & [Switch account / Get Premium] (Dark)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onViewChannel("ch_zentora_core") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("View channel", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {},
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626))
                    ) {
                        Text("Switch account", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ==================== 2. HISTORY SECTION ====================
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: "History >"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* View all history */ }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "View all",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (historyVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp)
                        ) {
                            Text("Videos you watch will appear here.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        // Horizontal Rail of History Video Cards
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(historyVideos, key = { it.first.id }) { (video, hist) ->
                                HistoryRailCard(
                                    video = video,
                                    history = hist,
                                    onClick = { onVideoClick(video.id) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ==================== 3. LIBRARY SECTION ====================
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // "Library" Header
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Filter Pills: [Recent v, Playlists, Music] (screenshot 14-21-44)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF262626),
                            modifier = Modifier.clickable { libraryFilter = "Recent" }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF262626),
                            modifier = Modifier.clickable { libraryFilter = "Playlists" }
                        ) {
                            Text("Playlists", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF262626),
                            modifier = Modifier.clickable { libraryFilter = "Music" }
                        ) {
                            Text("Music", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Playlist List Items matching screenshots 14-21-44 & 14-21-57:
                    // 1. Liked videos (thumbnail with thumbs up badge)
                    PlaylistStackedRowItem(
                        title = "Liked videos",
                        subtitle = "Private • ${likedList.size} videos",
                        iconBadge = Icons.Filled.ThumbUp,
                        cardColor = Color(0xFF2B1D12),
                        onClick = {}
                    )

                    // 2. Downloads (thumbnail with download badge)
                    PlaylistStackedRowItem(
                        title = "Downloads",
                        subtitle = "${allVideos.size} videos",
                        iconBadge = Icons.Filled.Download,
                        cardColor = Color(0xFF1E262B),
                        onClick = {}
                    )

                    // 3. Your videos (thumbnail with play badge)
                    PlaylistStackedRowItem(
                        title = "Your videos",
                        subtitle = "${myVideos.size} videos",
                        iconBadge = Icons.Filled.PlayArrow,
                        cardColor = Color(0xFF202028),
                        onClick = { onViewChannel("ch_zentora_core") }
                    )

                    // 4. Watch later (white card with clock icon, screenshot 14-21-57)
                    PlaylistStackedRowItem(
                        title = "Watch later",
                        subtitle = "Private • ${watchLaterList.size} videos",
                        iconBadge = Icons.Filled.Schedule,
                        cardColor = Color(0xFFFFFFFF),
                        badgeTint = Color.Black,
                        onClick = {}
                    )

                    // 5. Sounds (gradient card with Shorts S icon, screenshot 14-21-57)
                    PlaylistStackedRowItem(
                        title = "Sounds",
                        subtitle = "Private • Playlist",
                        iconBadge = Icons.Filled.GraphicEq,
                        cardColor = Color(0xFF7A2E5E),
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

/**
 * History card in horizontal rail matching screenshots 14-21-44 & 14-21-57:
 * Thumbnail with duration badge, red progress line, title, channel name, 3-dots.
 */
@Composable
fun HistoryRailCard(
    video: VideoEntity,
    history: HistoryEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
    ) {
        // Thumbnail with duration badge and red progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222))
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
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Duration badge at bottom right
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
            ) {
                Text(
                    text = formatMsToTime(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Red Progress Bar at bottom of thumbnail
            val progress = history.retentionScore.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.DarkGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceAtLeast(0.12f))
                        .fillMaxHeight()
                        .background(NexoraRed)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title and 3-dots row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = Color.White,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Channel Name
        Text(
            text = video.category,
            fontSize = 11.sp,
            color = Color.Gray,
            maxLines = 1
        )
    }
}

/**
 * Stacked playlist item matching exact screenshots 14-21-44 & 14-21-57:
 * Thumbnail container with layered stack look, badge icon in bottom right,
 * Title, subtitle, 3-dots menu button on right.
 */
@Composable
fun PlaylistStackedRowItem(
    title: String,
    subtitle: String,
    iconBadge: ImageVector,
    cardColor: Color,
    badgeTint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stacked Card Thumbnail
        Box(
            modifier = Modifier
                .width(135.dp)
                .height(76.dp)
        ) {
            // Top layered sliver to give stacked appearance (screenshot 14-21-44)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(6.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(cardColor.copy(alpha = 0.5f))
            )

            // Main Card Box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Centered or bottom-right icon badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconBadge, contentDescription = null, tint = badgeTint, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Playlist details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        // 3-dots menu icon
        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}
