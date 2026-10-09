/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Timer
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
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.ui.theme.NexoraRed

/**
 * Level 2 Screen: General Settings
 * Route: "settings/general"
 * Renders full-width clickable setting tiles that push Level 3 sub-screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    navController: NavController,
    repository: NexoraStreamRepository
) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val seekSeconds by prefsManager.seekDurationSeconds.collectAsState()
    val takeBreak by prefsManager.takeBreakReminder.collectAsState()
    val twoStep by prefsManager.twoStepVerification.collectAsState()

    var showAppearanceDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf("Device Default") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("general_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("General", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("general_settings_screen"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Level 3 Tile 1: Account Security & Credentials
            item {
                GeneralSettingNavRow(
                    icon = Icons.Outlined.Lock,
                    title = "Account Security & Credentials",
                    subtitle = if (twoStep) "Two-step verification active" else "Two-step verification, recovery alerts, password",
                    tag = "nav_account_security_tile",
                    onClick = { navController.navigate("settings/general/account_security") }
                )
            }

            // Level 3 Tile 2: Playback Controls & Seeking
            item {
                GeneralSettingNavRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    title = "Playback Controls & Seeking",
                    subtitle = "Double-tap seek ($seekSeconds seconds), inline feed playback",
                    tag = "nav_playback_controls_tile",
                    onClick = { navController.navigate("settings/general/playback") }
                )
            }

            // Level 3 Tile 3: Reminders & Well-being
            item {
                GeneralSettingNavRow(
                    icon = Icons.Outlined.Timer,
                    title = "Reminders & Well-being",
                    subtitle = if (takeBreak) "Take a break reminder is ON" else "Take a break, bedtime reminders",
                    tag = "nav_reminders_wellbeing_tile",
                    onClick = { navController.navigate("settings/general/reminders") }
                )
            }

            // Additional general options
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                GeneralSettingNavRow(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance",
                    subtitle = selectedTheme,
                    tag = "appearance_setting_tile",
                    onClick = { showAppearanceDialog = true }
                )
            }
        }
    }

    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Appearance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose your theme preference for Nexora Stream", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("Use device theme", "Light theme", "Dark theme").forEach { themeName ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTheme = themeName
                                    showAppearanceDialog = false
                                    Toast.makeText(context, "$themeName selected", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedTheme.contains(themeName.split(" ").first()),
                                onClick = {
                                    selectedTheme = themeName
                                    showAppearanceDialog = false
                                    Toast.makeText(context, "$themeName selected", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NexoraRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(themeName, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GeneralSettingNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tag: String,
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
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
