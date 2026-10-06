/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.zentora.nexora.stream.engine.NexoraSmartRank
import com.zentora.nexora.stream.engine.TrendingRadar
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * YouTube-style Home Screen matching screenshot (14-21-38):
 * - Compass Explore icon + category chips rail [All, Tech, Animation, Gaming, Natok, News, ...]
 * - High-fidelity video feed cards with duration badge, channel avatar, title, channel info, and 3-dots menu.
 * - In-feed Shorts section with red Shorts logo and side-by-side vertical cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: NexoraStreamRepository,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val allHistory by repository.allHistory.collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())
    val isKidsMode by repository.isKidsMode.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Tech", "Animation", "Gaming", "Natok", "Music", "Science", "Education", "Sports")
    }

    // Filter videos by category and kids mode
    val displayedVideos = remember(allVideos, allHistory, selectedCategory, isKidsMode) {
        val baseList = if (isKidsMode) {
            allVideos.filter { it.category == "Animation" || it.category == "Education" }
        } else {
            allVideos
        }

        if (selectedCategory == "All") {
            NexoraSmartRank.rankVideos(baseList, emptyMap(), allHistory)
        } else {
            baseList.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    // Shorts list for the in-feed Shorts carousel
    val shortsList = remember(allVideos) {
        allVideos.filter { it.duration <= 65000L }.take(6)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                delay(800)
                isRefreshing = false
                Toast.makeText(context, "Feed refreshed", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxSize().testTag("home_screen_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ==================== 1. CATEGORY CHIP RAIL WITH EXPLORE ICON ====================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Explore Compass icon button (like YouTube screenshot 14-21-38)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable {
                                Toast.makeText(context, "Explore & Trending topics", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = "Explore",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Category Chips
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { selectedCategory = cat }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 2. MAIN VIDEO FEED ====================
            if (displayedVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isKidsMode) "Kids Mode is active. No matching safe content." else "No videos in this category. Upload one using (+)!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                displayedVideos.forEachIndexed { index, video ->
                    item(key = video.id) {
                        val authorChannel = allChannels.find { it.channelId == video.authorChannelId }

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

                                // Duration Badge (Bottom-Right, black background with rounded corners)
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

                                // Premiere Badge if applicable
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

                            // Video Info Row below thumbnail
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Channel Avatar
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

                                // Title and Subtitle Info
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

                                    // Subtitle: Channel name • views • time
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${authorChannel?.channelName ?: "Zentora CLC"} • ${video.viewCount} views",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (authorChannel?.hasZentoraBadge == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Verified",
                                                tint = NexoraVerifiedTick,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                // 3-Dots Menu Button
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // In-Feed Shorts Section (matching screenshot 14-21-38 after the first video!)
                    if (index == 0 && shortsList.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                // Shorts Header (Red Shorts Icon + bold title "Shorts" + 3 dots)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.FlashOn,
                                            contentDescription = "Shorts",
                                            tint = NexoraRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Shorts",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Menu",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // 2-Column / Side-by-side vertical Shorts rail
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    shortsList.take(2).forEach { shortItem ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(260.dp)
                                                .clickable { onVideoClick(shortItem.id) }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                if (shortItem.thumbnailUri.isNotBlank()) {
                                                    AsyncImage(
                                                        model = shortItem.thumbnailUri,
                                                        contentDescription = shortItem.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                }

                                                // 3-dots on top right
                                                IconButton(
                                                    onClick = {},
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                // Title and view count overlay at bottom
                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = shortItem.title,
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "${shortItem.viewCount} views",
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    thickness = 4.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
