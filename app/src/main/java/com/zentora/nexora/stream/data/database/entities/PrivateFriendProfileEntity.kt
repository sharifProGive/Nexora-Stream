/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Isolated Private Friend Profile Entity (Pillar 4).
 * Completely separate from public video channels and global search indexes.
 */
@Entity(tableName = "private_friend_profile")
data class PrivateFriendProfileEntity(
    @PrimaryKey val handle: String,
    val displayName: String,
    val avatarUri: String = "",
    val statusBio: String = "Nexora Stream Private Enclave",
    val pinCode: String? = null,
    val isUnlocked: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Private Friend / Contact Entity.
 */
@Entity(tableName = "private_friends")
data class PrivateFriendEntity(
    @PrimaryKey val handle: String,
    val displayName: String,
    val avatarUri: String = "",
    val statusBio: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    /** The friend's permanent server id. Chat messages are addressed with it, so it must survive a handle change. */
    @ColumnInfo(defaultValue = "''") val nexoraId: String = ""
)

/** The message reached the server (or came from it). */
const val CHAT_SEND_STATE_SENT = 0

/** Written on this phone, not yet accepted by the server. It is sent again automatically. */
const val CHAT_SEND_STATE_PENDING = 1

/** The server refused the message (for example the two are no longer friends). The user can retry or delete it. */
const val CHAT_SEND_STATE_FAILED = 2

/**
 * Private Chat Message Entity. One row per message of a conversation between the signed-in account and a friend.
 *
 * messageId: the sending device's clientId for my own messages, "r<seq>" for received ones, so a message that is
 * delivered twice (a repeated sync, a second device) is stored once.
 * serverSeq: the message's number in the server's event log. It is written only by the sync that reads that log
 * (never by a send answer), which is what lets the highest value act as the sync cursor.
 * isRead: for received messages "I have read it"; for my own messages "the friend has read it".
 */
@Entity(
    tableName = "private_chat_messages",
    indices = [
        Index(value = ["conversationHandle", "timestamp"]),
        Index(value = ["conversationHandle", "isFromMe", "isRead"]),
        Index(value = ["peerId", "serverSeq"]),
        Index(value = ["serverSeq"])
    ]
)
data class PrivateChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationHandle: String,
    val senderHandle: String,
    val text: String,
    val mediaUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = true,
    val isRead: Boolean = true,
    @ColumnInfo(defaultValue = "''") val peerId: String = "",
    @ColumnInfo(defaultValue = "0") val serverSeq: Long = 0L,
    @ColumnInfo(defaultValue = "0") val sendState: Int = CHAT_SEND_STATE_SENT
)
