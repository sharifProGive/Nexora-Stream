/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
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
    var selectedCategory by remember { mutableStateOf("Smart Rank") }

    val categories = remember {
        listOf("Smart Rank", "Trending Radar", "Tech", "Animation", "Sports", "Music", "Education")
    }

    // On-Device Smart Rank & Trending Radar processing
    val displayedVideos = remember(allVideos, allHistory, selectedCategory, isKidsMode) {
        var baseList = if (isKidsMode) {
            allVideos.filter { it.category == "Animation" || it.category == "Education" }
        } else {
            allVideos
        }

        when (selectedCategory) {
            "Smart Rank" -> {
                NexoraSmartRank.rankVideos(baseList, emptyMap(), allHistory)
            }
            "Trending Radar" -> {
                TrendingRadar.getTrendingVideos(baseList, emptyMap())
            }
            else -> {
                baseList.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                delay(800)
                isRefreshing = false
                Toast.makeText(context, "Nexora Smart Rank recalculated", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxSize().testTag("home_screen_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            leadingIcon = {
                                if (cat == "Smart Rank") {
                                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = if (isSelected) Color.White else NexoraRed, modifier = Modifier.size(16.dp))
                                } else if (cat == "Trending Radar") {
                                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = if (isSelected) Color.White else NexoraSparkGold, modifier = Modifier.size(16.dp))
                                }
                            },
                            label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NexoraRed,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Algorithm Banner Indicator
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "Smart Rank")
                            "ENGINE: Nexora Neural Smart Rank (On-Device Retention Optimized)"
                        else if (selectedCategory == "Trending Radar")
                            "RADAR: Real-Time Trending Local Velocity (Live Window)"
                        else
                            "CATEGORY: $selectedCategory",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Video Cards List
            if (displayedVideos.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isKidsMode) "Kids Mode is active. No matching safe content." else "No videos in this category. Upload one in Studio!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(displayedVideos, key = { it.id }) { video ->
                    val authorChannel = allChannels.find { it.channelId == video.authorChannelId }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(video.id) }
                            .padding(bottom = 16.dp)
                    ) {
                        // 16:9 Thumbnail
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

                            // Duration badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formatMsToTime(video.duration),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Premiere badge if active
                            if (video.isPremiere) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NexoraRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("PREMIERE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        // Video Metadata Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Channel Avatar with Verified Tick
                            Box(modifier = Modifier.clickable { onChannelClick(video.authorChannelId) }) {
                                AsyncImage(
                                    model = authorChannel?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                    contentDescription = "Channel Avatar",
                                    modifier = Modifier
                                        .size(38.dp)
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

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = authorChannel?.channelName ?: "Zentora CLC",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (authorChannel?.hasZentoraBadge == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Verified Zentora Creator",
                                            tint = NexoraVerifiedTick,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    if (video.collabChannelId != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = NexoraSparkGold.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "COLLAB",
                                                color = NexoraSparkGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${video.viewCount} views • ${video.category}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
