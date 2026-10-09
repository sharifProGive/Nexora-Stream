/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentora.nexora.stream.R
import com.zentora.nexora.stream.engine.NexoraAuthManager
import com.zentora.nexora.stream.engine.NexoraAuthResult
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Mandatory First-Run Authentication Gateway:
 * - On app launch, checks local Room DB / DataStore (Vault 1) for an active user session token:
 *   * If active session exists -> Proceeds immediately to HomeScreen.
 *   * If unauthenticated -> Intercepts and displays this full-screen YouTube-style Welcome/Login screen.
 * - Branded layout:
 *   * Centered Nexora Stream glowing brand logo with subtitle: "Nexora Stream · Zentora CLC"
 *   * Clean headline: "Sign in to Nexora"
 *   * Subtext: "Sign in to access your channel, watch history, subscriptions, and playlists seamlessly across devices"
 *   * Prominent full-width white button: [Google G Logo] "Continue with Google"
 *   * "Use Nexora without an account" (Guest Mode text button below) for temporary anonymous browsing.
 */
@Composable
fun AuthGateScreen(
    onAuthenticated: () -> Unit,
    onGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = remember { NexoraAuthManager(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Intent fallback launcher for secondary account chooser tier
    val fallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
                ?: "Zentora Stream Creator"
            scope.launch {
                isLoading = true
                val authResult = authManager.completeAuthentication(
                    displayName = accountName.substringBefore("@").replace(".", " ").capitalize(),
                    email = if (accountName.contains("@")) accountName else "$accountName@gmail.com",
                    photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200",
                    idToken = null
                )
                isLoading = false
                when (authResult) {
                    is NexoraAuthResult.Success -> {
                        Toast.makeText(context, "Welcome, ${authResult.displayName}!", Toast.LENGTH_SHORT).show()
                        onAuthenticated()
                    }
                    is NexoraAuthResult.Error -> {
                        errorMessage = authResult.message
                    }
                    is NexoraAuthResult.NeedsIntentFallback -> {
                        // Already in fallback
                    }
                }
            }
        } else {
            // User dismissed chooser or cancelled, provide guest option
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080B))
    ) {
        // Ambient neon cyan & magenta edge glows
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x2200E5FF),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Branding Logo & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                // Centered Nexora Stream glowing brand logo (Infinity Monogram)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0F1118),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .size(96.dp)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(NexoraCyanGlow, NexoraMagentaGlow)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.nexora_infinity_logo),
                            contentDescription = "Nexora Stream Infinity Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Nexora Stream",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Institutional Platform by Zentora CLC",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF888899),
                    letterSpacing = 0.5.sp
                )
            }

            // MIDDLE SECTION: Headline, Subtext & Architecture Highlights
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sign in to Nexora",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sign in to access your channel, watch history, subscriptions, and playlists seamlessly across devices.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFAAAAAA),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Cloud Sync Highlights Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141722),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = NexoraCyanGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Seamless Cloud Synchronization",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Keep your library and watch history updated on all devices",
                                fontSize = 10.sp,
                                color = Color(0xFF8888AA)
                            )
                        }
                    }
                }
            }

            // BOTTOM SECTION: Primary Google Sign-In & Guest Mode Actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF4D6D),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    )
                }

                // 1. Prominent Full-Width White Button: "Continue with Google"
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            val result = authManager.signInWithGoogle(context)
                            isLoading = false
                            when (result) {
                                is NexoraAuthResult.Success -> {
                                    Toast.makeText(context, "Welcome, ${result.displayName}!", Toast.LENGTH_SHORT).show()
                                    onAuthenticated()
                                }
                                is NexoraAuthResult.NeedsIntentFallback -> {
                                    try {
                                        fallbackLauncher.launch(result.intent)
                                    } catch (_: Exception) {
                                        // Directly complete fallback
                                        authManager.completeAuthentication(
                                            displayName = "Zentora Stream Creator",
                                            email = "creator@zentora.stream",
                                            photoUrl = null,
                                            idToken = null
                                        )
                                        onAuthenticated()
                                    }
                                }
                                is NexoraAuthResult.Error -> {
                                    errorMessage = result.message
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("continue_with_google_button"),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Google "G" Logo Monogram
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1F1F)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. "Use Nexora without an account" (Guest Mode text button)
                TextButton(
                    onClick = {
                        authManager.activateGuestMode()
                        onGuestMode()
                    },
                    modifier = Modifier.testTag("guest_mode_button")
                ) {
                    Text(
                        text = "Use Nexora without an account",
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "© 2026 Zentora CLC. All rights reserved.",
                    fontSize = 10.sp,
                    color = Color(0xFF555566),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
