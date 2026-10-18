/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.NexoraStreamBottomNavBar
import com.zentora.nexora.stream.ui.components.NexoraStreamTopAppBar
import com.zentora.nexora.stream.ui.screens.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * NexoraNavHost coordinates the primary application navigation graph:
 * - Dedicated Top Notification Bell routing to "notifications_screen".
 * - Post-upload redirect to "manage_videos" Studio tracker.
 * - Clean 5-tab Bottom Navigation (Home, Shorts, Upload, Subscriptions, You).
 * - Friends Gateway linked directly from YouProfileScreen.
 */
@Composable
fun NexoraNavHost(
    repository: NexoraStreamRepository,
    isKidsMode: Boolean = false,
    deepLinkVideoId: MutableState<String?> = remember { mutableStateOf(null) },
    deepLinkChannelHandle: MutableState<String?> = remember { mutableStateOf(null) },
    incomingCallHandle: MutableState<String?> = remember { mutableStateOf(null) },
    incomingCallIsVideo: MutableState<Boolean> = remember { mutableStateOf(false) },
    onActiveVideoChange: (String?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val userSession by NexoraIdManager.sessionFlow.collectAsState()

    var currentRoute by remember { mutableStateOf("home") }
    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    // Private Friends & Calling Session State
    var activeChatHandle by remember { mutableStateOf<String?>(null) }
    var activeCallPeerName by remember { mutableStateOf<String?>("Nexora Contact") }
    var activeCallPeerHandle by remember { mutableStateOf<String?>("@contact") }
    var activeCallIsVideo by remember { mutableStateOf(false) }
    var activeCallIsIncoming by remember { mutableStateOf(false) }

    // Shared UploadStudioViewModel for upload streaming pipeline and ManageVideos tracking
    val uploadViewModel = remember { UploadStudioViewModel(repository) }

    // Deep-link triggers
    val dlVideoId by deepLinkVideoId
    val dlChannelHandle by deepLinkChannelHandle
    val incCallHandle by incomingCallHandle

    // Incoming Call intent trigger
    LaunchedEffect(incCallHandle) {
        incCallHandle?.let { handle ->
            activeCallPeerHandle = handle
            activeCallPeerName = handle.removePrefix("@").replaceFirstChar { it.uppercase() }
            activeCallIsVideo = incomingCallIsVideo.value
            activeCallIsIncoming = true
            currentRoute = "call_screen"
            incomingCallHandle.value = null
        }
    }

    LaunchedEffect(dlVideoId) {
        dlVideoId?.let { vId ->
            selectedVideoId = vId
            currentRoute = "player"
            deepLinkVideoId.value = null
        }
    }

    LaunchedEffect(dlChannelHandle) {
        dlChannelHandle?.let { handle ->
            scope.launch {
                val channels = repository.allChannels.firstOrNull() ?: emptyList()
                val cleanHandle = handle.removePrefix("@").lowercase()
                val matched = channels.find { ch ->
                    val chHandle = ch.channelName.lowercase().replace(" ", "_")
                    chHandle == cleanHandle || ch.channelId.equals(cleanHandle, ignoreCase = true)
                }
                selectedChannelId = matched?.channelId ?: "ch_zentora_core"
                currentRoute = "channel"
                deepLinkChannelHandle.value = null
            }
        }
    }

    LaunchedEffect(selectedVideoId) {
        onActiveVideoChange(selectedVideoId)
    }

    // Handle back press on sub-routes
    if (currentRoute != "home") {
        BackHandler {
            when (currentRoute) {
                "player" -> {
                    selectedVideoId = null
                    currentRoute = "home"
                }
                "channel" -> {
                    currentRoute = "you"
                }
                "manage_videos" -> {
                    currentRoute = "you"
                }
                "notifications_screen", "notifications" -> {
                    currentRoute = "home"
                }
                "upload" -> {
                    currentRoute = "home"
                }
                "search" -> {
                    currentRoute = "home"
                }
                "settings" -> {
                    currentRoute = "you"
                }
                "chat_detail" -> {
                    currentRoute = "friends_gateway"
                }
                "call_screen" -> {
                    currentRoute = if (activeChatHandle != null) "chat_detail" else "friends_gateway"
                }
                "friends_gateway", "friends" -> {
                    currentRoute = "you"
                }
                "you" -> {
                    currentRoute = "home"
                }
                else -> currentRoute = "home"
            }
        }
    }

    // TopBar & BottomBar visibility logic
    val showTopBar = currentRoute in listOf("home", "subscriptions", "you")
    val showBottomBar = currentRoute in listOf("home", "shorts", "subscriptions", "you")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // ইউটিউবের মতো এজ-টু-এজ ডিসপ্লে এবং ইনসেটস হ্যান্ডলিং
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                NexoraStreamTopAppBar(
                    onSearchClick = { currentRoute = "search" },
                    onCastClick = { currentRoute = "you" },
                    onNotificationClick = { currentRoute = "notifications_screen" },
                    isKidsMode = isKidsMode,
                    userAvatarUri = userSession.avatarUri
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                // ৩-বাটন নেভিগেশন বারের সাথে ওভারল্যাপ রোধ করতে navigationBarsPadding যুক্ত করা হয়েছে
                Box(modifier = Modifier.navigationBarsPadding()) {
                    NexoraStreamBottomNavBar(
                        currentRoute = currentRoute,
                        userAvatarUri = userSession.avatarUri,
                        onNavigate = { route -> currentRoute = route }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentRoute) {
                "home" -> {
                    HomeScreen(
                        repository = repository,
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        },
                        onChannelClick = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        }
                    )
                }
                "shorts" -> {
                    ShortsScreen(
                        repository = repository,
                        onChannelClick = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        }
                    )
                }
                "upload", "create" -> {
                    UploadStudioScreen(
                        repository = repository,
                        uploadViewModel = uploadViewModel,
                        onUploadComplete = { currentRoute = "manage_videos" },
                        onNavigateToManageVideos = { currentRoute = "manage_videos" },
                        onClose = { currentRoute = "home" }
                    )
                }
                "subscriptions" -> {
                    SubscriptionsScreen(
                        repository = repository,
                        onChannelClick = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        },
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        }
                    )
                }
                "friends_gateway", "friends" -> {
                    FriendsGatewayScreen(
                        repository = repository,
                        onOpenChat = { handle ->
                            activeChatHandle = handle
                            currentRoute = "chat_detail"
                        },
                        onStartCall = { name, handle, isVideo ->
                            activeCallPeerName = name
                            activeCallPeerHandle = handle
                            activeCallIsVideo = isVideo
                            activeCallIsIncoming = false
                            currentRoute = "call_screen"
                        }
                    )
                }
                "chat_detail" -> {
                    ChatDetailScreen(
                        friendHandle = activeChatHandle ?: "@friend",
                        repository = repository,
                        onBack = { currentRoute = "friends_gateway" },
                        onStartCall = { name, handle, isVideo ->
                            activeCallPeerName = name
                            activeCallPeerHandle = handle
                            activeCallIsVideo = isVideo
                            activeCallIsIncoming = false
                            currentRoute = "call_screen"
                        }
                    )
                }
                "call_screen" -> {
                    CallScreen(
                        peerName = activeCallPeerName ?: "Nexora Contact",
                        peerHandle = activeCallPeerHandle ?: "@contact",
                        isVideoCall = activeCallIsVideo,
                        isIncoming = activeCallIsIncoming,
                        onCallEnded = {
                            currentRoute = if (activeChatHandle != null) "chat_detail" else "friends_gateway"
                        }
                    )
                }
                "you" -> {
                    YouProfileScreen(
                        repository = repository,
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        },
                        onViewChannel = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        },
                        onSettingsClick = { currentRoute = "settings" },
                        onNotificationClick = { currentRoute = "notifications_screen" },
                        onManageVideos = { currentRoute = "manage_videos" }
                    )
                }
                "channel" -> {
                    ChannelScreen(
                        channelId = selectedChannelId ?: "ch_zentora_core",
                        repository = repository,
                        onBack = { currentRoute = "you" },
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        },
                        onManageVideos = { currentRoute = "manage_videos" }
                    )
                }
                "manage_videos" -> {
                    ManageVideosScreen(
                        repository = repository,
                        uploadViewModel = uploadViewModel,
                        onBack = { currentRoute = "you" },
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        }
                    )
                }
                "notifications_screen", "notifications" -> {
                    NotificationsScreen(
                        repository = repository,
                        onBack = { currentRoute = "home" },
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        },
                        onChannelClick = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        }
                    )
                }
                "player" -> {
                    selectedVideoId?.let { vId ->
                        PlayerScreen(
                            videoId = vId,
                            repository = repository,
                            onBack = {
                                selectedVideoId = null
                                currentRoute = "home"
                            },
                            onNavigateVideo = { newId ->
                                selectedVideoId = newId
                            },
                            onChannelClick = { chId ->
                                selectedChannelId = chId
                                currentRoute = "channel"
                            }
                        )
                    }
                }
                "search" -> {
                    SearchScreen(
                        repository = repository,
                        onBack = { currentRoute = "home" },
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        }
                    )
                }
                "settings" -> {
                    SettingsScreen(
                        repository = repository,
                        onBack = { currentRoute = "you" }
                    )
                }
                else -> {
                    HomeScreen(
                        repository = repository,
                        onVideoClick = { vId ->
                            selectedVideoId = vId
                            currentRoute = "player"
                        },
                        onChannelClick = { chId ->
                            selectedChannelId = chId
                            currentRoute = "channel"
                        }
                    )
                }
            }
        }
    }
}
