/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.ParentalControlDialog
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick

/**
 * Level 1 Screen: Root Settings
 * Route: "settings/root"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsList(
    navController: NavController,
    repository: NexoraStreamRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(NexoraIdManager.getSession()) }
    val isKidsMode by repository.isKidsMode.collectAsState()
    val isDataSaver by repository.isDataSaver.collectAsState()

    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }
    val mobileQuality by prefsManager.mobileVideoQuality.collectAsState()
    val downloadWifiOnly by prefsManager.downloadWifiOnly.collectAsState()

    var showParentalControlDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_root_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
                        .clickable { navController.navigate("settings/account") }
                        .testTag("settings_profile_card")
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
                                onClick = { navController.navigate("settings/account") },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("manage_account_button")
                            ) {
                                Text("Manage", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==================== 1. GENERAL (Navigates to "settings/general") ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Tune,
                    title = "General",
                    subtitle = "App playback, seeking, reminders, appearance",
                    tag = "nav_settings_general_row",
                    onClick = { navController.navigate("settings/general") }
                )
            }

            // ==================== 2. ACCOUNT & PROFILE (Navigates to "settings/account") ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.AccountCircle,
                    title = "Account & Profile",
                    subtitle = "Zentora ID, channel status, connected devices",
                    tag = "nav_settings_account_row",
                    onClick = { navController.navigate("settings/account") }
                )
            }

            // ==================== 3. VIDEO QUALITY PREFERENCES (Navigates to "settings/quality") ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.HighQuality,
                    title = "Video Quality Preferences",
                    subtitle = "Mobile: $mobileQuality",
                    tag = "nav_settings_quality_row",
                    onClick = { navController.navigate("settings/quality") }
                )
            }

            // ==================== 4. DOWNLOADS & OFFLINE (Navigates to "settings/downloads") ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Download,
                    title = "Downloads & Offline",
                    subtitle = if (downloadWifiOnly) "Download over Wi-Fi only" else "Download over any network",
                    tag = "nav_settings_downloads_row",
                    onClick = { navController.navigate("settings/downloads") }
                )
            }

            // ==================== 5. DATA SAVING (Instant toggle) ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.DataSaverOn,
                    title = "Data saving",
                    subtitle = if (isDataSaver) "Data saving mode is ON" else "Default video quality settings",
                    tag = "data_saving_toggle_row",
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
                        val next = !isDataSaver
                        repository.setDataSaver(next)
                    }
                )
            }

            // ==================== 6. PARENTAL CONTROLS ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.ChildCare,
                    title = "Kids & Restricted mode",
                    subtitle = if (isKidsMode) "Active (Locked with PIN)" else "Inactive (All content visible)",
                    tag = "kids_mode_setting_row",
                    onClick = { showParentalControlDialog = true }
                )
            }

            // ==================== 7. ABOUT ====================
            item {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Info,
                    title = "About Nexora Stream",
                    subtitle = "Institutional Platform · Zentora CLC · v1.0.0",
                    tag = "about_setting_row",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showParentalControlDialog) {
        ParentalControlDialog(
            isCurrentlyKidsMode = isKidsMode,
            onDismiss = { showParentalControlDialog = false },
            onToggleKidsMode = { enabled, pin ->
                val ok = repository.setKidsMode(enabled, pin)
                if (ok) {
                    showParentalControlDialog = false
                    Toast.makeText(context, if (enabled) "Kids mode activated" else "Kids mode deactivated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
                ok
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Nexora Stream", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nexora Stream - Video Sharing Platform", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Engineered by Zentora CLC.", fontSize = 13.sp)
                    Text("Version 1.0.0 (Release Build)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Copyright © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora.",
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
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    trailingWidget: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
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
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
