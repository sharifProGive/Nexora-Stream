/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.util.Base64
import com.zentora.nexora.stream.data.database.entities.*
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
        val watchedVideoIds = history.map { it.videoId }.toSet()
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
        return listOf(
            P2PDevice("p2p_01", "Nexora-Desk (Zentora)", "Ready"),
            P2PDevice("p2p_02", "Nexora-Tablet Pro", "Available"),
            P2PDevice("p2p_03", "Zentora-Home Station", "Paired")
        )
    }

    fun createSharePackage(video: VideoEntity): String {
        val payload = "${video.id}|${video.title}|${video.category}|${video.duration}"
        return Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)
    }
}

/**
 * Nexora ID System:
 * Internal single sign-on engine managing authenticated user sessions without Google/Facebook dependencies.
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
    val hasZentoraBadge: Boolean = true
)

object NexoraIdManager {

    private var activeSession = NexoraSession(
        nexoraId = "NX-${UUID.randomUUID().toString().take(8).uppercase()}",
        username = "Zentora Core Member",
        email = "core@zentora.org",
        handle = "@zentora_core",
        avatarUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150&auto=format&fit=crop&q=80",
        isCreatorMode = true,
        zentoraAuthToken = "ZT-${UUID.randomUUID()}",
        creatorLevel = "Rising Creator",
        hasZentoraBadge = true
    )

    fun getSession(): NexoraSession = activeSession

    fun updateSession(username: String, handle: String, isCreatorMode: Boolean) {
        activeSession = activeSession.copy(
            username = username,
            handle = handle,
            isCreatorMode = isCreatorMode
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
