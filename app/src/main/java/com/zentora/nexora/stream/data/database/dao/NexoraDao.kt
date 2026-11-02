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

    // ==================== PRIVATE FRIEND ENCLAVE (PILLAR 4) ====================
    @Query("SELECT * FROM private_friend_profile LIMIT 1")
    fun getPrivateProfile(): Flow<PrivateFriendProfileEntity?>

    @Query("SELECT * FROM private_friend_profile LIMIT 1")
    suspend fun getPrivateProfileSync(): PrivateFriendProfileEntity?

    @Query("SELECT COUNT(*) FROM private_friend_profile WHERE LOWER(handle) = LOWER(:handle)")
    suspend fun countHandleOccurrences(handle: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivateProfile(profile: PrivateFriendProfileEntity)

    @Update
    suspend fun updatePrivateProfile(profile: PrivateFriendProfileEntity)

    @Query("DELETE FROM private_friend_profile")
    suspend fun deletePrivateProfile()

    // Private Friends / Directory
    @Query("SELECT * FROM private_friends ORDER BY lastMessageTimestamp DESC")
    fun getAllPrivateFriends(): Flow<List<PrivateFriendEntity>>

    @Query("SELECT * FROM private_friends WHERE displayName LIKE '%' || :query || '%' OR handle LIKE '%' || :query || '%' ORDER BY lastMessageTimestamp DESC")
    fun searchPrivateFriends(query: String): Flow<List<PrivateFriendEntity>>

    @Query("SELECT * FROM private_friends WHERE handle = :handle LIMIT 1")
    suspend fun getPrivateFriendByHandle(handle: String): PrivateFriendEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivateFriend(friend: PrivateFriendEntity)

    @Update
    suspend fun updatePrivateFriend(friend: PrivateFriendEntity)

    @Query("DELETE FROM private_friends WHERE handle = :handle")
    suspend fun deletePrivateFriend(handle: String)

    // Private Chat Messages
    @Query("SELECT * FROM private_chat_messages WHERE conversationHandle = :handle ORDER BY timestamp ASC")
    fun getMessagesForConversation(handle: String): Flow<List<PrivateChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: PrivateChatMessageEntity)

    @Query("UPDATE private_chat_messages SET isRead = 1 WHERE conversationHandle = :handle AND isFromMe = 0")
    suspend fun markMessagesAsRead(handle: String)

    @Query("UPDATE private_friends SET unreadCount = 0 WHERE handle = :handle")
    suspend fun clearUnreadCount(handle: String)

    @Query("DELETE FROM private_chat_messages WHERE messageId = :messageId")
    suspend fun deleteChatMessage(messageId: String)

    // ---- Chat sync (Vault 35, sub-vault friend-chat) ----

    /** The newest [limit] messages of a conversation, oldest first. The screen asks for more when the user scrolls up. */
    @Query(
        "SELECT * FROM (SELECT * FROM private_chat_messages WHERE conversationHandle = :handle " +
            "ORDER BY timestamp DESC, messageId DESC LIMIT :limit) ORDER BY timestamp ASC, messageId ASC"
    )
    fun getRecentMessages(handle: String, limit: Int): Flow<List<PrivateChatMessageEntity>>

    @Query("SELECT * FROM private_chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getChatMessage(messageId: String): PrivateChatMessageEntity?

    /** Stores a message unless it is there already, so a repeated sync never resets what the user has read. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatMessageIfAbsent(message: PrivateChatMessageEntity): Long

    /** My own message came back from the server's log: it now has its number and the server's time. */
    @Query("UPDATE private_chat_messages SET serverSeq = :seq, timestamp = :timestamp, sendState = 0 WHERE messageId = :messageId AND isFromMe = 1")
    suspend fun confirmSentMessage(messageId: String, seq: Long, timestamp: Long): Int

    @Query("UPDATE private_chat_messages SET sendState = :state WHERE messageId = :messageId")
    suspend fun setChatSendState(messageId: String, state: Int)

    /** The server accepted the message; the numbering still comes from the sync. Does not touch a confirmed message. */
    @Query("UPDATE private_chat_messages SET sendState = 0 WHERE messageId = :messageId AND sendState <> 0")
    suspend fun markChatDelivered(messageId: String)

    /** Messages written here that the server has not accepted yet, oldest first. */
    @Query("SELECT * FROM private_chat_messages WHERE isFromMe = 1 AND sendState = 1 ORDER BY timestamp ASC LIMIT 200")
    suspend fun getPendingOutgoing(): List<PrivateChatMessageEntity>

    /** The friend has read my messages up to number [upTo]. */
    @Query("UPDATE private_chat_messages SET isRead = 1 WHERE peerId = :peerId AND isFromMe = 1 AND isRead = 0 AND serverSeq > 0 AND serverSeq <= :upTo")
    suspend fun markSentMessagesRead(peerId: String, upTo: Long)

    /** I have read the friend's messages up to number [upTo] (on this or another of my devices). */
    @Query("UPDATE private_chat_messages SET isRead = 1 WHERE peerId = :peerId AND isFromMe = 0 AND isRead = 0 AND serverSeq <= :upTo")
    suspend fun markReceivedMessagesRead(peerId: String, upTo: Long)

    /** Newest message number received from [peerId]; 0 when there is none. */
    @Query("SELECT COALESCE(MAX(serverSeq), 0) FROM private_chat_messages WHERE peerId = :peerId AND isFromMe = 0")
    suspend fun getNewestReceivedSeq(peerId: String): Long

    /** Where the sync continues: the highest event number this phone has stored. 0 = start from the beginning. */
    @Query("SELECT COALESCE(MAX(serverSeq), 0) FROM private_chat_messages")
    suspend fun getChatCursor(): Long

    @Query("SELECT conversationHandle FROM private_chat_messages WHERE peerId = :peerId LIMIT 1")
    suspend fun getConversationHandleOf(peerId: String): String?

    @Query("SELECT * FROM private_friends WHERE nexoraId = :nexoraId LIMIT 1")
    suspend fun getPrivateFriendByNexoraId(nexoraId: String): PrivateFriendEntity?

    /** A friend changed their handle: their conversation follows. */
    @Query("UPDATE private_chat_messages SET conversationHandle = :newHandle WHERE peerId = :peerId AND conversationHandle <> :newHandle")
    suspend fun renameConversation(peerId: String, newHandle: String)

    /** Unread count and last message of the given friends, recomputed from the stored messages. */
    @Query(
        "UPDATE private_friends SET " +
            "unreadCount = (SELECT COUNT(*) FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle AND m.isFromMe = 0 AND m.isRead = 0), " +
            "lastMessage = COALESCE((SELECT m.text FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle ORDER BY m.timestamp DESC LIMIT 1), lastMessage), " +
            "lastMessageTimestamp = COALESCE((SELECT m.timestamp FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle ORDER BY m.timestamp DESC LIMIT 1), lastMessageTimestamp) " +
            "WHERE handle IN (:handles)"
    )
    suspend fun syncFriendSummaries(handles: List<String>)

    /** The same for every friend (used after the friends list was refreshed). */
    @Query(
        "UPDATE private_friends SET " +
            "unreadCount = (SELECT COUNT(*) FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle AND m.isFromMe = 0 AND m.isRead = 0), " +
            "lastMessage = COALESCE((SELECT m.text FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle ORDER BY m.timestamp DESC LIMIT 1), lastMessage), " +
            "lastMessageTimestamp = COALESCE((SELECT m.timestamp FROM private_chat_messages m WHERE m.conversationHandle = private_friends.handle ORDER BY m.timestamp DESC LIMIT 1), lastMessageTimestamp)"
    )
    suspend fun syncAllFriendSummaries()

    /** Another account signed in on this phone: nothing of the previous account's chats may stay. */
    @Query("DELETE FROM private_chat_messages")
    suspend fun deleteAllChatMessages()

    /** The friends list is only a copy of the server's; a different account gets its own from the next refresh. */
    @Query("DELETE FROM private_friends")
    suspend fun deleteAllPrivateFriends()
}
