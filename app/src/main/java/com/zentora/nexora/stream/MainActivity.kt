/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NetworkConnectivityManager
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.NexoraNavHost
import com.zentora.nexora.stream.ui.screens.AuthGateScreen
import com.zentora.nexora.stream.ui.screens.OfflineBlockScreen
import com.zentora.nexora.stream.ui.theme.NexoraStreamTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: NexoraStreamRepository
    private var activePlayingVideoId: String? = null

    // Deep-link intent state observable by Compose
    private val deepLinkVideoId = mutableStateOf<String?>(null)
    private val deepLinkChannelHandle = mutableStateOf<String?>(null)
    private val incomingCallHandle = mutableStateOf<String?>(null)
    private val incomingCallIsVideo = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Vault 1 session and Room Database repository
        NexoraIdManager.initSession(applicationContext)
        repository = NexoraStreamRepository.getInstance(applicationContext)

        // Parse deep link and call intents from starting intent
        parseIntent(intent)

        setContent {
            val isKidsMode by repository.isKidsMode.collectAsState()

            // PILLAR 1: Real-time network listener and instant auto-resume
            val connectivityManager = remember { NetworkConnectivityManager(applicationContext) }
            val isOnline by connectivityManager.isOnline.collectAsState()

            NexoraStreamTheme(darkTheme = true) {
                // Hard block if offline: un-dismissible, blocks back press, auto-resumes smoothly when online
                if (!isOnline) {
                    OfflineBlockScreen(
                        onRetry = {
                            connectivityManager.checkConnectivityNow()
                        }
                    )
                } else {
                    // Check if user has an active session in Vault 1 (Room DB / DataStore / Prefs)
                    val hasInitialSession = remember { NexoraIdManager.hasActiveSession(applicationContext) }
                    var isAuthenticated by remember { mutableStateOf(hasInitialSession) }

                    if (!isAuthenticated) {
                        // Mandatory First-Run Authentication Gateway
                        AuthGateScreen(
                            onAuthenticated = {
                                isAuthenticated = true
                            },
                            onGuestMode = {
                                isAuthenticated = true
                            }
                        )
                    } else {
                        NexoraNavHost(
                            repository = repository,
                            isKidsMode = isKidsMode,
                            deepLinkVideoId = deepLinkVideoId,
                            deepLinkChannelHandle = deepLinkChannelHandle,
                            incomingCallHandle = incomingCallHandle,
                            incomingCallIsVideo = incomingCallIsVideo,
                            onActiveVideoChange = { vId ->
                                activePlayingVideoId = vId
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        if (intent == null) return

        // Incoming Call Intent handling
        val callHandle = intent.getStringExtra("CALL_HANDLE")
        if (!callHandle.isNullOrBlank()) {
            incomingCallHandle.value = callHandle
            incomingCallIsVideo.value = intent.getBooleanExtra("CALL_IS_VIDEO", false)
            return
        }

        val data: Uri? = intent.data
        if (data != null && intent.action == Intent.ACTION_VIEW) {
            val host = data.host?.lowercase()
            val path = data.path ?: ""

            when {
                // Video deep link: https://nexora.to/{videoId}
                host == "nexora.to" -> {
                    val segments = data.pathSegments
                    if (segments.isNotEmpty()) {
                        val videoId = segments[0]
                        if (videoId.isNotBlank()) {
                            deepLinkVideoId.value = videoId
                        }
                    }
                }
                // Channel deep link: https://nexora.stream/@{handle}
                host == "nexora.stream" -> {
                    val segments = data.pathSegments
                    if (segments.isNotEmpty()) {
                        val handle = segments[0]
                        if (handle.isNotBlank()) {
                            deepLinkChannelHandle.value = handle
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
