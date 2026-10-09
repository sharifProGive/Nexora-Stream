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
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick

/**
 * Level 2 Screen: Account & Profile
 * Route: "settings/account"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    navController: NavController,
    repository: NexoraStreamRepository
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf(NexoraIdManager.getSession()) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("account_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Account & Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("account_settings_screen"),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                AsyncImage(
                                    model = session.avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150" },
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(NexoraVerifiedTick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(session.handle, fontSize = 12.sp, color = NexoraRed, fontWeight = FontWeight.Medium)
                                Text(session.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showEditProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NexoraRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_button")
                        ) {
                            Text("Manage Google & Zentora Identity", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Route to security directly from account as well
            item {
                SettingNavigationTile(
                    icon = Icons.Outlined.Security,
                    title = "Account Security & Credentials",
                    subtitle = "Two-step verification prompt, password, recovery",
                    tag = "account_to_security_tile",
                    onClick = { navController.navigate("settings/general/account_security") }
                )
            }

            item {
                SettingNavigationTile(
                    icon = Icons.Outlined.Devices,
                    title = "Manage Linked Devices",
                    subtitle = "This device (Android App), Web session",
                    tag = "manage_devices_tile",
                    onClick = {
                        Toast.makeText(context, "Current device active. No unrecognized logins.", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                SettingNavigationTile(
                    icon = Icons.Outlined.Badge,
                    title = "Channel Status & Features",
                    subtitle = if (session.isCreatorMode) "Creator Studio unlocked" else "Standard viewer account",
                    tag = "channel_status_tile",
                    onClick = {
                        val newMode = !session.isCreatorMode
                        NexoraIdManager.updateSession(session.username, session.handle, newMode)
                        session = NexoraIdManager.getSession()
                        Toast.makeText(context, if (newMode) "Creator mode enabled" else "Standard viewer mode", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    if (showEditProfileDialog) {
        var uName by remember { mutableStateOf(session.username) }
        var uHandle by remember { mutableStateOf(session.handle) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Nexora Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uName, onValueChange = { uName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = uHandle, onValueChange = { uHandle = it }, label = { Text("Channel Handle") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        NexoraIdManager.updateSession(uName.trim(), uHandle.trim(), session.isCreatorMode)
                        session = NexoraIdManager.getSession()
                        showEditProfileDialog = false
                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
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
private fun SettingNavigationTile(
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
