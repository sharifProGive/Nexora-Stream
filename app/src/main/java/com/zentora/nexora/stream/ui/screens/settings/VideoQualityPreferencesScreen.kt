/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CellTower
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
 * Level 2 Screen: Video Quality Preferences
 * Route: "settings/quality"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualityPreferencesScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val mobileQuality by prefsManager.mobileVideoQuality.collectAsState()
    val wifiQuality by prefsManager.wifiVideoQuality.collectAsState()

    val qualityOptions = listOf(
        Pair("Auto (recommended)", "Adjusts to give you the best experience for your conditions"),
        Pair("Higher picture quality", "Uses more data"),
        Pair("Data saver", "Lower picture quality")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("video_quality_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Video Quality Preferences", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("video_quality_screen")
        ) {
            // Section 1: Mobile Networks
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CellTower, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIDEO QUALITY ON MOBILE NETWORKS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NexoraRed)
                }
            }

            items(qualityOptions.size) { idx ->
                val (title, subtitle) = qualityOptions[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            prefsManager.setMobileVideoQuality(title)
                            Toast.makeText(context, "Mobile quality: $title", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("mobile_quality_$idx"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mobileQuality == title,
                        onClick = {
                            prefsManager.setMobileVideoQuality(title)
                            Toast.makeText(context, "Mobile quality: $title", Toast.LENGTH_SHORT).show()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NexoraRed)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
            }

            // Section 2: Wi-Fi
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null, tint = NexoraRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIDEO QUALITY ON WI-FI", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NexoraRed)
                }
            }

            items(qualityOptions.size) { idx ->
                val (title, subtitle) = qualityOptions[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            prefsManager.setWifiVideoQuality(title)
                            Toast.makeText(context, "Wi-Fi quality: $title", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("wifi_quality_$idx"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = wifiQuality == title,
                        onClick = {
                            prefsManager.setWifiVideoQuality(title)
                            Toast.makeText(context, "Wi-Fi quality: $title", Toast.LENGTH_SHORT).show()
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NexoraRed)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
