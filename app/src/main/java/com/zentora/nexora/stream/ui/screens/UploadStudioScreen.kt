/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.engine.NexoraNetworkClient
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

data class LocalDeviceVideoItem(
    val id: Long,
    val uri: Uri,
    val durationMs: Long,
    val displayName: String
)

/**
 * Loads real device videos from MediaStore (MediaStore.Video.Media.EXTERNAL_CONTENT_URI) via ContentResolver.
 */
fun queryDeviceVideos(context: Context): List<LocalDeviceVideoItem> {
    val videos = mutableListOf<LocalDeviceVideoItem>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DISPLAY_NAME
    )
    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    try {
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val durationColumn = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val nameColumn = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val duration = if (durationColumn != -1) c.getLong(durationColumn) else 0L
                val name = if (nameColumn != -1) c.getString(nameColumn) ?: "Video" else "Video"
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                videos.add(
                    LocalDeviceVideoItem(
                        id = id,
                        uri = contentUri,
                        durationMs = duration,
                        displayName = name
                    )
                )
            }
        }
    } catch (_: Exception) {
    }
    return videos
}

/**
 * 4-in-1 Unified Creator Studio matching exact YouTube screenshots:
 * - TASK 1: Automatic In-App 3x3 Video Gallery Grid (Video Tab) via MediaStore.
 * - Live Camera Viewfinder with CameraX in Shorts mode.
 * - Add Details Metadata Suite with 10 full state-driven controls.
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
    val userSession by NexoraIdManager.sessionFlow.collectAsState()

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

    // On-device MediaStore Video List
    var deviceVideos by remember { mutableStateOf<List<LocalDeviceVideoItem>>(emptyList()) }
    var isLoadingMedia by remember { mutableStateOf(false) }

    // Read media permission launcher for Android 13+ (READ_MEDIA_VIDEO) or Android 12- (READ_EXTERNAL_STORAGE)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        scope.launch(Dispatchers.IO) {
            val list = queryDeviceVideos(context)
            withContext(Dispatchers.Main) {
                deviceVideos = list
            }
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            isLoadingMedia = true
            scope.launch(Dispatchers.IO) {
                val list = queryDeviceVideos(context)
                withContext(Dispatchers.Main) {
                    deviceVideos = list
                    isLoadingMedia = false
                }
            }
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    // Process selected video: copy to Vault 1 internal directory + 1.0s thumbnail extraction
    val processAndIngestVideo: (Uri, Boolean) -> Unit = { sourceUri, isShort ->
        selectedVideoUri = sourceUri
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Copy into Vault 1 internal directory (context.filesDir/nexora_vault_media/)
                val savedVideoFile = Vault1StorageEngine.persistVideoToVault1(context, sourceUri, isShort)
                persistedVideoFile = savedVideoFile

                // 2. Auto-extract 1.0s video frame via MediaMetadataRetriever
                val thumbFile = Vault1StorageEngine.extractAutomatic1SecThumbnail(context, savedVideoFile)
                generatedThumbnailPath = thumbFile?.absolutePath

                // 3. Extract duration
                val duration = Vault1StorageEngine.extractDurationMs(savedVideoFile)
                mediaDurationMs = duration

                withContext(Dispatchers.Main) {
                    isShortMode = isShort
                    studioStage = if (isShort) 1 else 2
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ingest failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ==================== FULL METADATA CONTROLS STATE ====================
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // 1. Visibility Selector
    var visibility by remember { mutableStateOf("Public") }
    var isInstantPremiere by remember { mutableStateOf(false) }
    var scheduledDateTimeText by remember { mutableStateOf("") }
    var showVisibilityModal by remember { mutableStateOf(false) }

    // 2. Audience & COPPA Compliance
    var isMadeForKids by remember { mutableStateOf(false) }
    var isAgeRestricted by remember { mutableStateOf(false) }
    var showAudienceModal by remember { mutableStateOf(false) }

    // 3. Location Selector
    var location by remember { mutableStateOf("") }
    var showLocationModal by remember { mutableStateOf(false) }

    // 4. Add to Playlists
    val selectedPlaylists = remember { mutableStateListOf<String>() }
    var showPlaylistModal by remember { mutableStateOf(false) }

    // 5. Shorts Remixing Permissions
    var shortsRemixing by remember { mutableStateOf("Allow video and audio remixing") }
    var showRemixingModal by remember { mutableStateOf(false) }

    // 6. Comments & Moderation Settings
    var commentsModeration by remember { mutableStateOf("On (Strict)") }
    var showLikesCount by remember { mutableStateOf(true) }
    var showCommentsModal by remember { mutableStateOf(false) }

    // 7. Paid Promotion
    var containsPaidPromotion by remember { mutableStateOf(false) }

    // 8. Tags, Category & Language
    var tagsInput by remember { mutableStateOf("Zentora, 4K, Nexora") }
    var category by remember { mutableStateOf("Tech") }
    var videoLanguage by remember { mutableStateOf("English") }
    var showAttributesModal by remember { mutableStateOf(false) }

    // 9. License & Distribution
    var licenseType by remember { mutableStateOf("Standard Nexora License") }
    var allowEmbedding by remember { mutableStateOf(true) }
    var notifySubscribers by remember { mutableStateOf(true) }
    var showLicenseModal by remember { mutableStateOf(false) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var showMoreDetails by remember { mutableStateOf(false) }
    var showDescriptionModal by remember { mutableStateOf(false) }

    // Live Mode State
    var liveTitle by remember { mutableStateOf("Zentora Stream Live Broadcasting") }
    var liveVisibility by remember { mutableStateOf("Public • Not Made for Kids") }
    var isLiveFlipped by remember { mutableStateOf(false) }
    var isLiveMuted by remember { mutableStateOf(false) }
    var isLiveVideoHidden by remember { mutableStateOf(false) }

    // Post Mode State
    var postText by remember { mutableStateOf("") }
    var postImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPollEditor by remember { mutableStateOf(false) }
    var pollOption1 by remember { mutableStateOf("") }
    var pollOption2 by remember { mutableStateOf("") }

    val customThumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val thumbFile = Vault1StorageEngine.persistCustomThumbnailToVault1(context, uri)
                    customThumbnailPath = thumbFile.absolutePath
                    withContext(Dispatchers.Main) {
                        studioStage = 2
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

    // Short mode fallback gallery picker
    val shortGalleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processAndIngestVideo(uri, true)
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
                when (activePillMode) {
                    StudioPillMode.VIDEO -> {
                        // TASK 1: In-App 3x3 MediaStore Video Gallery Grid
                        VideoGalleryPickerView(
                            videos = deviceVideos,
                            onClose = onClose,
                            onSelectVideo = { videoItem ->
                                processAndIngestVideo(videoItem.uri, false)
                            }
                        )
                    }

                    StudioPillMode.SHORT -> {
                        ShortCameraViewfinderView(
                            onClose = onClose,
                            onAddGallery = { shortGalleryPickerLauncher.launch("video/*") },
                            onRecord = { shortGalleryPickerLauncher.launch("video/*") }
                        )
                    }

                    StudioPillMode.LIVE -> {
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
                                Toast.makeText(context, "Live stream initialized on Vault 4 node", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    StudioPillMode.POST -> {
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
                ShortTrimmerEditView(
                    onBack = { studioStage = 0 },
                    onNext = { studioStage = 2 }
                )
            }

            2 -> {
                val activeThumb = customThumbnailPath ?: generatedThumbnailPath
                AddDetailsMetadataView(
                    isShort = isShortMode,
                    thumbnailPath = activeThumb,
                    videoDurationMs = mediaDurationMs,
                    channelName = userSession.username.ifBlank { "Zentora CLC" },
                    handle = userSession.handle.ifBlank { "@zentora" },
                    avatarUri = userSession.avatarUri,
                    title = title,
                    onTitleChange = { title = it },
                    visibility = visibility,
                    isInstantPremiere = isInstantPremiere,
                    scheduledDateTimeText = scheduledDateTimeText,
                    onOpenVisibility = { showVisibilityModal = true },
                    isMadeForKids = isMadeForKids,
                    isAgeRestricted = isAgeRestricted,
                    onOpenAudience = { showAudienceModal = true },
                    description = description,
                    onOpenDescription = { showDescriptionModal = true },
                    location = location,
                    onOpenLocation = { showLocationModal = true },
                    selectedPlaylists = selectedPlaylists,
                    onOpenPlaylists = { showPlaylistModal = true },
                    containsPaidPromotion = containsPaidPromotion,
                    onTogglePaidPromotion = { containsPaidPromotion = !containsPaidPromotion },
                    shortsRemixing = shortsRemixing,
                    onOpenRemixing = { showRemixingModal = true },
                    commentsModeration = commentsModeration,
                    showLikesCount = showLikesCount,
                    onOpenComments = { showCommentsModal = true },
                    tags = tagsInput,
                    category = category,
                    videoLanguage = videoLanguage,
                    onOpenAttributes = { showAttributesModal = true },
                    licenseType = licenseType,
                    allowEmbedding = allowEmbedding,
                    notifySubscribers = notifySubscribers,
                    onOpenLicense = { showLicenseModal = true },
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
                            uploadProgress = 0.15f

                            scope.launch {
                                val thumbFile = if (activeThumb != null) File(activeThumb) else null

                                // 1. Real OkHttp MultipartBody upload to Vault 4 Blobstore
                                uploadProgress = 0.45f
                                NexoraNetworkClient.uploadToVault4Blobstore(
                                    videoFile = videoFile,
                                    thumbnailFile = thumbFile,
                                    title = if (title.isNotBlank()) title.trim() else "My Video",
                                    description = description.trim(),
                                    category = category,
                                    tags = tagsInput.trim(),
                                    authorChannelId = "ch_zentora_core",
                                    isShort = isShortMode,
                                    visibility = visibility,
                                    isMadeForKids = isMadeForKids,
                                    isAgeRestricted = isAgeRestricted,
                                    location = location,
                                    shortsRemixing = shortsRemixing,
                                    commentsModeration = commentsModeration,
                                    showLikesCount = showLikesCount,
                                    containsPaidPromotion = containsPaidPromotion,
                                    videoLanguage = videoLanguage,
                                    licenseType = licenseType,
                                    allowEmbedding = allowEmbedding,
                                    notifySubscribers = notifySubscribers
                                )

                                uploadProgress = 0.85f
                                delay(200)

                                // 2. Room DB (Vault 1) + Vault 3 Registry + Vault 5 Grand Master Lock
                                repository.uploadVideo(
                                    title = if (title.isNotBlank()) title.trim() else "My Video",
                                    description = description.trim(),
                                    category = category,
                                    tags = tagsInput.trim(),
                                    localUri = videoFile.absolutePath,
                                    thumbnailUri = activeThumb ?: "",
                                    duration = mediaDurationMs,
                                    visibility = visibility,
                                    isMadeForKids = isMadeForKids,
                                    isAgeRestricted = isAgeRestricted,
                                    location = location,
                                    shortsRemixing = shortsRemixing,
                                    commentsModeration = commentsModeration,
                                    showLikesCount = showLikesCount,
                                    containsPaidPromotion = containsPaidPromotion,
                                    videoLanguage = videoLanguage,
                                    licenseType = licenseType,
                                    allowEmbedding = allowEmbedding,
                                    notifySubscribers = notifySubscribers
                                )

                                uploadProgress = 1.0f
                                delay(150)
                                isUploading = false

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Video uploaded successfully!", Toast.LENGTH_LONG).show()
                                    onUploadComplete()
                                }
                            }
                        }
                    }
                )
            }

            3 -> {
                EditThumbnailView(
                    currentThumbnailPath = customThumbnailPath ?: generatedThumbnailPath,
                    onChangeThumbnail = { customThumbnailPickerLauncher.launch("image/*") },
                    onDone = { studioStage = 2 },
                    onBack = { studioStage = 2 }
                )
            }
        }
    }

    // ==================== MODAL DIALOGS FOR UPLOAD CONTROLS ====================

    // 1. Visibility Modal (Public, Unlisted, Private, Premiere, Schedule)
    if (showVisibilityModal) {
        val visibilityOptions = listOf(
            Triple("Public", "Anyone can search for and view", Icons.Outlined.Public),
            Triple("Unlisted", "Anyone with the link can view", Icons.Outlined.Link),
            Triple("Private", "Only you and people you choose can view", Icons.Outlined.Lock)
        )
        AlertDialog(
            onDismissRequest = { showVisibilityModal = false },
            title = { Text("Set visibility", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    visibilityOptions.forEach { (opt, desc, icon) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { visibility = opt }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = visibility == opt, onClick = { visibility = opt })
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(opt, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(desc, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isInstantPremiere = !isInstantPremiere }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = isInstantPremiere, onCheckedChange = { isInstantPremiere = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Set as instant Premiere", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("When you set an instant Premiere, you and your viewers can watch it together at the same time.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("Schedule", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Select a date to make your video public", fontSize = 11.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = scheduledDateTimeText,
                        onValueChange = { scheduledDateTimeText = it },
                        placeholder = { Text("e.g., Oct 15, 2026, 6:00 PM") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVisibilityModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 2. Audience & COPPA Modal
    if (showAudienceModal) {
        AlertDialog(
            onDismissRequest = { showAudienceModal = false },
            title = { Text("Audience & COPPA", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Is this video made for kids? (Required)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "Regardless of your location, you're legally required to comply with COPPA and other laws. Features like comments and personalized ads won't be available on videos made for kids.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMadeForKids = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = isMadeForKids, onClick = { isMadeForKids = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yes, it's made for kids", fontSize = 14.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isMadeForKids = false }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = !isMadeForKids, onClick = { isMadeForKids = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No, it's not made for kids", fontSize = 14.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Text("Age restriction (Advanced)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Do you want to restrict your video to an adult audience?", fontSize = 11.sp, color = Color.Gray)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAgeRestricted = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = isAgeRestricted, onClick = { isAgeRestricted = true })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yes, restrict my video to viewers over 18", fontSize = 13.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAgeRestricted = false }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = !isAgeRestricted, onClick = { isAgeRestricted = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No, don't restrict my video to viewers over 18 only", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudienceModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // Description Modal
    if (showDescriptionModal) {
        AlertDialog(
            onDismissRequest = { showDescriptionModal = false },
            title = { Text("Description", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Add description, links, and hashtags…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )
            },
            confirmButton = {
                TextButton(onClick = { showDescriptionModal = false }) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 3. Location Selector Modal (Search-as-you-type)
    if (showLocationModal) {
        val suggestedPlaces = remember {
            listOf(
                "San Francisco, California",
                "New York, NY",
                "Tokyo, Japan",
                "London, United Kingdom",
                "Dhaka, Bangladesh",
                "Toronto, Canada",
                "Sydney, Australia",
                "Berlin, Germany",
                "Seoul, South Korea",
                "Singapore"
            )
        }
        val filteredPlaces = remember(location) {
            if (location.isBlank()) suggestedPlaces else suggestedPlaces.filter { it.contains(location, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showLocationModal = false },
            title = { Text("Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = { Text("Search places or venues…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    filteredPlaces.forEach { place ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { location = place }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = NexoraCyanGlow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(place, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 4. Add to Playlists Modal (with "Create new playlist" action)
    if (showPlaylistModal) {
        val availablePlaylists = remember {
            mutableStateListOf("Uploads", "Favorites", "Watch Later", "Zentora 4K Master", "Dev Series")
        }
        var newPlaylistName by remember { mutableStateOf("") }
        var showCreatePlaylistField by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPlaylistModal = false },
            title = { Text("Add to playlists", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availablePlaylists.forEach { pl ->
                        val isChecked = selectedPlaylists.contains(pl)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedPlaylists.remove(pl) else selectedPlaylists.add(pl)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { check ->
                                    if (check) selectedPlaylists.add(pl) else selectedPlaylists.remove(pl)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pl, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    if (showCreatePlaylistField) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                placeholder = { Text("Playlist title") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistName.isNotBlank()) {
                                        val name = newPlaylistName.trim()
                                        if (!availablePlaylists.contains(name)) {
                                            availablePlaylists.add(name)
                                        }
                                        selectedPlaylists.add(name)
                                        newPlaylistName = ""
                                        showCreatePlaylistField = false
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
                            ) {
                                Text("Add", fontSize = 12.sp)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showCreatePlaylistField = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create new playlist", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 5. Shorts Remixing Permissions Modal
    if (showRemixingModal) {
        val remixOptions = listOf(
            Pair("Allow video and audio remixing", "Others can create Shorts using parts of this video"),
            Pair("Allow only audio remixing", "Others can create Shorts using only the sound from this video"),
            Pair("Don't allow remixing", "Others can't create Shorts using this video")
        )
        AlertDialog(
            onDismissRequest = { showRemixingModal = false },
            title = { Text("Shorts Remixing", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    remixOptions.forEach { (opt, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { shortsRemixing = opt }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(selected = shortsRemixing == opt, onClick = { shortsRemixing = opt })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(opt, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(desc, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRemixingModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 6. Comments & Moderation Settings Modal
    if (showCommentsModal) {
        val commentModerationOptions = listOf(
            "Basic - Hold potentially inappropriate comments for review",
            "Strict - Increase strictness",
            "Hold all comments for review"
        )
        AlertDialog(
            onDismissRequest = { showCommentsModal = false },
            title = { Text("Comments & Ratings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Comments", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    // Radio: ON
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (commentsModeration == "Off") {
                                    commentsModeration = "Basic - Hold potentially inappropriate comments for review"
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = commentsModeration != "Off",
                            onClick = {
                                if (commentsModeration == "Off") {
                                    commentsModeration = "Basic - Hold potentially inappropriate comments for review"
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("On", fontWeight = FontWeight.Bold)
                    }

                    if (commentsModeration != "Off") {
                        Column(modifier = Modifier.padding(start = 28.dp)) {
                            commentModerationOptions.forEach { opt ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { commentsModeration = opt }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(selected = commentsModeration == opt, onClick = { commentsModeration = opt })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(opt, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Radio: OFF
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { commentsModeration = "Off" }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = commentsModeration == "Off", onClick = { commentsModeration = "Off" })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Off (Disable comments)", fontSize = 13.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLikesCount = !showLikesCount }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = showLikesCount, onCheckedChange = { showLikesCount = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Show how many viewers like this video", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommentsModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 8. Tags, Category & Language Modal (Full official categories)
    if (showAttributesModal) {
        val categories = listOf(
            "Film & Animation",
            "Autos & Vehicles",
            "Music",
            "Pets & Animals",
            "Sports",
            "Travel & Events",
            "Gaming",
            "People & Blogs",
            "Comedy",
            "Entertainment",
            "News & Politics",
            "Howto & Style",
            "Education",
            "Science & Technology"
        )
        val languages = listOf("English", "Spanish", "Japanese", "German", "Bengali", "French", "Hindi", "Mandarin")

        AlertDialog(
            onDismissRequest = { showAttributesModal = false },
            title = { Text("Tags, Category & Language", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tags (comma separated)", fontSize = 12.sp, color = Color.Gray)
                        Text("${tagsInput.length}/500", fontSize = 11.sp, color = Color.Gray)
                    }
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = {
                            if (it.length <= 500) tagsInput = it
                        },
                        placeholder = { Text("zentora, stream, 4k, tech…") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Category", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Video Language", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(languages) { lang ->
                            FilterChip(
                                selected = videoLanguage == lang,
                                onClick = { videoLanguage = lang },
                                label = { Text(lang, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttributesModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // 9. License & Distribution Modal
    if (showLicenseModal) {
        val licenses = listOf("Standard Nexora Stream License", "Creative Commons - Attribution")
        AlertDialog(
            onDismissRequest = { showLicenseModal = false },
            title = { Text("Licensing & Distribution", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("License", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    licenses.forEach { l ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { licenseType = l }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = licenseType == l, onClick = { licenseType = l })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(l, fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { allowEmbedding = !allowEmbedding }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = allowEmbedding, onCheckedChange = { allowEmbedding = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Allow embedding", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Enables or disables external player playback", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { notifySubscribers = !notifySubscribers }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = notifySubscribers, onCheckedChange = { notifySubscribers = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Publish to Subscriptions feed and notify subscribers", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Subscribers will get a notification when published", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseModal = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }
}

// =================================================================================
// 1. AUTOMATIC IN-APP 3x3 VIDEO GALLERY GRID (TASK 1 - EXACT YOUTUBE STYLE)
// =================================================================================
@Composable
fun VideoGalleryPickerView(
    videos: List<LocalDeviceVideoItem>,
    onClose: () -> Unit,
    onSelectVideo: (LocalDeviceVideoItem) -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(top = 16.dp)
    ) {
        // Top Bar: (X) Close + "Upload from gallery" + "Videos ⌵"
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

            // Dropdown menu chip: "Videos ⌵"
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF222222),
                    modifier = Modifier.clickable { showDropdownMenu = true }
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

                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false },
                    modifier = Modifier.background(Color(0xFF1E1E1E))
                ) {
                    DropdownMenuItem(
                        text = { Text("Videos (${videos.size})", color = Color.White) },
                        onClick = { showDropdownMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Recent Media", color = Color.LightGray) },
                        onClick = { showDropdownMenu = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // In-App Edge-to-Edge 3-column square grid
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No videos detected on device storage",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Grant media permissions to list your gallery videos",
                        color = Color.DarkGray,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(videos, key = { it.id }) { videoItem ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.5.dp)
                            .background(Color(0xFF222222))
                            .clickable { onSelectVideo(videoItem) }
                    ) {
                        AsyncImage(
                            model = videoItem.uri,
                            contentDescription = videoItem.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Duration pill badge on bottom-right corner (e.g. "0:10", "1:45:12")
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = formatMsToTime(videoItem.durationMs),
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
}

// =================================================================================
// 2. SHORT CAMERA VIEWFINDER (Live Camera Preview with Permissions + Gallery Button)
// =================================================================================
@Composable
fun ShortCameraViewfinderView(
    onClose: () -> Unit,
    onAddGallery: () -> Unit,
    onRecord: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1418)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Camera viewfinder ready", color = Color.White, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        }

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

        // Right Vertical Tool Rail: Flip, Timer, 15 s, Effects, 1x, Layout, More
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
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White)
                }
                Icon(Icons.Outlined.Timer, contentDescription = "Timer", tint = Color.White, modifier = Modifier.size(24.dp))
                Text("15 s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Effects", tint = Color.White, modifier = Modifier.size(24.dp))
                Text("1x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ViewQuilt, contentDescription = "Layout", tint = Color.White, modifier = Modifier.size(24.dp))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "More", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // Bottom Controls: Functional "Add from Gallery" button at bottom-left & Red Record Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 100.dp, start = 30.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // "Add from Gallery" Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAddGallery() }
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Add from Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Add", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Big Red Circle Record Button
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

            Spacer(modifier = Modifier.size(46.dp))
        }
    }
}

// =================================================================================
// 3. SHORT TRIMMER & EDIT VIEW
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
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
// 4. LIVE BROADCASTER PREVIEW
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
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White)
                }
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White)
                }
            }
        }

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
                IconButton(onClick = onFlip, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip", tint = Color.White)
                }
                IconButton(onClick = onMute, modifier = Modifier.size(24.dp)) {
                    Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mic", tint = Color.White)
                }
                IconButton(onClick = onHideVideo, modifier = Modifier.size(24.dp)) {
                    Icon(if (isVideoHidden) Icons.Filled.VideocamOff else Icons.Filled.Videocam, contentDescription = "Video", tint = Color.White)
                }
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Filters", tint = Color.White, modifier = Modifier.size(24.dp))
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150" },
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNext,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Go Live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// =================================================================================
// 5. CREATE POST VIEW
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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
            Text("Create post", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            Button(
                onClick = onPublishPost,
                enabled = postText.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
            ) {
                Text("Post", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = postText,
            onValueChange = onPostTextChange,
            placeholder = { Text("Post an update to your fans…", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        if (postImageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = postImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (showPoll) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pollOption1,
                    onValueChange = onPoll1Change,
                    placeholder = { Text("Poll Option 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pollOption2,
                    onValueChange = onPoll2Change,
                    placeholder = { Text("Poll Option 2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onPickImage,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF222222), CircleShape)
            ) {
                Icon(Icons.Filled.Image, contentDescription = "Image", tint = Color.White)
            }
            IconButton(
                onClick = onTogglePoll,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF222222), CircleShape)
            ) {
                Icon(Icons.Filled.Poll, contentDescription = "Poll", tint = Color.White)
            }
        }
    }
}

// =================================================================================
// 6. ADD DETAILS METADATA SUITE (COMPLETE YOUTUBE-STYLE CONTROLS)
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
    isInstantPremiere: Boolean,
    scheduledDateTimeText: String,
    onOpenVisibility: () -> Unit,
    isMadeForKids: Boolean,
    isAgeRestricted: Boolean,
    onOpenAudience: () -> Unit,
    description: String,
    onOpenDescription: () -> Unit,
    location: String,
    onOpenLocation: () -> Unit,
    selectedPlaylists: List<String>,
    onOpenPlaylists: () -> Unit,
    containsPaidPromotion: Boolean,
    onTogglePaidPromotion: () -> Unit,
    shortsRemixing: String,
    onOpenRemixing: () -> Unit,
    commentsModeration: String,
    showLikesCount: Boolean,
    onOpenComments: () -> Unit,
    tags: String,
    category: String,
    videoLanguage: String,
    onOpenAttributes: () -> Unit,
    licenseType: String,
    allowEmbedding: Boolean,
    notifySubscribers: Boolean,
    onOpenLicense: () -> Unit,
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

            // 1. Visibility Selector
            val visibilitySubtitle = buildString {
                append(visibility)
                if (isInstantPremiere) append(" • Instant Premiere")
                if (scheduledDateTimeText.isNotBlank()) append(" • Scheduled: $scheduledDateTimeText")
            }
            DetailSettingsRow(Icons.Outlined.Public, "Visibility", visibilitySubtitle, onClick = onOpenVisibility)

            // 2. Audience & COPPA Compliance
            val audienceSubtitle = if (isMadeForKids) "Yes, it's Made for Kids" else "No, it's not Made for Kids" + if (isAgeRestricted) " • 18+ Restricted" else ""
            DetailSettingsRow(Icons.Outlined.Group, "Audience", audienceSubtitle, onClick = onOpenAudience)

            // Description Row
            DetailSettingsRow(
                Icons.Outlined.Subject,
                "Description",
                if (description.isNotBlank()) description.take(35) + "…" else "Add description",
                onClick = onOpenDescription
            )

            // 3. Location Selector
            DetailSettingsRow(
                Icons.Outlined.Place,
                "Location",
                if (location.isNotBlank()) location else "Add location",
                onClick = onOpenLocation
            )

            // 4. Add to Playlists
            val playlistsSubtitle = if (selectedPlaylists.isNotEmpty()) selectedPlaylists.joinToString(", ") else null
            DetailSettingsRow(
                Icons.Outlined.PlaylistAdd,
                "Add to playlists",
                playlistsSubtitle,
                isAdd = selectedPlaylists.isEmpty(),
                onClick = onOpenPlaylists
            )

            // 5. Shorts Remixing Permissions
            DetailSettingsRow(
                Icons.Outlined.GraphicEq,
                "Shorts remixing",
                shortsRemixing,
                onClick = onOpenRemixing
            )

            // 6. Comments & Moderation Settings
            val commentsSubtitle = "$commentsModeration • ${if (showLikesCount) "Likes visible" else "Likes hidden"}"
            DetailSettingsRow(
                Icons.Outlined.ChatBubbleOutline,
                "Comments",
                commentsSubtitle,
                onClick = onOpenComments
            )

            // 7. Paid Promotion Checkbox Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTogglePaidPromotion() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Paid, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Paid promotion", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text("My video contains paid promotion (sponsorship/endorsement)", fontSize = 12.sp, color = Color.Gray)
                }
                Checkbox(checked = containsPaidPromotion, onCheckedChange = { onTogglePaidPromotion() })
            }

            if (showMore) {
                // 8. Tags, Category & Language
                DetailSettingsRow(
                    Icons.Outlined.Article,
                    "Category, Tags & Language",
                    "$category • $videoLanguage • $tags",
                    onClick = onOpenAttributes
                )

                // 9. License & Distribution
                val licenseSubtitle = "$licenseType • ${if (allowEmbedding) "Embedding on" else "Embedding off"}"
                DetailSettingsRow(
                    Icons.Outlined.Gavel,
                    "License and distribution",
                    licenseSubtitle,
                    onClick = onOpenLicense
                )
            }

            // "Show more" / "Show less" toggle button
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

            // COPPA Compliance Legal Notice
            Text(
                text = "Regardless of your location, you're legally required to comply with the US Children's Online Privacy Protection Act (COPPA) and/or other laws. You're required to tell us whether your videos are Made for Kids.",
                fontSize = 11.sp,
                color = Color.Gray,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (isUploading) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Uploading video…", fontSize = 12.sp, color = Color.White)
                        Text("${(uploadProgress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NexoraRed)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NexoraRed
                    )
                }
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
// 7. EDIT THUMBNAIL VIEW
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
