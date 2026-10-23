/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.*
import com.zentora.nexora.stream.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

// ---------------------------------------------------------------------------
// Recovery state exposed to the UI ("Restoring your account...")
// ---------------------------------------------------------------------------

enum class RecoveryStep { VERIFYING, REGISTERING, RESTORING, SYNCING }
enum class FailureReason { UNAUTHORIZED, SERVER_UNAVAILABLE, UNKNOWN }

sealed interface RecoveryState {
    data object Idle : RecoveryState
    data object NeedsSignIn : RecoveryState
    /** attempt > 1 means the server is cold-starting -> show "Waking up the server...". */
    data class Restoring(val step: RecoveryStep, val attempt: Int = 1) : RecoveryState
    data class Ready(val nexoraId: String, val isNewUser: Boolean) : RecoveryState
    data class Failed(val reason: FailureReason, val message: String) : RecoveryState
}

class NexoraStreamRepository(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = AccountBackend(BACKEND_BASE_URL)

    /** Serialises recovery / background resync so they can never race each other. */
    private val recoveryMutex = Mutex()

    /** Set from the app layer (silent Google token refresh). Falls back to the last sign-in token. */
    @Volatile var tokenProvider: IdTokenProvider? = null
    @Volatile private var cachedIdToken: String? = null

    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    val allVideos: Flow<List<VideoEntity>> = dao.getAllVideos()
    val allChannels: Flow<List<ChannelEntity>> = dao.getAllChannels()
    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()
    val watchLaterInteractions: Flow<List<InteractionEntity>> = dao.getWatchLaterInteractions()
    val likedInteractions: Flow<List<InteractionEntity>> = dao.getLikedInteractions()
    val allCommunityPosts: Flow<List<CommunityPostEntity>> = dao.getAllCommunityPosts()

    fun getVideosByAuthor(authorChannelId: String): Flow<List<VideoEntity>> =
        dao.getVideosByAuthor(authorChannelId)

    private val _isKidsMode = MutableStateFlow(false)
    val isKidsMode: StateFlow<Boolean> = _isKidsMode.asStateFlow()

    private val _masterPin = MutableStateFlow("1234")
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    private val _isDataSaver = MutableStateFlow(false)
    val isDataSaver: StateFlow<Boolean> = _isDataSaver.asStateFlow()

    init {
        SessionStore.init(context)
        // Only warms the Render instance. NO identity is created and NO recovery runs here any more.
        scope.launch { backend.warmUp() }
    }

    // =====================================================================
    // SESSION GATE + RECOVERY
    //   a. Google sign-in (UI)  -> idToken
    //   b. GET  /get-profile    -> 200 original id | 404 new user
    //   c. 404 -> POST /register (server derives id from verified `sub`)
    //   d. existing -> save id -> restoreAccountFromCentralVaults()
    //   e. syncFeedFromCentralVaults()
    // =====================================================================

    /** Call once from the gate. No verified session on disk => user must sign in first. */
    fun beginSession() {
        val s = SessionStore.current()
        if (s == null) {
            _recoveryState.value = RecoveryState.NeedsSignIn
        } else {
            _recoveryState.value = RecoveryState.Ready(s.nexoraId, isNewUser = false)
            scope.launch { backgroundResync() }
        }
    }

    fun reportSignInCancelled() {
        _recoveryState.value = RecoveryState.NeedsSignIn
    }

    fun reportSignInFailure(message: String) {
        _recoveryState.value = RecoveryState.Failed(FailureReason.UNAUTHORIZED, message)
    }

    suspend fun currentIdToken(): String? =
        tokenProvider?.idToken(false) ?: cachedIdToken

    /** Idempotent: safe to call again after a Failed state or a crash half-way through. */
    suspend fun recoverOrRegister(idToken: String, hint: GoogleProfileHint): RecoveryState {
        cachedIdToken = idToken
        val result = recoveryMutex.withLock { performRecovery(idToken, hint) }
        _recoveryState.value = result
        return result
    }

    private suspend fun performRecovery(idToken: String, hint: GoogleProfileHint): RecoveryState {
        try {
            // b. verify via backend
            _recoveryState.value = RecoveryState.Restoring(RecoveryStep.VERIFYING)
            var isNew = false
            val identity: CloudIdentity = when (
                val r = backend.withBackoff(
                    onRetry = { a -> _recoveryState.value = RecoveryState.Restoring(RecoveryStep.VERIFYING, a) }
                ) { backend.fetchProfile(idToken) }
            ) {
                is BackendResult.Ok -> r.value
                // c. unknown account -> register (server derives the id)
                BackendResult.NotFound -> {
                    isNew = true
                    _recoveryState.value = RecoveryState.Restoring(RecoveryStep.REGISTERING)
                    val reg = backend.withBackoff(
                        onRetry = { a -> _recoveryState.value = RecoveryState.Restoring(RecoveryStep.REGISTERING, a) }
                    ) { backend.register(idToken, hint) }
                    if (reg is BackendResult.Ok) reg.value else return failure(reg)
                }
                else -> return failure(r)
            }

            // Persist the SERVER-confirmed id before any data step, so a crash resumes with the right id.
            SessionStore.save(AccountSession(identity.nexoraId, hint.email, hint.displayName, hint.avatarUri))

            if (isNew) {
                ensureOwnChannel(hint)
            } else {
                // d. restore profile + uploaded videos under the ORIGINAL id
                _recoveryState.value = RecoveryState.Restoring(RecoveryStep.RESTORING)
                val restored = restoreAccountFromCentralVaults()
                if (restored.isFailure) {
                    return RecoveryState.Failed(
                        FailureReason.SERVER_UNAVAILABLE,
                        "Could not restore your account yet. Please try again."
                    )
                }
            }

            // e. feed sync (non-critical: a failure here must not block the user)
            _recoveryState.value = RecoveryState.Restoring(RecoveryStep.SYNCING)
            syncFeedFromCentralVaults().onFailure {
                Log.w(TAG, "Feed sync failed after recovery: ${it.message}")
            }

            return RecoveryState.Ready(identity.nexoraId, isNewUser = isNew)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed: ${e.message}")
            return RecoveryState.Failed(FailureReason.UNKNOWN, "Something went wrong. Please try again.")
        }
    }

    private fun failure(r: BackendResult<*>): RecoveryState.Failed = when (r) {
        BackendResult.Unauthorized ->
            RecoveryState.Failed(FailureReason.UNAUTHORIZED, "We couldn't verify your Google sign-in. Please sign in again.")
        is BackendResult.Retryable ->
            RecoveryState.Failed(FailureReason.SERVER_UNAVAILABLE, "The server is not responding. Check your connection and try again.")
        is BackendResult.Fatal ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Unexpected server response (${r.code}).")
        BackendResult.NotFound ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Account not found.")
        is BackendResult.Ok ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Unexpected state.")
    }

    /** Signed-in app start: refresh data quietly, never blocks the UI. */
    private suspend fun backgroundResync() {
        recoveryMutex.withLock {
            try {
                restoreAccountFromCentralVaults()
                syncFeedFromCentralVaults()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background resync failed: ${e.message}")
            }
        }
    }

    private suspend fun ensureOwnChannel(hint: GoogleProfileHint) {
        dao.insertChannel(
            ChannelEntity(
                channelId = OWN_CHANNEL_ID,
                channelName = hint.displayName.ifBlank { "Nexora Creator" },
                avatarUri = hint.avatarUri.ifBlank { DEFAULT_AVATAR_URL },
                bannerUri = DEFAULT_BANNER_URL,
                subscriberCount = 0L,
                isSubscribed = false,
                creatorLevel = NexoraIdManager.calculateCreatorLevel(0L, 0L),
                hasZentoraBadge = true,
                description = "Official channel of Zentora CLC."
            )
        )
    }

    // =====================================================================
    // REMOTE -> ROOM (idempotent: REPLACE/upsert keyed by primary key, batched in one transaction)
    // =====================================================================

    suspend fun syncFeedFromCentralVaults(): Result<Int> = try {
        val r = backend.withBackoff(maxAttempts = 4) {
            backend.apiCall { NexoraNetworkClient.apiService.getFeed() }
        }
        when (r) {
            is BackendResult.Ok -> Result.success(upsertVideos(r.value.videos))
            BackendResult.NotFound -> Result.success(0)
            else -> Result.failure(IllegalStateException("feed sync failed: $r"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Feed sync error: ${e.message}")
        Result.failure(e)
    }

    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        val id = SessionStore.current()?.nexoraId
            ?: return Result.failure(IllegalStateException("No signed-in session"))
        return try {
            when (val r = backend.withBackoff { backend.apiCall { NexoraNetworkClient.apiService.restoreUserProfile(id) } }) {
                is BackendResult.Ok -> {
                    val profile = r.value
                    db.withTransaction {
                        dao.insertChannel(
                            ChannelEntity(
                                channelId = OWN_CHANNEL_ID,
                                channelName = profile.username.ifBlank { "Zentora Creator" },
                                avatarUri = profile.avatarUri.ifBlank { DEFAULT_AVATAR_URL },
                                bannerUri = DEFAULT_BANNER_URL,
                                subscriberCount = profile.subscriberCount,
                                isSubscribed = false,
                                creatorLevel = profile.creatorLevel,
                                hasZentoraBadge = true,
                                description = "Official channel of Zentora CLC. Restored from Central Vaults."
                            )
                        )
                        upsertVideos(profile.uploadedVideos)
                    }
                    Result.success(true)
                }
                BackendResult.NotFound -> Result.success(false) // account exists but nothing uploaded yet
                else -> Result.failure(IllegalStateException("restore failed: $r"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Account restore error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun searchGlobalVaults(query: String) {
        if (query.isBlank()) return
        scope.launch {
            try {
                val r = backend.withBackoff(maxAttempts = 2) {
                    backend.apiCall { NexoraNetworkClient.apiService.searchGlobalVideos(query) }
                }
                if (r is BackendResult.Ok) upsertVideos(r.value.videos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Search error: ${e.message}")
            }
        }
    }

    /** One transaction, per-row isolation: one bad row never aborts the batch. Returns rows written. */
    private suspend fun upsertVideos(items: List<RemoteVideo>): Int = db.withTransaction {
        var written = 0
        for (item in items) {
            try {
                dao.insertVideo(item.toEntity())   // @Insert(onConflict = REPLACE) / @Upsert, keyed by id
                ensureInteractionExists(item.id)   // never overwrites local like/watch-later state
                written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Skipping bad video row ${item.id}: ${e.message}")
            }
        }
        written
    }

    // Rename `RemoteVideo` to the DTO class used by getFeed()/searchGlobalVideos()/restoreUserProfile().
    private fun RemoteVideo.toEntity() = VideoEntity(
        id = id,
        localUri = localUri,
        title = title,
        description = description,
        category = category,
        tags = tags,
        timestamp = timestamp,
        duration = duration,
        viewCount = viewCount,
        chapterMarkersJson = chapterMarkersJson,
        thumbnailUri = thumbnailUri,
        authorChannelId = authorChannelId,
        collabChannelId = collabChannelId,
        isPremiere = isPremiere,
        premiereScheduledTimeMs = premiereScheduledTimeMs
    )

    private suspend fun ensureInteractionExists(videoId: String) {
        if (dao.getInteractionSync(videoId) == null) {
            dao.insertInteraction(
                InteractionEntity(
                    videoId = videoId,
                    likesCount = 0L,
                    dislikesCount = 0L,
                    isLikedByMe = false,
                    isDislikedByMe = false,
                    savedToWatchLater = false,
                    sparksReceived = 0L
                )
            )
        }
    }

    // =====================================================================
    // PRIVATE PROFILE DATA (phone / billing) – separate, authenticated, never part of recovery
    // =====================================================================

    suspend fun saveUserDataToCloud(phoneNumber: String, billingInfo: String): Boolean {
        val token = currentIdToken() ?: return false
        return backend.withBackoff(maxAttempts = 3) {
            backend.savePrivate(token, phoneNumber, billingInfo)
        } is BackendResult.Ok
    }

    suspend fun restoreUserDataFromCloud(onDataRestored: (phone: String, billing: String) -> Unit) {
        val token = currentIdToken() ?: return
        val r = backend.withBackoff(maxAttempts = 3) { backend.fetchPrivate(token) }
        if (r is BackendResult.Ok) {
            withContext(Dispatchers.Main) { onDataRestored(r.value.phoneNumber, r.value.billingInfo) }
        }
    }

    // =====================================================================
    // INTERACTIONS (Vault 3). Server resolves the user from the bearer token; ids in bodies are not trusted.
    // =====================================================================

    /** Runs [block] in the background only for a verified session; failures are logged, not silent. */
    private fun syncInBackground(tag: String, block: suspend (nexoraId: String) -> Unit) {
        val id = SessionStore.current()?.nexoraId ?: return
        scope.launch {
            try {
                block(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$tag background sync failed: ${e.message}")
            }
        }
    }

    suspend fun recordView(videoId: String, durationMs: Long, positionMs: Long) {
        dao.incrementViewCount(videoId)
        dao.insertHistory(HistoryEntity(videoId = videoId, playbackPositionMs = positionMs, timestamp = System.currentTimeMillis()))
        syncInBackground("view") { id ->
            NexoraNetworkClient.apiService.sendInteraction(InteractRequest(videoId = videoId, viewDelta = 1L, nexoraId = id))
        }
    }

    suspend fun uploadVideo(
        title: String,
        description: String,
        category: String,
        tags: String,
        localUri: String,
        thumbnailUri: String,
        collabChannelId: String? = null,
        isPremiere: Boolean = false,
        scheduledTimeMs: Long? = null,
        duration: Long = 60000L,
        chapterMarkersJson: String = "[]",
        visibility: String = "Public",
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
        notifySubscribers: Boolean = true
    ): VideoEntity {
        val newVideo = VideoEntity(
            id = "vid_${UUID.randomUUID()}",
            localUri = localUri,
            title = title,
            description = description,
            category = category,
            tags = tags,
            timestamp = System.currentTimeMillis(),
            duration = duration,
            viewCount = 0L,
            chapterMarkersJson = chapterMarkersJson,
            thumbnailUri = thumbnailUri,
            authorChannelId = OWN_CHANNEL_ID,
            collabChannelId = collabChannelId,
            isPremiere = isPremiere,
            premiereScheduledTimeMs = scheduledTimeMs
        )
        dao.insertVideo(newVideo)
        ensureInteractionExists(newVideo.id)

        // IMPORTANT: `localUri` must already be a REMOTE url (Vault 4 / Cloudinary) before this point,
        // otherwise the restored video is unplayable after a reinstall. See IMPLEMENTATION_GUIDE.md.
        scope.launch {
            val token = currentIdToken() ?: return@launch
            val payload = JSONObject().apply {
                put("videoId", newVideo.id)
                put("title", title)
                put("description", description)
                put("category", category)
                put("tags", tags)
                put("videoUrl", localUri)
                put("thumbnailUrl", thumbnailUri)
                put("visibility", visibility)
            }
            val r = backend.withBackoff(maxAttempts = 5) { backend.publishVideo(token, payload) }
            if (r !is BackendResult.Ok) Log.w(TAG, "Publish not confirmed: $r (queue for WorkManager retry)")
        }
        return newVideo
    }

    suspend fun updateVideoMetadata(id: String, title: String, description: String, category: String, tags: String) {
        val existing = dao.getVideoByIdSync(id) ?: return
        dao.updateVideo(existing.copy(title = title, description = description, category = category, tags = tags))
    }

    suspend fun deleteVideo(id: String) {
        dao.deleteVideoById(id)
        dao.deleteHistoryById(id)
    }

    fun getInteraction(videoId: String): Flow<InteractionEntity?> = dao.getInteraction(videoId)

    suspend fun toggleLike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newLiked = !current.isLikedByMe
        val newLikes = if (newLiked) current.likesCount + 1 else maxOf(0L, current.likesCount - 1)
        dao.updateInteraction(current.copy(isLikedByMe = newLiked, likesCount = newLikes))
        syncInBackground("like") { id ->
            NexoraNetworkClient.apiService.sendInteraction(
                InteractRequest(videoId = videoId, likeDelta = if (newLiked) 1L else -1L, nexoraId = id)
            )
        }
    }

    suspend fun toggleDislike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newDisliked = !current.isDislikedByMe
        val newDislikes = if (newDisliked) current.dislikesCount + 1 else maxOf(0L, current.dislikesCount - 1)
        dao.updateInteraction(current.copy(isDislikedByMe = newDisliked, dislikesCount = newDislikes))
    }

    suspend fun toggleWatchLater(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        dao.updateInteraction(current.copy(savedToWatchLater = !current.savedToWatchLater))
    }

    suspend fun sendSparks(videoId: String, sparks: Long) {
        dao.addSparks(videoId, sparks)
        syncInBackground("sparks") { id ->
            NexoraNetworkClient.apiService.sendInteraction(
                InteractRequest(videoId = videoId, sparksDelta = sparks, nexoraId = id)
            )
        }
    }

    // =====================================================================
    // COMMENTS (Vault 5 filter applied before anything is stored or sent)
    // =====================================================================

    fun getComments(videoId: String): Flow<List<CommentEntity>> = dao.getCommentsForVideo(videoId)

    suspend fun addComment(videoId: String, authorName: String, text: String): Boolean {
        val isSafe = CommunityShield.isContentSafe(text)
        val sanitized = if (isSafe) text else CommunityShield.filterText(text)
        val comment = CommentEntity(
            commentId = "c_${UUID.randomUUID()}",
            videoId = videoId,
            authorName = authorName,
            commentText = sanitized,
            timestamp = System.currentTimeMillis(),
            likeCount = 0L,
            isFiltered = !isSafe,
            isLikedByMe = false
        )
        dao.insertComment(comment)
        syncInBackground("comment") { id ->
            NexoraNetworkClient.apiService.postComment(
                PostCommentRequest(
                    commentId = comment.commentId,
                    videoId = videoId,
                    authorName = authorName,
                    commentText = sanitized,
                    nexoraId = id,
                    timestamp = comment.timestamp
                )
            )
        }
        return isSafe
    }

    suspend fun toggleCommentLike(comment: CommentEntity) {
        val newLiked = !comment.isLikedByMe
        val newCount = if (newLiked) comment.likeCount + 1 else maxOf(0L, comment.likeCount - 1)
        dao.updateComment(comment.copy(isLikedByMe = newLiked, likeCount = newCount))
    }

    // =====================================================================
    // CHANNELS / NOTES / COMMUNITY / SETTINGS / PRIVATE FRIENDS (unchanged behaviour)
    // =====================================================================

    fun getChannel(channelId: String): Flow<ChannelEntity?> = dao.getChannelById(channelId)

    suspend fun toggleSubscribe(channelId: String) {
        val channel = dao.getChannelByIdSync(channelId) ?: return
        val newSubscribed = !channel.isSubscribed
        val newSubs = if (newSubscribed) channel.subscriberCount + 1 else maxOf(0L, channel.subscriberCount - 1)
        val newLevel = NexoraIdManager.calculateCreatorLevel(newSubs, 0L)
        dao.updateChannel(channel.copy(isSubscribed = newSubscribed, subscriberCount = newSubs, creatorLevel = newLevel))
    }

    fun getNotes(videoId: String): Flow<List<NotesEntity>> = dao.getNotesForVideo(videoId)

    suspend fun addNote(videoId: String, timestampMs: Long, text: String) {
        dao.insertNote(NotesEntity(noteId = "note_${UUID.randomUUID()}", videoId = videoId, timestampMs = timestampMs, noteText = text))
    }

    suspend fun deleteNote(noteId: String) = dao.deleteNote(noteId)

    suspend fun createCommunityPost(channelId: String, contentText: String, imageUri: String?, pollOptionsJson: String?) {
        dao.insertCommunityPost(
            CommunityPostEntity(
                postId = "post_${UUID.randomUUID()}",
                channelId = channelId,
                contentText = contentText,
                imageUri = imageUri,
                pollOptionsJson = pollOptionsJson,
                upvotes = 0L,
                isUpvotedByMe = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePostUpvote(post: CommunityPostEntity) {
        val newUpvoted = !post.isUpvotedByMe
        val newCount = if (newUpvoted) post.upvotes + 1 else maxOf(0L, post.upvotes - 1)
        dao.updateCommunityPost(post.copy(isUpvotedByMe = newUpvoted, upvotes = newCount))
    }

    fun setKidsMode(enabled: Boolean, enteredPin: String): Boolean {
        if (enteredPin == _masterPin.value) { _isKidsMode.value = enabled; return true }
        return false
    }

    fun updateMasterPin(oldPin: String, newPin: String): Boolean {
        if (oldPin == _masterPin.value && newPin.length == 4) { _masterPin.value = newPin; return true }
        return false
    }

    fun setDataSaver(enabled: Boolean) { _isDataSaver.value = enabled }

    suspend fun clearHistory() = dao.clearHistory()
    suspend fun removeFromHistory(videoId: String) = dao.deleteHistoryById(videoId)
    suspend fun upsertChannel(channel: ChannelEntity) = dao.insertChannel(channel)

    val privateProfile: Flow<PrivateFriendProfileEntity?> = dao.getPrivateProfile()
    suspend fun getPrivateProfile(): PrivateFriendProfileEntity? = dao.getPrivateProfileSync()
    suspend fun isHandleTaken(handle: String): Boolean = dao.countHandleOccurrences(handle.trim()) > 0
    suspend fun savePrivateProfile(profile: PrivateFriendProfileEntity) = dao.insertPrivateProfile(profile)
    suspend fun updatePrivateProfile(profile: PrivateFriendProfileEntity) = dao.updatePrivateProfile(profile)

    fun getAllPrivateFriends(): Flow<List<PrivateFriendEntity>> = dao.getAllPrivateFriends()
    fun searchPrivateFriends(query: String): Flow<List<PrivateFriendEntity>> = dao.searchPrivateFriends(query)
    suspend fun getPrivateFriend(handle: String): PrivateFriendEntity? = dao.getPrivateFriendByHandle(handle)
    suspend fun addPrivateFriend(friend: PrivateFriendEntity) = dao.insertPrivateFriend(friend)

    fun getMessagesForConversation(handle: String): Flow<List<PrivateChatMessageEntity>> =
        dao.getMessagesForConversation(handle)

    suspend fun sendChatMessage(conversationHandle: String, senderHandle: String, text: String, mediaUri: String? = null) {
        dao.insertChatMessage(
            PrivateChatMessageEntity(
                messageId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                conversationHandle = conversationHandle,
                senderHandle = senderHandle,
                text = text,
                mediaUri = mediaUri,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                isRead = true
            )
        )
    }

    suspend fun markConversationAsRead(handle: String) {
        dao.markMessagesAsRead(handle)
        dao.clearUnreadCount(handle)
    }

    companion object {
        private const val TAG = "NexoraAccountSync"
        private const val BACKEND_BASE_URL = "https://nexora-backend-fq54.onrender.com"
        private const val OWN_CHANNEL_ID = "ch_zentora_core"
        private const val DEFAULT_AVATAR_URL =
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80"
        private const val DEFAULT_BANNER_URL =
            "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80"

        @Volatile
        private var INSTANCE: NexoraStreamRepository? = null

        fun getInstance(context: Context): NexoraStreamRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NexoraStreamRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
