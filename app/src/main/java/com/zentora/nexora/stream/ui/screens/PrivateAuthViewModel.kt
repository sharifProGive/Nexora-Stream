/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentora.nexora.stream.data.database.entities.PrivateFriendProfileEntity
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * PrivateAuthViewModel (Pillar 4: Separate Private Identity & Chat Enclave).
 * Enforces real-time handle uniqueness validation with 300ms debounce.
 * Isolates private friend credentials from public channel indices.
 */
@OptIn(FlowPreview::class)
class PrivateAuthViewModel(
    private val repository: NexoraStreamRepository
) : ViewModel() {

    private val _handleInput = MutableStateFlow("")
    val handleInput: StateFlow<String> = _handleInput.asStateFlow()

    private val _displayNameInput = MutableStateFlow("")
    val displayNameInput: StateFlow<String> = _displayNameInput.asStateFlow()

    private val _isHandleTaken = MutableStateFlow(false)
    val isHandleTaken: StateFlow<Boolean> = _isHandleTaken.asStateFlow()

    private val _isCheckingHandle = MutableStateFlow(false)
    val isCheckingHandle: StateFlow<Boolean> = _isCheckingHandle.asStateFlow()

    val isFormValid: StateFlow<Boolean> = combine(
        _handleInput,
        _displayNameInput,
        _isHandleTaken,
        _isCheckingHandle
    ) { handle, name, taken, checking ->
        val cleanHandle = handle.removePrefix("@").trim()
        cleanHandle.length >= 3 && name.trim().isNotBlank() && !taken && !checking
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // 300ms debounce validation for handle uniqueness
        _handleInput
            .debounce(300)
            .distinctUntilChanged()
            .onEach { rawHandle ->
                val clean = rawHandle.removePrefix("@").trim().lowercase()
                if (clean.length >= 3) {
                    _isCheckingHandle.value = true
                    val taken = repository.isHandleTaken(clean)
                    _isHandleTaken.value = taken
                    _isCheckingHandle.value = false
                } else {
                    _isHandleTaken.value = false
                    _isCheckingHandle.value = false
                }
            }
            .launchIn(viewModelScope)
    }

    fun onHandleChanged(newHandle: String) {
        val sanitized = if (newHandle.startsWith("@")) newHandle else "@$newHandle"
        _handleInput.value = sanitized.filter { it.isLetterOrDigit() || it == '@' || it == '_' }
    }

    fun onDisplayNameChanged(newName: String) {
        _displayNameInput.value = newName
    }

    fun registerPrivateProfile(onSuccess: () -> Unit) {
        val handle = _handleInput.value.removePrefix("@").trim().lowercase()
        val displayName = _displayNameInput.value.trim()

        if (handle.length < 3 || displayName.isBlank() || _isHandleTaken.value) return

        viewModelScope.launch {
            val profile = PrivateFriendProfileEntity(
                handle = "@$handle",
                displayName = displayName,
                avatarUri = "",
                statusBio = "Nexora Stream Private Enclave Member",
                isUnlocked = true,
                createdAt = System.currentTimeMillis()
            )
            repository.savePrivateProfile(profile)
            onSuccess()
        }
    }
}
