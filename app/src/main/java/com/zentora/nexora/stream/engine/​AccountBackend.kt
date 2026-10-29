/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

data class GoogleProfileHint(val email: String, val displayName: String, val avatarUri: String)

/** What the server knows about an account. [sessionToken] is issued by the Nexora backend itself. */
data class CloudIdentity(
    val nexoraId: String,
    val sessionToken: String,
    val email: String,
    val username: String,
    val handle: String,
    val avatarUri: String
)

data class AccountProfile(
    val nexoraId: String,
    val email: String,
    val username: String,
    val handle: String,
    val avatarUri: String
)

data class PrivateDetails(val phoneNumber: String, val billingInfo: String)

/** One video as returned by the Nexora backend. [localUri] is the playable absolute https URL. */
data class ServerVideo(
    val id: String,
    val localUri: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val timestamp: Long,
    val duration: Long,
    val viewCount: Long,
    val chapterMarkersJson: String,
    val thumbnailUri: String,
    val authorChannelId: String,
    val collabChannelId: String?,
    val isPremiere: Boolean,
    val premiereScheduledTimeMs: Long?,
    val likeCount: Long,
    val sparkCount: Long,
    val ownerNexoraId: String,
    val channelName: String,
    val channelAvatarUri: String,
    val visibility: String
)

data class ServerComment(
    val commentId: String,
    val videoId: String,
    val authorName: String,
    val text: String,
    val timestamp: Long,
    val likeCount: Long
)

data class ServerProfile(
    val nexoraId: String,
    val username: String,
    val handle: String,
    val avatarUri: String,
    val creatorLevel: String,
    val videos: List<ServerVideo>,
    val likedVideoIds: List<String>
)

data class InteractionCounts(val viewCount: Long, val likeCount: Long, val sparkCount: Long)

data class UploadedMedia(val name: String, val path: String, val url: String)

/** A readable media source. [open] is called again every time an upload has to resume from an offset. */
class MediaPayload(val length: Long, val open: () -> InputStream)

sealed interface BackendResult<out T> {
    data class Ok<T>(val value: T) : BackendResult<T>
    data object NotFound : BackendResult<Nothing>
    data object Unauthorized : BackendResult<Nothing>
    data class Retryable(val code: Int?, val cause: Throwable? = null) : BackendResult<Nothing>
    data class Fatal(val code: Int, val message: String) : BackendResult<Nothing>
}

// ---------------------------------------------------------------------------
// JSON helpers (org.json turns JSON null into the string "null", so every read goes through these)
// ---------------------------------------------------------------------------

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")

private fun JSONObject.textOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").takeIf { it.isNotEmpty() }

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

private fun JSONObject.toAccountProfile(): AccountProfile {
    val id = text("nexoraId")
    if (id.isBlank()) throw JSONException("nexoraId missing")
    return AccountProfile(
        nexoraId = id,
        email = text("email"),
        username = text("username"),
        handle = text("handle"),
        avatarUri = text("avatarUri")
    )
}

private fun JSONObject.toServerVideo(): ServerVideo = ServerVideo(
    id = text("id"),
    localUri = text("localUri"),
    title = text("title"),
    description = text("description"),
    category = text("category").ifBlank { "General" },
    tags = text("tags"),
    timestamp = optLong("timestamp", 0L),
    duration = optLong("duration", 0L),
    viewCount = optLong("viewCount", 0L),
    chapterMarkersJson = text("chapterMarkersJson").ifBlank { "[]" },
    thumbnailUri = text("thumbnailUri"),
    authorChannelId = text("authorChannelId"),
    collabChannelId = textOrNull("collabChannelId"),
    isPremiere = optBoolean("isPremiere", false),
    premiereScheduledTimeMs = longOrNull("premiereScheduledTimeMs"),
    likeCount = optLong("likeCount", 0L),
    sparkCount = optLong("sparkCount", 0L),
    ownerNexoraId = text("ownerNexoraId"),
    channelName = text("channelName"),
    channelAvatarUri = text("channelAvatarUri"),
    visibility = text("visibility").ifBlank { "Public" }
)

private fun parseVideos(array: JSONArray?): List<ServerVideo> {
    if (array == null) return emptyList()
    val out = ArrayList<ServerVideo>(array.length())
    for (i in 0 until array.length()) {
        val o = array.optJSONObject(i) ?: continue
        val v = o.toServerVideo()
        if (v.id.isNotBlank() && v.localUri.isNotBlank()) out.add(v)
    }
    return out
}

private fun parseStrings(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val out = ArrayList<String>(array.length())
    for (i in 0 until array.length()) {
        val s = array.optString(i, "")
        if (s.isNotBlank()) out.add(s)
    }
    return out
}

private fun JSONObject.toServerProfile(): ServerProfile {
    val profile = optJSONObject("profile") ?: throw JSONException("profile missing")
    val account = profile.toAccountProfile()
    return ServerProfile(
        nexoraId = account.nexoraId,
        username = account.username,
        handle = account.handle,
        avatarUri = account.avatarUri,
        creatorLevel = profile.text("creatorLevel").ifBlank { "Rising Creator" },
        videos = parseVideos(optJSONArray("videos")),
        likedVideoIds = parseStrings(optJSONArray("likedVideoIds"))
    )
}

private fun JSONObject.toCloudIdentity(): CloudIdentity {
    val account = toAccountProfile()
    val token = text("sessionToken")
    if (token.isBlank()) throw JSONException("sessionToken missing")
    return CloudIdentity(
        nexoraId = account.nexoraId,
        sessionToken = token,
        email = account.email,
        username = account.username,
        handle = account.handle,
        avatarUri = account.avatarUri
    )
}

/** Maps the server's machine readable error to a sentence that can be shown to the user. */
private fun friendlyError(error: String, code: Int): String = when (error) {
    "invalid_video_url", "invalid_id" -> "the uploaded file could not be attached to the video"
    "title_required" -> "the video needs a title"
    "file_too_large" -> "the file is larger than the server accepts"
    "quota_exceeded" -> "your storage space on the server is full"
    "unsupported_media_type" -> "this file type is not supported (use MP4, MOV, 3GP, WebM or MKV for video, JPG, PNG or WebP for images)"
    "not_owner" -> "this video belongs to another account"
    "too_many_uploads" -> "too many uploads are running at the same time"
    "invalid_request" -> "the upload request was not valid"
    "" -> "the server answered with an error ($code)"
    else -> "the server rejected the request ($error)"
}

// ---------------------------------------------------------------------------
// Backend client for the self-hosted Nexora server
// ---------------------------------------------------------------------------

/**
 * @param baseUrl                e.g. https://nexora-backend-fq54.onrender.com
 * @param onSessionTokenRenewed  called when the server sends a fresh session token (X-Nexora-Session-Token)
 *
 * Every call that needs the signed-in account takes the Nexora [sessionToken] explicitly, so the caller decides
 * what happens on [BackendResult.Unauthorized]. Only fetchProfile/register take the Google ID token.
 */
class AccountBackend(
    baseUrl: String,
    private val onSessionTokenRenewed: (String) -> Unit
) {

    private val baseUrl: String = baseUrl.trimEnd('/')

    private companion object {
        // Render's free tier needs up to a minute to wake up, so the first request is allowed to be slow.
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 90_000
        const val CHUNK_READ_TIMEOUT_MS = 120_000
        const val USER_AGENT = "NexoraStream/3.0 (Zentora CLC)"
        const val RENEWED_TOKEN_HEADER = "X-Nexora-Session-Token"
        const val CLIENT_CHUNK_BYTES = 4 * 1024 * 1024
        const val MIN_MEDIA_BYTES = 64L
        const val MAX_CONSECUTIVE_CHUNK_FAILURES = 8
    }

    private class RawResponse(val code: Int, val text: String) {
        fun json(): JSONObject = try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: JSONException) {
            JSONObject()
        }
    }

    // ------------------------------------------------------------------ transport

    private suspend fun exchange(
        method: String,
        path: String,
        bearer: String?,
        body: ByteArray?,
        contentType: String?,
        readTimeoutMs: Int
    ): RawResponse = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val renewed = connection.getHeaderField(RENEWED_TOKEN_HEADER)
            if (!renewed.isNullOrBlank()) onSessionTokenRenewed(renewed)
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            RawResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorOf(text: String, code: Int): String {
        val error = try {
            JSONObject(text).optString("error", "")
        } catch (e: JSONException) {
            ""
        }
        return friendlyError(error, code)
    }

    private fun classify(code: Int, text: String): BackendResult<JSONObject> = when {
        code in 200..299 ->
            if (text.isBlank()) {
                BackendResult.Ok(JSONObject())
            } else {
                try {
                    BackendResult.Ok(JSONObject(text))
                } catch (e: JSONException) {
                    // A 2xx that is not JSON is the proxy / "service waking up" page, not our API.
                    BackendResult.Retryable(code, e)
                }
            }
        code == 404 -> BackendResult.NotFound
        code == 401 || code == 403 -> BackendResult.Unauthorized
        code == 408 || code == 425 || code == 429 || code >= 500 -> BackendResult.Retryable(code)
        else -> BackendResult.Fatal(code, errorOf(text, code))
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        bearer: String?,
        body: JSONObject? = null,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): BackendResult<JSONObject> = try {
        val raw = exchange(method, path, bearer, body?.toString()?.toByteArray(Charsets.UTF_8), null, readTimeoutMs)
        classify(raw.code, raw.text)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        BackendResult.Retryable(null, e)
    }

    /** Parses a 2xx body. A body that does not have the expected shape is treated as a retryable server hiccup. */
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

    // ------------------------------------------------------------------ retry policy

    /**
     * Runs [block] and retries only [BackendResult.Retryable] results with exponential backoff and jitter
     * (the delay is random between half and the full exponential ceiling). Cancellation is never swallowed.
     * [onRetry] receives the number of the attempt that is about to start (2, 3, ...), which the UI can use to
     * show "Waking up the server...".
     */
    suspend fun <T> withBackoff(
        maxAttempts: Int = 6,
        baseDelayMs: Long = 1_000L,
        maxDelayMs: Long = 20_000L,
        onRetry: (attempt: Int) -> Unit = {},
        block: suspend () -> BackendResult<T>
    ): BackendResult<T> {
        var attempt = 1
        while (true) {
            val result = block()
            if (result !is BackendResult.Retryable || attempt >= maxAttempts) return result
            attempt += 1
            onRetry(attempt)
            val ceiling = min(maxDelayMs, baseDelayMs shl min(attempt - 2, 10))
            delay(Random.nextLong(ceiling / 2, ceiling + 1))
        }
    }

    // ------------------------------------------------------------------ account

    /** Wakes the Render instance while the user is still choosing a Google account. */
    suspend fun warmUp() {
        requestJson("GET", "/health", null)
    }

    /** 200 account + session token | 404 no such account (caller registers) | 401 token rejected | 5xx cold start. */
    suspend fun fetchProfile(idToken: String): BackendResult<CloudIdentity> =
        requestJson("GET", "/api/v1/account/get-profile", idToken).parse { it.toCloudIdentity() }

    suspend fun register(idToken: String, hint: GoogleProfileHint): BackendResult<CloudIdentity> {
        val body = JSONObject()
            .put("username", hint.displayName)
            .put("avatarUri", hint.avatarUri)
        return requestJson("POST", "/api/v1/account/register", idToken, body).parse { it.toCloudIdentity() }
    }

    suspend fun restoreProfile(sessionToken: String): BackendResult<ServerProfile> =
        requestJson("GET", "/api/v1/account/restore", sessionToken).parse { it.toServerProfile() }

    suspend fun saveProfile(
        sessionToken: String,
        username: String,
        handle: String,
        avatarUri: String
    ): BackendResult<AccountProfile> {
        val body = JSONObject().put("username", username).put("handle", handle).put("avatarUri", avatarUri)
        return requestJson("POST", "/api/v1/account/save-profile", sessionToken, body).parse { it.toAccountProfile() }
    }

    suspend fun fetchPrivate(sessionToken: String): BackendResult<PrivateDetails> =
        requestJson("GET", "/api/v1/account/get-private", sessionToken).parse {
            PrivateDetails(it.text("phoneNumber"), it.text("billingInfo"))
        }

    suspend fun savePrivate(sessionToken: String, phoneNumber: String, billingInfo: String): BackendResult<Unit> {
        val body = JSONObject().put("phoneNumber", phoneNumber).put("billingInfo", billingInfo)
        return requestJson("POST", "/api/v1/account/save-private", sessionToken, body).parse { }
    }

    // ------------------------------------------------------------------ videos, comments

    /** Public feed, newest first. [before] is the timestamp of the last video of the previous page. */
    suspend fun fetchFeed(limit: Int, before: Long? = null): BackendResult<List<ServerVideo>> {
        val cursor = if (before != null) "&before=$before" else ""
        return requestJson("GET", "/api/v1/feed?limit=$limit$cursor", null).parse { parseVideos(it.optJSONArray("videos")) }
    }

    suspend fun searchVideos(query: String): BackendResult<List<ServerVideo>> {
        val q = URLEncoder.encode(query, "UTF-8")
        return requestJson("GET", "/api/v1/videos/search?q=$q", null).parse { parseVideos(it.optJSONArray("videos")) }
    }

    /**
     * Creates or updates a video of the signed-in account. For a new video the payload needs id, title and videoUrl.
     * For an existing video any subset of the fields may be sent; the others keep their stored value.
     */
    suspend fun publishVideo(sessionToken: String, payload: JSONObject): BackendResult<ServerVideo> =
        requestJson("POST", "/api/v1/videos/publish", sessionToken, payload).parse { json ->
            val video = json.optJSONObject("video") ?: throw JSONException("video missing")
            video.toServerVideo()
        }

    /** Ok(true) = removed, Ok(false) = it was already gone. */
    suspend fun deleteVideo(sessionToken: String, videoId: String): BackendResult<Boolean> =
        requestJson("POST", "/api/v1/videos/delete", sessionToken, JSONObject().put("id", videoId))
            .parse { it.optBoolean("removed", false) }

    /**
     * [action]: "view" (+1 view), "like" / "unlike" (idempotent per account), "spark" (+[amount] sparks).
     */
    suspend fun interact(
        sessionToken: String,
        videoId: String,
        action: String,
        amount: Long = 1L
    ): BackendResult<InteractionCounts> {
        val body = JSONObject().put("id", videoId)
        when (action) {
            "view" -> body.put("views", 1)
            "like" -> body.put("like", 1)
            "unlike" -> body.put("like", -1)
            "spark" -> body.put("sparks", amount)
            else -> return BackendResult.Fatal(400, "Unknown interaction: $action")
        }
        return requestJson("POST", "/api/v1/videos/interact", sessionToken, body).parse {
            InteractionCounts(it.optLong("viewCount", 0L), it.optLong("likeCount", 0L), it.optLong("sparkCount", 0L))
        }
    }

    suspend fun postComment(
        sessionToken: String,
        commentId: String,
        videoId: String,
        text: String,
        timestamp: Long = System.currentTimeMillis()
    ): BackendResult<Unit> {
        val body = JSONObject()
            .put("commentId", commentId)
            .put("videoId", videoId)
            .put("text", text)
            .put("timestamp", timestamp)
        return requestJson("POST", "/api/v1/comments/post", sessionToken, body).parse { }
    }

    suspend fun fetchComments(videoId: String): BackendResult<List<ServerComment>> {
        val id = URLEncoder.encode(videoId, "UTF-8")
        return requestJson("GET", "/api/v1/comments?videoId=$id", null).parse { json ->
            val array = json.optJSONArray("comments")
            val out = ArrayList<ServerComment>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val commentId = o.text("commentId")
                    if (commentId.isBlank()) continue
                    out.add(
                        ServerComment(
                            commentId = commentId,
                            videoId = o.text("videoId").ifBlank { videoId },
                            authorName = o.text("authorName"),
                            text = o.text("text"),
                            timestamp = o.optLong("timestamp", 0L),
                            likeCount = o.optLong("likeCount", 0L)
                        )
                    )
                }
            }
            out
        }
    }

    // ------------------------------------------------------------------ resumable media upload

    private fun openAt(media: MediaPayload, offset: Long): InputStream {
        val stream = media.open()
        try {
            var remaining = offset
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else if (stream.read() >= 0) {
                    remaining -= 1
                } else {
                    throw IOException("The file ended before offset $offset")
                }
            }
            return stream
        } catch (e: IOException) {
            stream.close()
            throw e
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var read = 0
        while (read < buffer.size) {
            val n = stream.read(buffer, read, buffer.size - read)
            if (n < 0) throw IOException("The file ended unexpectedly")
            read += n
        }
    }

    /**
     * Uploads [payload] to the Nexora server in resumable chunks. A dropped connection (very common on mobile data
     * and while Render is waking up) costs at most one chunk: the server's confirmed offset is queried and the
     * transfer continues from there. [resumeUploadId] continues a session started earlier, even after the app was
     * restarted; [onUploadSession] reports the id of a newly started session so the caller can persist it.
     * [kind] is "video" or "image".
     *
     * Ok(UploadedMedia) | NotFound (the server forgot the session: start again without resumeUploadId) |
     * Unauthorized | Retryable (try again later) | Fatal (the file was rejected or cannot be read).
     */
    suspend fun uploadMedia(
        sessionToken: String,
        kind: String,
        payload: MediaPayload,
        resumeUploadId: String? = null,
        onUploadSession: (uploadId: String) -> Unit = {},
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): BackendResult<UploadedMedia> {
        if (payload.length < MIN_MEDIA_BYTES) {
            return BackendResult.Fatal(400, "the selected file is too small to be a valid media file")
        }

        var uploadId = resumeUploadId.orEmpty()
        var offset = 0L
        var alreadyComplete = false

        if (uploadId.isNotEmpty()) {
            val status = withBackoff(maxAttempts = 4) {
                requestJson("GET", "/api/v1/media/status/$uploadId", sessionToken)
            }
            when (status) {
                is BackendResult.Ok -> {
                    offset = status.value.optLong("offset", 0L)
                    alreadyComplete = status.value.optBoolean("complete", false)
                    if (offset < 0L || offset > payload.length) {
                        uploadId = ""
                        offset = 0L
                        alreadyComplete = false
                    }
                }
                is BackendResult.Retryable -> return status
                BackendResult.Unauthorized -> return BackendResult.Unauthorized
                BackendResult.NotFound -> uploadId = ""
                is BackendResult.Fatal -> uploadId = ""
            }
        }

        if (uploadId.isEmpty()) {
            val started = withBackoff(maxAttempts = 6) {
                requestJson(
                    "POST",
                    "/api/v1/media/start",
                    sessionToken,
                    JSONObject().put("kind", kind).put("size", payload.length)
                )
            }
            when (started) {
                is BackendResult.Ok -> uploadId = started.value.text("uploadId")
                is BackendResult.Retryable -> return started
                is BackendResult.Fatal -> return started
                BackendResult.Unauthorized -> return BackendResult.Unauthorized
                BackendResult.NotFound -> return BackendResult.NotFound
            }
            if (uploadId.isEmpty()) return BackendResult.Fatal(502, "the server did not create an upload session")
            offset = 0L
            alreadyComplete = false
            onUploadSession(uploadId)
        }

        var stream: InputStream? = null
        var streamPosition = -1L
        var failures = 0
        try {
            while (!alreadyComplete && offset < payload.length) {
                currentCoroutineContext().ensureActive()

                val chunk: ByteArray = try {
                    val reusable = stream
                    val source: InputStream = if (reusable != null && streamPosition == offset) {
                        reusable
                    } else {
                        reusable?.close()
                        stream = null
                        openAt(payload, offset)
                    }
                    stream = source
                    val buffer = ByteArray(min(CLIENT_CHUNK_BYTES.toLong(), payload.length - offset).toInt())
                    readFully(source, buffer)
                    streamPosition = offset + buffer.size
                    buffer
                } catch (e: IOException) {
                    return BackendResult.Fatal(422, "the selected file could not be read (${e.message})")
                }

                var next: Long? = null
                var resync = false
                try {
                    val raw = exchange(
                        "PUT",
                        "/api/v1/media/chunk/$uploadId?offset=$offset",
                        sessionToken,
                        chunk,
                        "application/octet-stream",
                        CHUNK_READ_TIMEOUT_MS
                    )
                    when {
                        raw.code in 200..299 -> next = raw.json().optLong("offset", -1L).takeIf { it >= 0L }
                        raw.code == 409 -> {
                            val expected = raw.json().optLong("expectedOffset", -1L)
                            if (expected in 0L..payload.length) {
                                offset = expected
                                failures += 1
                            } else {
                                resync = true
                            }
                        }
                        raw.code == 401 || raw.code == 403 -> return BackendResult.Unauthorized
                        raw.code == 404 -> return BackendResult.NotFound
                        raw.code == 408 || raw.code == 425 || raw.code == 429 || raw.code >= 500 -> resync = true
                        else -> return BackendResult.Fatal(raw.code, errorOf(raw.text, raw.code))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    resync = true
                }

                val confirmed = next
                if (confirmed != null) {
                    failures = 0
                    offset = confirmed
                    onProgress(offset, payload.length)
                    continue
                }
                if (!resync) {
                    if (failures > MAX_CONSECUTIVE_CHUNK_FAILURES) return BackendResult.Retryable(409)
                    continue
                }

                failures += 1
                if (failures > MAX_CONSECUTIVE_CHUNK_FAILURES) return BackendResult.Retryable(null)
                val ceiling = min(20_000L, 1_000L shl min(failures - 1, 10))
                delay(Random.nextLong(ceiling / 2, ceiling + 1))
                val status = requestJson("GET", "/api/v1/media/status/$uploadId", sessionToken)
                when (status) {
                    is BackendResult.Ok -> {
                        val serverOffset = status.value.optLong("offset", offset)
                        if (serverOffset in 0L..payload.length) {
                            offset = serverOffset
                            alreadyComplete = status.value.optBoolean("complete", false)
                        }
                    }
                    BackendResult.Unauthorized -> return BackendResult.Unauthorized
                    BackendResult.NotFound -> return BackendResult.NotFound
                    is BackendResult.Retryable -> Unit
                    is BackendResult.Fatal -> return status
                }
            }
        } finally {
            try {
                stream?.close()
            } catch (e: IOException) {
                // The source stream is read-only; there is nothing to recover.
            }
        }

        val finished = withBackoff(maxAttempts = 6) {
            requestJson("POST", "/api/v1/media/finish/$uploadId", sessionToken, JSONObject())
        }
        return finished.parse { json ->
            val name = json.text("name")
            val url = json.text("url")
            if (name.isBlank() || url.isBlank()) throw JSONException("finish response incomplete")
            UploadedMedia(name = name, path = json.text("path"), url = url)
        }
    }
}
