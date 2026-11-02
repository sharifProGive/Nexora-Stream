/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

// ---------------------------------------------------------------------------
// Types (what Vault 35, sub-vault friend-chat, returns)
// ---------------------------------------------------------------------------

enum class ChatEventKind { MESSAGE, READ }

/**
 * One entry of the chat event log as the signed-in account sees it.
 * MESSAGE: [clientId] and [body] are set. READ: [upTo] is the newest message number that was read
 * (fromMe = I read the friend's messages, otherwise the friend read mine).
 */
data class ChatEvent(
    val seq: Long,
    val kind: ChatEventKind,
    val peerId: String,
    val fromMe: Boolean,
    val createdAt: Long,
    val clientId: String,
    val body: String,
    val upTo: Long
)

/** The other side of a conversation as the server knows them right now. */
data class ChatPeer(val nexoraId: String, val username: String, val handle: String, val avatarUri: String)

data class ChatPage(
    val events: List<ChatEvent>,
    val peers: Map<String, ChatPeer>,
    /** Pass this as `after` next time. */
    val cursor: Long,
    val more: Boolean,
    /** The server asks the device to wait this long before the next poll (0 = no wish). */
    val retryAfterMs: Long
)

// ---------------------------------------------------------------------------
// JSON helpers (org.json turns JSON null into the string "null", so every read goes through these)
// ---------------------------------------------------------------------------

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

private fun JSONObject.long(key: String): Long = if (isNull(key)) 0L else optLong(key, 0L)

private fun JSONObject.toEvent(): ChatEvent? {
    val kind = when (str("kind")) {
        "message" -> ChatEventKind.MESSAGE
        "read" -> ChatEventKind.READ
        // A kind added by a later sub-vault (files, calls) that this app version does not know: skipped.
        else -> return null
    }
    val seq = long("seq")
    val peerId = str("peerId")
    if (seq < 1 || peerId.isBlank()) throw JSONException("event without seq or peerId")
    return ChatEvent(
        seq = seq,
        kind = kind,
        peerId = peerId,
        fromMe = optBoolean("fromMe", false),
        createdAt = long("createdAt"),
        clientId = str("clientId"),
        body = str("body"),
        upTo = long("upTo")
    )
}

private fun JSONArray?.toPeers(): Map<String, ChatPeer> {
    if (this == null) return emptyMap()
    val out = HashMap<String, ChatPeer>(length())
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        val id = item.str("nexoraId")
        if (id.isBlank()) continue
        out[id] = ChatPeer(id, item.str("username"), item.str("handle"), item.str("avatarUri"))
    }
    return out
}

/** Turns the server's machine readable error into a sentence the user can read. */
private fun chatError(error: String, code: Int): String = when (error) {
    "not_friends" -> "You are not friends with this person any more."
    "cannot_message_self" -> "You cannot message yourself."
    "invalid_message" -> "That message is empty or longer than 4000 characters."
    "invalid_request" -> "That message could not be sent."
    "slow_down" -> "You are sending too fast. Wait a minute and try again."
    "" -> "The server answered with an error ($code)."
    else -> "The server rejected the message ($error)."
}

// ---------------------------------------------------------------------------
// Client for /api/v1/account/friends/chat/* on the self-hosted Nexora server
// ---------------------------------------------------------------------------

/**
 * Every call takes the Nexora session token explicitly, so the caller decides what happens on
 * [BackendResult.Unauthorized]. Retrying is the caller's job (see SessionCalls); a sync call must not be retried
 * inside, because the sync loop paces itself.
 */
class ChatBackend(private val http: NexoraHttp) {

    private companion object {
        const val PATH = "/api/v1/account/friends/chat"
        // The server holds an empty sync for at most this many seconds (see chat-routes.js).
        const val MAX_WAIT_SECONDS = 25
        const val MAX_PAGE = 200
        // Longer than the longest hold, so a quiet poll is never cut off by the client.
        const val SYNC_READ_SLACK_MS = 20_000
    }

    private fun classify(code: Int, text: String): BackendResult<JSONObject> {
        val json = http.parseObject(text)
        val error = json?.optString("error", "").orEmpty()
        return when {
            // A 2xx that is not JSON is the proxy / "service waking up" page, not our API.
            code in 200..299 -> if (json != null) BackendResult.Ok(json) else BackendResult.Retryable(code)
            code == 404 -> BackendResult.NotFound
            code == 401 -> BackendResult.Unauthorized
            code == 403 && error == "not_friends" -> BackendResult.Fatal(code, chatError(error, code))
            code == 403 -> BackendResult.Unauthorized
            // The per-account sending limit is an answer, not a hiccup: retrying at once would never help.
            code == 429 && error == "slow_down" -> BackendResult.Fatal(code, chatError(error, code))
            code == 408 || code == 425 || code == 429 || code >= 500 -> BackendResult.Retryable(code)
            else -> BackendResult.Fatal(code, chatError(error, code))
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        bearer: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = NexoraHttp.DEFAULT_READ_TIMEOUT_MS
    ): BackendResult<JSONObject> =
        try {
            val raw = http.exchange(method, path, bearer, body, readTimeoutMs)
            classify(raw.code, raw.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            BackendResult.Retryable(null, e)
        }

    private inline fun <R> BackendResult<JSONObject>.parse(transform: (JSONObject) -> R): BackendResult<R> =
        when (this) {
            is BackendResult.Ok -> try {
                BackendResult.Ok(transform(value))
            } catch (e: JSONException) {
                BackendResult.Retryable(null, e)
            }
            BackendResult.NotFound -> BackendResult.NotFound
            BackendResult.Unauthorized -> BackendResult.Unauthorized
            is BackendResult.Retryable -> this
            is BackendResult.Fatal -> this
        }

    /**
     * Stores [text] for the friend [peerId]. [clientId] is chosen by this device: sending the same message again
     * (a retry after a lost answer) returns the stored one instead of creating a second copy.
     */
    suspend fun send(sessionToken: String, peerId: String, clientId: String, text: String): BackendResult<Unit> =
        call(
            "POST", "$PATH/send", sessionToken,
            JSONObject().put("nexoraId", peerId).put("clientId", clientId).put("text", text)
        ).parse { }

    /** "I have read everything [peerId] sent up to message number [upToSeq]". NotFound = nothing to mark. */
    suspend fun markRead(sessionToken: String, peerId: String, upToSeq: Long): BackendResult<Unit> =
        call(
            "POST", "$PATH/read", sessionToken,
            JSONObject().put("nexoraId", peerId).put("upToSeq", upToSeq)
        ).parse { }

    /**
     * Events after [after]. With [waitSeconds] > 0 an empty answer is held by the server until something arrives
     * (long poll) or the time is over.
     */
    suspend fun sync(sessionToken: String, after: Long, waitSeconds: Int, limit: Int): BackendResult<ChatPage> {
        val wait = waitSeconds.coerceIn(0, MAX_WAIT_SECONDS)
        val page = limit.coerceIn(1, MAX_PAGE)
        val path = "$PATH/sync?after=$after&wait=$wait&limit=$page"
        return call("GET", path, sessionToken, null, wait * 1000 + SYNC_READ_SLACK_MS).parse { json ->
            val array = json.optJSONArray("events")
            val events = ArrayList<ChatEvent>(array?.length() ?: 0)
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    item.toEvent()?.let { events.add(it) }
                }
            }
            ChatPage(
                events = events,
                peers = json.optJSONArray("people").toPeers(),
                cursor = json.long("cursor"),
                more = json.optBoolean("more", false),
                retryAfterMs = json.long("retryAfterMs")
            )
        }
    }
}
