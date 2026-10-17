/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.engine.IncomingCallNotificationManager
import com.zentora.nexora.stream.engine.OngoingCallService

enum class CallUiStatus {
    INCOMING_RINGING,
    OUTGOING_DIALING,
    CONNECTED,
    ENDED
}

/**
 * Full-screen Phone & Video Call Screen (Pillar 3: Real Hardware Phone Calling Pipeline).
 * Controls real hardware audio via OngoingCallService (MODE_IN_COMMUNICATION) and
 * silences ringtones via IncomingCallNotificationManager.
 */
@Composable
fun CallScreen(
    peerName: String,
    peerHandle: String,
    peerAvatarUri: String = "",
    isVideoCall: Boolean = false,
    isIncoming: Boolean = false,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val notifManager = remember { IncomingCallNotificationManager.getInstance(context) }

    var callStatus by remember {
        mutableStateOf(if (isIncoming) CallUiStatus.INCOMING_RINGING else CallUiStatus.OUTGOING_DIALING)
    }

    var ongoingService by remember { mutableStateOf<OngoingCallService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    // Service Connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? OngoingCallService.CallBinder
                ongoingService = binder?.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ongoingService = null
                isBound = false
            }
        }
    }

    // Bind to ongoing service if connected
    LaunchedEffect(callStatus) {
        if (callStatus == CallUiStatus.CONNECTED) {
            val intent = Intent(context, OngoingCallService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isBound) {
                try {
                    context.unbindService(serviceConnection)
                } catch (_: Exception) {}
            }
        }
    }

    // Call duration timer from service or local fallback
    val durationSeconds by ongoingService?.callDurationSeconds?.collectAsState() ?: remember { mutableLongStateOf(0L) }
    val isMicMuted by ongoingService?.isMicrophoneMuted?.collectAsState() ?: remember { mutableStateOf(false) }
    val isSpeakerOn by ongoingService?.isSpeakerphoneOn?.collectAsState() ?: remember { mutableStateOf(isVideoCall) }

    // Ringing radar pulse
    val infiniteTransition = rememberInfiniteTransition(label = "CallPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Hardware back press: if connected, asks to end or minimizes
    BackHandler {
        if (callStatus == CallUiStatus.INCOMING_RINGING) {
            notifManager.stopRinging()
            callStatus = CallUiStatus.ENDED
            onCallEnded()
        } else if (callStatus == CallUiStatus.CONNECTED) {
            // Leave call running in background foreground service
            onCallEnded()
        } else {
            OngoingCallService.stopCall(context)
            onCallEnded()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .testTag("call_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Peer Information & Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                // Call Type & Encrypted Stream Badge
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Encrypted",
                            tint = Color(0xFF2BA640),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVideoCall) "Encrypted Video Call" else "Encrypted Voice Call",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = peerName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = peerHandle,
                    color = Color.Gray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (callStatus) {
                        CallUiStatus.INCOMING_RINGING -> "Incoming call…"
                        CallUiStatus.OUTGOING_DIALING -> "Calling…"
                        CallUiStatus.CONNECTED -> {
                            val mins = durationSeconds / 60
                            val secs = durationSeconds % 60
                            String.format("%02d:%02d", mins, secs)
                        }
                        CallUiStatus.ENDED -> "Call ended"
                    },
                    color = if (callStatus == CallUiStatus.CONNECTED) Color(0xFF2BA640) else Color.LightGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Center Avatar / Radar Animation
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp)
            ) {
                if (callStatus == CallUiStatus.INCOMING_RINGING || callStatus == CallUiStatus.OUTGOING_DIALING) {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                if (callStatus == CallUiStatus.INCOMING_RINGING)
                                    Color(0xFF2BA640).copy(alpha = 0.25f)
                                else
                                    Color(0xFF3EA6FF).copy(alpha = 0.25f)
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = if (callStatus == CallUiStatus.CONNECTED) Color(0xFF2BA640) else Color(0xFF333333),
                            shape = CircleShape
                        )
                        .background(Color(0xFF222222)),
                    contentAlignment = Alignment.Center
                ) {
                    if (peerAvatarUri.isNotBlank()) {
                        AsyncImage(
                            model = peerAvatarUri,
                            contentDescription = peerName,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = peerName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Controls Bar
            when (callStatus) {
                CallUiStatus.INCOMING_RINGING -> {
                    // Incoming call controls: Green Answer + Red Decline
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline (Red)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    notifManager.stopRinging()
                                    callStatus = CallUiStatus.ENDED
                                    onCallEnded()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFFE53935),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("call_decline_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Decline Call",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = Color.LightGray, fontSize = 13.sp)
                        }

                        // Answer (Green)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    notifManager.stopRinging()
                                    OngoingCallService.startCall(context, peerName, peerHandle, isVideoCall)
                                    callStatus = CallUiStatus.CONNECTED
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF2BA640),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("call_answer_button")
                            ) {
                                Icon(
                                    imageVector = if (isVideoCall) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "Answer Call",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Answer", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }

                CallUiStatus.OUTGOING_DIALING -> {
                    // Outgoing dialing: Simulate answer or End call
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    OngoingCallService.stopCall(context)
                                    callStatus = CallUiStatus.ENDED
                                    onCallEnded()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFFE53935),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("call_cancel_outgoing_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Cancel Call",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cancel", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }

                    // Simulated peer connection after short delay
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2500)
                        OngoingCallService.startCall(context, peerName, peerHandle, isVideoCall)
                        callStatus = CallUiStatus.CONNECTED
                    }
                }

                CallUiStatus.CONNECTED -> {
                    // In-call active controls: Mute, Speaker, Video, End
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Mute Toggle
                        IconButton(
                            onClick = { ongoingService?.toggleMicrophone() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isMicMuted) Color.White else Color(0xFF222222))
                                .testTag("call_toggle_mic")
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = "Mute Microphone",
                                tint = if (isMicMuted) Color.Black else Color.White
                            )
                        }

                        // Speakerphone Toggle
                        IconButton(
                            onClick = { ongoingService?.toggleSpeakerphone() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isSpeakerOn) Color.White else Color(0xFF222222))
                                .testTag("call_toggle_speaker")
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown,
                                contentDescription = "Speakerphone",
                                tint = if (isSpeakerOn) Color.Black else Color.White
                            )
                        }

                        // End Call (Red)
                        FilledIconButton(
                            onClick = {
                                OngoingCallService.stopCall(context)
                                callStatus = CallUiStatus.ENDED
                                onCallEnded()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFE53935),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("call_end_active_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "End Call",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                CallUiStatus.ENDED -> {
                    Text(
                        text = "Call Disconnected",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
