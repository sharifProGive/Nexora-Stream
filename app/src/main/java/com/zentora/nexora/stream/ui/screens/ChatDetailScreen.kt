/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.database.entities.CHAT_SEND_STATE_FAILED
import com.zentora.nexora.stream.data.database.entities.CHAT_SEND_STATE_PENDING
import com.zentora.nexora.stream.data.database.entities.PrivateChatMessageEntity
import com.zentora.nexora.stream.data.database.entities.PrivateFriendEntity
import com.zentora.nexora.stream.data.repository.ChatActionResult
import com.zentora.nexora.stream.data.repository.ChatRepository
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val FIRST_PAGE = 60
private const val NEXT_PAGE = 60
private const val DAY_MS = 86_400_000L

/**
 * Private chat with one friend (Vault 35, sub-vault friend-chat). Messages come from and go to the Nexora server through
 * [ChatRepository]; Room holds the copy this screen shows, so it opens instantly and works offline.
 *
 * Every message of mine shows its real state: sending (clock), delivered (one tick), read by the friend (two blue
 * ticks) or not sent (red, with Retry and Delete). Files are added to this screen with the file-sending step.
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
    val chat = remember { ChatRepository.getInstance(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    BackHandler { onBack() }

    var friend by remember(friendHandle) { mutableStateOf<PrivateFriendEntity?>(null) }
    var limit by remember(friendHandle) { mutableIntStateOf(FIRST_PAGE) }
    var shown by remember(friendHandle) { mutableStateOf<List<PrivateChatMessageEntity>>(emptyList()) }
    var input by remember(friendHandle) { mutableStateOf("") }

    LaunchedEffect(friendHandle) {
        friend = repository.getPrivateFriend(friendHandle)
    }

    // The list keeps showing the old messages while a longer page is being loaded (no flash to empty).
    val messagesFlow = remember(friendHandle, limit) { chat.messages(friendHandle, limit) }
    LaunchedEffect(messagesFlow) {
        messagesFlow.collect { shown = it }
    }

    // While this screen is open, the friend's new messages are read at once and the friend gets the double tick.
    DisposableEffect(friendHandle) {
        chat.openConversation(friendHandle)
        onDispose { chat.closeConversation(friendHandle) }
    }
    val newestIncomingSeq = remember(shown) { shown.lastOrNull { !it.isFromMe }?.serverSeq ?: 0L }
    LaunchedEffect(friendHandle, newestIncomingSeq) {
        chat.markConversationRead(friendHandle)
    }

    val listState = rememberLazyListState()

    // Follow new messages only while the user is at the bottom; a user who scrolled up to read is not pulled down.
    var followBottom by remember(friendHandle) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followBottom = !listState.canScrollForward
        }
    }
    val newestId = shown.lastOrNull()?.messageId
    val newestIsMine = shown.lastOrNull()?.isFromMe == true
    LaunchedEffect(newestId) {
        if (newestId != null && (followBottom || newestIsMine)) {
            listState.scrollToItem(shown.lastIndex)
        }
    }

    // Older messages: when the user reaches the top and the page is full, load the next page.
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    LaunchedEffect(atTop, shown.size) {
        if (atTop && shown.isNotEmpty() && shown.size >= limit) limit += NEXT_PAGE
    }

    fun report(result: ChatActionResult) {
        when (result) {
            ChatActionResult.Done -> Unit
            is ChatActionResult.Queued -> scope.launch { snackbar.showSnackbar(result.message) }
            is ChatActionResult.Failed -> scope.launch { snackbar.showSnackbar(result.message) }
            is ChatActionResult.Rejected -> scope.launch { snackbar.showSnackbar(result.message) }
        }
    }

    fun submit() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        followBottom = true
        scope.launch {
            val result = chat.send(friendHandle, text)
            // Nothing was stored: give the text back instead of losing it.
            if (result is ChatActionResult.Rejected && input.isEmpty()) input = text
            report(result)
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
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFF0F0F0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(shown, key = { _, message -> message.messageId }) { index, message ->
                        val previous = shown.getOrNull(index - 1)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (previous == null || dayNumber(previous.timestamp) != dayNumber(message.timestamp)) {
                                DayLabel(message.timestamp)
                            }
                            ChatMessageBubble(
                                message = message,
                                onRetry = { scope.launch { report(chat.retry(message.messageId)) } },
                                onDiscard = { scope.launch { chat.discardUnsent(message.messageId) } }
                            )
                        }
                    }
                }

                if (shown.isEmpty()) {
                    Text(
                        text = "No messages yet.\nSay hello to $friendName.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(ChatRepository.MAX_MESSAGE_CHARS) },
                    placeholder = { Text("Message", fontSize = 14.sp, color = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

                val canSend = input.isNotBlank()
                FilledIconButton(
                    onClick = { submit() },
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF2BA640),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF333333),
                        disabledContentColor = Color.Gray
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

/** Days since 1970 in the phone's time zone, so two messages of the same calendar day get the same number. */
private fun dayNumber(timestamp: Long): Long = (timestamp + TimeZone.getDefault().getOffset(timestamp)) / DAY_MS

@Composable
private fun DayLabel(timestamp: Long) {
    val label = remember(timestamp) {
        val today = dayNumber(System.currentTimeMillis())
        when (today - dayNumber(timestamp)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))
        }
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF202C33)) {
            Text(
                text = label,
                color = Color.LightGray,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: PrivateChatMessageEntity,
    onRetry: () -> Unit,
    onDiscard: () -> Unit
) {
    val isFromMe = message.isFromMe
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }
    val failed = isFromMe && message.sendState == CHAT_SEND_STATE_FAILED
    val pending = isFromMe && message.sendState == CHAT_SEND_STATE_PENDING

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
            color = if (isFromMe) Color(0xFF005C4B) else Color(0xFF202C33),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                        when {
                            failed -> Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Not sent",
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(14.dp)
                            )
                            pending -> Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Sending",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            message.isRead -> Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Read",
                                tint = Color(0xFF53BDEB),
                                modifier = Modifier.size(14.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Delivered",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (failed) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Not sent", color = Color(0xFFFF453A), fontSize = 11.sp)
                        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Retry", color = Color(0xFF53BDEB), fontSize = 12.sp)
                        }
                        TextButton(onClick = onDiscard, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Delete", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
