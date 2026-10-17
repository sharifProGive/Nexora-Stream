/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentora.nexora.stream.data.database.entities.CommentEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.engine.NexoraIdManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * CommentsViewModel (Pillar 2: Zero Dummy Data & Pure Empty States).
 * Streams real comments from Room Database without any simulated or mock records.
 */
class CommentsViewModel(
    private val repository: NexoraStreamRepository,
    val videoId: String
) : ViewModel() {

    val comments: StateFlow<List<CommentEntity>> = repository.getComments(videoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun postComment(text: String, onSuccess: () -> Unit = {}) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val session = NexoraIdManager.getSession()
            repository.addComment(videoId, session.username, text.trim())
            onSuccess()
        }
    }

    fun toggleLike(comment: CommentEntity) {
        viewModelScope.launch {
            repository.toggleCommentLike(comment)
        }
    }
}
