/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UploadProgressUiState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val phaseText: String = "",
    val speedText: String = "",
    val timeRemainingText: String = "",
    val bytesTransferredText: String = "",
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val videoTitle: String = "",
    val thumbnailPath: String? = null
)

class UploadStudioViewModel(
    private val repository: NexoraStreamRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow(UploadProgressUiState())
    val uploadState: StateFlow<UploadProgressUiState> = _uploadState.asStateFlow()

    private var activeUploadJob: Job? = null

    /**
     * Saves the video to this phone and hands it to the repository's upload queue.
     * The queue sends the file to the Nexora server in resumable chunks (with retry and backoff) and publishes it.
     * Real progress per video is exposed by NexoraStreamRepository.uploadProgress; nothing is simulated here.
     */
    fun startChunkedUpload(
        videoFile: File,
        thumbnailPath: String?,
        title: String,
        description: String,
        category: String,
        tags: String,
        visibility: String,
        isShort: Boolean,
        durationMs: Long,
        isMadeForKids: Boolean = false,
        isAgeRestricted: Boolean = false,
        location: String = "",
        shortsRemixing: String = "Allow video and audio remixing",
        commentsModeration: String = "On (Strict)",
        showLikesCount: Boolean = true,
        containsPaidPromotion: Boolean = false,
        videoLanguage: String = "English",
        licenseType: String = "Standard Nexora License",
        allowEmbedding: Boolean = true,
        notifySubscribers: Boolean = true,
        onSuccess: () -> Unit
    ) {
        if (_uploadState.value.isUploading) return

        val finalTitle = title.ifBlank { "My Video" }

        activeUploadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uploadState.value = UploadProgressUiState(
                    isUploading = true,
                    progress = 0f,
                    phaseText = "Saving video...",
                    videoTitle = finalTitle,
                    thumbnailPath = thumbnailPath
                )

                repository.uploadVideo(
                    title = finalTitle,
                    description = description,
                    category = category,
                    tags = tags,
                    localUri = videoFile.absolutePath,
                    thumbnailUri = thumbnailPath ?: "",
                    duration = durationMs,
                    visibility = visibility,
                    isMadeForKids = isMadeForKids,
                    isAgeRestricted = isAgeRestricted,
                    location = location,
                    shortsRemixing = shortsRemixing,
                    commentsModeration = commentsModeration,
                    showLikesCount = showLikesCount,
                    containsPaidPromotion = containsPaidPromotion,
                    videoLanguage = videoLanguage,
                    licenseType = licenseType,
                    allowEmbedding = allowEmbedding,
                    notifySubscribers = notifySubscribers
                )

                _uploadState.value = _uploadState.value.copy(
                    progress = 1f,
                    phaseText = "Saved. Uploading to Nexora in the background.",
                    isCompleted = true,
                    isUploading = false
                )

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = e.message ?: "The video could not be saved"
                )
            }
        }
    }

    fun cancelUpload() {
        activeUploadJob?.cancel()
        activeUploadJob = null
        _uploadState.value = UploadProgressUiState()
    }

    fun dismissCard() {
        _uploadState.value = UploadProgressUiState()
    }

    fun resetUploadState() {
        _uploadState.value = UploadProgressUiState()
    }
}
