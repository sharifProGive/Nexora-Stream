/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Top App Bar matching YouTube screenshot (14-21-38):
 * Left: Brand logo (Red play badge + Nexora Stream)
 * Right: Cast icon, Notifications Bell icon, Search icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoraStreamTopAppBar(
    onSearchClick: () -> Unit,
    onCastClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    isKidsMode: Boolean = false,
    userAvatarUri: String = ""
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.zentora.nexora.stream.R.drawable.nexora_infinity_logo),
                        contentDescription = "Nexora Stream Infinity Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
            }
        },
        actions = {
            // 1. Cast Icon
            IconButton(onClick = onCastClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Cast,
                    contentDescription = "Cast",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            // 2. Notification Bell Icon
            IconButton(onClick = onNotificationClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            // 3. Search Icon
            IconButton(onClick = onSearchClick, modifier = Modifier.size(40.dp).testTag("search_button")) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/**
 * YouTube-style Permanent 5-Tab Layout matching screenshot (14-21-38):
 * [Home | Shorts | (+) | Subscriptions | You]
 * - Central (+) is a circle with a border and plus symbol inside.
 * - Subscriptions has red notification badge dot.
 * - You displays user avatar circle with border and "You" text.
 */
@Composable
fun NexoraStreamBottomNavBar(
    currentRoute: String,
    userAvatarUri: String = "",
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("stream_bottom_nav_bar")
    ) {
        // 1. Home Tab
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "home") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(23.dp)
                )
            },
            label = { Text("Home", fontSize = 10.sp, fontWeight = if (currentRoute == "home") FontWeight.Bold else FontWeight.Normal) },
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            )
        )

        // 2. Shorts Tab
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentRoute == "shorts") Icons.Filled.FlashOn else Icons.Outlined.FlashOn,
                    contentDescription = "Shorts",
                    modifier = Modifier.size(23.dp)
                )
            },
            label = { Text("Shorts", fontSize = 10.sp, fontWeight = if (currentRoute == "shorts") FontWeight.Bold else FontWeight.Normal) },
            selected = currentRoute == "shorts",
            onClick = { onNavigate("shorts") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            )
        )

        // 3. Central (+) Upload Action Button
        NavigationBarItem(
            icon = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Create / Upload",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            label = null,
            selected = currentRoute == "upload",
            onClick = { onNavigate("upload") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = Color.Transparent
            )
        )

        // 4. Subscriptions Tab (With red notification dot like in YouTube)
        NavigationBarItem(
            icon = {
                Box {
                    Icon(
                        imageVector = if (currentRoute == "subscriptions") Icons.Filled.Subscriptions else Icons.Outlined.Subscriptions,
                        contentDescription = "Subscriptions",
                        modifier = Modifier.size(22.dp)
                    )
                    // Red Notification Dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NexoraRed)
                    )
                }
            },
            label = { Text("Subscriptions", fontSize = 10.sp, fontWeight = if (currentRoute == "subscriptions") FontWeight.Bold else FontWeight.Normal) },
            selected = currentRoute == "subscriptions",
            onClick = { onNavigate("subscriptions") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            )
        )

        // 5. You Tab (Circular avatar with ring border like screenshot 14-21-38)
        val isYouSelected = currentRoute == "you" || currentRoute == "library"
        NavigationBarItem(
            icon = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (isYouSelected) 1.5.dp else 1.dp,
                            color = if (isYouSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                ) {
                    if (userAvatarUri.isNotBlank()) {
                        AsyncImage(
                            model = userAvatarUri,
                            contentDescription = "You Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NexoraZentoraBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Z", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            },
            label = { Text("You", fontSize = 10.sp, fontWeight = if (isYouSelected) FontWeight.Bold else FontWeight.Normal) },
            selected = isYouSelected,
            onClick = { onNavigate("you") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            )
        )
    }
}

/**
 * Dynamic Comments BottomSheet with Community Guideline Shield active.
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
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
