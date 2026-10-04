/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.CommentEntity
import com.zentora.nexora.stream.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoraStreamTopAppBar(
    onSearchClick: () -> Unit,
    onP2PClick: () -> Unit,
    onProfileClick: () -> Unit,
    isKidsMode: Boolean,
    userAvatarUri: String
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NexoraRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Nexora Stream Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nexora Stream",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isKidsMode) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NexoraZentoraBlue
                            ) {
                                Text(
                                    text = "KIDS",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "by Zentora CLC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            // P2P Share Quick Icon (Feature 30)
            IconButton(onClick = onP2PClick, modifier = Modifier.testTag("p2p_share_button")) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = "Nexora P2P Share",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            // Search Icon
            IconButton(onClick = onSearchClick, modifier = Modifier.testTag("search_button")) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Profile Avatar with Verified Badge
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clickable { onProfileClick() }
            ) {
                AsyncImage(
                    model = userAvatarUri,
                    contentDescription = "Nexora ID Profile",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NexoraVerifiedTick)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
fun NexoraStreamBottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("stream_bottom_nav_bar")
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home", fontSize = 10.sp) },
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NexoraRed, selectedTextColor = NexoraRed)
        )

        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "shorts") Icons.Filled.FlashOn else Icons.Outlined.FlashOn,
                    contentDescription = "Shorts"
                )
            },
            label = { Text("Shorts", fontSize = 10.sp) },
            selected = currentRoute == "shorts",
            onClick = { onNavigate("shorts") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NexoraRed, selectedTextColor = NexoraRed)
        )

        // Studio Button (Center Action)
        NavigationBarItem(
            icon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexoraRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Studio", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            },
            label = { Text("Studio", fontSize = 10.sp) },
            selected = currentRoute == "studio",
            onClick = { onNavigate("studio") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = NexoraRed)
        )

        // Community Tab (Feature 21)
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "community") Icons.Filled.Forum else Icons.Outlined.Forum,
                    contentDescription = "Community"
                )
            },
            label = { Text("Community", fontSize = 10.sp) },
            selected = currentRoute == "community",
            onClick = { onNavigate("community") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NexoraRed, selectedTextColor = NexoraRed)
        )

        // Subscriptions Tab
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "subscriptions") Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                    contentDescription = "Subscriptions"
                )
            },
            label = { Text("Subscribed", fontSize = 10.sp) },
            selected = currentRoute == "subscriptions",
            onClick = { onNavigate("subscriptions") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NexoraRed, selectedTextColor = NexoraRed)
        )

        // Library Tab
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "library") Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                    contentDescription = "Library"
                )
            },
            label = { Text("Library", fontSize = 10.sp) },
            selected = currentRoute == "library",
            onClick = { onNavigate("library") },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = NexoraRed, selectedTextColor = NexoraRed)
        )
    }
}

/**
 * Feature 23 & 27: Dynamic Comments BottomSheet with Community Guideline Shield active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamCommentsBottomSheet(
    comments: List<CommentEntity>,
    onDismiss: () -> Unit,
    onPostComment: (String) -> Boolean,
    onLikeComment: (CommentEntity) -> Unit
) {
    val context = LocalContext.current
    var commentText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(16.dp)
        ) {
            // Header with Community Shield indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Live Comments (${comments.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = NexoraSuccess, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Community Shield Active", color = NexoraSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

            // Comments List
            if (comments.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Be the first to start the discussion!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(comments, key = { it.commentId }) { comment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NexoraRedDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = comment.authorName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(comment.commentText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onLikeComment(comment) }
                                ) {
                                    Icon(
                                        imageVector = if (comment.isLikedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Like",
                                        tint = if (comment.isLikedByMe) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    if (comment.likeCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${comment.likeCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Post bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Comment respectfully…", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            val isSafe = onPostComment(commentText.trim())
                            if (!isSafe) {
                                Toast.makeText(context, "Comment filtered by Community Guideline Shield", Toast.LENGTH_LONG).show()
                            }
                            commentText = ""
                        }
                    },
                    enabled = commentText.isNotBlank()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = NexoraRed)
                }
            }
        }
    }
}
