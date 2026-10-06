/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.zentora.nexora.stream.engine.NexoraNetworkClient
import com.zentora.nexora.stream.engine.NexoraStorageManager
import com.zentora.nexora.stream.engine.Vault1StorageEngine
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class StudioPillMode(val label: String) {
    VIDEO("Video"),
    SHORT("Short"),
    LIVE("Live"),
    POST("Post")
}

/**
 * 4-in-1 Unified Creator Studio matching exact YouTube screenshots:
 * - Screenshots 14-18-27 (Live), 14-18-35 (Gallery Grid), 14-18-39 (Short Camera),
 *   14-18-43 (Create Post), 14-18-51 / 14-18-56 (Short Trim & Edit),
 *   14-19-09 / 14-19-56 / 14-20-01 (Add Details Metadata), 14-19-54 (Edit Thumbnail).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStudioScreen(
    repository: NexoraStreamRepository,
    onUploadComplete: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userSession = remember { NexoraIdManager.getSession() }

    var activePillMode by remember { mutableStateOf(StudioPillMode.VIDEO) }

    // Navigation stages within Studio
    // 0: Initial Screen for selected mode
    // 1: Short trimmer/preview (for Shorts)
    // 2: Add Details metadata screen (for Video & Short)
    // 3: Edit Thumbnail screen
    var studioStage by remember { mutableIntStateOf(0) }

    // Media & Ingest State
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var persistedVideoFile by remember { mutableStateOf<File?>(null) }
    var generatedThumbnailPath by remember { mutableStateOf<String?>(null) }
    var customThumbnailPath by remember { mutableStateOf<String?>(null) }
    var mediaDurationMs by remember { mutableLongStateOf(60000L) }
    var isShortMode by remember { mutableStateOf(false) }

    // Metadata Fields (Screenshots 14-19-09, 14-20-01, 14-19-56)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("Public") }
    var audience by remember { mutableStateOf("No, it's not Made for Kids") }
    var location by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("Zentora, 4K") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var showMoreDetails by remember { mutableStateOf(false) }

    // Live Mode State (Screenshot 14-18-27)
    var liveTitle by remember { mutableStateOf("Skyline Pro gamer is live") }
    var liveVisibility by remember { mutableStateOf("Unlisted • Not Made for Kids") }
    var isLiveFlipped by remember { mutableStateOf(false) }
    var isLiveMuted by remember { mutableStateOf(false) }
    var isLiveVideoHidden by remember { mutableStateOf(false) }

    // Post Mode State (Screenshot 14-18-43)
    var postText by remember { mutableStateOf("") }
    var postImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPollEditor by remember { mutableStateOf(false) }
    var pollOption1 by remember { mutableStateOf("") }
    var pollOption2 by remember { mutableStateOf("") }

    // System File Pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            scope.launch(Dispatchers.IO) {
                try {
                    val uniqueId = UUID.randomUUID().toString().take(8)
                    // Persist using NexoraStorageManager (Vault 1)
                    val localPath = NexoraStorageManager.persistVideoLocally(context, uri, uniqueId)
                    val localFile = File(localPath)
                    persistedVideoFile = localFile

                    // Auto-generate 1-second fallback thumbnail
                    val thumbPath = NexoraStorageManager.generateAutoThumbnail(context, localPath, uniqueId)
                    generatedThumbnailPath = thumbPath

                    val duration = Vault1StorageEngine.extractDurationMs(localFile)
                    mediaDurationMs = duration

                    withContext(Dispatchers.Main) {
                        isShortMode = activePillMode == StudioPillMode.SHORT
                        studioStage = if (isShortMode) 1 else 2 // Go to Short edit or Add details
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ingest failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val customThumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val thumbFile = Vault1StorageEngine.persistCustomThumbnailToVault1(context, uri)
                    customThumbnailPath = thumbFile.absolutePath
                    withContext(Dispatchers.Main) {
                        studioStage = 2 // Return to Add details
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Thumbnail error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val postImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            postImageUri = uri
        }
    }

    // ==================== SCREEN ROUTING BY STAGE ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        when (studioStage) {
            0 -> {
                // Initial creation screen for current pill mode
                when (activePillMode) {
                    StudioPillMode.VIDEO -> {
                        // Video Gallery Grid (Screenshot 14-18-35)
                        VideoGalleryPickerView(
                            onClose = onClose,
                            onPickFromStorage = { videoPickerLauncher.launch("video/*") },
                            onSelectSample = { sampleUri, duration ->
                                videoPickerLauncher.launch("video/*")
                            }
                        )
                    }

                    StudioPillMode.SHORT -> {
                        // Short Camera Viewfinder (Screenshot 14-18-39)
                        ShortCameraViewfinderView(
                            onClose = onClose,
                            onAddGallery = { videoPickerLauncher.launch("video/*") },
                            onRecord = {
                                // Direct camera/media selection for Short
                                videoPickerLauncher.launch("video/*")
                            }
                        )
                    }

                    StudioPillMode.LIVE -> {
                        // Live Broadcaster Preview (Screenshot 14-18-27)
                        LiveBroadcasterPreviewView(
                            title = liveTitle,
                            subtitle = liveVisibility,
                            avatarUri = userSession.avatarUri,
                            isFlipped = isLiveFlipped,
                            isMuted = isLiveMuted,
                            isVideoHidden = isLiveVideoHidden,
                            onFlip = { isLiveFlipped = !isLiveFlipped },
                            onMute = { isLiveMuted = !isLiveMuted },
                            onHideVideo = { isLiveVideoHidden = !isLiveVideoHidden },
                            onClose = onClose,
                            onNext = {
                                Toast.makeText(context, "Live stream ready on Vault 4 node", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    StudioPillMode.POST -> {
                        // Create Post Screen (Screenshot 14-18-43)
                        CreatePostView(
                            postText = postText,
                            onPostTextChange = { postText = it },
                            postImageUri = postImageUri,
                            showPoll = showPollEditor,
                            pollOption1 = pollOption1,
                            pollOption2 = pollOption2,
                            onPoll1Change = { pollOption1 = it },
                            onPoll2Change = { pollOption2 = it },
                            onTogglePoll = { showPollEditor = !showPollEditor },
                            onPickImage = { postImagePickerLauncher.launch("image/*") },
                            onRemoveImage = { postImageUri = null },
                            onClose = onClose,
                            onPublishPost = {
                                if (postText.isNotBlank()) {
                                    scope.launch {
                                        val pollJson = if (showPollEditor && pollOption1.isNotBlank()) {
                                            "[\"$pollOption1\", \"$pollOption2\"]"
                                        } else null
                                        repository.createCommunityPost(
                                            channelId = "ch_zentora_core",
                                            contentText = postText.trim(),
                                            imageUri = postImageUri?.toString(),
                                            pollOptionsJson = pollJson
                                        )
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Post published!", Toast.LENGTH_SHORT).show()
                                            onUploadComplete()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Bottom floating mode switcher pill: [Video | Short | Live | Post]
                BottomModeSwitcherPill(
                    activeMode = activePillMode,
                    onSelectMode = { mode ->
                        activePillMode = mode
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                )
            }

            1 -> {
                // Short Trimmer & Edit Screen (Screenshots 14-18-51 & 14-18-56)
                ShortTrimmerEditView(
                    onBack = { studioStage = 0 },
                    onNext = { studioStage = 2 }
                )
            }

            2 -> {
                // Add Details Screen (Screenshots 14-19-09, 14-19-56, 14-20-01)
                val activeThumb = customThumbnailPath ?: generatedThumbnailPath
                AddDetailsMetadataView(
                    isShort = isShortMode,
                    thumbnailPath = activeThumb,
                    videoDurationMs = mediaDurationMs,
                    channelName = if (userSession.username.isNotBlank()) userSession.username else "Skyline Pro gamer",
                    handle = if (userSession.handle.isNotBlank()) userSession.handle else "@SkylineProgamer",
                    avatarUri = userSession.avatarUri,
                    title = title,
                    onTitleChange = { title = it },
                    visibility = visibility,
                    audience = audience,
                    showMore = showMoreDetails,
                    onToggleShowMore = { showMoreDetails = !showMoreDetails },
                    onEditThumbnail = { studioStage = 3 },
                    onBack = { studioStage = 0 },
                    isUploading = isUploading,
                    uploadProgress = uploadProgress,
                    onUpload = {
                        val videoFile = persistedVideoFile
                        if (videoFile != null && videoFile.exists()) {
                            isUploading = true
                            uploadProgress = 0.2f

                            scope.launch {
                                val thumbFile = if (activeThumb != null) File(activeThumb) else null

                                // 1. Real OkHttp upload to Vault 4
                                uploadProgress = 0.5f
                                NexoraNetworkClient.uploadToVault4Blobstore(
                                    videoFile = videoFile,
                                    thumbnailFile = thumbFile,
                                    title = if (title.isNotBlank()) title.trim() else "My Video",
                                    description = description.trim(),
                                    category = "Tech",
                                    tags = tags.trim(),
                                    authorChannelId = "ch_zentora_core",
                                    isShort = isShortMode
                                )

                                uploadProgress = 0.85f
                                delay(200)

                                // 2. Insert into local Room DB (Vault 1)
                                repository.uploadVideo(
                                    title = if (title.isNotBlank()) title.trim() else "My Video",
                                    description = description.trim(),
                                    category = "Tech",
                                    tags = tags.trim(),
                                    localUri = videoFile.absolutePath,
                                    thumbnailUri = activeThumb ?: "",
                                    duration = mediaDurationMs
                                )

                                uploadProgress = 1.0f
                                delay(150)
                                isUploading = false

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Video uploaded successfully to Vault 1 & 4!", Toast.LENGTH_LONG).show()
                                    onUploadComplete()
                                }
                            }
                        }
                    }
                )
            }

            3 -> {
                // Edit Thumbnail Screen (Screenshots 14-19-54 & 14-19-23)
                EditThumbnailView(
                    currentThumbnailPath = customThumbnailPath ?: generatedThumbnailPath,
                    onChangeThumbnail = { customThumbnailPickerLauncher.launch("image/*") },
                    onDone = { studioStage = 2 },
                    onBack = { studioStage = 2 }
                )
            }
        }
    }
}

// =================================================================================
// 1. VIDEO GALLERY PICKER (Screenshot 14-18-35)
// =================================================================================
@Composable
fun VideoGalleryPickerView(
    onClose: () -> Unit,
    onPickFromStorage: () -> Unit,
    onSelectSample: (String, Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Top Bar: (X) Close + "Upload from gallery" + "Videos v"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Upload from gallery",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF222222),
                modifier = Modifier.clickable { onPickFromStorage() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Videos", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Tap to pick file card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1F1F1F),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onPickFromStorage() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(NexoraRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Select video from your device", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Instant persistent copy to Vault 1 internal directory", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3-Column Media Grid (matching screenshot 14-18-35)
        val sampleGrid = remember {
            listOf(
                Pair("0:10", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=300"),
                Pair("0:20", "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=300"),
                Pair("1:00", "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=300"),
                Pair("0:23", "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=300"),
                Pair("0:24", "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=300"),
                Pair("0:07", "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=300"),
                Pair("1:45:12", "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=300"),
                Pair("0:05", "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=300"),
                Pair("2:10:24", "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=300")
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(sampleGrid) { (duration, url) ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { onPickFromStorage() }
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Duration badge
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

// =================================================================================
// 2. SHORT CAMERA VIEWFINDER (Screenshot 14-18-39)
// =================================================================================
@Composable
fun ShortCameraViewfinderView(
    onClose: () -> Unit,
    onAddGallery: () -> Unit,
    onRecord: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Viewfinder background (Dark gradient representing camera lens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1418))
        )

        // Top Controls: (X) Close, "Add sound" pill, Magic Wand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }

            // "Add sound" Pill Button
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.clickable { onAddGallery() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add sound", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Magic Wand Sparkle Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B2B72)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Right Vertical Tool Rail (matching screenshot 14-18-39):
        // Flip, Timer, 15 s, Effects, 1x, Layout, More
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(24.dp))
                Icon(Icons.Outlined.Timer, contentDescription = "Timer", tint = Color.White, modifier = Modifier.size(24.dp))
                Text("15 s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Effects", tint = Color.White, modifier = Modifier.size(24.dp))
                Text("1x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ViewQuilt, contentDescription = "Layout", tint = Color.White, modifier = Modifier.size(24.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "More", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // Bottom Controls: "Add" gallery button (left), Red circle record button (center)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 100.dp, start = 30.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // "Add" gallery thumbnail
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAddGallery() }
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Add", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Big Red Circle Record Button (with white outer ring)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(NexoraRed)
                    .clickable { onRecord() }
            )

            // Empty spacer for balance
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

// =================================================================================
// 3. SHORT TRIMMER & EDIT VIEW (Screenshots 14-18-51 & 14-18-56)
// =================================================================================
@Composable
fun ShortTrimmerEditView(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1E))
    ) {
        // Top Bar: Back arrow, "Add sound", volume/mute icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add sound", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Color.White)
            }
        }

        // Center visual preview placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MovieCreation, contentDescription = null, tint = NexoraZentoraBlue, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Short Preview Loaded", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // Right Vertical Tool Rail (matching screenshot 14-18-56):
        // Text (Aa), Effects, Filters, Stickers, Captions, More
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ShortToolItem("Text", Icons.Filled.TextFields)
                ShortToolItem("Effects", Icons.Filled.AutoAwesome)
                ShortToolItem("Filters", Icons.Filled.ColorLens)
                ShortToolItem("Stickers", Icons.Filled.SentimentSatisfied)
                ShortToolItem("Captions", Icons.Filled.ClosedCaption)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Bottom Controls: Video trimming bar (10.0s) & Next button (screenshot 14-18-51)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Trimmer Strip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White
                    ) {
                        Text(
                            text = "10.0s",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Drag to adjust video", color = Color.Gray, fontSize = 12.sp)

                // White pill "Next" button
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text("Next", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ShortToolItem(label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}

// =================================================================================
// 4. LIVE BROADCASTER PREVIEW (Screenshot 14-18-27)
// =================================================================================
@Composable
fun LiveBroadcasterPreviewView(
    title: String,
    subtitle: String,
    avatarUri: String,
    isFlipped: Boolean,
    isMuted: Boolean,
    isVideoHidden: Boolean,
    onFlip: () -> Unit,
    onMute: () -> Unit,
    onHideVideo: () -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF160D12))
    ) {
        // Top Bar: (X) Close, Calendar, Share
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Color.White)
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White)
                }
            }
        }

        // Right Vertical Tool Rail (matching screenshot 14-18-27):
        // Flip, Mute, Hide live video, Orientation, More
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onFlip() }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Flip", color = Color.White, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onMute() }) {
                    Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mute", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Mute", color = Color.White, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onHideVideo() }) {
                    Icon(if (isVideoHidden) Icons.Filled.VideocamOff else Icons.Filled.Videocam, contentDescription = "Hide video", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Hide live video", color = Color.White, fontSize = 9.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.StayCurrentPortrait, contentDescription = "Orientation", tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Orientation", color = Color.White, fontSize = 9.sp)
                }
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "More", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Bottom Details and Action Buttons (matching screenshot 14-18-27)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 90.dp)
        ) {
            // Identity row with cyan border avatar and edit pencil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(2.dp, NexoraZentoraBlue, CircleShape)
                ) {
                    AsyncImage(
                        model = avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150" },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit title", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // White rounded "Next" button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Next", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dark rounded "Practice mode" button
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF262626),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onNext() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Practice mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// =================================================================================
// 5. CREATE POST VIEW (Screenshot 14-18-43)
// =================================================================================
@Composable
fun CreatePostView(
    postText: String,
    onPostTextChange: (String) -> Unit,
    postImageUri: Uri?,
    showPoll: Boolean,
    pollOption1: String,
    pollOption2: String,
    onPoll1Change: (String) -> Unit,
    onPoll2Change: (String) -> Unit,
    onTogglePoll: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onClose: () -> Unit,
    onPublishPost: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Top Bar: (X) Close, "Create post", "Post" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Create post", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }

            Button(
                onClick = onPublishPost,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (postText.isNotBlank()) Color.White else Color(0xFF333333)
                ),
                enabled = postText.isNotBlank()
            ) {
                Text("Post", color = if (postText.isNotBlank()) Color.Black else Color.Gray, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Identity row (Avatar, Channel name, "🌐 Public" pill, 3-dots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text("Skyline Pro gamer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF262626)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Public, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Public", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text area: "Post an update to your fans"
        OutlinedTextField(
            value = postText,
            onValueChange = onPostTextChange,
            placeholder = { Text("Post an update to your fans", color = Color.Gray, fontSize = 16.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // Poll options if active
        if (showPoll) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = pollOption1,
                    onValueChange = onPoll1Change,
                    placeholder = { Text("Add option 1") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = pollOption2,
                    onValueChange = onPoll2Change,
                    placeholder = { Text("Add option 2") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Image attachment preview if selected
        if (postImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(model = postImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Bottom Action Bar: Poll icon, Image icon, Quiz icon (screenshot 14-18-43)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IconButton(onClick = onTogglePoll) {
                Icon(Icons.AutoMirrored.Filled.Segment, contentDescription = "Poll", tint = if (showPoll) NexoraRed else Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onPickImage) {
                Icon(Icons.Outlined.Image, contentDescription = "Image", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onTogglePoll) {
                Icon(Icons.Outlined.CheckBox, contentDescription = "Quiz", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

// =================================================================================
// 6. ADD DETAILS METADATA VIEW (Screenshots 14-19-09, 14-19-56, 14-20-01)
// =================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDetailsMetadataView(
    isShort: Boolean,
    thumbnailPath: String?,
    videoDurationMs: Long,
    channelName: String,
    handle: String,
    avatarUri: String,
    title: String,
    onTitleChange: (String) -> Unit,
    visibility: String,
    audience: String,
    showMore: Boolean,
    onToggleShowMore: () -> Unit,
    onEditThumbnail: () -> Unit,
    onBack: () -> Unit,
    isUploading: Boolean,
    uploadProgress: Float,
    onUpload: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Full-width rounded white Upload Button (Screenshots 14-19-09 & 14-20-01)
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onUpload,
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(
                        text = if (isShort) "Upload Short" else "Upload",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Section: Thumbnail preview + Pencil icon + Title/Caption field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail Box with Pencil Icon on top-left overlay
                Box(
                    modifier = Modifier
                        .width(if (isShort) 80.dp else 120.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222222))
                        .clickable { onEditThumbnail() }
                ) {
                    if (thumbnailPath != null) {
                        AsyncImage(
                            model = File(thumbnailPath),
                            contentDescription = "Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Edit Pencil overlay on top left
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Thumbnail", tint = Color.White, modifier = Modifier.size(15.dp))
                    }

                    // Duration badge on bottom right
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = formatMsToTime(videoDurationMs),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Title / Caption Input Field
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = {
                        Text(
                            text = if (isShort) "Caption your Short" else "Create a title (type @ to mention a channel)",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Channel Row (Avatar, Name, @handle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, NexoraZentoraBlue, CircleShape)
                ) {
                    AsyncImage(
                        model = avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150" },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(channelName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(handle, fontSize = 12.sp, color = Color.Gray)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // List of metadata settings with right chevrons (matching screenshots 14-20-01 & 14-19-56):
            // 🌐 Visibility: Public >
            DetailSettingsRow(Icons.Outlined.Public, "Visibility", visibility, onClick = {})
            // 👥 Audience: No, it's not Made for Kids >
            DetailSettingsRow(Icons.Outlined.Group, "Audience", audience, onClick = {})
            // ≡ Add description >
            DetailSettingsRow(Icons.Outlined.Subject, "Add description", null, onClick = {})
            // 📍 Location >
            DetailSettingsRow(Icons.Outlined.Place, "Location", null, onClick = {})
            // ▶ Related video >
            DetailSettingsRow(Icons.Outlined.PlayCircle, "Related video", null, onClick = {})
            // ☰+ Add to playlists +
            DetailSettingsRow(Icons.Outlined.PlaylistAdd, "Add to playlists", null, isAdd = true, onClick = {})
            // 💲 Paid promotion and brands >
            DetailSettingsRow(Icons.Outlined.Paid, "Paid promotion and brands", null, onClick = {})

            if (showMore) {
                // 💬 Community: Collaborations, Comments, and Remixing >
                DetailSettingsRow(Icons.Outlined.ChatBubbleOutline, "Community", "Collaborations, Comments, and Remixing", onClick = {})
                // 📑 Attributes: AI use, Tags >
                DetailSettingsRow(Icons.Outlined.Article, "Attributes", "AI use, Tags", onClick = {})
            }

            // "Show more v" / "Show less ^" toggle button
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF222222),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickable { onToggleShowMore() }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (showMore) "Show less" else "Show more", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(if (showMore) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // COPPA Compliance Legal Notice (matching screenshot 14-19-56)
            Text(
                text = "Regardless of your location, you're legally required to comply with the US Children's Online Privacy Protection Act (COPPA) and/or other laws. You're required to tell us whether your videos are Made for Kids.",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (isUploading) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = NexoraRed
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun DetailSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    isAdd: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color.LightGray, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
        Icon(
            imageVector = if (isAdd) Icons.Filled.Add else Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

// =================================================================================
// 7. EDIT THUMBNAIL VIEW (Screenshots 14-19-54 & 14-19-23)
// =================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditThumbnailView(
    currentThumbnailPath: String?,
    onChangeThumbnail: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit thumbnail", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 16:9 Thumbnail preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222)),
                contentAlignment = Alignment.Center
            ) {
                if (currentThumbnailPath != null) {
                    AsyncImage(
                        model = File(currentThumbnailPath),
                        contentDescription = "Thumbnail Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                }
            }

            // "Change" Button Card with + image icon (matching screenshot 14-19-54)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                modifier = Modifier
                    .size(width = 130.dp, height = 75.dp)
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                    .clickable { onChangeThumbnail() }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Change", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =================================================================================
// 8. BOTTOM MODE SWITCHER PILL [Video | Short | Live | Post]
// =================================================================================
@Composable
fun BottomModeSwitcherPill(
    activeMode: StudioPillMode,
    onSelectMode: (StudioPillMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E1E1E),
        tonalElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioPillMode.values().forEach { mode ->
                val isSelected = activeMode == mode
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF383838) else Color.Transparent,
                    modifier = Modifier.clickable { onSelectMode(mode) }
                ) {
                    Text(
                        text = mode.label,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
