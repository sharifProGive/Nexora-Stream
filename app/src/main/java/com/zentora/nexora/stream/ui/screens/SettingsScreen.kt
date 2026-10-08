/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.ParentalControlDialog
import com.zentora.nexora.stream.ui.theme.*

/**
 * SettingsScreen (Module 2):
 * Official YouTube-style hierarchical settings screen:
 * - General
 * - Data saving
 * - Autoplay
 * - Video quality preferences
 * - Downloads
 * - About Nexora Stream
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: NexoraStreamRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(NexoraIdManager.getSession()) }
    val isKidsMode by repository.isKidsMode.collectAsState()
    val isDataSaver by repository.isDataSaver.collectAsState()

    var showParentalControlDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Dialog toggles for nested sections
    var showGeneralDialog by remember { mutableStateOf(false) }
    var showAutoplayDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }

    // Local toggles connected to settings
    var autoplayNextVideo by remember { mutableStateOf(true) }
    var downloadOverWifiOnly by remember { mutableStateOf(true) }
    var defaultQualityPreference by remember { mutableStateOf("Auto (recommended)") }
    var doubleTapToSeekSec by remember { mutableStateOf(10) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Profile & Account Summary Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                AsyncImage(
                                    model = session.avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150" },
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(NexoraVerifiedTick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(9.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.username, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(session.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            OutlinedButton(
                                onClick = { showEditProfileDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Manage", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==================== 1. GENERAL ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Tune,
                    title = "General",
                    subtitle = "App playback, double-tap seek, appearance",
                    onClick = { showGeneralDialog = true }
                )
            }

            // ==================== 2. DATA SAVING ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.DataSaverOn,
                    title = "Data saving",
                    subtitle = if (isDataSaver) "Data saving mode is ON" else "Default video quality settings",
                    trailingWidget = {
                        Switch(
                            checked = isDataSaver,
                            onCheckedChange = {
                                repository.setDataSaver(it)
                                Toast.makeText(context, if (it) "Data saver enabled" else "Data saver disabled", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed)
                        )
                    },
                    onClick = {
                        val newState = !isDataSaver
                        repository.setDataSaver(newState)
                    }
                )
            }

            // ==================== 3. AUTOPLAY ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    title = "Autoplay",
                    subtitle = if (autoplayNextVideo) "Autoplay next video is ON" else "Autoplay next video is OFF",
                    onClick = { showAutoplayDialog = true }
                )
            }

            // ==================== 4. VIDEO QUALITY PREFERENCES ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.HighQuality,
                    title = "Video quality preferences",
                    subtitle = defaultQualityPreference,
                    onClick = { showVideoQualityDialog = true }
                )
            }

            // ==================== 5. DOWNLOADS ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Download,
                    title = "Downloads",
                    subtitle = if (downloadOverWifiOnly) "Download over Wi-Fi only" else "Download over any network",
                    onClick = { showDownloadsDialog = true }
                )
            }

            // ==================== 6. PARENTAL CONTROLS ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.ChildCare,
                    title = "Kids & Restricted mode",
                    subtitle = if (isKidsMode) "Active (Locked with PIN)" else "Inactive (All content visible)",
                    onClick = { showParentalControlDialog = true }
                )
            }

            // ==================== 7. ABOUT ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Info,
                    title = "About",
                    subtitle = "Nexora Stream · Zentora CLC · v1.0.0",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    // --- Sub-Dialogs ---

    // General Dialog
    if (showGeneralDialog) {
        AlertDialog(
            onDismissRequest = { showGeneralDialog = false },
            title = { Text("General", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Double-tap to seek", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    listOf(5, 10, 15, 20).forEach { sec ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { doubleTapToSeekSec = sec }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = doubleTapToSeekSec == sec, onClick = { doubleTapToSeekSec = sec })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$sec seconds", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGeneralDialog = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // Autoplay Dialog
    if (showAutoplayDialog) {
        AlertDialog(
            onDismissRequest = { showAutoplayDialog = false },
            title = { Text("Autoplay", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autoplay next video", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("When you finish a video, another plays automatically", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoplayNextVideo,
                        onCheckedChange = { autoplayNextVideo = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoplayDialog = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // Video Quality Dialog
    if (showVideoQualityDialog) {
        val qualities = listOf("Auto (recommended)", "Higher picture quality", "Data saver (360p - 480p)")
        AlertDialog(
            onDismissRequest = { showVideoQualityDialog = false },
            title = { Text("Video quality preferences", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualities.forEach { q ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { defaultQualityPreference = q }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = defaultQualityPreference == q, onClick = { defaultQualityPreference = q })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(q, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVideoQualityDialog = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // Downloads Dialog
    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = { Text("Downloads", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download over Wi-Fi only", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Avoid carrier cellular data charges", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = downloadOverWifiOnly,
                        onCheckedChange = { downloadOverWifiOnly = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) { Text("Done", fontWeight = FontWeight.Bold) }
            }
        )
    }

    // Parental Controls Dialog
    if (showParentalControlDialog) {
        ParentalControlDialog(
            isCurrentlyKidsMode = isKidsMode,
            onDismiss = { showParentalControlDialog = false },
            onToggleKidsMode = { targetState, pin ->
                repository.setKidsMode(targetState, pin)
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NexoraRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nexora Stream", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Application Name: Nexora Stream", fontWeight = FontWeight.Bold)
                    Text("Organization: Zentora CLC", fontWeight = FontWeight.Bold)
                    Text("Developer: Zentora", fontWeight = FontWeight.Bold)
                    Text("Package: com.zentora.nexora.stream")
                    Text("Version: 1.0.0")
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copyright (c) 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var uName by remember { mutableStateOf(session.username) }
        var uHandle by remember { mutableStateOf(session.handle) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Nexora ID Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uName, onValueChange = { uName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uHandle, onValueChange = { uHandle = it }, label = { Text("Handle") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        NexoraIdManager.updateSession(uName.trim(), uHandle.trim(), session.isCreatorMode)
                        session = NexoraIdManager.getSession()
                        showEditProfileDialog = false
                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingWidget: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailingWidget != null) {
            trailingWidget()
        } else {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
