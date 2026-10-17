/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.VisibilityOff
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

enum class NotificationFilter(val label: String) {
    ALL("All"),
    MENTIONS("Mentions"),
    COMMENTS("Comments")
}

data class NotificationItemUi(
    val id: String,
    val type: NotificationFilter,
    val channelName: String,
    val channelAvatar: String,
    val message: String,
    val timestampText: String,
    val isToday: Boolean,
    val videoId: String? = null,
    val videoThumbnail: String? = null,
    val isRead: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    repository: NexoraStreamRepository,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    val allVideos: List<VideoEntity> by repository.allVideos.collectAsState(initial = emptyList())

    // Generate notification list combining video uploads and user interactions
    var hiddenNotificationIds by remember { mutableStateOf(setOf<String>()) }
    var readNotificationIds by remember { mutableStateOf(setOf<String>()) }

    val notifications = remember(allVideos, hiddenNotificationIds, readNotificationIds) {
        val list = mutableListOf<NotificationItemUi>()

        // Video alerts from repository
        allVideos.take(6).forEachIndexed { index, video ->
            val isToday = index < 3
            val timeText = when (index) {
                0 -> "45m ago"
                1 -> "2h ago"
                2 -> "5h ago"
                3 -> "Yesterday"
                4 -> "2 days ago"
                else -> "4 days ago"
            }
            list.add(
                NotificationItemUi(
                    id = "notif_vid_${video.id}",
                    type = NotificationFilter.ALL,
                    channelName = if (video.authorChannelId == "ch_zentora_core") "Zentora" else "Nexora Creator",
                    channelAvatar = "https://picsum.photos/seed/${video.id}_avatar/100/100",
                    message = "uploaded: ${video.title}",
                    timestampText = timeText,
                    isToday = isToday,
                    videoId = video.id,
                    videoThumbnail = video.thumbnailUri.ifBlank { video.localUri },
                    isRead = readNotificationIds.contains("notif_vid_${video.id}")
                )
            )
        }

        // Mentions
        list.add(
            NotificationItemUi(
                id = "notif_mention_1",
                type = NotificationFilter.MENTIONS,
                channelName = "Tech Insights",
                channelAvatar = "https://picsum.photos/seed/tech_avatar/100/100",
                message = "mentioned you in a comment: \"Check this performance breakdown!\"",
                timestampText = "3h ago",
                isToday = true,
                videoId = allVideos.firstOrNull()?.id,
                videoThumbnail = allVideos.firstOrNull()?.thumbnailUri,
                isRead = readNotificationIds.contains("notif_mention_1")
            )
        )
        list.add(
            NotificationItemUi(
                id = "notif_mention_2",
                type = NotificationFilter.MENTIONS,
                channelName = "Kotlin Systems Lab",
                channelAvatar = "https://picsum.photos/seed/lab_avatar/100/100",
                message = "tagged your channel in a community post",
                timestampText = "Yesterday",
                isToday = false,
                videoId = null,
                videoThumbnail = null,
                isRead = readNotificationIds.contains("notif_mention_2")
            )
        )

        // Comments
        list.add(
            NotificationItemUi(
                id = "notif_comment_1",
                type = NotificationFilter.COMMENTS,
                channelName = "Alex Rivera",
                channelAvatar = "https://picsum.photos/seed/alex_avatar/100/100",
                message = "commented: \"Super smooth 4K playback and clean UI!\"",
                timestampText = "1h ago",
                isToday = true,
                videoId = allVideos.firstOrNull()?.id,
                videoThumbnail = allVideos.firstOrNull()?.thumbnailUri,
                isRead = readNotificationIds.contains("notif_comment_1")
            )
        )
        list.add(
            NotificationItemUi(
                id = "notif_comment_2",
                type = NotificationFilter.COMMENTS,
                channelName = "Sarah Chen",
                channelAvatar = "https://picsum.photos/seed/sarah_avatar/100/100",
                message = "replied to your comment: \"Agreed, the new stream engine is blazing fast.\"",
                timestampText = "3 days ago",
                isToday = false,
                videoId = allVideos.getOrNull(1)?.id,
                videoThumbnail = allVideos.getOrNull(1)?.thumbnailUri,
                isRead = readNotificationIds.contains("notif_comment_2")
            )
        )

        list.filterNot { hiddenNotificationIds.contains(it.id) }
    }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            NotificationFilter.ALL -> notifications
            NotificationFilter.MENTIONS -> notifications.filter { it.type == NotificationFilter.MENTIONS }
            NotificationFilter.COMMENTS -> notifications.filter { it.type == NotificationFilter.COMMENTS }
        }
    }

    val todayNotifications = remember(filteredNotifications) {
        filteredNotifications.filter { it.isToday }
    }
    val earlierNotifications = remember(filteredNotifications) {
        filteredNotifications.filter { !it.isToday }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("notifications_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val allIds = notifications.map { it.id }.toSet()
                        readNotificationIds = readNotificationIds + allIds
                        Toast.makeText(context, "All marked as read", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Mark all as read",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F0F)
                )
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Pills Row: [All | Mentions | Comments]
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotificationFilter.values()) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color.White else Color(0xFF272727),
                        contentColor = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier
                            .clickable { selectedFilter = filter }
                            .testTag("filter_pill_${filter.name.lowercase()}")
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredNotifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No notifications right now",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Updates, mentions, and comments will show up here",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Today Section
                    if (todayNotifications.isNotEmpty()) {
                        item {
                            NotificationSectionHeader(title = "Today")
                        }
                        items(todayNotifications, key = { it.id }) { item ->
                            NotificationRowItem(
                                item = item,
                                onClick = {
                                    readNotificationIds = readNotificationIds + item.id
                                    if (item.videoId != null) {
                                        onVideoClick(item.videoId)
                                    }
                                },
                                onHide = {
                                    hiddenNotificationIds = hiddenNotificationIds + item.id
                                    Toast.makeText(context, "Notification hidden", Toast.LENGTH_SHORT).show()
                                },
                                onTurnOffChannel = {
                                    hiddenNotificationIds = hiddenNotificationIds + item.id
                                    Toast.makeText(context, "Turned off notifications from ${item.channelName}", Toast.LENGTH_SHORT).show()
                                },
                                onToggleRead = {
                                    readNotificationIds = if (readNotificationIds.contains(item.id)) {
                                        readNotificationIds - item.id
                                    } else {
                                        readNotificationIds + item.id
                                    }
                                }
                            )
                        }
                    }

                    // Earlier Section
                    if (earlierNotifications.isNotEmpty()) {
                        item {
                            NotificationSectionHeader(title = "Earlier")
                        }
                        items(earlierNotifications, key = { it.id }) { item ->
                            NotificationRowItem(
                                item = item,
                                onClick = {
                                    readNotificationIds = readNotificationIds + item.id
                                    if (item.videoId != null) {
                                        onVideoClick(item.videoId)
                                    }
                                },
                                onHide = {
                                    hiddenNotificationIds = hiddenNotificationIds + item.id
                                    Toast.makeText(context, "Notification hidden", Toast.LENGTH_SHORT).show()
                                },
                                onTurnOffChannel = {
                                    hiddenNotificationIds = hiddenNotificationIds + item.id
                                    Toast.makeText(context, "Turned off notifications from ${item.channelName}", Toast.LENGTH_SHORT).show()
                                },
                                onToggleRead = {
                                    readNotificationIds = if (readNotificationIds.contains(item.id)) {
                                        readNotificationIds - item.id
                                    } else {
                                        readNotificationIds + item.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun NotificationRowItem(
    item: NotificationItemUi,
    onClick: () -> Unit,
    onHide: () -> Unit,
    onTurnOffChannel: () -> Unit,
    onToggleRead: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (item.isRead) Color.Transparent else Color(0xFF161616))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Channel / User Avatar with unread indicator dot
        Box(
            modifier = Modifier.size(40.dp)
        ) {
            AsyncImage(
                model = item.channelAvatar,
                contentDescription = item.channelName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF272727)),
                contentScale = ContentScale.Crop
            )

            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3EA6FF))
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Notification body text & timestamp
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${item.channelName} ${item.message}",
                color = if (item.isRead) Color(0xFFAAAAAA) else Color.White,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.timestampText,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Video Thumbnail (if linked)
        if (item.videoThumbnail != null) {
            AsyncImage(
                model = item.videoThumbnail,
                contentDescription = null,
                modifier = Modifier
                    .width(64.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF222222)),
                contentScale = ContentScale.Crop
            )
        }

        // 3-dot overflow menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF222222))
            ) {
                DropdownMenuItem(
                    text = { Text("Hide this notification", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onHide()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Turn off all from ${item.channelName}", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onTurnOffChannel()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (item.isRead) "Mark as unread" else "Mark as read",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onToggleRead()
                    }
                )
            }
        }
    }
}
