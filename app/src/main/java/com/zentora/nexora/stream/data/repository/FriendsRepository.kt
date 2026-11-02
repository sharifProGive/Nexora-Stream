/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.PrivateFriendEntity
import com.zentora.nexora.stream.data.database.entities.PrivateFriendProfileEntity
import com.zentora.nexora.stream.engine.BackendResult
import com.zentora.nexora.stream.engine.FriendPerson
import com.zentora.nexora.stream.engine.FriendRequestOutcome
import com.zentora.nexora.stream.engine.FriendsBackend
import com.zentora.nexora.stream.engine.FriendsOverview
import com.zentora.nexora.stream.engine.PersonHit
import com.zentora.nexora.stream.engine.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Friends screen shows besides the friends list itself (that list comes from Room, see [FriendsRepository.friends]). */
data class FriendsState(
    /** The signed-in account exactly as the server holds it. Null until the first successful load. */
    val account: FriendPerson? = null,
    /** Confirmed friends with their server ids (the Room copy has no ids; this is what unfriending needs). */
    val friends: List<FriendPerson> = emptyList(),
    val incoming: List<FriendPerson> = emptyList(),
    val outgoing: List<FriendPerson> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    /** Why the last refresh failed, in words the user can read. Null when it worked. */
    val errorMessage: String? = null
)

sealed interface FriendsActionResult {
    data class Done(val message: String) : FriendsActionResult
    data class Failed(val message: String) : FriendsActionResult
}

sealed interface FriendsSearchResult {
    data class Hits(val people: List<PersonHit>) : FriendsSearchResult
    data class Failed(val message: String) : FriendsSearchResult
}

/**
 * Friends, backed by the Nexora server (Vault 35). The server is the only source of truth: two phones see each
 * other because both ask the same server. Room only keeps a copy of the confirmed friends (table private_friends)
 * so the list and the chat screen open instantly and work offline; it is rewritten from the server on every refresh.
 *
 * The identity used for friends is the signed-in account (its server handle and name). There is no second,
 * phone-only identity any more.
 */
class FriendsRepository private constructor(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val calls = SessionCalls.getInstance(context)
    private val backend = FriendsBackend(calls.http)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    /** Confirmed friends (cached copy of the server list). */
    val friends: Flow<List<PrivateFriendEntity>> = dao.getAllPrivateFriends()

    private suspend fun <T> authed(block: suspend (sessionToken: String) -> BackendResult<T>): BackendResult<T>? =
        calls.authed(block = block)

    private fun describe(result: BackendResult<*>?, notFound: String): String = when (result) {
        null -> "Sign in with Google to use Friends."
        BackendResult.Unauthorized -> "Your session has expired. Please sign in again."
        BackendResult.NotFound -> notFound
        is BackendResult.Retryable -> "Cannot reach the server. Check your connection and try again."
        is BackendResult.Fatal -> result.message
        is BackendResult.Ok -> ""
    }

    // ------------------------------------------------------------------ refresh

    /** Loads friends, requests and the own profile from the server. Returns true when the server answered. */
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        val session = SessionStore.current()
        if (session == null) {
            _state.value = FriendsState()
            return@withLock false
        }
        val shown = _state.value.account
        if (shown != null && shown.nexoraId != session.nexoraId) _state.value = FriendsState()

        _state.update { it.copy(isRefreshing = true) }
        val result = authed { token -> backend.overview(token) }
        val ok = result is BackendResult.Ok
        if (result is BackendResult.Ok) {
            try {
                applyOverview(result.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not store the friends list on this phone: ${e.message}")
            }
            _state.update {
                it.copy(
                    account = result.value.me,
                    friends = result.value.friends,
                    incoming = result.value.incoming,
                    outgoing = result.value.outgoing,
                    isRefreshing = false,
                    hasLoaded = true,
                    errorMessage = null
                )
            }
        } else {
            val message = describe(
                result,
                "The server does not have the Friends service yet. Deploy the latest backend and try again."
            )
            _state.update { it.copy(isRefreshing = false, errorMessage = message) }
        }
        ok
    }

    /**
     * Rewrites the Room copy from the server list in one transaction. The chat preview (last message, unread count) of
     * each friend is recomputed from the stored messages; a friend who changed their handle keeps their conversation.
     */
    private suspend fun applyOverview(overview: FriendsOverview) {
        db.withTransaction {
            val current = dao.getAllPrivateFriends().first()
            val byHandle = current.associateBy { it.handle }
            val byId = current.filter { it.nexoraId.isNotBlank() }.associateBy { it.nexoraId }
            val serverHandles = HashSet<String>()
            for (friend in overview.friends) {
                if (friend.handle.isBlank()) continue
                serverHandles.add(friend.handle)
                val old = byHandle[friend.handle] ?: byId[friend.nexoraId]
                if (old != null && old.handle != friend.handle) dao.renameConversation(friend.nexoraId, friend.handle)
                dao.insertPrivateFriend(
                    PrivateFriendEntity(
                        handle = friend.handle,
                        displayName = friend.username.ifBlank { friend.handle.removePrefix("@") },
                        avatarUri = friend.avatarUri,
                        statusBio = old?.statusBio.orEmpty(),
                        lastMessage = old?.lastMessage.orEmpty(),
                        lastMessageTimestamp = old?.lastMessageTimestamp ?: friend.sinceMs,
                        unreadCount = old?.unreadCount ?: 0,
                        // The server has no presence information yet, so nobody is shown as online.
                        isOnline = false,
                        nexoraId = friend.nexoraId
                    )
                )
            }
            for (old in current) {
                if (old.handle !in serverHandles) dao.deletePrivateFriend(old.handle)
            }
            dao.syncAllFriendSummaries()

            // The chat screen reads the own handle from this table: keep it equal to the real account.
            val previousProfile = dao.getPrivateProfileSync()
            dao.deletePrivateProfile()
            dao.insertPrivateProfile(
                PrivateFriendProfileEntity(
                    handle = overview.me.handle,
                    displayName = overview.me.username.ifBlank { overview.me.handle.removePrefix("@") },
                    avatarUri = overview.me.avatarUri,
                    statusBio = previousProfile?.statusBio ?: "Nexora Stream",
                    pinCode = previousProfile?.pinCode,
                    isUnlocked = true,
                    createdAt = previousProfile?.createdAt ?: System.currentTimeMillis()
                )
            )
        }
    }

    // ------------------------------------------------------------------ search and actions

    suspend fun search(query: String): FriendsSearchResult {
        val clean = query.trim().removePrefix("@")
        if (clean.length < MIN_QUERY_LENGTH) return FriendsSearchResult.Hits(emptyList())
        val result = authed { token -> backend.search(token, clean) }
        return if (result is BackendResult.Ok) {
            FriendsSearchResult.Hits(result.value)
        } else {
            FriendsSearchResult.Failed(
                describe(result, "The server does not have the Friends service yet. Deploy the latest backend and try again.")
            )
        }
    }

    suspend fun sendRequest(person: FriendPerson): FriendsActionResult =
        afterRequest(authed { token -> backend.requestById(token, person.nexoraId) }, person.handle)

    suspend fun sendRequestToHandle(handle: String): FriendsActionResult {
        val clean = handle.trim().removePrefix("@")
        if (clean.length < MIN_QUERY_LENGTH) return FriendsActionResult.Failed("Type at least 3 characters.")
        return afterRequest(authed { token -> backend.requestByHandle(token, "@$clean") }, "@$clean")
    }

    private suspend fun afterRequest(result: BackendResult<FriendRequestOutcome>?, handle: String): FriendsActionResult {
        if (result !is BackendResult.Ok) {
            return FriendsActionResult.Failed(describe(result, "No account with that handle exists."))
        }
        refresh()
        return FriendsActionResult.Done(
            when (result.value) {
                FriendRequestOutcome.SENT -> "Friend request sent to $handle."
                FriendRequestOutcome.ALREADY_SENT -> "You already sent a request to $handle."
                FriendRequestOutcome.ALREADY_FRIENDS -> "You are already friends with $handle."
                FriendRequestOutcome.ACCEPTED -> "You and $handle are now friends."
            }
        )
    }

    suspend fun respond(person: FriendPerson, accept: Boolean): FriendsActionResult {
        val result = authed { token -> backend.respond(token, person.nexoraId, accept) }
        // NotFound means the other person withdrew the request: the refresh below removes it from the list.
        if (result !is BackendResult.Ok && result !is BackendResult.NotFound) {
            return FriendsActionResult.Failed(describe(result, ""))
        }
        refresh()
        if (result is BackendResult.NotFound) return FriendsActionResult.Failed("That request no longer exists.")
        return FriendsActionResult.Done(
            if (accept) "You and ${person.handle} are now friends." else "Request from ${person.handle} declined."
        )
    }

    suspend fun cancelRequest(person: FriendPerson): FriendsActionResult {
        val result = authed { token -> backend.cancel(token, person.nexoraId) }
        if (result !is BackendResult.Ok && result !is BackendResult.NotFound) {
            return FriendsActionResult.Failed(describe(result, ""))
        }
        refresh()
        return FriendsActionResult.Done("Request to ${person.handle} withdrawn.")
    }

    /** Ends the friendship with the friend whose handle is [handle]. The Room row is removed by the refresh that follows. */
    suspend fun removeFriend(handle: String): FriendsActionResult {
        val friend = _state.value.friends.firstOrNull { it.handle.equals(handle, ignoreCase = true) }
            ?: return FriendsActionResult.Failed("Your friends list is still loading. Try again in a moment.")
        val result = authed { token -> backend.remove(token, friend.nexoraId) }
        if (result !is BackendResult.Ok && result !is BackendResult.NotFound) {
            return FriendsActionResult.Failed(describe(result, ""))
        }
        refresh()
        return FriendsActionResult.Done("${friend.handle} was removed from your friends.")
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 3
        private const val TAG = "FriendsRepository"

        @Volatile
        private var INSTANCE: FriendsRepository? = null

        fun getInstance(context: Context): FriendsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FriendsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
