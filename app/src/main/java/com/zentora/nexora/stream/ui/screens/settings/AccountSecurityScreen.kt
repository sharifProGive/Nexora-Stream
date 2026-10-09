/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
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
 * Level 3 Screen: Account Security & Credentials
 * Route: "settings/general/account_security"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val twoStep by prefsManager.twoStepVerification.collectAsState()
    val recoveryAlerts by prefsManager.passwordRecoveryAlerts.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("account_security_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Account Security", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("account_security_screen")
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = NexoraRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Enhanced Account Protection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Manage two-factor challenges and security alerts for your Zentora Account.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. Two-step verification prompt
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !twoStep
                            prefsManager.setTwoStepVerification(next)
                            Toast.makeText(
                                context,
                                if (next) "Two-step verification activated" else "Two-step verification turned off",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("two_step_verification_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Two-step verification prompt", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "Require mobile prompt verification when signing in on a new device or browser.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = twoStep,
                        onCheckedChange = {
                            prefsManager.setTwoStepVerification(it)
                            Toast.makeText(
                                context,
                                if (it) "Two-step verification activated" else "Two-step verification turned off",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("two_step_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 2. Allow password recovery alerts
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = !recoveryAlerts
                            prefsManager.setPasswordRecoveryAlerts(next)
                            Toast.makeText(
                                context,
                                if (next) "Recovery alerts enabled" else "Recovery alerts disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("password_recovery_alerts_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow password recovery alerts", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "Receive prompt push notifications and recovery SMS if an unusual sign-in attempt occurs.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = recoveryAlerts,
                        onCheckedChange = {
                            prefsManager.setPasswordRecoveryAlerts(it)
                            Toast.makeText(
                                context,
                                if (it) "Recovery alerts enabled" else "Recovery alerts disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("recovery_alerts_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 3. Change account password
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPasswordDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .testTag("change_password_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Change account password", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text("Update your security credentials for Nexora Stream & Zentora CLC", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = { currentPass = it },
                        label = { Text("Current password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New password (min 8 chars)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Confirm new password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.length < 8) {
                            Toast.makeText(context, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                        } else if (newPass != confirmPass) {
                            Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        } else {
                            showPasswordDialog = false
                            Toast.makeText(context, "Password updated successfully", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
