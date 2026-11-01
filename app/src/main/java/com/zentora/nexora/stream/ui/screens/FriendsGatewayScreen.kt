/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.SessionStore
import com.zentora.nexora.stream.ui.theme.PremiumIconBadge
import com.zentora.nexora.stream.ui.theme.premiumGlow

/**
 * Friends entry point. Friends belong to the signed-in Nexora account (the server holds the friend list), so there is
 * no separate phone-only profile to create any more: a signed-in user goes straight to [FriendsScreen], a guest is
 * told that an account is needed.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FriendsGatewayScreen(
    repository: NexoraStreamRepository,
    onOpenChat: (String) -> Unit,
    onStartCall: (name: String, handle: String, isVideo: Boolean) -> Unit
) {
    if (SessionStore.current() == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .premiumGlow()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PremiumIconBadge(
                    icon = Icons.Outlined.Lock,
                    badgeSize = 72.dp,
                    iconSize = 34.dp,
                    contentDescription = "Account required"
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Sign in to use Friends",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your friends are stored on your Nexora account, so they follow you to every phone you sign in on.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        FriendsScreen(
            onOpenChat = onOpenChat,
            onStartCall = onStartCall
        )
    }
}
