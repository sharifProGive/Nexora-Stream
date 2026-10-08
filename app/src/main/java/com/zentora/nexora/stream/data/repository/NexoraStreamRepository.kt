/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.*
import com.zentora.nexora.stream.engine.CommunityShield
import com.zentora.nexora.stream.engine.InteractRequest
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.engine.NexoraNetworkClient
import com.zentora.nexora.stream.engine.PostCommentRequest
import com.zentora.nexora.stream.engine.PublishVideoRequest
import com.zentora.nexora.stream.engine.RegisterUserRequest
import com.zentora.nexora.stream.engine.VaultAuditResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class NexoraStreamRepository(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allVideos: Flow<List<VideoEntity>> = dao.getAllVideos()
    val allChannels: Flow<List<ChannelEntity>> = dao.getAllChannels()
    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()
    val watchLaterInteractions: Flow<List<InteractionEntity>> = dao.getWatchLaterInteractions()
    val likedInteractions: Flow<List<InteractionEntity>> = dao.getLikedInteractions()
    val allCommunityPosts: Flow<List<CommunityPostEntity>> = dao.getAllCommunityPosts()

    fun getVideosByAuthor(authorChannelId: String): Flow<List<VideoEntity>> = dao.getVideosByAuthor(authorChannelId)

    // Parental Controls & Data Settings State
    private val _isKidsMode = MutableStateFlow(false)
    val isKidsMode: StateFlow<Boolean> = _isKidsMode.asStateFlow()

    private val _masterPin = MutableStateFlow("1234") // Default master PIN
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    private val _isDataSaver = MutableStateFlow(false)
    val isDataSaver: StateFlow<Boolean> = _isDataSaver.asStateFlow()

    init {
        scope.launch {
            seedInitialCoreDataIfNeeded()
            syncFeedFromCentralVaults()
            restoreAccountFromCentralVaults()
        }
    }

    /**
     * DUAL-SYNC LOGIC (Room DB Vault 1 <-> Central Vaults)
     * When online, sync latest public feed from /api/v1/feed and upsert into local Room entities.
     * When offline, local Room Database (Vault 1) continues serving all cached videos seamlessly.
     */
    suspend fun syncFeedFromCentralVaults(): Result<Int> {
        return runCatching {
            val response = NexoraNetworkClient.apiService.getFeed()
            if (response.isSuccessful && response.body() != null) {
                val feed = response.body()!!
                for (item in feed.videos) {
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
                    if (dao.getInteractionSync(item.id) == null) {
                        dao.insertInteraction(
                            InteractionEntity(
                                videoId = item.id,
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
                feed.videos.size
            } else {
                0
            }
        }
    }

    /**
     * Account Restoration:
     * Upon reinstalling and signing into Nexora ID, the app restores the creator's
     * uploaded videos, channel statistics, and metadata from the central vaults into local Vault 1.
     */
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
            } catch (_: Exception) {
            }
        }
    }

    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        return runCatching {
            val session = NexoraIdManager.getSession()
            val response = NexoraNetworkClient.apiService.restoreUserProfile(session.nexoraId)
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                // Update local channel
                val channel = ChannelEntity(
                    channelId = "ch_zentora_core",
                    channelName = if (profile.username.isNotBlank()) profile.username else "Zentora CLC",
                    avatarUri = profile.avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80" },
                    bannerUri = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
                    subscriberCount = profile.subscriberCount,
                    isSubscribed = false,
                    creatorLevel = profile.creatorLevel,
                    hasZentoraBadge = true,
                    description = "Official channel of Zentora CLC. Restored from Zentora Central Vault 2 & 5."
                )
                dao.insertChannel(channel)

                // Restore user's uploaded videos
                for (item in profile.uploadedVideos) {
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
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * Global Search: Query Central Registry (Vault 2) and merge into local Room DB.
     */
    suspend fun searchGlobalVaults(query: String) {
        if (query.isBlank()) return
        scope.launch {
            try {
                val response = NexoraNetworkClient.apiService.searchGlobalVideos(query)
                if (response.isSuccessful && response.body() != null) {
                    for (item in response.body()!!.videos) {
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
                    }
                }
            } catch (_: Exception) {
                // Offline fallback relies on local Room database search
            }
        }
    }

    suspend fun auditVaultIntegrity(): VaultAuditResponse? {
        return try {
            val response = NexoraNetworkClient.apiService.auditVaults()
            if (response.isSuccessful) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun seedInitialCoreDataIfNeeded() {
        // Seed official Zentora Creator Channel with zero subscribers (Strict Rule 2)
        val existingChannel = dao.getChannelByIdSync("ch_zentora_core")
        if (existingChannel == null) {
            val zentoraChannel = ChannelEntity(
                channelId = "ch_zentora_core",
                channelName = "Zentora CLC",
                avatarUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80",
                bannerUri = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
                subscriberCount = 0L, // Initialized at 0 as required
                isSubscribed = false,
                creatorLevel = "Rising Creator",
                hasZentoraBadge = true,
                description = "Official channel of Zentora CLC. Next-generation media systems, distributed software platforms, and audio-visual engineering.",
                joinedTimestamp = System.currentTimeMillis()
            )
            dao.insertChannel(zentoraChannel)
        }

        // Secondary creator channel for Collab features
        val secondaryChannel = dao.getChannelByIdSync("ch_blender_open")
        if (secondaryChannel == null) {
            val collabChannel = ChannelEntity(
                channelId = "ch_blender_open",
                channelName = "Open Media Lab",
                avatarUri = "https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=150&auto=format&fit=crop&q=80",
                bannerUri = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&auto=format&fit=crop&q=80",
                subscriberCount = 0L,
                isSubscribed = false,
                creatorLevel = "Gold Creator",
                hasZentoraBadge = true,
                description = "Open source media graphics and research.",
                joinedTimestamp = System.currentTimeMillis()
            )
            dao.insertChannel(collabChannel)
        }

        // Check if videos exist; if empty, insert initial platform reference streams with 0 views and 0 likes
        val videoList = dao.getAllVideos().first()
        if (videoList.isEmpty()) {
            val sampleVideos = listOf(
                VideoEntity(
                    id = "vid_tears_of_steel",
                    localUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    title = "Tears of Steel - Open Source VFX Project",
                    description = "Exploring open rendering pipelines and visual effects composition by Zentora CLC.",
                    category = "Tech",
                    tags = "VFX, OpenSource, CGI, Cyberpunk",
                    timestamp = System.currentTimeMillis() - 86400000L,
                    duration = 734000L,
                    viewCount = 0L, // Initialized strictly at 0
                    chapterMarkersJson = """[{"timeMs":0,"title":"Intro"},{"timeMs":120000,"title":"Lab Diagnostics"},{"timeMs":340000,"title":"Combat Protocol"},{"timeMs":560000,"title":"Climax"}]""",
                    thumbnailUri = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop&q=80",
                    authorChannelId = "ch_zentora_core",
                    collabChannelId = "ch_blender_open"
                ),
                VideoEntity(
                    id = "vid_big_buck_bunny",
                    localUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    title = "Big Buck Bunny - 3D Computer Animation",
                    description = "Classic computer graphics benchmark showcasing high framerate rendering.",
                    category = "Animation",
                    tags = "Animation, 3D, CGI, Nature",
                    timestamp = System.currentTimeMillis() - 172800000L,
                    duration = 596000L,
                    viewCount = 0L,
                    chapterMarkersJson = """[{"timeMs":0,"title":"Morning Awakening"},{"timeMs":180000,"title":"Forest Encounters"},{"timeMs":400000,"title":"The Trap"}]""",
                    thumbnailUri = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&auto=format&fit=crop&q=80",
                    authorChannelId = "ch_blender_open"
                ),
                VideoEntity(
                    id = "vid_alpine_escapes",
                    localUri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    title = "Alpine Ridgeline Mountain Descent",
                    description = "High speed mountain biking pursuit through alpine ridgelines.",
                    category = "Sports",
                    tags = "MTB, Extreme, Alps, 4K",
                    timestamp = System.currentTimeMillis() - 43200000L,
                    duration = 15000L,
                    viewCount = 0L,
                    chapterMarkersJson = """[{"timeMs":0,"title":"Summit Drop"},{"timeMs":8000,"title":"Rocky Gap"}]""",
                    thumbnailUri = "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?w=800&auto=format&fit=crop&q=80",
                    authorChannelId = "ch_zentora_core"
                )
            )

            for (video in sampleVideos) {
                dao.insertVideo(video)
                // Initialize clean interaction entity with 0 counters
                dao.insertInteraction(
                    InteractionEntity(
                        videoId = video.id,
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
    }

    // ==================== VIDEO METHODS ====================
    fun getVideo(id: String): Flow<VideoEntity?> = dao.getVideoById(id)

    suspend fun recordView(videoId: String, durationMs: Long, positionMs: Long) {
        dao.incrementViewCount(videoId)
        val retention = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        dao.insertHistory(
            HistoryEntity(
                videoId = videoId,
                playbackPositionMs = positionMs,
                timestamp = System.currentTimeMillis(),
                retentionScore = retention
            )
        )
        // NCP Protocol: Asynchronously dispatch real-time view to Central Vaults
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        viewDelta = 1L,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (_: Exception) {
                // Room DB Vault 1 preserves local views when offline
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
        dao.insertInteraction(
            InteractionEntity(
                videoId = newVideo.id,
                likesCount = 0L,
                dislikesCount = 0L,
                isLikedByMe = false,
                isDislikedByMe = false,
                savedToWatchLater = false,
                sparksReceived = 0L
            )
        )
        // NCP Protocol: Register uploaded video to Vault 3 and lock into Vault 5 Grand Master
        scope.launch {
            try {
                val session = NexoraIdManager.getSession()
                NexoraNetworkClient.apiService.publishVideo(
                    PublishVideoRequest(
                        videoId = newVideo.id,
                        title = title,
                        description = description,
                        category = category,
                        tags = tags,
                        mediaUri = localUri,
                        thumbnailUri = thumbnailUri,
                        duration = duration,
                        authorChannelId = newVideo.authorChannelId,
                        nexoraAuthToken = session.zentoraAuthToken,
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
                )
            } catch (_: Exception) {
                // Offline fallback: Persisted locally in Vault 1 Room DB
            }
        }
        return newVideo
    }

    suspend fun updateVideoMetadata(
        id: String,
        title: String,
        description: String,
        category: String,
        tags: String
    ) {
        val existing = dao.getVideoByIdSync(id) ?: return
        dao.updateVideo(
            existing.copy(
                title = title,
                description = description,
                category = category,
                tags = tags
            )
        )
    }

    suspend fun deleteVideo(id: String) {
        dao.deleteVideoById(id)
        dao.deleteHistoryById(id)
    }

    // ==================== INTERACTIONS ====================
    fun getInteraction(videoId: String): Flow<InteractionEntity?> = dao.getInteraction(videoId)

    suspend fun toggleLike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newLiked = !current.isLikedByMe
        val newLikes = if (newLiked) current.likesCount + 1 else maxOf(0L, current.likesCount - 1)
        val newDisliked = if (newLiked) false else current.isDislikedByMe
        val newDislikes = if (current.isDislikedByMe && newLiked) maxOf(0L, current.dislikesCount - 1) else current.dislikesCount

        dao.updateInteraction(
            current.copy(
                isLikedByMe = newLiked,
                likesCount = newLikes,
                isDislikedByMe = newDisliked,
                dislikesCount = newDislikes
            )
        )

        // NCP Protocol: Asynchronously dispatch like update to Vault 2 and Vault 5
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        likeDelta = if (newLiked) 1L else -1L,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (_: Exception) {
                // Room DB Vault 1 preserves local interaction state
            }
        }
    }

    suspend fun toggleDislike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newDisliked = !current.isDislikedByMe
        val newDislikes = if (newDisliked) current.dislikesCount + 1 else maxOf(0L, current.dislikesCount - 1)
        val newLiked = if (newDisliked) false else current.isLikedByMe
        val newLikes = if (current.isLikedByMe && newDisliked) maxOf(0L, current.likesCount - 1) else current.likesCount

        dao.updateInteraction(
            current.copy(
                isDislikedByMe = newDisliked,
                dislikesCount = newDislikes,
                isLikedByMe = newLiked,
                likesCount = newLikes
            )
        )
    }

    suspend fun toggleWatchLater(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        dao.updateInteraction(current.copy(savedToWatchLater = !current.savedToWatchLater))
    }

    suspend fun sendSparks(videoId: String, sparks: Long) {
        dao.addSparks(videoId, sparks)
        // NCP Protocol: Asynchronously dispatch spark count to Vault 2 and Vault 5
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(
                        videoId = videoId,
                        sparksDelta = sparks,
                        nexoraId = NexoraIdManager.getSession().nexoraId
                    )
                )
            } catch (_: Exception) {
                // Local Vault 1 preserves sparks count
            }
        }
    }

    // ==================== COMMENTS & COMMUNITY SHIELD ====================
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

        // NCP Global Sync: Dispatch comment to Vault 2 & Vault 5
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
            } catch (_: Exception) {
                // Preserved in local Room DB Vault 1
            }
        }
        return isSafe
    }

    suspend fun toggleCommentLike(comment: CommentEntity) {
        val newLiked = !comment.isLikedByMe
        val newCount = if (newLiked) comment.likeCount + 1 else maxOf(0L, comment.likeCount - 1)
        dao.updateComment(comment.copy(isLikedByMe = newLiked, likeCount = newCount))
    }

    // ==================== CHANNELS & CREATOR LEVEL ====================
    fun getChannel(channelId: String): Flow<ChannelEntity?> = dao.getChannelById(channelId)

    suspend fun toggleSubscribe(channelId: String) {
        val channel = dao.getChannelByIdSync(channelId) ?: return
        val newSubscribed = !channel.isSubscribed
        val newSubs = if (newSubscribed) channel.subscriberCount + 1 else maxOf(0L, channel.subscriberCount - 1)
        val newLevel = NexoraIdManager.calculateCreatorLevel(newSubs, 0L)

        dao.updateChannel(
            channel.copy(
                isSubscribed = newSubscribed,
                subscriberCount = newSubs,
                creatorLevel = newLevel
            )
        )
    }

    // ==================== NOTES & BOOKMARKS ====================
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

    // ==================== COMMUNITY POSTS ====================
    suspend fun createCommunityPost(channelId: String, contentText: String, imageUri: String?, pollOptionsJson: String?) {
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

    // ==================== PARENTAL CONTROLS & SETTINGS ====================
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

    companion object {
        @Volatile
        private var INSTANCE: NexoraStreamRepository? = null

        fun getInstance(context: Context): NexoraStreamRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NexoraStreamRepository(context).also { INSTANCE = it }
            }
        }
    }
}
