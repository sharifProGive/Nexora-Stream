/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zentora.nexora.stream.ui.theme.NexoraRed

data class AudioTrackItem(val id: String, val languageName: String, val isDefault: Boolean = false)

/**
 * Feature 6: Multi-Language Audio Track Switcher Dialog.
 */
@Composable
fun AudioTrackDialog(
    currentTrackId: String,
    onDismiss: () -> Unit,
    onSelectTrack: (AudioTrackItem) -> Unit
) {
    val tracks = listOf(
        AudioTrackItem("en", "English (Original Studio Audio)", isDefault = true),
        AudioTrackItem("bn", "Bengali (বাংলা ডাবিং)"),
        AudioTrackItem("es", "Spanish (Español)"),
        AudioTrackItem("ja", "Japanese (日本語)"),
        AudioTrackItem("synth", "Instrumental / Ambient Core")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = NexoraRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Audio Track Language", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                tracks.forEach { track ->
                    val isSelected = track.id == currentTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrack(track) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = track.languageName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NexoraRed else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = NexoraRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
