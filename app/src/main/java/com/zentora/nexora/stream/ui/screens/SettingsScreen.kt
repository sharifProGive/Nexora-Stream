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
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.ui.components.ParentalControlDialog
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import com.zentora.nexora.stream.ui.theme.NexoraSuccess
import com.zentora.nexora.stream.ui.theme.NexoraVerifiedTick

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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings & Nexora ID", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Feature 1: Nexora ID Single Sign-On System Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Badge, contentDescription = null, tint = NexoraRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nexora ID Authentication", fontWeight = FontWeight.Bold, color = NexoraRed)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                AsyncImage(
                                    model = session.avatarUri,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(56.dp).clip(CircleShape),
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

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(session.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Token: ${session.zentoraAuthToken.take(16)}…", fontSize = 10.sp, color = NexoraSparkGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Creator Mode Status", fontWeight = FontWeight.SemiBold)
                                Text(if (session.isCreatorMode) "Studio & Upload Enabled" else "Viewer Mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = session.isCreatorMode,
                                onCheckedChange = {
                                    NexoraIdManager.updateSession(session.username, session.handle, it)
                                    session = NexoraIdManager.getSession()
                                    Toast.makeText(context, if (it) "Creator Mode active" else "Viewer Mode active", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Nexora ID Profile")
                        }
                    }
                }
            }

            // Feature 28: Nexora Smart Sync (Low-Data Saver mode)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DataSaverOn, contentDescription = null, tint = NexoraSuccess)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Nexora Smart Sync (Data Saver)", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Low-Data Saver Mode", fontWeight = FontWeight.SemiBold)
                                Text("Optimizes buffer chunks and reduces background telemetry consumption", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isDataSaver,
                                onCheckedChange = {
                                    repository.setDataSaver(it)
                                    Toast.makeText(context, if (it) "Smart Sync Data Saver ON" else "Data Saver OFF", Toast.LENGTH_SHORT).show()
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = NexoraSuccess)
                            )
                        }
                    }
                }
            }

            // Feature 29: Parental Control & Kids Mode protected by 4-digit master PIN
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ChildCare, contentDescription = null, tint = NexoraRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Parental Control & Kids Mode", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Restricted Kids Mode", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (isKidsMode) "Active (Locked by 4-Digit PIN)" else "Inactive (Full catalog visible)",
                                    fontSize = 11.sp,
                                    color = if (isKidsMode) NexoraRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showParentalControlDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isKidsMode) NexoraRed else MaterialTheme.colorScheme.surface),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(if (isKidsMode) "Exit PIN" else "Activate", color = if (isKidsMode) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Feature 31: Settings & Official About Screen
            item {
                Card(
                    onClick = { showAboutDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = NexoraRed)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("About Nexora Stream", fontWeight = FontWeight.Bold)
                                Text("Zentora CLC • Developer: Zentora • v1.0.0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }

    // Feature 29: Parental Control Dialog
    if (showParentalControlDialog) {
        ParentalControlDialog(
            isCurrentlyKidsMode = isKidsMode,
            onDismiss = { showParentalControlDialog = false },
            onToggleKidsMode = { targetState, pin ->
                repository.setKidsMode(targetState, pin)
            }
        )
    }

    // Feature 31: Official About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(NexoraRed), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nexora Stream", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Application Name: Nexora Stream", fontWeight = FontWeight.Bold)
                    Text("Organization: Zentora CLC", fontWeight = FontWeight.Bold)
                    Text("Developer: Zentora", fontWeight = FontWeight.Bold)
                    Text("Package: com.zentora.nexora.stream")
                    Text("Version: 1.0.0")
                    Text("Output Artifact: Nexora-Stream-v1.0.apk")
                    Text("Target Repository: sharifProGive/nexora-app (main)")
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MIT Open-Source License\n\nCopyright (c) 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora.\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files.",
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

    // Edit Nexora ID Profile Dialog
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
                        Toast.makeText(context, "Nexora ID updated", Toast.LENGTH_SHORT).show()
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
