/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,
    val localUri: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val timestamp: Long,
    val duration: Long,
    val viewCount: Long = 0L,
    val chapterMarkersJson: String = "[]",
    val thumbnailUri: String = "",
    val authorChannelId: String = "ch_zentora_core",
    val collabChannelId: String? = null,
    val isPremiere: Boolean = false,
    val premiereScheduledTimeMs: Long? = null
)

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey val videoId: String,
    val likesCount: Long = 0L,
    val dislikesCount: Long = 0L,
    val isLikedByMe: Boolean = false,
    val isDislikedByMe: Boolean = false,
    val savedToWatchLater: Boolean = false,
    val sparksReceived: Long = 0L
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val commentId: String,
    val videoId: String,
    val authorName: String,
    val commentText: String,
    val timestamp: Long,
    val likeCount: Long = 0L,
    val isFiltered: Boolean = false,
    val isLikedByMe: Boolean = false
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val avatarUri: String,
    val bannerUri: String,
    val subscriberCount: Long = 0L,
    val isSubscribed: Boolean = false,
    val creatorLevel: String = "Rising Creator",
    val hasZentoraBadge: Boolean = true,
    val description: String = "",
    val joinedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val playbackPositionMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val retentionScore: Float = 0f
)

@Entity(tableName = "notes")
data class NotesEntity(
    @PrimaryKey val noteId: String,
    val videoId: String,
    val timestampMs: Long,
    val noteText: String
)

@Entity(tableName = "community_posts")
data class CommunityPostEntity(
    @PrimaryKey val postId: String,
    val channelId: String,
    val contentText: String,
    val imageUri: String? = null,
    val pollOptionsJson: String? = null,
    val upvotes: Long = 0L,
    val isUpvotedByMe: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
