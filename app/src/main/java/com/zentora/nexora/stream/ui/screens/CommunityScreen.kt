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
import androidx.compose.material.icons.outlined.ThumbUp
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
import com.zentora.nexora.stream.data.database.entities.CommunityPostEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Feature 21: Community Posts Tab supporting text updates, photos, and interactive polls.
 */
@Composable
fun CommunityScreen(
    repository: NexoraStreamRepository,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val posts by repository.allCommunityPosts.collectAsState(initial = emptyList())
    val allChannels by repository.allChannels.collectAsState(initial = emptyList())

    var showCreatePostDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreatePostDialog = true },
                containerColor = NexoraRed,
                contentColor = Color.White,
                modifier = Modifier.testTag("create_community_post_fab")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New Post")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("community_screen")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Forum, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Community Feed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Creator polls, updates & discussions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No community posts yet", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap the floating button to publish a post or poll!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(posts, key = { it.postId }) { post ->
                        val author = allChannels.find { it.channelId == post.channelId }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Author Header
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AsyncImage(
                                        model = author?.avatarUri ?: "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150",
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onChannelClick(post.channelId) },
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(author?.channelName ?: "Zentora Creator", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (author?.hasZentoraBadge == true) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NexoraVerifiedTick, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                        Text("Community Update", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Content Text
                                Text(text = post.contentText, style = MaterialTheme.typography.bodyLarge)

                                // Image if attached
                                if (!post.imageUri.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                    ) {
                                        AsyncImage(
                                            model = post.imageUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }

                                // Interactive Poll if attached
                                if (!post.pollOptionsJson.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    PollSection(pollJson = post.pollOptionsJson!!)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))

                                // Upvote action
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { scope.launch { repository.togglePostUpvote(post) } }
                                ) {
                                    Icon(
                                        imageVector = if (post.isUpvotedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = "Upvote",
                                        tint = if (post.isUpvotedByMe) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${post.upvotes} Upvotes",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (post.isUpvotedByMe) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Community Post Dialog
    if (showCreatePostDialog) {
        CreatePostDialog(
            onDismiss = { showCreatePostDialog = false },
            onPublish = { content, imageUri, pollOptions ->
                scope.launch {
                    val pollJson = if (pollOptions.isNotEmpty()) {
                        val arr = JSONArray()
                        pollOptions.forEach { opt ->
                            val obj = JSONObject()
                            obj.put("text", opt)
                            obj.put("votes", 0)
                            arr.put(obj)
                        }
                        arr.toString()
                    } else null

                    repository.createCommunityPost(
                        channelId = "ch_zentora_core",
                        contentText = content,
                        imageUri = imageUri,
                        pollOptionsJson = pollJson
                    )
                    showCreatePostDialog = false
                    Toast.makeText(context, "Community post published!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun PollSection(pollJson: String) {
    var selectedOptionIndex by remember { mutableStateOf<Int?>(null) }
    val options = remember(pollJson) {
        try {
            val arr = JSONArray(pollJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getJSONObject(i).optString("text"))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { index, optionText ->
            val isSelected = selectedOptionIndex == index
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) NexoraRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedOptionIndex = index }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(optionText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    if (isSelected) {
                        Text("Selected ✓", color = NexoraRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPublish: (content: String, imageUri: String?, pollOptions: List<String>) -> Unit
) {
    var textContent by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    var pollOption1 by remember { mutableStateOf("") }
    var pollOption2 by remember { mutableStateOf("") }
    var isPollMode by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedImageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Community Post", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text("What's on your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedImageUri != null) "Photo Added ✓" else "Add Photo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { isPollMode = !isPollMode },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPollMode) NexoraRed else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Poll, contentDescription = null, tint = if (isPollMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Poll", color = if (isPollMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }

                if (isPollMode) {
                    OutlinedTextField(
                        value = pollOption1,
                        onValueChange = { pollOption1 = it },
                        label = { Text("Poll Option 1") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pollOption2,
                        onValueChange = { pollOption2 = it },
                        label = { Text("Poll Option 2") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val polls = if (isPollMode && pollOption1.isNotBlank() && pollOption2.isNotBlank()) {
                        listOf(pollOption1.trim(), pollOption2.trim())
                    } else emptyList()
                    onPublish(textContent.trim(), selectedImageUri, polls)
                },
                enabled = textContent.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
            ) {
                Text("Publish", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
