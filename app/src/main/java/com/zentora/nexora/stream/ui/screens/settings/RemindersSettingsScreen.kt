/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Coffee
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
import com.zentora.nexora.stream.ui.theme.NexoraRed
import java.util.Locale

/**
 * Level 3 Screen: Reminders & Well-being
 * Route: "settings/general/reminders"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefsManager = remember { SettingsPreferencesManager.getInstance(context) }

    val takeBreak by prefsManager.takeBreakReminder.collectAsState()
    val breakMinutes by prefsManager.breakReminderMinutes.collectAsState()

    val bedtime by prefsManager.bedtimeReminder.collectAsState()
    val bedtimeHour by prefsManager.bedtimeHour.collectAsState()
    val bedtimeMinute by prefsManager.bedtimeMinute.collectAsState()

    fun showBreakTimePicker() {
        val currentHours = breakMinutes / 60
        val currentMin = breakMinutes % 60
        val dialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val totalMin = maxOf(15, (hourOfDay * 60) + minute)
                prefsManager.setBreakReminderMinutes(totalMin)
                Toast.makeText(context, "Break reminder set to ${totalMin / 60}h ${totalMin % 60}m", Toast.LENGTH_SHORT).show()
            },
            currentHours,
            currentMin,
            true
        )
        dialog.setTitle("Set reminder frequency")
        dialog.show()
    }

    fun showBedtimePicker() {
        val dialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                prefsManager.setBedtimeTime(hourOfDay, minute)
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                Toast.makeText(context, "Bedtime reminder set to $formatted", Toast.LENGTH_SHORT).show()
            },
            bedtimeHour,
            bedtimeMinute,
            false
        )
        dialog.setTitle("Set bedtime")
        dialog.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("reminders_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Reminders & Well-being", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("reminders_settings_screen")
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
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = NexoraRed,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Digital Well-being Tools", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Manage your viewing time and maintain healthy screen habits on Nexora Stream.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. Remind me to take a break
            item {
                val breakSubtitle = if (takeBreak) {
                    val h = breakMinutes / 60
                    val m = breakMinutes % 60
                    "Every ${if (h > 0) "${h}h " else ""}${m}m (Tap to adjust)"
                } else {
                    "Off"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!takeBreak) {
                                prefsManager.setTakeBreakReminder(true)
                                showBreakTimePicker()
                            } else {
                                showBreakTimePicker()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("take_break_reminder_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remind me to take a break", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(breakSubtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = takeBreak,
                        onCheckedChange = { isChecked ->
                            prefsManager.setTakeBreakReminder(isChecked)
                            if (isChecked) {
                                showBreakTimePicker()
                            } else {
                                Toast.makeText(context, "Break reminder turned off", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("take_break_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 2. Remind me when it's bedtime
            item {
                val bedtimeStr = String.format(Locale.getDefault(), "%02d:%02d", bedtimeHour, bedtimeMinute)
                val bedtimeSubtitle = if (bedtime) "At $bedtimeStr (Tap to adjust)" else "Off"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!bedtime) {
                                prefsManager.setBedtimeReminder(true)
                                showBedtimePicker()
                            } else {
                                showBedtimePicker()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("bedtime_reminder_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remind me when it's bedtime", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(bedtimeSubtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = bedtime,
                        onCheckedChange = { isChecked ->
                            prefsManager.setBedtimeReminder(isChecked)
                            if (isChecked) {
                                showBedtimePicker()
                            } else {
                                Toast.makeText(context, "Bedtime reminder turned off", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = NexoraRed),
                        modifier = Modifier.testTag("bedtime_switch")
                    )
                }
            }
        }
    }
}
