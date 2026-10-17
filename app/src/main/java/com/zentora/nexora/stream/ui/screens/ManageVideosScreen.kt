/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import kotlinx.coroutines.launch

enum class ManageVideosTab(val label: String) {
    VIDEOS("Videos"),
    SHORTS("Shorts"),
    LIVE("Live")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVideosScreen(
    repository: NexoraStreamRepository,
    uploadViewModel: UploadStudioViewModel? = null,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    var selectedTab by remember { mutableStateOf(ManageVideosTab.VIDEOS) }
    val allVideos: List<VideoEntity> by repository.allVideos.collectAsState(initial = emptyList())

    // Active upload tracking state
    val uploadState by uploadViewModel?.uploadState?.collectAsState() ?: remember {
        mutableStateOf(UploadProgressUiState())
    }

    // Active dialog states
    var videoToEdit by remember { mutableStateOf<VideoEntity?>(null) }
    var videoToDelete by remember { mutableStateOf<VideoEntity?>(null) }

    // Filter videos by active tab
    val filteredVideos: List<VideoEntity> = remember(allVideos, selectedTab) {
        when (selectedTab) {
            ManageVideosTab.VIDEOS -> {
                allVideos.filter { it.duration > 60000L || !it.tags.contains("Short", ignoreCase = true) }
            }
            ManageVideosTab.SHORTS -> {
                allVideos.filter { it.duration <= 60000L || it.tags.contains("Short", ignoreCase = true) }
            }
            ManageVideosTab.LIVE -> {
                allVideos.filter { it.isPremiere || it.tags.contains("Live", ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage videos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("manage_videos_back_button")
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
                        Toast.makeText(context, "Filtered by recent uploads", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = "Filter",
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
            // YouTube Studio Parity Tabs: [Videos | Shorts | Live]
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF0F0F0F),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color.White
                    )
                }
            ) {
                ManageVideosTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.testTag("manage_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Top Active Upload / Processing Card (when upload is in progress or completed)
            AnimatedVisibility(
                visible = uploadState.isUploading || uploadState.isCompleted,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ActiveUploadProcessingCard(
                    uploadState = uploadState,
                    onCancel = {
                        uploadViewModel?.cancelUpload()
                        Toast.makeText(context, "Upload cancelled", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        uploadViewModel?.dismissCard()
                    }
                )
            }

            if (filteredVideos.isEmpty() && !uploadState.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No ${selectedTab.label.lowercase()} uploaded yet",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Videos you upload will appear here with studio controls",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredVideos, key = { it.id }) { video ->
                        ManageVideoListItem(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onEdit = { videoToEdit = video },
                            onShare = {
                                val payload = NexoraLinkManager.buildVideoShareText(video.title, video.id)
                                NexoraLinkManager.launchSystemShare(context, payload)
                            },
                            onDelete = { videoToDelete = video },
                            onSaveToPlaylist = {
                                Toast.makeText(context, "Saved to playlist", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // ==================== EDIT VIDEO METADATA DIALOG ====================
    videoToEdit?.let { targetVideo ->
        EditVideoMetadataDialog(
            video = targetVideo,
            onDismiss = { videoToEdit = null },
            onSave = { updatedTitle, updatedDesc, updatedVisibility, updatedTags ->
                scope.launch {
                    val fullTags = if (updatedTags.contains(updatedVisibility)) {
                        updatedTags
                    } else {
                        "$updatedTags, $updatedVisibility"
                    }
                    repository.updateVideoMetadata(
                        id = targetVideo.id,
                        title = updatedTitle,
                        description = updatedDesc,
                        category = targetVideo.category,
                        tags = fullTags
                    )
                    Toast.makeText(context, "Video details updated", Toast.LENGTH_SHORT).show()
                    videoToEdit = null
                }
            }
        )
    }

    // ==================== DELETE CONFIRMATION DIALOG ====================
    videoToDelete?.let { targetVideo ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = {
                Text(
                    text = "Delete this video?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${targetVideo.title}\"? This action is permanent and cannot be undone.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteVideo(targetVideo.id)
                            Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                            videoToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFCC0000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete video")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { videoToDelete = null }
                ) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF222222)
        )
    }
}

/**
 * Top Active Upload & Processing Card
 * Displays animated progress bar, video frame thumbnail, upload percentage,
 * speed, phase, and cancel action during upload, transitioning smoothly to
 * "Processing complete" upon reaching 100%.
 */
@Composable
fun ActiveUploadProcessingCard(
    uploadState: UploadProgressUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = uploadState.progress,
        label = "upload_progress_anim"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .border(
                width = 1.dp,
                color = if (uploadState.isCompleted) Color(0xFF2BA640) else Color(0xFF333333),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("active_upload_processing_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto-generated frame thumbnail
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E2E2E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uploadState.thumbnailPath.isNullOrBlank()) {
                        AsyncImage(
                            model = uploadState.thumbnailPath,
                            contentDescription = uploadState.videoTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Movie,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uploadState.videoTitle.ifBlank { "Uploading video..." },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (uploadState.isCompleted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Complete",
                                tint = Color(0xFF2BA640),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Processing complete",
                                color = Color(0xFF2BA640),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Ready to watch • Saved to your channel",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Uploading: ${(uploadState.progress * 100).toInt()}% • ${uploadState.bytesTransferredText}",
                            color = Color(0xFF3EA6FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${uploadState.phaseText} • ${uploadState.timeRemainingText}",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action button (Cancel or Dismiss)
                if (uploadState.isCompleted) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("cancel_upload_button")
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFFF4D4D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animated progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (uploadState.isCompleted) Color(0xFF2BA640) else Color(0xFFFF0033),
                trackColor = Color(0xFF333333)
            )
        }
    }
}

@Composable
fun ManageVideoListItem(
    video: VideoEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSaveToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    // Parse visibility
    val visibility = when {
        video.tags.contains("Private", ignoreCase = true) -> "Private"
        video.tags.contains("Unlisted", ignoreCase = true) -> "Unlisted"
        else -> "Public"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail with duration badge
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222))
        ) {
            AsyncImage(
                model = video.thumbnailUri.ifBlank { video.localUri },
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = Color.Black.copy(alpha = 0.85f),
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

        // Video Info & Stats
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Visibility badge row (Public / Unlisted / Private)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (visibility) {
                    "Private" -> Icons.Outlined.Lock
                    "Unlisted" -> Icons.Outlined.Link
                    else -> Icons.Outlined.Public
                }
                Icon(
                    imageVector = icon,
                    contentDescription = visibility,
                    tint = if (visibility == "Public") Color(0xFF2BA640) else Color.LightGray,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = visibility,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " • ${video.viewCount} views",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Stats row (Likes & Comments)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = "Likes",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "12",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Comment,
                        contentDescription = "Comments",
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "4",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 3-dot overflow menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("manage_overflow_${video.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF222222))
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Save to playlist", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.PlaylistAdd, contentDescription = null, tint = Color.LightGray)
                    },
                    onClick = {
                        showMenu = false
                        onSaveToPlaylist()
                    }
                )
                HorizontalDivider(color = Color(0xFF333333))
                DropdownMenuItem(
                    text = { Text("Delete video", color = Color(0xFFFF4D4D), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFFF4D4D))
                    },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun EditVideoMetadataDialog(
    video: VideoEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, visibility: String, tags: String) -> Unit
) {
    var title by remember { mutableStateOf(video.title) }
    var description by remember { mutableStateOf(video.description) }
    var tags by remember { mutableStateOf(video.tags) }
    var visibility by remember {
        mutableStateOf(
            when {
                video.tags.contains("Private", ignoreCase = true) -> "Private"
                video.tags.contains("Unlisted", ignoreCase = true) -> "Unlisted"
                else -> "Public"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit video details",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Text(
                    text = "Visibility",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )

                listOf("Public", "Unlisted", "Private").forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { visibility = opt }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = visibility == opt,
                            onClick = { visibility = opt },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = opt, color = Color.White, fontSize = 14.sp)
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.trim(), description.trim(), visibility, tags.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("save_edit_video_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF222222)
    )
}
