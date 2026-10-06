/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database.dao

import androidx.room.*
import com.zentora.nexora.stream.data.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NexoraDao {

    // ==================== VIDEOS ====================
    @Query("SELECT * FROM videos ORDER BY timestamp DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    fun getVideoById(id: String): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoByIdSync(id: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: String)

    @Query("UPDATE videos SET viewCount = viewCount + 1 WHERE id = :id")
    suspend fun incrementViewCount(id: String)

    @Query("SELECT * FROM videos WHERE authorChannelId = :authorChannelId ORDER BY timestamp DESC")
    fun getVideosByAuthor(authorChannelId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE category = :category ORDER BY timestamp DESC")
    fun getVideosByCategory(category: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    // ==================== INTERACTIONS ====================
    @Query("SELECT * FROM interactions WHERE videoId = :videoId")
    fun getInteraction(videoId: String): Flow<InteractionEntity?>

    @Query("SELECT * FROM interactions WHERE videoId = :videoId")
    suspend fun getInteractionSync(videoId: String): InteractionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: InteractionEntity)

    @Update
    suspend fun updateInteraction(interaction: InteractionEntity)

    @Query("SELECT * FROM interactions WHERE savedToWatchLater = 1")
    fun getWatchLaterInteractions(): Flow<List<InteractionEntity>>

    @Query("SELECT * FROM interactions WHERE isLikedByMe = 1")
    fun getLikedInteractions(): Flow<List<InteractionEntity>>

    @Query("UPDATE interactions SET sparksReceived = sparksReceived + :sparks WHERE videoId = :videoId")
    suspend fun addSparks(videoId: String, sparks: Long)

    // ==================== COMMENTS ====================
    @Query("SELECT * FROM comments WHERE videoId = :videoId AND isFiltered = 0 ORDER BY timestamp DESC")
    fun getCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Update
    suspend fun updateComment(comment: CommentEntity)

    @Query("DELETE FROM comments WHERE commentId = :commentId")
    suspend fun deleteComment(commentId: String)

    // ==================== CHANNELS ====================
    @Query("SELECT * FROM channels ORDER BY subscriberCount DESC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE channelId = :channelId")
    fun getChannelById(channelId: String): Flow<ChannelEntity?>

    @Query("SELECT * FROM channels WHERE channelId = :channelId")
    suspend fun getChannelByIdSync(channelId: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Update
    suspend fun updateChannel(channel: ChannelEntity)

    // ==================== HISTORY ====================
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE videoId = :videoId")
    suspend fun deleteHistoryById(videoId: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // ==================== NOTES ====================
    @Query("SELECT * FROM notes WHERE videoId = :videoId ORDER BY timestampMs ASC")
    fun getNotesForVideo(videoId: String): Flow<List<NotesEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NotesEntity)

    @Query("DELETE FROM notes WHERE noteId = :noteId")
    suspend fun deleteNote(noteId: String)

    // ==================== COMMUNITY POSTS ====================
    @Query("SELECT * FROM community_posts ORDER BY timestamp DESC")
    fun getAllCommunityPosts(): Flow<List<CommunityPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommunityPost(post: CommunityPostEntity)

    @Update
    suspend fun updateCommunityPost(post: CommunityPostEntity)

    @Query("DELETE FROM community_posts WHERE postId = :postId")
    suspend fun deleteCommunityPost(postId: String)
}
