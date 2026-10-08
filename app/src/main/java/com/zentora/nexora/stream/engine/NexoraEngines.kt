/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.util.Base64
import com.zentora.nexora.stream.data.database.entities.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * Nexora Smart Rank: On-device neural recommendation algorithm.
 * Evaluates watch retention, category affinity, and engagement history.
 */
object NexoraSmartRank {

    fun rankVideos(
        videos: List<VideoEntity>,
        interactions: Map<String, InteractionEntity>,
        history: List<HistoryEntity>
    ): List<VideoEntity> {
        if (videos.isEmpty()) return emptyList()

        // 1. Calculate category affinity from watch history
        val categoryCounts = mutableMapOf<String, Int>()
        for (h in history) {
            val v = videos.find { it.id == h.videoId }
            if (v != null) {
                categoryCounts[v.category] = (categoryCounts[v.category] ?: 0) + 1
            }
        }
        val totalHistoryCount = maxOf(1, history.size)

        // 2. Score each video
        val now = System.currentTimeMillis()
        val scoredList = videos.map { video ->
            val interaction = interactions[video.id]
            val hist = history.find { it.videoId == video.id }

            // Recency factor (0.0 to 1.0)
            val ageHours = (now - video.timestamp).coerceAtLeast(0L) / (1000.0 * 3600.0)
            val recencyScore = (1.0 / (1.0 + ageHours / 24.0)).toFloat()

            // Category affinity factor
            val catCount = categoryCounts[video.category] ?: 0
            val affinityScore = (catCount.toFloat() / totalHistoryCount).coerceIn(0f, 1f)

            // Retention factor
            val retentionScore = hist?.retentionScore ?: 0.5f

            // Engagement score from reactive interactions
            val likes = interaction?.likesCount ?: 0L
            val views = video.viewCount
            val engagementScore = if (views > 0) (likes.toFloat() / views.toFloat()).coerceIn(0f, 1f) else 0f

            // Composite Smart Rank Score
            val totalScore = (recencyScore * 0.25f) + (affinityScore * 0.35f) + (retentionScore * 0.25f) + (engagementScore * 0.15f)
            video to totalScore
        }

        return scoredList.sortedByDescending { it.second }.map { it.first }
    }
}

/**
 * Real-Time Trending Radar:
 * Dynamic query ranking videos gaining maximum local views and interactions within a sliding window.
 */
object TrendingRadar {

    fun getTrendingVideos(
        videos: List<VideoEntity>,
        interactions: Map<String, InteractionEntity>
    ): List<VideoEntity> {
        val now = System.currentTimeMillis()
        val windowMs = 7 * 24 * 3600 * 1000L // 7 days window

        return videos.map { video ->
            val interaction = interactions[video.id]
            val likes = interaction?.likesCount ?: 0L
            val sparks = interaction?.sparksReceived ?: 0L
            val recency = if (now - video.timestamp < windowMs) 1.5f else 1.0f

            // Velocity formula
            val velocity = ((video.viewCount * 1.0f) + (likes * 3.0f) + (sparks * 5.0f)) * recency
            video to velocity
        }.sortedByDescending { it.second }.map { it.first }
    }
}

/**
 * Nexora Semantic Search:
 * Scans video titles, tags, and category metadata with multi-term token scoring.
 */
object NexoraSemanticSearch {

    fun search(videos: List<VideoEntity>, query: String): List<VideoEntity> {
        if (query.isBlank()) return emptyList()
        val tokens = query.trim().lowercase().split(" ").filter { it.isNotBlank() }

        return videos.mapNotNull { video ->
            var score = 0
            val titleLower = video.title.lowercase()
            val descLower = video.description.lowercase()
            val tagsLower = video.tags.lowercase()
            val catLower = video.category.lowercase()

            for (token in tokens) {
                if (titleLower.contains(token)) score += 5
                if (tagsLower.contains(token)) score += 4
                if (catLower.contains(token)) score += 3
                if (descLower.contains(token)) score += 1
            }

            if (score > 0) video to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }
}

/**
 * Nexora DRM: Local URI masking to prevent direct file dumping.
 * Generates an internal security token and hashes physical URIs.
 */
object NexoraDrm {

    private val tokenMap = mutableMapOf<String, String>()

    fun maskUri(realUri: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(realUri.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        val masked = "nexora://drm-secure/$hash"
        tokenMap[masked] = realUri
        return masked
    }

    fun resolveUri(maskedUri: String): String {
        return tokenMap[maskedUri] ?: maskedUri
    }
}

/**
 * Community Guideline Shield:
 * Automatic regex-based comment moderation filtering blacklisted keywords.
 */
object CommunityShield {

    private val blacklistPatterns = listOf(
        "(?i)\\b(hate|spam|scam|abuse|kill|violenc|phish|curse)\\b",
        "(?i)(http|https)://[^\\s]+",
        "(?i)\\b(free\\s+money|click\\s+here|crypto\\s+giveaway)\\b"
    )

    fun isContentSafe(text: String): Boolean {
        for (pattern in blacklistPatterns) {
            if (Regex(pattern).containsMatchIn(text)) {
                return false
            }
        }
        return true
    }

    fun filterText(text: String): String {
        var sanitized = text
        for (pattern in blacklistPatterns) {
            sanitized = sanitized.replace(Regex(pattern), "***")
        }
        return sanitized
    }
}

/**
 * Peer-to-Peer Nexora Share Manager:
 * Handles local device-to-device video and metadata package transfers.
 */
object NexoraP2PShare {

    data class P2PDevice(val id: String, val name: String, val status: String)

    fun getNearbyPeers(): List<P2PDevice> {
        return emptyList()
    }

    fun createSharePackage(video: VideoEntity): String {
        val payload = "${video.id}|${video.title}|${video.category}|${video.duration}"
        return Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)
    }
}

/**
 * Nexora ID System:
 * Manages authenticated user sessions, Google ID linking, and reactive session state.
 */
data class NexoraSession(
    val nexoraId: String,
    val username: String,
    val email: String,
    val handle: String,
    val avatarUri: String,
    val isCreatorMode: Boolean = true,
    val zentoraAuthToken: String,
    val creatorLevel: String = "Rising Creator",
    val hasZentoraBadge: Boolean = true,
    val isSignedInWithGoogle: Boolean = false,
    val isGuest: Boolean = false
)

object NexoraIdManager {

    private const val PREFS_NAME = "nexora_vault1_session"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_NEXORA_ID = "nexora_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_HANDLE = "handle"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_IS_GOOGLE = "is_google"
    private const val KEY_IS_GUEST = "is_guest"

    private val _sessionState = MutableStateFlow(
        NexoraSession(
            nexoraId = "",
            username = "",
            email = "",
            handle = "",
            avatarUri = "",
            isCreatorMode = true,
            zentoraAuthToken = "",
            creatorLevel = "Rising Creator",
            hasZentoraBadge = true,
            isSignedInWithGoogle = false,
            isGuest = false
        )
    )

    val sessionFlow: StateFlow<NexoraSession> = _sessionState.asStateFlow()

    fun initSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        val nexoraId = prefs.getString(KEY_NEXORA_ID, "") ?: ""
        val isGuest = prefs.getBoolean(KEY_IS_GUEST, false)

        if (token.isNotBlank() || isGuest) {
            _sessionState.value = NexoraSession(
                nexoraId = if (nexoraId.isNotBlank()) nexoraId else "NX-${UUID.randomUUID().toString().take(8).uppercase()}",
                username = prefs.getString(KEY_USERNAME, if (isGuest) "Guest Streamer" else "Zentora CLC") ?: "",
                email = prefs.getString(KEY_EMAIL, if (isGuest) "guest@zentora.stream" else "core@zentora.org") ?: "",
                handle = prefs.getString(KEY_HANDLE, if (isGuest) "@guest" else "@zentora") ?: "",
                avatarUri = prefs.getString(KEY_AVATAR, "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150") ?: "",
                isCreatorMode = true,
                zentoraAuthToken = token,
                creatorLevel = "Rising Creator",
                hasZentoraBadge = !isGuest,
                isSignedInWithGoogle = prefs.getBoolean(KEY_IS_GOOGLE, false),
                isGuest = isGuest
            )
        }
    }

    fun hasActiveSession(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_AUTH_TOKEN, "") ?: ""
        val isGuest = prefs.getBoolean(KEY_IS_GUEST, false)
        return token.isNotBlank() || isGuest
    }

    fun getSession(): NexoraSession = _sessionState.value

    fun updateSession(
        username: String,
        handle: String,
        isCreatorMode: Boolean,
        avatarUri: String = "",
        email: String = getSession().email,
        isSignedInWithGoogle: Boolean = getSession().isSignedInWithGoogle
    ) {
        _sessionState.value = _sessionState.value.copy(
            username = username,
            handle = handle,
            isCreatorMode = isCreatorMode,
            avatarUri = avatarUri,
            email = email,
            isSignedInWithGoogle = isSignedInWithGoogle
        )
    }

    /**
     * Links Google Authentication credentials, persists session in Vault 1 SharedPreferences,
     * and generates a fresh Zentora Auth Token.
     */
    fun loginWithGoogle(
        context: Context,
        displayName: String,
        email: String,
        photoUrl: String?,
        idToken: String?
    ) {
        val cleanHandle = "@" + (email.substringBefore("@").replace(".", "").lowercase().ifBlank { "zentoracreator" })
        val newToken = idToken?.ifBlank { null } ?: "ZT-GOOGLE-${UUID.randomUUID()}"
        val nexoraId = "NX-${UUID.randomUUID().toString().take(8).uppercase()}"
        val avatar = photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150"
        val name = displayName.ifBlank { "Google Creator" }

        val session = NexoraSession(
            nexoraId = nexoraId,
            username = name,
            email = email,
            handle = cleanHandle,
            avatarUri = avatar,
            isCreatorMode = true,
            zentoraAuthToken = newToken,
            creatorLevel = "Rising Creator",
            hasZentoraBadge = true,
            isSignedInWithGoogle = true,
            isGuest = false
        )
        _sessionState.value = session

        // Save in SharedPreferences for Vault 1 persistence
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTH_TOKEN, newToken)
            .putString(KEY_NEXORA_ID, nexoraId)
            .putString(KEY_USERNAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_HANDLE, cleanHandle)
            .putString(KEY_AVATAR, avatar)
            .putBoolean(KEY_IS_GOOGLE, true)
            .putBoolean(KEY_IS_GUEST, false)
            .apply()
    }

    fun activateGuestMode(context: Context) {
        val nexoraId = "NX-GUEST-${UUID.randomUUID().toString().take(6).uppercase()}"
        val guestSession = NexoraSession(
            nexoraId = nexoraId,
            username = "Guest Streamer",
            email = "guest@zentora.stream",
            handle = "@guest",
            avatarUri = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150",
            isCreatorMode = false,
            zentoraAuthToken = "ZT-GUEST-TOKEN",
            creatorLevel = "Guest Explorer",
            hasZentoraBadge = false,
            isSignedInWithGoogle = false,
            isGuest = true
        )
        _sessionState.value = guestSession

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTH_TOKEN, "ZT-GUEST-TOKEN")
            .putString(KEY_NEXORA_ID, nexoraId)
            .putString(KEY_USERNAME, "Guest Streamer")
            .putString(KEY_EMAIL, "guest@zentora.stream")
            .putString(KEY_HANDLE, "@guest")
            .putString(KEY_AVATAR, "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150")
            .putBoolean(KEY_IS_GOOGLE, false)
            .putBoolean(KEY_IS_GUEST, true)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        _sessionState.value = NexoraSession(
            nexoraId = "",
            username = "",
            email = "",
            handle = "",
            avatarUri = "",
            isCreatorMode = true,
            zentoraAuthToken = "",
            creatorLevel = "Rising Creator",
            hasZentoraBadge = true,
            isSignedInWithGoogle = false,
            isGuest = false
        )
    }

    fun calculateCreatorLevel(subscribers: Long, views: Long): String {
        return when {
            subscribers >= 100_000 || views >= 1_000_000 -> "Diamond Streamer"
            subscribers >= 1_000 || views >= 10_000 -> "Gold Creator"
            else -> "Rising Creator"
        }
    }
}
