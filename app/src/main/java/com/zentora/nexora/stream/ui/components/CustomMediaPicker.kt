/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalDeviceVideoItem(
    val id: Long,
    val uri: Uri,
    val durationMs: Long,
    val displayName: String
)

/**
 * In-memory LRU thumbnail cache to avoid re-extracting frames during scrolling.
 */
object VideoThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available memory

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(uri: Uri): Bitmap? = cache.get(uri.toString())

    fun put(uri: Uri, bitmap: Bitmap) {
        cache.put(uri.toString(), bitmap)
    }
}

/**
 * Extracts 1.0-second video frame (1,000,000 microseconds) or ContentResolver thumbnail.
 */
suspend fun extractVideoFrameThumbnail(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    // 1. Check LRU Cache
    VideoThumbnailCache.get(videoUri)?.let { return@withContext it }

    var resultBitmap: Bitmap? = null

    // 2. On Android 10+ (Q+), use ContentResolver.loadThumbnail for maximum efficiency
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            resultBitmap = context.contentResolver.loadThumbnail(
                videoUri,
                Size(360, 360),
                null
            )
        } catch (_: Exception) {
            // Fallback to MediaMetadataRetriever
        }
    }

    // 3. MediaMetadataRetriever fallback (frame at 1.0s or 0s)
    if (resultBitmap == null) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            // 1,000,000 microseconds = 1.0 second
            resultBitmap = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    // 4. Cache and return
    resultBitmap?.let {
        VideoThumbnailCache.put(videoUri, it)
    }
    resultBitmap
}

/**
 * Queries real device videos from MediaStore via ContentResolver.
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
 * Custom Media Gallery Picker (3x3 Grid with real 1st-frame thumbnails, duration badge, instant selection).
 */
@Composable
fun CustomMediaPicker(
    videos: List<LocalDeviceVideoItem>,
    onClose: () -> Unit,
    onSelectVideo: (LocalDeviceVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
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
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("gallery_close_button")
            ) {
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
                        Text(
                            text = "Videos (${videos.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
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
                        text = "Grant media permissions or record a video to begin",
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
                    .padding(horizontal = 1.dp)
                    .testTag("media_picker_grid"),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(videos, key = { it.id }) { videoItem ->
                    VideoGridThumbnailTile(
                        videoItem = videoItem,
                        onClick = { onSelectVideo(videoItem) }
                    )
                }
            }
        }
    }
}

/**
 * Individual video grid tile with asynchronous 1st-frame thumbnail extraction and duration badge.
 */
@Composable
fun VideoGridThumbnailTile(
    videoItem: LocalDeviceVideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, key1 = videoItem.uri) {
        value = extractVideoFrameThumbnail(context, videoItem.uri)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
            .testTag("gallery_video_tile_${videoItem.id}")
    ) {
        val bitmap = thumbnailBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = videoItem.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Elegant loading placeholder with subtle play icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF444444),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Duration pill badge on bottom-right corner (e.g. "04:15")
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
