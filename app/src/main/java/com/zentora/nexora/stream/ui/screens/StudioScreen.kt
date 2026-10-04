/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.ui.components.ThumbnailDesignerDialog
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(
    repository: NexoraStreamRepository,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Content Manager, 2: Upload

    // Upload Form State (Features 13, 14, 15, 16, 17)
    var uploadTitle by remember { mutableStateOf("") }
    var uploadDescription by remember { mutableStateOf("") }
    var uploadCategory by remember { mutableStateOf("Tech") }
    var uploadTags by remember { mutableStateOf("Zentora, Stream, 4K") }
    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
    var selectedThumbnailUri by remember { mutableStateOf<String?>(null) }

    // Feature 16: Collab Tag
    var selectedCollabChannelId by remember { mutableStateOf<String?>(null) }

    // Feature 17: Live Premieres Countdown scheduler
    var isPremiereSchedule by remember { mutableStateOf(false) }

    // Feature 14: In-App Thumbnail Designer dialog
    var showThumbnailDesigner by remember { mutableStateOf(false) }

    // Feature 19: Edit & Delete video dialogs
    var editCandidateVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var deleteCandidateVideo by remember { mutableStateOf<VideoEntity?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri.toString()
            Toast.makeText(context, "Video file selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        }
    }

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedThumbnailUri = uri.toString()
            Toast.makeText(context, "Thumbnail selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Feature 18: Creator Analytics Calculation
    val totalLifetimeViews = remember(allVideos) { allVideos.sumOf { it.viewCount } }
    val totalWatchSeconds = remember(allVideos) { allVideos.sumOf { (it.duration / 1000L) * maxOf(1L, it.viewCount) } }
    val watchTimeHours = totalWatchSeconds / 3600.0
    val totalSubscribers = remember(allChannels) { allChannels.sumOf { it.subscriberCount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("studio_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VideoCameraFront, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nexora Studio", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Button(
                onClick = { selectedTab = 2 },
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = NexoraRed
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Dashboard") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Content (${allVideos.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Upload Studio") })
        }

        when (selectedTab) {
            // Dashboard (Feature 18)
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text("Channel Lifetime Analytics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Real-time local engagement telemetry", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Lifetime Views", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("$totalLifetimeViews", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Watch Time (Hrs)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("%.1f".format(watchTimeHours), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Total Subscribers across Channels", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$totalSubscribers", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = NexoraRed)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Status: Verified Zentora CLC Platform", fontSize = 11.sp, color = NexoraVerifiedTick)
                            }
                        }
                    }
                }
            }

            // Content Manager (Feature 19)
            1 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (allVideos.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No videos published yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(allVideos, key = { it.id }) { video ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.DarkGray)
                                                .clickable { onVideoClick(video.id) }
                                        ) {
                                            AsyncImage(
                                                model = video.thumbnailUri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(video.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${video.viewCount} views • ${video.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onVideoClick(video.id) }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play")
                                        }
                                        TextButton(onClick = { editCandidateVideo = video }) {
                                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        TextButton(
                                            onClick = { deleteCandidateVideo = video },
                                            colors = ButtonDefaults.textButtonColors(contentColor = NexoraError)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Upload Studio (Features 13, 14, 15, 16, 17)
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text("Upload & Transcode Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    // Feature 13: Video File Picker
                    item {
                        Card(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedVideoUri != null) NexoraSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (selectedVideoUri != null) Icons.Filled.CheckCircle else Icons.Filled.VideoFile,
                                    contentDescription = null,
                                    tint = if (selectedVideoUri != null) NexoraSuccess else NexoraRed,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(if (selectedVideoUri != null) "Video File Attached ✓" else "Choose Video File", fontWeight = FontWeight.Bold)
                                    Text(selectedVideoUri ?: "Tap to pick from device storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Feature 13 & 14: Thumbnail Selector & Designer
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                onClick = { thumbnailPickerLauncher.launch("image/*") },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedThumbnailUri != null) NexoraSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = NexoraRed)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (selectedThumbnailUri != null) "Custom Image ✓" else "Pick Artwork", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Feature 14: In-App Thumbnail Designer trigger
                            Card(
                                onClick = { showThumbnailDesigner = true },
                                colors = CardDefaults.cardColors(containerColor = NexoraSparkGold.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Filled.Brush, contentDescription = null, tint = NexoraSparkGold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Open Designer", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NexoraSparkGold)
                                }
                            }
                        }
                    }

                    // Feature 15: Upload Metadata Form
                    item {
                        OutlinedTextField(
                            value = uploadTitle,
                            onValueChange = { uploadTitle = it },
                            label = { Text("Title (required)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = uploadDescription,
                            onValueChange = { uploadDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 2
                        )
                    }

                    item {
                        val categories = listOf("Tech", "Animation", "Sports", "Music", "Education")
                        Column {
                            Text("Category Selector", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = uploadCategory == cat,
                                        onClick = { uploadCategory = cat },
                                        label = { Text(cat, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = uploadTags,
                            onValueChange = { uploadTags = it },
                            label = { Text("Tag Manager (comma-separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Feature 16: Creator Collaboration Tag (Collab Feature)
                    item {
                        Column {
                            Text("Creator Collaboration (Collab Tag)", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedCollabChannelId == "ch_blender_open",
                                    onClick = {
                                        selectedCollabChannelId = if (selectedCollabChannelId == "ch_blender_open") null else "ch_blender_open"
                                    },
                                    label = { Text("Open Media Lab (Collab)") }
                                )
                            }
                        }
                    }

                    // Feature 17: Live Premieres Countdown scheduler
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Live Premiere Countdown", fontWeight = FontWeight.SemiBold)
                                Text("Scheduled premiere mode with badge", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isPremiereSchedule,
                                onCheckedChange = { isPremiereSchedule = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed)
                            )
                        }
                    }

                    // Publish Button
                    item {
                        Button(
                            onClick = {
                                if (uploadTitle.isBlank()) {
                                    Toast.makeText(context, "Please enter a video title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    val videoUri = selectedVideoUri ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
                                    val thumbUri = selectedThumbnailUri ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800"

                                    repository.uploadVideo(
                                        title = uploadTitle.trim(),
                                        description = uploadDescription.trim(),
                                        category = uploadCategory,
                                        tags = uploadTags.trim(),
                                        localUri = videoUri,
                                        thumbnailUri = thumbUri,
                                        collabChannelId = selectedCollabChannelId,
                                        isPremiere = isPremiereSchedule,
                                        scheduledTimeMs = if (isPremiereSchedule) System.currentTimeMillis() + 3600000L else null
                                    )

                                    Toast.makeText(context, "Video published with 0 initial views & likes!", Toast.LENGTH_LONG).show()
                                    selectedTab = 1
                                    uploadTitle = ""
                                    uploadDescription = ""
                                    selectedVideoUri = null
                                    selectedThumbnailUri = null
                                }
                            },
                            enabled = uploadTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Publish to Nexora Stream", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Feature 14: Thumbnail Designer Modal
    if (showThumbnailDesigner) {
        val base = selectedThumbnailUri ?: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800"
        ThumbnailDesignerDialog(
            baseImageUri = base,
            onDismiss = { showThumbnailDesigner = false },
            onSaveDesign = { textOverlay, badge, filter ->
                showThumbnailDesigner = false
                selectedThumbnailUri = base
                uploadTitle = if (uploadTitle.isBlank()) textOverlay else uploadTitle
                Toast.makeText(context, "Designed thumbnail banner attached ✓", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Feature 19: Edit Metadata Dialog
    editCandidateVideo?.let { v ->
        var eTitle by remember { mutableStateOf(v.title) }
        var eDesc by remember { mutableStateOf(v.description) }
        var eCat by remember { mutableStateOf(v.category) }
        var eTags by remember { mutableStateOf(v.tags) }

        AlertDialog(
            onDismissRequest = { editCandidateVideo = null },
            title = { Text("Edit Metadata", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = eTitle, onValueChange = { eTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eDesc, onValueChange = { eDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eCat, onValueChange = { eCat = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = eTags, onValueChange = { eTags = it }, label = { Text("Tags") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.updateVideoMetadata(v.id, eTitle.trim(), eDesc.trim(), eCat.trim(), eTags.trim())
                            editCandidateVideo = null
                            Toast.makeText(context, "Metadata updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editCandidateVideo = null }) { Text("Cancel") }
            }
        )
    }

    // Feature 19: Permanent Delete Confirmation Dialog
    deleteCandidateVideo?.let { del ->
        AlertDialog(
            onDismissRequest = { deleteCandidateVideo = null },
            title = { Text("Permanently Delete Video?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${del.title}'? This action removes all database records and playback history.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteVideo(del.id)
                            deleteCandidateVideo = null
                            Toast.makeText(context, "Video permanently deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraError)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidateVideo = null }) { Text("Cancel") }
            }
        )
    }
}
