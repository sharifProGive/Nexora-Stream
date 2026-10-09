/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Wifi
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
 * Level 2 Screen: Downloads & Offline
 * Route: "settings/downloads"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val wifiOnly by prefsManager.downloadWifiOnly.collectAsState()
    val quality by prefsManager.downloadQuality.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("downloads_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Downloads & Offline", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("downloads_settings_screen"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Download Quality
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQualityDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("download_quality_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download quality", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(quality, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 2. Download over Wi-Fi only
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !wifiOnly
                            prefsManager.setDownloadWifiOnly(next)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("download_wifi_only_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Download over Wi-Fi only", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Save mobile data by only downloading when connected to Wi-Fi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = { prefsManager.setDownloadWifiOnly(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("download_wifi_only_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 3. Available storage
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Available device storage", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Used by Nexora: 48.2 MB · Free space: 24.8 GB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 4. Delete all downloads
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Toast.makeText(context, "All downloaded videos cleared from offline cache", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("delete_downloads_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Delete all downloads", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = NexoraRed)
                        Text("Remove all saved offline content from this device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showQualityDialog) {
        val options = listOf("Full HD (1080p)", "High (720p)", "Medium (360p)", "Low (144p)", "Ask each time")
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Download Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefsManager.setDownloadQuality(opt)
                                    showQualityDialog = false
                                    Toast.makeText(context, "Download quality: $opt", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = quality == opt,
                                onClick = {
                                    prefsManager.setDownloadQuality(opt)
                                    showQualityDialog = false
                                    Toast.makeText(context, "Download quality: $opt", Toast.LENGTH_SHORT).show()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NexoraRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Cancel") }
            }
        )
    }
}
