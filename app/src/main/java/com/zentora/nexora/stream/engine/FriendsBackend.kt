/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

// ---------------------------------------------------------------------------
// Types (what the Nexora server's Friends vault, Vault 35, returns)
// ---------------------------------------------------------------------------

/** A person as the server shares them: nothing but id, name, handle and picture. [sinceMs] is when the link began. */
data class FriendPerson(
    val nexoraId: String,
    val username: String,
    val handle: String,
    val avatarUri: String,
    val sinceMs: Long
)

enum class FriendRelation { NONE, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED }

data class PersonHit(val person: FriendPerson, val relation: FriendRelation)

/** Everything the Friends screen needs, from one request. [me] is the signed-in account exactly as the server holds it. */
data class FriendsOverview(
    val me: FriendPerson,
    val friends: List<FriendPerson>,
    val incoming: List<FriendPerson>,
    val outgoing: List<FriendPerson>
)

enum class FriendRequestOutcome { SENT, ALREADY_SENT, ALREADY_FRIENDS, ACCEPTED }

// ---------------------------------------------------------------------------
// JSON helpers (org.json turns JSON null into the string "null", so every read goes through these)
// ---------------------------------------------------------------------------

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

private fun JSONObject.toPerson(): FriendPerson {
    val id = str("nexoraId")
    if (id.isBlank()) throw JSONException("nexoraId missing")
    return FriendPerson(
        nexoraId = id,
        username = str("username"),
        handle = str("handle"),
        avatarUri = str("avatarUri"),
        sinceMs = if (isNull("since")) 0L else optLong("since", 0L)
    )
}

private fun JSONArray?.toPeople(): List<FriendPerson> {
    if (this == null) return emptyList()
    val out = ArrayList<FriendPerson>(length())
    for (i in 0 until length()) {
        val item = optJSONObject(i) ?: continue
        out.add(item.toPerson())
    }
    return out
}

private fun relationOf(value: String): FriendRelation = when (value) {
    "friends" -> FriendRelation.FRIENDS
    "request_sent" -> FriendRelation.REQUEST_SENT
    "request_received" -> FriendRelation.REQUEST_RECEIVED
    else -> FriendRelation.NONE
}

/** Turns the server's machine readable error into a sentence the user can read. */
private fun friendsError(error: String, code: Int): String = when (error) {
    "user_not_found" -> "No account with that handle exists."
    "cannot_add_self" -> "You cannot add yourself."
    "too_many_requests" -> "You have too many open requests. Wait for answers or cancel some first."
    "query_too_short" -> "Type at least 3 characters."
    "invalid_request" -> "That request was not valid."
    "request_not_found" -> "That request no longer exists."
    "friend_not_found" -> "You are no longer friends."
    "" -> "The server answered with an error ($code)."
    else -> "The server rejected the request ($error)."
}

// ---------------------------------------------------------------------------
// Client for /api/v1/account/friends/* on the self-hosted Nexora server
// ---------------------------------------------------------------------------

/**
 * Every call takes the Nexora session token explicitly, so the caller decides what happens on
 * [BackendResult.Unauthorized]. Retrying (exponential backoff with jitter) is done by SessionCalls.
 */
class FriendsBackend(private val http: NexoraHttp) {

    private companion object {
        const val PATH = "/api/v1/account/friends"
    }

    private fun classify(code: Int, text: String): BackendResult<JSONObject> {
        val json = http.parseObject(text)
        val error = json?.optString("error", "").orEmpty()
        return when {
            // A 2xx that is not JSON is the proxy / "service waking up" page, not our API.
            code in 200..299 -> if (json != null) BackendResult.Ok(json) else BackendResult.Retryable(code)
            code == 404 -> BackendResult.NotFound
            code == 401 || code == 403 -> BackendResult.Unauthorized
            // The per-account request limit is an answer, not a hiccup: retrying would never help.
            code == 429 && error == "too_many_requests" -> BackendResult.Fatal(code, friendsError(error, code))
            code == 408 || code == 425 || code == 429 || code >= 500 -> BackendResult.Retryable(code)
            else -> BackendResult.Fatal(code, friendsError(error, code))
        }
    }

    private suspend fun call(method: String, path: String, bearer: String, body: JSONObject? = null): BackendResult<JSONObject> =
        try {
            val raw = http.exchange(method, path, bearer, body)
            classify(raw.code, raw.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            BackendResult.Retryable(null, e)
        }

    /** Parses a 2xx body. A body without the expected shape is treated as a retryable server hiccup. */
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

    // ------------------------------------------------------------------ endpoints

    /** Friends, requests received and requests sent, plus the signed-in account as the server knows it. */
    suspend fun overview(sessionToken: String): BackendResult<FriendsOverview> =
        call("GET", "$PATH/list", sessionToken).parse { json ->
            val me = json.optJSONObject("me") ?: throw JSONException("me missing")
            FriendsOverview(
                me = me.toPerson(),
                friends = json.optJSONArray("friends").toPeople(),
                incoming = json.optJSONArray("incoming").toPeople(),
                outgoing = json.optJSONArray("outgoing").toPeople()
            )
        }

    /** People whose handle starts with [query] (3+ characters). NotFound = this server has no Friends vault yet. */
    suspend fun search(sessionToken: String, query: String): BackendResult<List<PersonHit>> {
        val q = URLEncoder.encode(query, "UTF-8")
        return call("GET", "$PATH/search?q=$q", sessionToken).parse { json ->
            val array = json.optJSONArray("people")
            val out = ArrayList<PersonHit>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    out.add(PersonHit(item.toPerson(), relationOf(item.str("relation"))))
                }
            }
            out
        }
    }

    /** NotFound = nobody has that handle. */
    suspend fun requestByHandle(sessionToken: String, handle: String): BackendResult<FriendRequestOutcome> =
        call("POST", "$PATH/request", sessionToken, JSONObject().put("handle", handle)).parse { it.toOutcome() }

    suspend fun requestById(sessionToken: String, nexoraId: String): BackendResult<FriendRequestOutcome> =
        call("POST", "$PATH/request", sessionToken, JSONObject().put("nexoraId", nexoraId)).parse { it.toOutcome() }

    /** NotFound = the request is gone (withdrawn or already answered). */
    suspend fun respond(sessionToken: String, nexoraId: String, accept: Boolean): BackendResult<Unit> =
        call("POST", "$PATH/respond", sessionToken, JSONObject().put("nexoraId", nexoraId).put("accept", accept)).parse { }

    suspend fun cancel(sessionToken: String, nexoraId: String): BackendResult<Unit> =
        call("POST", "$PATH/cancel", sessionToken, JSONObject().put("nexoraId", nexoraId)).parse { }

    suspend fun remove(sessionToken: String, nexoraId: String): BackendResult<Unit> =
        call("POST", "$PATH/remove", sessionToken, JSONObject().put("nexoraId", nexoraId)).parse { }

    private fun JSONObject.toOutcome(): FriendRequestOutcome = when (str("outcome")) {
        "sent" -> FriendRequestOutcome.SENT
        "already_sent" -> FriendRequestOutcome.ALREADY_SENT
        "already_friends" -> FriendRequestOutcome.ALREADY_FRIENDS
        "accepted" -> FriendRequestOutcome.ACCEPTED
        else -> throw JSONException("unknown outcome")
    }
}
