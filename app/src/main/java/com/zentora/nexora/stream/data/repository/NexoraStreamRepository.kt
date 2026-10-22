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
import com.zentora.nexora.stream.engine.PublishVideoRequest
import com.zentora.nexora.stream.engine.RegisterUserRequest
import com.zentora.nexora.stream.engine.VaultAuditResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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

    private val _isKidsMode = MutableStateFlow(false)
    val isKidsMode: StateFlow<Boolean> = _isKidsMode.asStateFlow()

    private val _masterPin = MutableStateFlow("1234")
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    private val _isDataSaver = MutableStateFlow(false)
    val isDataSaver: StateFlow<Boolean> = _isDataSaver.asStateFlow()

    init {
        scope.launch {
            seedInitialCoreDataIfNeeded()
            syncFeedFromCentralVaults()
            restoreAccountFromCentralVaults()
            
            // অ্যাপ চালু হওয়ার সাথে সাথে ইউজারের জিমেইল দিয়ে ক্লাউড থেকে ডেটা অটো-রিকভার করবে
            val session = NexoraIdManager.getSession()
            if (session.email.isNotBlank()) {
                restoreUserDataFromCloud(session.email) { _, _ ->
                    Log.d("NexoraAccountSync", "SUCCESS: User account data auto-restored for ${session.email}")
                }
            }
        }
    }

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
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Register session error: ${e.message}")
            }
        }
    }

    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        return runCatching {
            val session = NexoraIdManager.getSession()
            val response = NexoraNetworkClient.apiService.restoreUserProfile(session.nexoraId)
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                val channel = ChannelEntity(
                    channelId = "ch_zentora_core",
                    channelName = if (profile.username.isNotBlank()) profile.username else "Zentora CLC",
                    avatarUri = profile.avatarUri.ifBlank { "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80" },
                    bannerUri = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
                    subscriberCount = profile.subscriberCount,
                    isSubscribed = false,
                    creatorLevel = profile.creatorLevel,
                    hasZentoraBadge = true,
                    description = "Official channel of Zentora CLC."
                )
                dao.insertChannel(channel)

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

    suspend fun saveUserDataToCloud(userGoogleEmail: String, phoneNumber: String, billingInfo: String) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://nexora-backend-fq54.onrender.com/api/v1/user/save-profile")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val jsonPayload = JSONObject().apply {
                    put("email", userGoogleEmail)
                    put("phoneNumber", phoneNumber)
                    put("billingInfo", billingInfo)
                }

                connection.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    Log.d("NexoraAccountSync", "SUCCESS: User profile saved to cloud!")
                } else {
                    Log.e("NexoraAccountSync", "FAILED: Server response code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Error saving profile: ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    suspend fun restoreUserDataFromCloud(userGoogleEmail: String, onDataRestored: (String, String) -> Unit) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://nexora-backend-fq54.onrender.com/api/v1/user/get-profile?email=$userGoogleEmail")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val respText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(respText)
                    if (json.optBoolean("success", false)) {
                        val phone = json.optString("phoneNumber", "")
                        val billing = json.optString("billingInfo", "")
                        withContext(Dispatchers.Main) {
                            onDataRestored(phone, billing)
                        }
                        Log.d("NexoraAccountSync", "SUCCESS: Data auto-restored for $userGoogleEmail")
                    }
                }
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Error restoring profile: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * রিয়েল-টাইম অটো-সিঙ্ক ও অটো-সেভ ইঞ্জিন (YouTube স্টাইল)
     */
    fun syncUserActivityToCloud(actionType: String, targetId: String, metadataJson: String) {
        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                val session = NexoraIdManager.getSession()
                if (session.email.isBlank()) return@launch

                val url = URL("https://nexora-backend-fq54.onrender.com/api/v1/user/sync-activity")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val payload = JSONObject().apply {
                    put("email", session.email)
                    put("nexoraId", session.nexoraId)
                    put("actionType", actionType)
                    put("targetId", targetId)
                    put("metadata", metadataJson)
                    put("timestamp", System.currentTimeMillis())
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    Log.d("NexoraAutoSync", "SUCCESS: Activity [$actionType] auto-saved to cloud!")
                } else {
                    Log.e("NexoraAutoSync", "FAILED: Server sync returned code $responseCode")
                }
            } catch (e: Exception) {
                Log.e("NexoraAutoSync", "ERROR during auto-save: ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
        }
    }

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
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Search error: ${e.message}")
            }
        }
    }

    suspend fun auditVaultIntegrity(): VaultAuditResponse? {
        return try {
            val response = NexoraNetworkClient.apiService.auditVaults()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun seedInitialCoreDataIfNeeded() {
        val existingChannel = dao.getChannelByIdSync("ch_zentora_core")
        if (existingChannel == null) {
            val zentoraChannel = ChannelEntity(
                channelId = "ch_zentora_core",
                channelName = "Zentora CLC",
                avatarUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80",
                bannerUri = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200&auto=format&fit=crop&q=80",
                subscriberCount = 0L,
                isSubscribed = false,
                creatorLevel = "Rising Creator",
                hasZentoraBadge = true,
                description = "Official channel of Zentora CLC.",
                joinedTimestamp = System.currentTimeMillis()
            )
            dao.insertChannel(zentoraChannel)
        }
    }

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
        syncUserActivityToCloud("WATCH_HISTORY", videoId, "{\"positionMs\": $positionMs}")

        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(videoId = videoId, viewDelta = 1L, nexoraId = NexoraIdManager.getSession().nexoraId)
                )
            } catch (_: Exception) {}
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
            InteractionEntity(videoId = newVideo.id, likesCount = 0L, dislikesCount = 0L, isLikedByMe = false, isDislikedByMe = false, savedToWatchLater = false, sparksReceived = 0L)
        )
        
        syncUserActivityToCloud("UPLOAD_VIDEO", newVideo.id, "{\"title\": \"$title\"}")

        scope.launch {
            try {
                val session = NexoraIdManager.getSession()
                NexoraNetworkClient.apiService.publishVideo(
                    PublishVideoRequest(
                        videoId = newVideo.id, title = title, description = description, category = category, tags = tags,
                        mediaUri = localUri, thumbnailUri = thumbnailUri, duration = duration, authorChannelId = newVideo.authorChannelId,
                        nexoraAuthToken = session.zentoraAuthToken, visibility = visibility, isMadeForKids = isMadeForKids,
                        isAgeRestricted = isAgeRestricted, location = location, shortsRemixing = shortsRemixing,
                        commentsModeration = commentsModeration, showLikesCount = showLikesCount, containsPaidPromotion = containsPaidPromotion,
                        videoLanguage = videoLanguage, licenseType = licenseType, allowEmbedding = allowEmbedding, notifySubscribers = notifySubscribers
                    )
                )
            } catch (_: Exception) {}
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
        syncUserActivityToCloud("TOGGLE_LIKE", videoId, "{\"isLiked\": $newLiked}")

        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(videoId = videoId, likeDelta = if (newLiked) 1L else -1L, nexoraId = NexoraIdManager.getSession().nexoraId)
                )
            } catch (_: Exception) {}
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
        syncUserActivityToCloud("WATCH_LATER", videoId, "{\"saved\": ${!current.savedToWatchLater}}")
    }

    suspend fun sendSparks(videoId: String, sparks: Long) {
        dao.addSparks(videoId, sparks)
        syncUserActivityToCloud("SEND_SPARKS", videoId, "{\"sparks\": $sparks}")
        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(videoId = videoId, sparksDelta = sparks, nexoraId = NexoraIdManager.getSession().nexoraId)
                )
            } catch (_: Exception) {}
        }
    }

    fun getComments(videoId: String): Flow<List<CommentEntity>> = dao.getCommentsForVideo(videoId)

    suspend fun addComment(videoId: String, authorName: String, text: String): Boolean {
        val isSafe = CommunityShield.isContentSafe(text)
        val sanitized = if (isSafe) text else CommunityShield.filterText(text)

        val comment = CommentEntity(
            commentId = "c_${UUID.randomUUID()}", videoId = videoId, authorName = authorName,
            commentText = sanitized, timestamp = System.currentTimeMillis(), likeCount = 0L, isFiltered = !isSafe, isLikedByMe = false
        )
        dao.insertComment(comment)
        syncUserActivityToCloud("ADD_COMMENT", videoId, "{\"commentId\": \"${comment.commentId}\"}")

        scope.launch {
            try {
                val session = NexoraIdManager.getSession()
                NexoraNetworkClient.apiService.postComment(
                    PostCommentRequest(commentId = comment.commentId, videoId = videoId, authorName = authorName, commentText = sanitized, nexoraId = session.nexoraId, timestamp = comment.timestamp)
                )
            } catch (_: Exception) {}
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
        syncUserActivityToCloud("TOGGLE_SUBSCRIBE", channelId, "{\"subscribed\": $newSubscribed}")
    }

    fun getNotes(videoId: String): Flow<List<NotesEntity>> = dao.getNotesForVideo(videoId)

    suspend fun addNote(videoId: String, timestampMs: Long, text: String) {
        val note = NotesEntity(noteId = "note_${UUID.randomUUID()}", videoId = videoId, timestampMs = timestampMs, noteText = text)
        dao.insertNote(note)
        syncUserActivityToCloud("ADD_NOTE", videoId, "{\"timestampMs\": $timestampMs}")
    }

    suspend fun deleteNote(noteId: String) {
        dao.deleteNote(noteId)
    }

    suspend fun createCommunityPost(channelId: String, contentText: String, imageUri: String?, pollOptionsJson: String?) {
        val post = CommunityPostEntity(
            postId = "post_${UUID.randomUUID()}", channelId = channelId, contentText = contentText,
            imageUri = imageUri, pollOptionsJson = pollOptionsJson, upvotes = 0L, isUpvotedByMe = false, timestamp = System.currentTimeMillis()
        )
        dao.insertCommunityPost(post)
        syncUserActivityToCloud("COMMUNITY_POST", post.postId, "{\"channelId\": \"$channelId\"}")
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

    suspend fun clearHistory() { dao.clearHistory() }

    suspend fun removeFromHistory(videoId: String) { dao.deleteHistoryById(videoId) }

    suspend fun upsertChannel(channel: ChannelEntity) { dao.insertChannel(channel) }

    val privateProfile: Flow<PrivateFriendProfileEntity?> = dao.getPrivateProfile()

    suspend fun getPrivateProfile(): PrivateFriendProfileEntity? = dao.getPrivateProfileSync()

    suspend fun isHandleTaken(handle: String): Boolean = dao.countHandleOccurrences(handle.trim()) > 0

    suspend fun savePrivateProfile(profile: PrivateFriendProfileEntity) { dao.insertPrivateProfile(profile) }

    suspend fun updatePrivateProfile(profile: PrivateFriendProfileEntity) { dao.updatePrivateProfile(profile) }

    fun getAllPrivateFriends(): Flow<List<PrivateFriendEntity>> = dao.getAllPrivateFriends()

    fun searchPrivateFriends(query: String): Flow<List<PrivateFriendEntity>> = dao.searchPrivateFriends(query)

    suspend fun getPrivateFriend(handle: String): PrivateFriendEntity? = dao.getPrivateFriendByHandle(handle)

    suspend fun addPrivateFriend(friend: PrivateFriendEntity) { dao.insertPrivateFriend(friend) }

    fun getMessagesForConversation(handle: String): Flow<List<PrivateChatMessageEntity>> = dao.getMessagesForConversation(handle)

    suspend fun sendChatMessage(conversationHandle: String, senderHandle: String, text: String, mediaUri: String? = null) {
        val message = PrivateChatMessageEntity(
            messageId = "msg_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}",
            conversationHandle = conversationHandle, senderHandle = senderHandle, text = text,
            mediaUri = mediaUri, timestamp = System.currentTimeMillis(), isFromMe = true, isRead = true
        )
        dao.insertChatMessage(message)
        syncUserActivityToCloud("CHAT_MESSAGE", conversationHandle, "{\"msgId\": \"${message.messageId}\"}")

        val existingFriend = dao.getPrivateFriendByHandle(conversationHandle)
        if (existingFriend != null) {
            dao.updatePrivateFriend(existingFriend.copy(lastMessage = text, lastMessageTimestamp = System.currentTimeMillis()))
        } else {
            dao.insertPrivateFriend(PrivateFriendEntity(handle = conversationHandle, displayName = conversationHandle.removePrefix("@"), lastMessage = text, lastMessageTimestamp = System.currentTimeMillis(), isOnline = true))
        }
    }

    suspend fun markConversationAsRead(handle: String) {
        dao.markMessagesAsRead(handle)
        dao.clearUnreadCount(handle)
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
