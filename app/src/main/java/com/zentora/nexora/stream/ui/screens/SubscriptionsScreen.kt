/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Subscriptions
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
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Features 2, 3, 24: Subscriptions, Zentora Verified Badges, Creator Levels.
 */
@Composable
fun SubscriptionsScreen(
    repository: NexoraStreamRepository,
    onChannelClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channels by repository.allChannels.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("subscriptions_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Subscriptions, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Channels & Subscriptions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Official Zentora Creators & Live Subscriber Telemetry", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.channelId }) { channel ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar with Zentora Verified Badge (Feature 2)
                            Box(modifier = Modifier.clickable { onChannelClick(channel.channelId) }) {
                                AsyncImage(
                                    model = channel.avatarUri,
                                    contentDescription = channel.channelName,
                                    modifier = Modifier.size(52.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                if (channel.hasZentoraBadge) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(NexoraVerifiedTick),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = "Zentora Verified", tint = Color.Black, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(channel.channelName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    if (channel.hasZentoraBadge) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = NexoraVerifiedTick.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "VERIFIED",
                                                color = NexoraVerifiedTick,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Feature 3: Creator Level System badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = when (channel.creatorLevel) {
                                            "Diamond Streamer" -> NexoraDiamondStreamer
                                            "Gold Creator" -> NexoraGoldCreator
                                            else -> NexoraRisingCreator
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = channel.creatorLevel,
                                        color = when (channel.creatorLevel) {
                                            "Diamond Streamer" -> NexoraDiamondStreamer
                                            "Gold Creator" -> NexoraGoldCreator
                                            else -> NexoraRisingCreator
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${channel.subscriberCount} Subscribers (Real-Time)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Subscribe Button (Feature 24)
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.toggleSubscribe(channel.channelId)
                                        val newState = !channel.isSubscribed
                                        Toast.makeText(
                                            context,
                                            if (newState) "Subscribed to ${channel.channelName}" else "Unsubscribed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (channel.isSubscribed) MaterialTheme.colorScheme.surface else NexoraRed
                                ),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (channel.isSubscribed) "Subscribed" else "Subscribe",
                                    color = if (channel.isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (channel.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(channel.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
