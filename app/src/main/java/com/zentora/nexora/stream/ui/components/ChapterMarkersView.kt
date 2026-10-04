/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zentora.nexora.stream.ui.theme.NexoraRed
import org.json.JSONArray

data class ChapterMarker(
    val timeMs: Long,
    val title: String
)

fun parseChapterMarkers(json: String): List<ChapterMarker> {
    return try {
        val array = JSONArray(json)
        val list = mutableListOf<ChapterMarker>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ChapterMarker(
                    timeMs = obj.optLong("timeMs", 0L),
                    title = obj.optString("title", "Chapter $i")
                )
            )
        }
        list.sortedBy { it.timeMs }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Feature 11: Creator Chapter Markers Display.
 */
@Composable
fun ChapterMarkersBar(
    markers: List<ChapterMarker>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (markers.isEmpty() || totalDurationMs <= 0) return

    val currentChapter = remember(markers, currentPositionMs) {
        markers.lastOrNull { it.timeMs <= currentPositionMs } ?: markers.firstOrNull()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        currentChapter?.let { ch ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NexoraRed.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = ch.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        markers.forEach { marker ->
            val isActive = currentChapter == marker
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f))
                    .clickable { onSeekTo(marker.timeMs) }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = formatMsToTime(marker.timeMs),
                    color = if (isActive) Color.Black else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun formatMsToTime(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
