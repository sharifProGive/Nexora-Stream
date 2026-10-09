/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zentora.nexora.stream.ui.theme.NexoraRed

/**
 * Level 3 Screen: Playback Controls & Seeking
 * Route: "settings/general/playback"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val doubleTapEnabled by prefsManager.doubleTapSeekEnabled.collectAsState()
    val seekSeconds by prefsManager.seekDurationSeconds.collectAsState()
    val inlinePlayback by prefsManager.inlinePlaybackFeeds.collectAsState()

    var showSeekDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("playback_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Playback Controls & Seeking", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("playback_settings_screen")
        ) {
            // 1. Double-tap to seek toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !doubleTapEnabled
                            prefsManager.setDoubleTapSeekEnabled(next)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("double_tap_seek_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Double-tap to fast forward/rewind", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "Double tap the left or right side of the video player screen to skip forward or backward.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = doubleTapEnabled,
                        onCheckedChange = { prefsManager.setDoubleTapSeekEnabled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("double_tap_seek_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 2. Dropdown / Selection for Seek duration (enabled when double tap is ON)
            if (doubleTapEnabled) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Seek increment", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Select the amount of time skipped on double-tap",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val durations = listOf(5, 10, 15, 30)
                        durations.forEach { sec ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefsManager.setSeekDurationSeconds(sec)
                                        Toast.makeText(context, "Seek increment set to $sec seconds", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("seek_duration_${sec}s")
                            ) {
                                RadioButton(
                                    selected = seekSeconds == sec,
                                    onClick = {
                                        prefsManager.setSeekDurationSeconds(sec)
                                        Toast.makeText(context, "Seek increment set to $sec seconds", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = NexoraRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$sec seconds", fontSize = 14.sp, fontWeight = if (seekSeconds == sec) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // 3. Inline playback in feeds
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !inlinePlayback
                            prefsManager.setInlinePlaybackFeeds(next)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("inline_playback_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Inline playback in feeds", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "Choose whether videos play with sound off as you browse Home and Subscriptions feeds.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = inlinePlayback,
                        onCheckedChange = { prefsManager.setInlinePlaybackFeeds(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("inline_playback_switch")
                    )
                }
            }
        }
    }
}
