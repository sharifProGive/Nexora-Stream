/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Videocam
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
import com.zentora.nexora.stream.data.database.entities.PrivateChatMessageEntity
import com.zentora.nexora.stream.data.database.entities.PrivateFriendEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style Private Chat Detail Screen (Pillar 4).
 * Voice & Video call buttons at the top, attachment (+) button on the left,
 * send button on the right, and encrypted local message store.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    friendHandle: String,
    repository: NexoraStreamRepository,
    onBack: () -> Unit,
    onStartCall: (name: String, handle: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }

    val myProfile by repository.privateProfile.collectAsState(initial = null)
    var friend by remember { mutableStateOf<PrivateFriendEntity?>(null) }
    val messages by repository.getMessagesForConversation(friendHandle).collectAsState(initial = emptyList())

    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    var attachedMediaUri by remember { mutableStateOf<String?>(null) }

    // Media Picker for Attachments (+)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedMediaUri = uri.toString()
            Toast.makeText(context, "Attachment ready", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(friendHandle) {
        friend = repository.getPrivateFriend(friendHandle)
        repository.markConversationAsRead(friendHandle)
    }

    // Scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val friendName = friend?.displayName ?: friendHandle.removePrefix("@")

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (friend?.avatarUri?.isNotBlank() == true) {
                                AsyncImage(
                                    model = friend?.avatarUri,
                                    contentDescription = friendName,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = friendName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = friendName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (friend?.isOnline == true) "Online" else friendHandle,
                                fontSize = 11.sp,
                                color = if (friend?.isOnline == true) Color(0xFF2BA640) else Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    // Voice Call Button
                    IconButton(
                        onClick = { onStartCall(friendName, friendHandle, false) },
                        modifier = Modifier.testTag("chat_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Voice Call",
                            tint = Color.White
                        )
                    }

                    // Video Call Button
                    IconButton(
                        onClick = { onStartCall(friendName, friendHandle, true) },
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Call",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages Conversation History
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isFromMe = msg.isFromMe
                    )
                }
            }

            // Attached Media Preview if selected
            if (!attachedMediaUri.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF282828))
                    ) {
                        AsyncImage(
                            model = attachedMediaUri,
                            contentDescription = "Attachment Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Media attached",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { attachedMediaUri = null }) {
                        Text("Remove", color = Color(0xFFFF453A), fontSize = 12.sp)
                    }
                }
            }

            // Bottom Input Bar: (+) Attachment on Left, Rounded Field, Send on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Attachment (+) Button
                IconButton(
                    onClick = { mediaPickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("chat_attachment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach Media",
                        tint = Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Middle Text Field
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Message", fontSize = 14.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFF2B2B2B),
                        unfocusedContainerColor = Color(0xFF2B2B2B)
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Right Send Button
                val canSend = textInput.isNotBlank() || attachedMediaUri != null
                FilledIconButton(
                    onClick = {
                        if (canSend) {
                            val sender = myProfile?.handle ?: "@me"
                            val toSendText = textInput.trim()
                            val toSendMedia = attachedMediaUri

                            scope.launch {
                                repository.sendChatMessage(
                                    conversationHandle = friendHandle,
                                    senderHandle = sender,
                                    text = toSendText.ifBlank { "Sent an attachment" },
                                    mediaUri = toSendMedia
                                )
                                textInput = ""
                                attachedMediaUri = null
                            }
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend) Color(0xFF2BA640) else Color(0xFF333333),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: PrivateChatMessageEntity,
    isFromMe: Boolean
) {
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isFromMe) 14.dp else 2.dp,
                bottomEnd = if (isFromMe) 2.dp else 14.dp
            ),
            color = if (isFromMe) Color(0xFF005C4B) else Color(0xFF202C33), // Authentic WhatsApp palette
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (!message.mediaUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        AsyncImage(
                            model = message.mediaUri,
                            contentDescription = "Attached Media",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatted,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Read",
                            tint = Color(0xFF53BDEB),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
