/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.database.entities

import androidx.room.Entity
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
    val isOnline: Boolean = false
)

/**
 * Private Chat Message Entity (WhatsApp-style conversation store).
 */
@Entity(tableName = "private_chat_messages")
data class PrivateChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationHandle: String,
    val senderHandle: String,
    val text: String,
    val mediaUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = true,
    val isRead: Boolean = true
)
