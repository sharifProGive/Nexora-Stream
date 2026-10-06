/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.NexoraStreamBottomNavBar
import com.zentora.nexora.stream.ui.components.NexoraStreamTopAppBar
import com.zentora.nexora.stream.ui.screens.*
import com.zentora.nexora.stream.ui.theme.NexoraStreamTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: NexoraStreamRepository
    private var activePlayingVideoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = NexoraStreamRepository.getInstance(applicationContext)

        setContent {
            val isKidsMode by repository.isKidsMode.collectAsState()
            val userSession = remember { NexoraIdManager.getSession() }

            NexoraStreamTheme(darkTheme = true) {
                var currentRoute by remember { mutableStateOf("home") }
                var selectedVideoId by remember { mutableStateOf<String?>(null) }
                var selectedChannelId by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(selectedVideoId) {
                    activePlayingVideoId = selectedVideoId
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
                            "upload" -> {
                                currentRoute = "home"
                            }
                            else -> currentRoute = "home"
                        }
                    }
                }

                val showTopBar = currentRoute in listOf("home", "subscriptions", "you")
                val showBottomBar = currentRoute in listOf("home", "shorts", "subscriptions", "you")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showTopBar) {
                            NexoraStreamTopAppBar(
                                onSearchClick = { currentRoute = "search" },
                                onCastClick = { currentRoute = "you" },
                                onNotificationClick = { currentRoute = "you" },
                                isKidsMode = isKidsMode,
                                userAvatarUri = userSession.avatarUri
                            )
                        }
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            NexoraStreamBottomNavBar(
                                currentRoute = currentRoute,
                                userAvatarUri = userSession.avatarUri,
                                onNavigate = { route -> currentRoute = route }
                            )
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
                            "upload" -> {
                                UploadStudioScreen(
                                    repository = repository,
                                    onUploadComplete = { currentRoute = "you" },
                                    onClose = { currentRoute = "home" }
                                )
                            }
                            "subscriptions" -> {
                                SubscriptionsScreen(
                                    repository = repository,
                                    onChannelClick = { chId ->
                                        selectedChannelId = chId
                                        currentRoute = "channel"
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
                                    onSettingsClick = { currentRoute = "settings" }
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
                                    onBack = { currentRoute = "home" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (activePlayingVideoId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
