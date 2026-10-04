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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.engine.NexoraP2PShare
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import com.zentora.nexora.stream.ui.theme.NexoraSuccess
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    repository: NexoraStreamRepository,
    onVideoClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val history by repository.allHistory.collectAsState(initial = emptyList())
    val watchLaterList by repository.watchLaterInteractions.collectAsState(initial = emptyList())
    val likedList by repository.likedInteractions.collectAsState(initial = emptyList())
    val userSession = remember { NexoraIdManager.getSession() }

    var showP2PDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_screen")
    ) {
        // User Profile Summary Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = userSession.avatarUri,
                        contentDescription = "Profile",
                        modifier = Modifier.size(50.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(userSession.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${userSession.handle} • Nexora ID: ${userSession.nexoraId}", fontSize = 11.sp, color = NexoraRed)
                    }
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        // Feature 30: Peer-to-Peer Nexora Share Banner
        item {
            Card(
                onClick = { showP2PDialog = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = NexoraSuccess, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Peer-to-Peer Nexora Share", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Fast direct device-to-device video transfers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        // History Section with Retention Scores
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Watch History (${history.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { repository.clearHistory() } }) {
                            Text("Clear", color = NexoraRed, fontSize = 12.sp)
                        }
                    }
                }

                if (history.isEmpty()) {
                    Text("No playback history recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        history.forEach { h ->
                            val video = allVideos.find { it.id == h.videoId }
                            if (video != null) {
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable { onVideoClick(video.id) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        AsyncImage(
                                            model = video.thumbnailUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Retention score indicator
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .background(Color.Gray.copy(alpha = 0.5f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(h.retentionScore)
                                                    .background(NexoraRed)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(video.title, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Retention: ${(h.retentionScore * 100).toInt()}%", fontSize = 10.sp, color = NexoraSparkGold)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
        }

        // Saved to Watch Later
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Saved to Watch Later (${watchLaterList.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (watchLaterList.isEmpty()) {
                    Text("No videos saved to Watch Later.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    watchLaterList.forEach { item ->
                        val video = allVideos.find { it.id == item.videoId }
                        if (video != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(video.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
        }

        // Liked Videos
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Liked Videos (${likedList.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (likedList.isEmpty()) {
                    Text("Videos you like will appear here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    likedList.forEach { item ->
                        val video = allVideos.find { it.id == item.videoId }
                        if (video != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(video.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    // Feature 30: P2P Share Dialog
    if (showP2PDialog) {
        val peers = remember { NexoraP2PShare.getNearbyPeers() }
        AlertDialog(
            onDismissRequest = { showP2PDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = NexoraSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nexora P2P Share Beacon", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nearby devices ready for local zero-data high speed transfer:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    peers.forEach { peer ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(peer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Status: ${peer.status}", fontSize = 11.sp, color = NexoraSuccess)
                                }
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Direct peer transfer initiated with ${peer.name}", Toast.LENGTH_SHORT).show()
                                        showP2PDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showP2PDialog = false }) { Text("Close") }
            }
        )
    }
}
