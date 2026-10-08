/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraSemanticSearch
import com.zentora.nexora.stream.ui.components.formatMsToTime
import com.zentora.nexora.stream.ui.theme.NexoraRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: NexoraStreamRepository,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allVideos by repository.allVideos.collectAsState(initial = emptyList())

    // In-House Nexora Semantic Search + Central Registry Query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            repository.searchGlobalVaults(searchQuery.trim())
        }
    }

    val searchResults = remember(searchQuery, allVideos) {
        NexoraSemanticSearch.search(allVideos, searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Nexora Semantic Search…", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("semantic_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NexoraRed)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Nexora Semantic Search Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Search local catalog by title, semantic tags, and category", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No semantic matches found for '$searchQuery'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(searchResults, key = { it.id }) { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVideoClick(video.id) }
                                .padding(vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = video.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(formatMsToTime(video.duration), color = Color.White, fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(video.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${video.viewCount} views • Category: ${video.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tags: ${video.tags}", fontSize = 10.sp, color = NexoraRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
