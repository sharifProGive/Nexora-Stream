/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import com.zentora.nexora.stream.ui.theme.NexoraZentoraBlue

enum class ThumbnailFilter(val label: String, val overlayColors: List<Color>) {
    NONE("Original", listOf(Color.Transparent, Color.Transparent)),
    NEON_RED("Nexora Crimson", listOf(NexoraRed.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.7f))),
    CYBERPUNK("Cyberpunk Glow", listOf(NexoraZentoraBlue.copy(alpha = 0.4f), NexoraRed.copy(alpha = 0.6f))),
    CINEMATIC_GOLD("Cinematic Gold", listOf(NexoraSparkGold.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f)))
}

/**
 * Feature 14: In-App Thumbnail Designer with text/filter overlays.
 */
@Composable
fun ThumbnailDesignerDialog(
    baseImageUri: String,
    onDismiss: () -> Unit,
    onSaveDesign: (textOverlay: String, badge: String, filter: ThumbnailFilter) -> Unit
) {
    var textOverlay by remember { mutableStateOf("ULTRA HD 60FPS") }
    var selectedBadge by remember { mutableStateOf("ZENTORA EXCLUSIVE") }
    var selectedFilter by remember { mutableStateOf(ThumbnailFilter.NEON_RED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Brush, contentDescription = null, tint = NexoraRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nexora Thumbnail Designer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                ) {
                    if (baseImageUri.isNotBlank()) {
                        AsyncImage(
                            model = baseImageUri,
                            contentDescription = "Thumbnail base",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Filter overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(GradientBrush.verticalGradient(selectedFilter.overlayColors))
                    )

                    // Badge top-left
                    if (selectedBadge.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NexoraRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectedBadge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Text overlay bottom
                    if (textOverlay.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = textOverlay,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = textOverlay,
                    onValueChange = { textOverlay = it },
                    label = { Text("Main Text Banner") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = selectedBadge,
                    onValueChange = { selectedBadge = it },
                    label = { Text("Badge Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Visual Filter Preset", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThumbnailFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveDesign(textOverlay, selectedBadge, selectedFilter) },
                colors = ButtonDefaults.buttonColors(containerColor = NexoraRed)
            ) {
                Text("Apply Design", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
