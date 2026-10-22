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
                }
                feed.videos.size
            } else {
                0
            }
        }
    }

    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        return runCatching {
            val session = NexoraIdManager.getSession()
            val response = NexoraNetworkClient.apiService.restoreUserProfile(session.nexoraId)
            if (response.isSuccessful && response.body() != null) {
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

                connection.responseCode
            } catch (e: Exception) {
                Log.e("NexoraAccountSync", "Error saving profile: ${e.message}")
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

                if (connection.responseCode == 200) {
                    val respText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(respText)
                    if (json.optBoolean("success", false)) {
                        val phone = json.optString("phoneNumber", "")
                        val billing = json.optString("billingInfo", "")
                        withContext(Dispatchers.Main) {
                            onDataRestored(phone, billing)
                        }
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
     * আপনার Zentora 6-Vault সার্ভারের সাথে ইন্টারঅ্যাকশন ও অ্যাক্টিভিটি সিঙ্ক করার মেকানিজম
     */
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
        channelName: String = "Zentora Creator"
    ): VideoEntity {
        val newVideo = VideoEntity(
            id = "NEX-" + System.currentTimeMillis(),
            localUri = localUri,
            title = title,
            description = description,
            category = category,
            tags = tags,
            timestamp = System.currentTimeMillis(),
            duration = 60000L,
            viewCount = 0L,
            thumbnailUri = thumbnailUri,
            authorChannelId = "ch_zentora_core"
        )
        dao.insertVideo(newVideo)

        // সরাসরি আপনার সার্ভারের /api/v1/videos/publish রুটে ডেটা ইনজেক্ট ও ৬ষ্ঠ মহা-ঘরে লক করা
        scope.launch {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://nexora-backend-fq54.onrender.com/api/v1/videos/publish")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val payload = JSONObject().apply {
                    put("title", title)
                    put("description", description)
                    put("category", category)
                    put("tags", tags)
                    put("videoUrl", localUri)
                    put("thumbnailUrl", thumbnailUri)
                    put("channelName", channelName)
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200 || responseCode == 201) {
                    Log.d("ZentoraVault", "SUCCESS: Video published and locked into 6th Grand Master Vault!")
                }
            } catch (e: Exception) {
                Log.e("ZentoraVault", "Error publishing video to backend: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return newVideo
    }

    suspend fun toggleLike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newLiked = !current.isLikedByMe
        val newLikes = if (newLiked) current.likesCount + 1 else maxOf(0L, current.likesCount - 1)
        dao.updateInteraction(current.copy(isLikedByMe = newLiked, likesCount = newLikes))

        scope.launch {
            try {
                NexoraNetworkClient.apiService.sendInteraction(
                    InteractRequest(videoId = videoId, likeDelta = if (newLiked) 1L else -1L, nexoraId = NexoraIdManager.getSession().nexoraId)
                )
            } catch (_: Exception) {}
        }
    }

    fun getVideo(id: String): Flow<VideoEntity?> = dao.getVideoById(id)
    fun getComments(videoId: String): Flow<List<CommentEntity>> = dao.getCommentsForVideo(videoId)

    private suspend fun seedInitialCoreDataIfNeeded() {
        // প্রাথমিক সিডিং লজিক
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
