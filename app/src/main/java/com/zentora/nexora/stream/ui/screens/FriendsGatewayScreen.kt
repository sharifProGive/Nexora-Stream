/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository

/**
 * Friends Tab Gateway (Pillar 4: Dual-Identity Architecture).
 * Checks if a separate Private Friend Profile exists in the isolated local enclave:
 * - If profile does not exist: opens PrivateProfileSetupScreen.kt.
 * - If profile exists: opens FriendsScreen.kt.
 */
@Composable
fun FriendsGatewayScreen(
    repository: NexoraStreamRepository,
    onOpenChat: (String) -> Unit,
    onStartCall: (name: String, handle: String, isVideo: Boolean) -> Unit
) {
    val privateProfile by repository.privateProfile.collectAsState(initial = null)
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(privateProfile) {
        // Small check delay for initial Room DB flow emission
        kotlinx.coroutines.delay(100)
        isChecking = false
    }

    if (isChecking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF3EA6FF))
        }
    } else if (privateProfile == null) {
        PrivateProfileSetupScreen(
            repository = repository,
            onProfileCreated = {
                // Profile created, state automatically advances to FriendsScreen
            }
        )
    } else {
        FriendsScreen(
            repository = repository,
            onOpenChat = onOpenChat,
            onStartCall = onStartCall
        )
    }
}
