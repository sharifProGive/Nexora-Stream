/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.CHAT_SEND_STATE_FAILED
import com.zentora.nexora.stream.data.database.entities.CHAT_SEND_STATE_PENDING
import com.zentora.nexora.stream.data.database.entities.CHAT_SEND_STATE_SENT
import com.zentora.nexora.stream.data.database.entities.PrivateChatMessageEntity
import com.zentora.nexora.stream.engine.AccountSession
import com.zentora.nexora.stream.engine.BackendResult
import com.zentora.nexora.stream.engine.ChatBackend
import com.zentora.nexora.stream.engine.ChatEventKind
import com.zentora.nexora.stream.engine.ChatPage
import com.zentora.nexora.stream.engine.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.min
import kotlin.random.Random

/** What happened to a message the user tried to send or send again. */
sealed interface ChatActionResult {
    /** The server has the message. */
    data object Done : ChatActionResult

    /** The server could not be reached; the message is kept and is sent automatically when it can be. */
    data class Queued(val message: String) : ChatActionResult

    /** The server refused the message. It stays in the conversation, marked "not sent". */
    data class Failed(val message: String) : ChatActionResult

    /** Nothing was stored (empty, too long, nobody signed in, unknown friend): the screen keeps the typed text. */
    data class Rejected(val message: String) : ChatActionResult
}

/**
 * Private chat between friends, backed by the Nexora server (Vault 35, sub-vault friend-chat).
 *
 * The server keeps one ordered log of events (messages and read receipts) per account. This phone keeps a copy in Room
 * (table private_chat_messages) so conversations open instantly and work offline:
 *
 *  - [runSync] follows the log with a long poll while the app is on screen and writes new events into Room in one
 *    transaction per page. The cursor is the highest event number stored in Room, so wiping the table (another
 *    account, a new phone) automatically makes the next sync restore the whole history.
 *  - [send] writes the message to Room first (it appears at once as "sending"), then delivers it. The device chooses
 *    the message's clientId, so a retry after a lost answer can never create a second copy. Undelivered messages stay
 *    in Room and are sent again by the outbox flush.
 *
 * Messages only arrive while the app is open: there is no push service (no Firebase), by design.
 */
class ChatRepository private constructor(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val calls = SessionCalls.getInstance(context)
    private val backend = ChatBackend(calls.http)
    private val friendsRepo = FriendsRepository.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Messages are delivered one at a time, in the order they were written. */
    private val deliverMutex = Mutex()

    /** The conversation the user is looking at right now: its new messages count as read at once. */
    @Volatile
    private var activeConversation: String? = null

    fun openConversation(handle: String) {
        activeConversation = handle
    }

    fun closeConversation(handle: String) {
        if (activeConversation == handle) activeConversation = null
    }

    /** The newest [limit] messages of the conversation with [handle], oldest first. */
    fun messages(handle: String, limit: Int): Flow<List<PrivateChatMessageEntity>> = dao.getRecentMessages(handle, limit)

    // ------------------------------------------------------------------ who is the friend

    /** The friend's server id for a handle, from the stored list, then the loaded one, then (optionally) a refresh. */
    private suspend fun peerIdOf(handle: String, refreshIfMissing: Boolean): String? {
        dao.getPrivateFriendByHandle(handle)?.nexoraId?.takeIf { it.isNotBlank() }?.let { return it }
        friendsRepo.state.value.friends
            .firstOrNull { it.handle.equals(handle, ignoreCase = true) }
            ?.nexoraId?.let { return it }
        if (!refreshIfMissing) return null
        friendsRepo.refresh()
        return dao.getPrivateFriendByHandle(handle)?.nexoraId?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ sending

    /** Writes [text] to the conversation with [handle] and delivers it. The message is visible before this returns. */
    suspend fun send(handle: String, text: String): ChatActionResult {
        val body = text.trim()
        if (body.isEmpty()) return ChatActionResult.Rejected("Type a message first.")
        if (body.length > MAX_MESSAGE_CHARS) {
            return ChatActionResult.Rejected("A message can have at most $MAX_MESSAGE_CHARS characters.")
        }
        val session = SessionStore.current() ?: return ChatActionResult.Rejected("Sign in with Google to chat.")
        val peerId = peerIdOf(handle, refreshIfMissing = true)
            ?: return ChatActionResult.Rejected("This friend is not available right now. Open Friends and refresh.")

        val clientId = "nx" + UUID.randomUUID().toString().replace("-", "")
        db.withTransaction {
            dao.insertChatMessageIfAbsent(
                PrivateChatMessageEntity(
                    messageId = clientId,
                    conversationHandle = handle,
                    senderHandle = session.handle,
                    text = body,
                    timestamp = System.currentTimeMillis(),
                    isFromMe = true,
                    isRead = false,
                    peerId = peerId,
                    serverSeq = 0L,
                    sendState = CHAT_SEND_STATE_PENDING
                )
            )
            dao.syncFriendSummaries(listOf(handle))
        }
        return deliver(clientId)
    }

    /** Sends a message again that was refused earlier (for example while a friendship was being renewed). */
    suspend fun retry(messageId: String): ChatActionResult {
        val message = dao.getChatMessage(messageId) ?: return ChatActionResult.Done
        if (!message.isFromMe || message.sendState != CHAT_SEND_STATE_FAILED) return ChatActionResult.Done
        dao.setChatSendState(messageId, CHAT_SEND_STATE_PENDING)
        return deliver(messageId)
    }

    /** Removes a message that was refused and that the user does not want to send. Delivered messages cannot be removed. */
    suspend fun discardUnsent(messageId: String) {
        val message = dao.getChatMessage(messageId) ?: return
        if (!message.isFromMe || message.sendState != CHAT_SEND_STATE_FAILED) return
        db.withTransaction {
            dao.deleteChatMessage(messageId)
            dao.syncFriendSummaries(listOf(message.conversationHandle))
        }
    }

    private suspend fun deliver(messageId: String): ChatActionResult = deliverMutex.withLock {
        val message = dao.getChatMessage(messageId) ?: return@withLock ChatActionResult.Done
        if (!message.isFromMe || message.sendState != CHAT_SEND_STATE_PENDING) return@withLock ChatActionResult.Done
        val result = calls.authed { token -> backend.send(token, message.peerId, message.messageId, message.text) }
        when (result) {
            is BackendResult.Ok -> {
                dao.markChatDelivered(messageId)
                ChatActionResult.Done
            }
            is BackendResult.Fatal -> {
                dao.setChatSendState(messageId, CHAT_SEND_STATE_FAILED)
                ChatActionResult.Failed(result.message)
            }
            null -> ChatActionResult.Queued("Sign in with Google to send messages.")
            BackendResult.Unauthorized -> ChatActionResult.Queued("Your session has expired. Please sign in again.")
            BackendResult.NotFound ->
                ChatActionResult.Queued("The server does not have the chat service yet. The message will be sent later.")
            is BackendResult.Retryable ->
                ChatActionResult.Queued("Cannot reach the server. The message will be sent when you are online.")
        }
    }

    /** Delivers every message still waiting, oldest first; stops at the first one the server cannot be reached for. */
    private suspend fun flushOutbox() {
        for (message in dao.getPendingOutgoing()) {
            if (deliver(message.messageId) is ChatActionResult.Queued) return
        }
    }

    // ------------------------------------------------------------------ reading

    /**
     * Marks the conversation as read on this phone at once and tells the server (best effort: the friend sees the
     * double tick). The server keeps only the highest number, so sending it again is harmless.
     */
    suspend fun markConversationRead(handle: String) {
        dao.markMessagesAsRead(handle)
        dao.clearUnreadCount(handle)
        val peerId = peerIdOf(handle, refreshIfMissing = false) ?: return
        val newest = dao.getNewestReceivedSeq(peerId)
        if (newest <= 0L) return
        calls.authed(maxAttempts = RECEIPT_ATTEMPTS) { token -> backend.markRead(token, peerId, newest) }
    }

    // ------------------------------------------------------------------ sync

    /**
     * Follows the server's chat log until the calling coroutine is cancelled (call it from a lifecycle-aware scope
     * that is active while the app is on screen). Failures never end the loop: it waits with exponential backoff and
     * jitter and tries again. While nobody is signed in it only checks for a session now and then.
     */
    suspend fun runSync() {
        var failures = 0
        var ownerId: String? = null
        var cursor = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val session = SessionStore.current()
            if (session == null) {
                ownerId = null
                delay(SIGNED_OUT_POLL_MS)
                continue
            }
            if (session.nexoraId != ownerId) {
                claimOwnership(session.nexoraId)
                ownerId = session.nexoraId
                cursor = dao.getChatCursor()
                flushOutbox()
            }

            val startedAt = SystemClock.elapsedRealtime()
            // After a failure ask without waiting, so the first answer shows at once whether the server is back.
            val wait = if (failures == 0) WAIT_SECONDS else 0
            val after = cursor
            val result = calls.authed(maxAttempts = 1) { token -> backend.sync(token, after, wait, PAGE_SIZE) }
            when (result) {
                is BackendResult.Ok -> {
                    val page = result.value
                    val stored = try {
                        applyPage(page, session)
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not store chat events: ${e.message}")
                        false
                    }
                    if (!stored) {
                        failures += 1
                        delay(backoffMs(failures))
                        continue
                    }
                    if (page.cursor > cursor) cursor = page.cursor
                    val recovered = failures > 0
                    failures = 0
                    if (recovered) flushOutbox()
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    when {
                        page.retryAfterMs > 0L -> delay(min(page.retryAfterMs, MAX_SERVER_PAUSE_MS))
                        // An empty answer that came back at once (a proxy that ignores the wait): never spin.
                        page.events.isEmpty() && elapsed < MIN_POLL_GAP_MS -> delay(MIN_POLL_GAP_MS - elapsed)
                    }
                }
                null, BackendResult.Unauthorized -> delay(SIGNED_OUT_POLL_MS)
                BackendResult.NotFound -> {
                    // The server runs a version without the chat service. Look again now and then.
                    failures += 1
                    delay(SERVICE_MISSING_RETRY_MS)
                }
                is BackendResult.Retryable, is BackendResult.Fatal -> {
                    failures += 1
                    delay(backoffMs(failures))
                }
            }
        }
    }

    private fun backoffMs(failures: Int): Long {
        val ceiling = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl min(failures - 1, 5))
        return Random.nextLong(ceiling / 2, ceiling + 1)
    }

    /** Nothing of another account's chats may be visible: the first sync of a different account starts from empty. */
    private suspend fun claimOwnership(nexoraId: String) {
        if (prefs.getString(KEY_OWNER, null) == nexoraId) return
        db.withTransaction {
            dao.deleteAllChatMessages()
            dao.deleteAllPrivateFriends()
        }
        prefs.edit().putString(KEY_OWNER, nexoraId).commit()
    }

    /**
     * Stores one page of events in a single transaction. Every write is idempotent (a message is keyed by its clientId
     * or its event number, a receipt only ever raises what is read), so a page that is applied twice changes nothing.
     */
    private suspend fun applyPage(page: ChatPage, me: AccountSession) {
        if (page.events.isEmpty()) return
        val unknownPeer = db.withTransaction {
            val active = activeConversation
            val handles = HashMap<String, String>()
            val touched = LinkedHashSet<String>()
            var unknown = false

            for (event in page.events) {
                var handle = handles[event.peerId]
                if (handle == null) {
                    val friend = dao.getPrivateFriendByNexoraId(event.peerId)
                    handle = if (friend != null) {
                        friend.handle
                    } else {
                        unknown = true
                        dao.getConversationHandleOf(event.peerId)
                            ?: page.peers[event.peerId]?.handle?.takeIf { it.isNotBlank() }
                            ?: event.peerId
                    }
                    handles[event.peerId] = handle
                }
                touched.add(handle)

                when (event.kind) {
                    ChatEventKind.MESSAGE ->
                        if (event.fromMe) {
                            // My own message came back from the log (or was written on another device of mine).
                            val updated = dao.confirmSentMessage(event.clientId, event.seq, event.createdAt)
                            if (updated == 0) {
                                dao.insertChatMessageIfAbsent(
                                    PrivateChatMessageEntity(
                                        messageId = event.clientId,
                                        conversationHandle = handle,
                                        senderHandle = me.handle,
                                        text = event.body,
                                        timestamp = event.createdAt,
                                        isFromMe = true,
                                        isRead = false,
                                        peerId = event.peerId,
                                        serverSeq = event.seq,
                                        sendState = CHAT_SEND_STATE_SENT
                                    )
                                )
                            }
                        } else {
                            dao.insertChatMessageIfAbsent(
                                PrivateChatMessageEntity(
                                    messageId = "r${event.seq}",
                                    conversationHandle = handle,
                                    senderHandle = handle,
                                    text = event.body,
                                    timestamp = event.createdAt,
                                    isFromMe = false,
                                    isRead = handle == active,
                                    peerId = event.peerId,
                                    serverSeq = event.seq,
                                    sendState = CHAT_SEND_STATE_SENT
                                )
                            )
                        }
                    ChatEventKind.READ ->
                        if (event.fromMe) {
                            dao.markReceivedMessagesRead(event.peerId, event.upTo)
                        } else {
                            dao.markSentMessagesRead(event.peerId, event.upTo)
                        }
                }
            }
            dao.syncFriendSummaries(touched.toList())
            unknown
        }
        // A conversation with someone the stored friends list does not know yet (a friendship that was just accepted).
        if (unknownPeer) friendsRepo.refresh()
    }

    companion object {
        private const val PREFS = "nexora_chat_prefs"
        private const val KEY_OWNER = "owner_nexora_id"
        private const val TAG = "ChatRepository"

        const val MAX_MESSAGE_CHARS = 4000

        private const val WAIT_SECONDS = 25
        private const val PAGE_SIZE = 100
        private const val RECEIPT_ATTEMPTS = 2
        private const val MIN_POLL_GAP_MS = 1_000L
        private const val SIGNED_OUT_POLL_MS = 5_000L
        private const val SERVICE_MISSING_RETRY_MS = 60_000L
        private const val MAX_SERVER_PAUSE_MS = 60_000L
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(context: Context): ChatRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
