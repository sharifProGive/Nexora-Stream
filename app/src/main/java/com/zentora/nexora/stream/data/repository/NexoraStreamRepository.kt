/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import android.util.Log
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.*
import com.zentora.nexora.stream.engine.CommunityShield
import com.zentora.nexora.stream.engine.InteractRequest
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.engine.NexoraNetworkClient
import com.zentora.nexora.stream.engine.PostCommentRequest
import com.zentora.nexora.stream.engine.RegisterUserRequest
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Data returned by /api/v1/user/get-profile.
 * [nexoraId] is the ORIGINAL id stored on the server for the user's Google email.
 */
data class CloudUserProfile(
    val phoneNumber: String,
    val billingInfo: String,
    val nexoraId: String
)

class NexoraStreamRepository(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Prevents the startup recovery and any manual call from racing each other.
    private val recoveryMutex = Mutex()

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
        scope.launch { runStartupRecovery() }
    }

    // ---------------------------------------------------------------------
    // Startup recovery: email -> cloud profile -> original nexoraId ->
    // restore account/videos -> sync feed
    // ---------------------------------------------------------------------
    private suspend fun runStartupRecovery() {
        recoveryMutex.withLock {
            try {
                seedInitialCoreDataIfNeeded()

                // Step 1: recover the ORIGINAL nexoraId using the Google email.
                val session = NexoraIdManager.getSession()
                if (session.email.isNotBlank()) {
                    val profile = fetchCloudProfile(session.email)
                    if (profile != null) {
                        Log.d(
                            "NexoraAccountSync",
                            "Cloud profile found for ${session.email} (Phone: ${profile.phoneNumber})"
                        )
                        if (profile.nexoraId.isNotBlank() && profile.nexoraId != session.nexoraId) {
                            // Safely persist the original id via SharedPreferences to avoid missing method errors.
                            val prefs = context.applicationContext.getSharedPreferences("nexora_session_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("nexora_id", profile.nexoraId).apply()
                            Log.d("NexoraAccountSync", "Local nexoraId replaced with original id from cloud")
                        }
                    } else {
                        Log.w("NexoraAccountSync", "No cloud profile available for ${session.email}")
                    }
                }

                // Step 2: restore account + uploaded videos using the (now correct) id.
                val restoreResult = restoreAccountFromCentralVaults()
                Log.d("NexoraAccountSync", "Account restore result on startup: $restoreResult")

                // Step 3: pull the global feed into Room (Vault 1).
                val syncResult = syncFeedFromCentralVaults()
                Log.d("NexoraAccountSync", "Feed sync result on startup: $syncResult")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Startup recovery failed: ${e.message}")
            }
        }
    }

    /**
     * Fetches the cloud profile (phone, billing and the original nexoraId) for [email].
     * Returns null on any network/parse failure or if the server reports no profile.
     */
    suspend fun fetchCloudProfile(email: String): CloudUserProfile? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
            val url = URL("$BACKEND_BASE_URL/api/v1/user/get-profile?email=$encodedEmail")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w("NexoraAccountSync", "get-profile HTTP ${connection.responseCode}")
                return@withContext null
            }

            val respText = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                .use { it.readText() }
            val json = JSONObject(respText)
            if (!json.optBoolean("success", false)) return@withContext null

            // Some backends nest the data under "profile"/"data"; support both shapes.
            val source = json.optJSONObject("profile") ?: json.optJSONObject("data") ?: json

            val originalId = source.optString("nexoraId", "").ifBlank {
                source.optString("nexora_id", "").ifBlank { json.optString("nexoraId", "") }
            }

            CloudUserProfile(
                phoneNumber = source.optString("phoneNumber", json.optString("phoneNumber", "")),
                billingInfo = source.optString("billingInfo", json.optString("billingInfo", "")),
                nexoraId = originalId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NexoraAccountSync", "Error fetching profile: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun syncFeedFromCentralVaults(): Result<Int> {
        return try {
            val response = NexoraNetworkClient.apiService.getFeed()
            val feed = response.body()
            if (response.isSuccessful && feed != null) {
                var inserted = 0
                for (item in feed.videos) {
                    try {
                        val video = VideoEntity(
                            id = item.id,
                            localUri = item.localUri,
                            title = item.title,
                            description = item.description,
                            category = item.category,
                            tags = item.tags,
                            timestamp = item.timestamp,
                            duration = item.duration,
                            viewCount = item.viewCount,
                            chapterMarkersJson = item.chapterMarkersJson,
                            thumbnailUri = item.thumbnailUri,
                            authorChannelId = item.authorChannelId,
                            collabChannelId = item.collabChannelId,
                            isPremiere = item.isPremiere,
                            premiereScheduledTimeMs = item.premiereScheduledTimeMs
                        )
                        dao.insertVideo(video)
                        ensureInteractionExists(item.id)
                        inserted++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("NexoraAccountSync", "Skipping bad feed item: ${e.message}")
                    }
                }
                Result.success(inserted)
            } else {
                Result.success(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NexoraAccountSync", "Feed sync error: ${e.message}")
            Result.failure(e)
        }
    }

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

    suspend fun registerUserSessionWithCentralVault() {
        scope.launch {
            try {
                val session = NexoraIdManager.getSession()
                NexoraNetworkClient.apiService.registerUser(
                    RegisterUserRequest(
                        nexoraId = session.nexoraId,
                        username = session.username,
                        email = session.email,
                        handle = session.handle,
                        avatarUri = session.avatarUri,
                        zentoraAuthToken = session.zentoraAuthToken
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Register session error: ${e.message}")
            }
        }
    }

    suspend fun searchGlobalVaults(query: String) {
        if (query.isBlank()) return
        scope.launch {
            try {
                val response = NexoraNetworkClient.apiService.searchGlobalVideos(query)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    for (item in body.videos) {
                        try {
                            dao.insertVideo(
                                VideoEntity(
                                    id = item.id,
                                    localUri = item.localUri,
                                    title = item.title,
                                    description = item.description,
                                    category = item.category,
                                    tags = item.tags,
                                    timestamp = item.timestamp,
                                    duration = item.duration,
                                    viewCount = item.viewCount,
                                    chapterMarkersJson = item.chapterMarkersJson,
                                    thumbnailUri = item.thumbnailUri,
                                    authorChannelId = item.authorChannelId,
                                    collabChannelId = item.collabChannelId,
                                    isPremiere = item.isPremiere,
                                    premiereScheduledTimeMs = item.premiereScheduledTimeMs
                                )
                            )
                            ensureInteractionExists(item.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("NexoraAccountSync", "Skipping bad search item: ${e.message}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Search error: ${e.message}")
            }
        }
    }

    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        return try {
            val session = NexoraIdManager.getSession()
            val response = NexoraNetworkClient.apiService.restoreUserProfile(session.nexoraId)
            val profile = response.body()
            if (response.isSuccessful && profile != null) {
                val channel = ChannelEntity(
                    channelId = "ch_zentora_core",
                    channelName = if (profile.username.isNotBlank()) profile.username else "Zentora Creator",
                    avatarUri = profile.avatarUri.ifBlank { DEFAULT_AVATAR_URL },
                    bannerUri = DEFAULT_BANNER_URL,
                    subscriberCount = profile.subscriberCount,
                    isSubscribed = false,
                    creatorLevel = profile.creatorLevel,
                    hasZentoraBadge = true,
                    description = "Official channel of Zentora CLC. Restored from Central Vaults."
                )
                dao.insertChannel(channel)

                for (item in profile.uploadedVideos) {
                    try {
                        dao.insertVideo(
                            VideoEntity(
                                id = item.id,
                                localUri = item.localUri,
                                title = item.title,
                                description = item.description,
                                category = item.category,
                                tags = item.tags,
                                timestamp = item.timestamp,
                                duration = item.duration,
                                viewCount = item.viewCount,
                                chapterMarkersJson = item.chapterMarkersJson,
                                thumbnailUri = item.thumbnailUri,
                                authorChannelId = item.authorChannelId,
                                collabChannelId = item.collabChannelId,
                                isPremiere = item.isPremiere,
                                premiereScheduledTimeMs = item.premiereScheduledTimeMs
                            )
                        )
                        ensureInteractionExists(item.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("NexoraAccountSync", "Skipping bad restored video: ${e.message}")
                    }
                }
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NexoraAccountSync", "Account restore error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveUserDataToCloud(userGoogleEmail: String, phoneNumber: String, billingInfo: String) {
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("email", userGoogleEmail)
                put("phoneNumber", phoneNumber)
                put("billingInfo", billingInfo)
                put("nexoraId", NexoraIdManager.getSession().nexoraId)
            }
            postJson("/api/v1/user/save-profile", payload, "NexoraAccountSync")
        }
    }

    suspend fun restoreUserDataFromCloud(userGoogleEmail: String, onDataRestored: (String, String) -> Unit) {
        val profile = fetchCloudProfile(userGoogleEmail) ?: return
        withContext(Dispatchers.Main) {
            onDataRestored(profile.phoneNumber, profile.billingInfo)
        }
    }

    suspend fun recordView(videoId: String, durationMs: Long, positionMs: Long) {
        dao.incrementViewCount(videoId)
        dao.insertHistory(
            HistoryEntity(
                videoId = videoId,
                playbackPositionMs = positionMs,
                timestamp = System.currentTimeMillis()
            )
        )
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        viewDelta = 1L,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
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
            authorChannelId = "ch_zentora_core",
            collabChannelId = collabChannelId,
            isPremiere = isPremiere,
            premiereScheduledTimeMs = scheduledTimeMs
        )
        dao.insertVideo(newVideo)
        ensureInteractionExists(newVideo.id)

        scope.launch {
            val session = NexoraIdManager.getSession()
            val payload = JSONObject().apply {
                put("title", title)
                put("description", description)
                put("category", category)
                put("tags", tags)
                put("videoUrl", localUri)
                put("thumbnailUrl", thumbnailUri)
                put("channelName", "Zentora Creator")
                put("nexoraId", session.nexoraId)
                put("email", session.email)
            }
            postJson("/api/v1/videos/publish", payload, "ZentoraVault")
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

        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        likeDelta = if (newLiked) 1L else -1L,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
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
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        sparksDelta = sparks,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

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

        scope.launch {
            try {
                val session = NexoraIdManager.getSession()
                NexoraNetworkClient.apiService.postComment(
                    PostCommentRequest(
                        commentId = comment.commentId,
                        videoId = videoId,
                        authorName = authorName,
                        commentText = sanitized,
                        nexoraId = session.nexoraId,
                        timestamp = comment.timestamp
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return isSafe
    }

    suspend fun toggleCommentLike(comment: CommentEntity) {
        val newLiked = !comment.isLikedByMe
        val newCount = if (newLiked) comment.likeCount + 1 else maxOf(0L, comment.likeCount - 1)
        dao.updateComment(comment.copy(isLikedByMe = newLiked, likeCount = newCount))
    }

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
        val note = NotesEntity(
            noteId = "note_${UUID.randomUUID()}",
            videoId = videoId,
            timestampMs = timestampMs,
            noteText = text
        )
        dao.insertNote(note)
    }

    suspend fun deleteNote(noteId: String) {
        dao.deleteNote(noteId)
    }

    suspend fun createCommunityPost(
        channelId: String,
        contentText: String,
        imageUri: String?,
        pollOptionsJson: String?
    ) {
        val post = CommunityPostEntity(
            postId = "post_${UUID.randomUUID()}",
            channelId = channelId,
            contentText = contentText,
            imageUri = imageUri,
            pollOptionsJson = pollOptionsJson,
            upvotes = 0L,
            isUpvotedByMe = false,
            timestamp = System.currentTimeMillis()
        )
        dao.insertCommunityPost(post)
    }

    suspend fun togglePostUpvote(post: CommunityPostEntity) {
        val newUpvoted = !post.isUpvotedByMe
        val newCount = if (newUpvoted) post.upvotes + 1 else maxOf(0L, post.upvotes - 1)
        dao.updateCommunityPost(post.copy(isUpvotedByMe = newUpvoted, upvotes = newCount))
    }

    fun setKidsMode(enabled: Boolean, enteredPin: String): Boolean {
        if (enteredPin == _masterPin.value) {
            _isKidsMode.value = enabled
            return true
        }
        return false
    }

    fun updateMasterPin(oldPin: String, newPin: String): Boolean {
        if (oldPin == _masterPin.value && newPin.length == 4) {
            _masterPin.value = newPin
            return true
        }
        return false
    }

    fun setDataSaver(enabled: Boolean) {
        _isDataSaver.value = enabled
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun removeFromHistory(videoId: String) {
        dao.deleteHistoryById(videoId)
    }

    suspend fun upsertChannel(channel: ChannelEntity) {
        dao.insertChannel(channel)
    }

    val privateProfile: Flow<PrivateFriendProfileEntity?> = dao.getPrivateProfile()

    suspend fun getPrivateProfile(): PrivateFriendProfileEntity? = dao.getPrivateProfileSync()

    suspend fun isHandleTaken(handle: String): Boolean = dao.countHandleOccurrences(handle.trim()) > 0

    suspend fun savePrivateProfile(profile: PrivateFriendProfileEntity) {
        dao.insertPrivateProfile(profile)
    }

    suspend fun updatePrivateProfile(profile: PrivateFriendProfileEntity) {
        dao.updatePrivateProfile(profile)
    }

    fun getAllPrivateFriends(): Flow<List<PrivateFriendEntity>> = dao.getAllPrivateFriends()

    fun searchPrivateFriends(query: String): Flow<List<PrivateFriendEntity>> = dao.searchPrivateFriends(query)

    suspend fun getPrivateFriend(handle: String): PrivateFriendEntity? = dao.getPrivateFriendByHandle(handle)

    suspend fun addPrivateFriend(friend: PrivateFriendEntity) {
        dao.insertPrivateFriend(friend)
    }

    fun getMessagesForConversation(handle: String): Flow<List<PrivateChatMessageEntity>> =
        dao.getMessagesForConversation(handle)

    suspend fun sendChatMessage(
        conversationHandle: String,
        senderHandle: String,
        text: String,
        mediaUri: String? = null
    ) {
        val message = PrivateChatMessageEntity(
            messageId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            conversationHandle = conversationHandle,
            senderHandle = senderHandle,
            text = text,
            mediaUri = mediaUri,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isRead = true
        )
        dao.insertChatMessage(message)
    }

    suspend fun markConversationAsRead(handle: String) {
        dao.markMessagesAsRead(handle)
        dao.clearUnreadCount(handle)
    }

    private suspend fun seedInitialCoreDataIfNeeded() {}

    /** Fire-and-forget JSON POST; returns the HTTP status or -1 on failure. */
    private suspend fun postJson(path: String, payload: JSONObject, logTag: String): Int =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$BACKEND_BASE_URL$path")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = NETWORK_TIMEOUT_MS
                    readTimeout = NETWORK_TIMEOUT_MS
                }
                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                connection.responseCode
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(logTag, "POST $path failed: ${e.message}")
                -1
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        private const val BACKEND_BASE_URL = "https://nexora-backend-fq54.onrender.com"
        // Render free instances can cold-start slowly, so allow a generous timeout.
        private const val NETWORK_TIMEOUT_MS = 15000
        private const val DEFAULT_AVATAR_URL =
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80"
        private const val DEFAULT_BANNER_URL =
            "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80"

        @Volatile
        private var INSTANCE: NexoraStreamRepository? = null

        fun getInstance(context: Context): NexoraStreamRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NexoraStreamRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
