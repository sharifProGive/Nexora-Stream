/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentora.nexora.stream.data.database.entities.VideoEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraSmartRank
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * HomeViewModel (Pillar 2: Zero Dummy Data & Pure Empty States).
 * State strictly starts with emptyList. Never introduces mock or fake videos.
 */
class HomeViewModel(
    private val repository: NexoraStreamRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val categories = listOf("All", "Tech", "Animation", "Gaming", "Music", "Science", "Education", "Sports")

    val isKidsMode = repository.isKidsMode

    // Real videos strictly loaded from database (initial = emptyList)
    val allVideos: StateFlow<List<VideoEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChannels = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pure filtered feed
    val displayedVideos: StateFlow<List<VideoEntity>> = combine(
        allVideos,
        allHistory,
        _selectedCategory,
        isKidsMode
    ) { videos, history, category, kidsMode ->
        val baseList = if (kidsMode) {
            videos.filter { it.category.equals("Animation", ignoreCase = true) || it.category.equals("Education", ignoreCase = true) }
        } else {
            videos
        }

        if (category == "All") {
            NexoraSmartRank.rankVideos(baseList, emptyMap(), history)
        } else {
            baseList.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(600)
            _isRefreshing.value = false
        }
    }
}
