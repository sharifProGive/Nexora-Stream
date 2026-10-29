/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.zentora.nexora.stream.data.database.NexoraDatabase
import com.zentora.nexora.stream.data.database.entities.*
import com.zentora.nexora.stream.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Recovery state exposed to the UI ("Restoring your account...")
// ---------------------------------------------------------------------------

enum class RecoveryStep { VERIFYING, REGISTERING, RESTORING, SYNCING }
enum class FailureReason { UNAUTHORIZED, SERVER_UNAVAILABLE, UNKNOWN }

sealed interface RecoveryState {
    data object Idle : RecoveryState
    data object NeedsSignIn : RecoveryState

    /** attempt > 1 means the server is cold-starting -> show "Waking up the server...". */
    data class Restoring(val step: RecoveryStep, val attempt: Int = 1) : RecoveryState
    data class Ready(val nexoraId: String, val isNewUser: Boolean) : RecoveryState
    data class Failed(val reason: FailureReason, val message: String) : RecoveryState
}

/**
 * Single source of truth for Nexora data.
 *
 *  - Room is the on-device cache and drives every screen through Flow.
 *  - The self-hosted Nexora server (Vault 2/3/4) is the source of truth for the account, its videos and its media.
 *  - The account id is never created on the phone. It is returned by the server after it verified the Google ID
 *    token, so reinstalling the app and signing in with the same Google account always restores the same account.
 */
class NexoraStreamRepository(private val context: Context) {

    private val db = NexoraDatabase.getInstance(context)
    private val dao = db.nexoraDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backend = AccountBackend(BACKEND_BASE_URL) { token -> onSessionTokenRenewed(token) }

    /** Serialises recovery / sign-out / background resync so they can never race each other. */
    private val recoveryMutex = Mutex()

    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    /** 0f..1f upload progress per video id while its media is being sent to the server. */
    private val _uploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, Float>> = _uploadProgress.asStateFlow()

    /** Human readable reason when a queued publish had to be given up (file removed, file too large ...). */
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    val allVideos: Flow<List<VideoEntity>> = dao.getAllVideos()
    val allChannels: Flow<List<ChannelEntity>> = dao.getAllChannels()
    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()
    val watchLaterInteractions: Flow<List<InteractionEntity>> = dao.getWatchLaterInteractions()
    val likedInteractions: Flow<List<InteractionEntity>> = dao.getLikedInteractions()
    val allCommunityPosts: Flow<List<CommunityPostEntity>> = dao.getAllCommunityPosts()
    val privateProfile: Flow<PrivateFriendProfileEntity?> = dao.getPrivateProfile()

    private val _isKidsMode = MutableStateFlow(false)
    val isKidsMode: StateFlow<Boolean> = _isKidsMode.asStateFlow()

    private val _masterPin = MutableStateFlow("1234")
    val masterPin: StateFlow<String> = _masterPin.asStateFlow()

    private val _isDataSaver = MutableStateFlow(false)
    val isDataSaver: StateFlow<Boolean> = _isDataSaver.asStateFlow()

    // ----- offline queue (publishes and deletes survive process death) -----
    private val pendingPrefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
    private val pendingLock = Any()
    private val processMutex = Mutex()
    private val drainLock = Any()
    @Volatile private var drainJob: Job? = null
    @Volatile private var drainWorking = false
    private val commentRefreshedAt = ConcurrentHashMap<String, Long>()

    init {
        SessionStore.init(context)
        dropUnverifiedLegacySession()
        // Warms the Render instance. No identity is created here.
        scope.launch { backend.warmUp() }
        beginSession()
    }

    // =====================================================================
    // SESSION GATE + RECOVERY
    // =====================================================================

    /** Called on every app start. With a verified session the app opens at once and syncs in the background. */
    fun beginSession() {
        val s = SessionStore.current()
        if (s == null) {
            _recoveryState.value = RecoveryState.NeedsSignIn
        } else {
            _recoveryState.value = RecoveryState.Ready(s.nexoraId, isNewUser = false)
            scope.launch { backgroundResync() }
        }
    }

    fun reportSignInCancelled() {
        _recoveryState.value = RecoveryState.NeedsSignIn
    }

    fun reportSignInFailure(message: String) {
        _recoveryState.value = RecoveryState.Failed(FailureReason.UNAUTHORIZED, message)
    }

    /**
     * Google ID token -> server verifies it -> 200 existing account (original nexoraId, restore everything)
     *                                          -> 404 new account (register, server derives the id).
     */
    suspend fun recoverOrRegister(idToken: String, hint: GoogleProfileHint): RecoveryState {
        val result = recoveryMutex.withLock { performRecovery(idToken, hint) }
        _recoveryState.value = result
        return result
    }

    private suspend fun performRecovery(idToken: String, hint: GoogleProfileHint): RecoveryState {
        try {
            _recoveryState.value = RecoveryState.Restoring(RecoveryStep.VERIFYING)
            var isNew = false
            val identity: CloudIdentity = when (
                val r = backend.withBackoff(
                    onRetry = { a -> _recoveryState.value = RecoveryState.Restoring(RecoveryStep.VERIFYING, a) }
                ) { backend.fetchProfile(idToken) }
            ) {
                is BackendResult.Ok -> r.value
                BackendResult.NotFound -> {
                    isNew = true
                    _recoveryState.value = RecoveryState.Restoring(RecoveryStep.REGISTERING)
                    val reg = backend.withBackoff(
                        onRetry = { a -> _recoveryState.value = RecoveryState.Restoring(RecoveryStep.REGISTERING, a) }
                    ) { backend.register(idToken, hint) }
                    if (reg is BackendResult.Ok) reg.value else return failure(reg)
                }
                else -> return failure(r)
            }

            val previousId = SessionStore.current()?.nexoraId
            if (previousId != null && previousId != identity.nexoraId) {
                // A different account is signing in on this phone: nothing of the previous one may stay visible.
                clearLocalAccountData()
            }

            val displayName = identity.username.ifBlank { hint.displayName }
            val avatar = identity.avatarUri.ifBlank { hint.avatarUri }
            SessionStore.save(
                AccountSession(
                    nexoraId = identity.nexoraId,
                    email = hint.email,
                    displayName = displayName,
                    handle = identity.handle,
                    avatarUri = avatar,
                    sessionToken = identity.sessionToken
                )
            )

            if (isNew) {
                ensureOwnChannel(displayName, avatar)
            } else {
                _recoveryState.value = RecoveryState.Restoring(RecoveryStep.RESTORING)
                val restored = restoreAccountFromCentralVaults()
                if (restored.isFailure) {
                    return RecoveryState.Failed(
                        FailureReason.SERVER_UNAVAILABLE,
                        "Could not restore your account yet. Please try again."
                    )
                }
            }

            _recoveryState.value = RecoveryState.Restoring(RecoveryStep.SYNCING)
            syncFeedFromCentralVaults().onFailure {
                Log.w(TAG, "Feed sync failed after recovery: ${it.message}")
            }
            kickPending()

            return RecoveryState.Ready(identity.nexoraId, isNewUser = isNew)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Recovery failed: ${e.message}")
            return RecoveryState.Failed(FailureReason.UNKNOWN, "Something went wrong. Please try again.")
        }
    }

    private fun failure(r: BackendResult<*>): RecoveryState.Failed = when (r) {
        BackendResult.Unauthorized ->
            RecoveryState.Failed(FailureReason.UNAUTHORIZED, "We couldn't verify your Google sign-in. Please sign in again.")
        is BackendResult.Retryable ->
            RecoveryState.Failed(FailureReason.SERVER_UNAVAILABLE, "The server is not responding. Check your connection and try again.")
        is BackendResult.Fatal ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Unexpected server response (${r.code}).")
        BackendResult.NotFound ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Account not found.")
        is BackendResult.Ok ->
            RecoveryState.Failed(FailureReason.UNKNOWN, "Unexpected state.")
    }

    private suspend fun backgroundResync() {
        recoveryMutex.withLock {
            try {
                restoreAccountFromCentralVaults()
                syncFeedFromCentralVaults()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background resync failed: ${e.message}")
            }
        }
        kickPending()
    }

    /**
     * Installs created by older builds hold an id that was generated on the phone and never confirmed by the
     * server. Such a session is dropped once so the sign-in gate appears and the real account is recovered.
     * Guest sessions are left alone.
     */
    private fun dropUnverifiedLegacySession() {
        val vault1 = context.getSharedPreferences(VAULT1_PREFS, Context.MODE_PRIVATE)
        val hasToken = !vault1.getString(KEY_AUTH_TOKEN, "").isNullOrBlank()
        val isGuest = vault1.getBoolean(KEY_IS_GUEST, false)
        if (hasToken && !isGuest && SessionStore.current() == null) {
            NexoraIdManager.clearSession(context)
        }
    }

    /** Removes the account from this phone: session, cached data and queued work. The server data is untouched. */
    suspend fun signOut() {
        synchronized(drainLock) {
            drainJob?.cancel()
            drainJob = null
        }
        recoveryMutex.withLock {
            SessionStore.clear()
            clearLocalAccountData()
        }
        _recoveryState.value = RecoveryState.NeedsSignIn
    }

    private suspend fun clearLocalAccountData() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            synchronized(pendingLock) {
                pendingPrefs.edit().clear().commit()
            }
            pendingDir().listFiles()?.forEach { it.delete() }
        }
        _uploadProgress.value = emptyMap()
        _syncError.value = null
    }

    /** A 401 on a session call: the session is expired or the account is unknown to the server. Sign in again. */
    private fun handleUnauthorized() {
        SessionStore.clear()
        NexoraIdManager.clearSession(context)
        _recoveryState.value = RecoveryState.NeedsSignIn
    }

    private fun onSessionTokenRenewed(token: String) {
        if (token.isBlank() || SessionStore.current() == null) return
        SessionStore.updateSessionToken(token)
        context.getSharedPreferences(VAULT1_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    /**
     * Runs a call that needs the server session. Repeats on cold starts / network errors with backoff + jitter.
     * Returns null when nobody is signed in (guest mode): nothing is sent in that case.
     */
    private suspend fun <T> authed(
        maxAttempts: Int = 4,
        block: suspend (sessionToken: String) -> BackendResult<T>
    ): BackendResult<T>? {
        val first = SessionStore.current()?.sessionToken ?: return null
        val result = backend.withBackoff(maxAttempts = maxAttempts) {
            block(SessionStore.current()?.sessionToken ?: first)
        }
        if (result is BackendResult.Unauthorized) handleUnauthorized()
        return result
    }

    private suspend fun ensureOwnChannel(displayName: String, avatarUri: String) {
        val existing = dao.getChannelByIdSync(OWN_CHANNEL_ID)
        dao.insertChannel(
            ChannelEntity(
                channelId = OWN_CHANNEL_ID,
                channelName = displayName.ifBlank { existing?.channelName ?: "Nexora Creator" },
                avatarUri = avatarUri.ifBlank { existing?.avatarUri.orEmpty() },
                bannerUri = existing?.bannerUri.orEmpty(),
                subscriberCount = existing?.subscriberCount ?: 0L,
                isSubscribed = existing?.isSubscribed ?: false,
                creatorLevel = NexoraIdManager.calculateCreatorLevel(existing?.subscriberCount ?: 0L, 0L),
                hasZentoraBadge = true,
                description = existing?.description.orEmpty(),
                joinedTimestamp = existing?.joinedTimestamp ?: System.currentTimeMillis()
            )
        )
    }

    // =====================================================================
    // SERVER -> ROOM
    // =====================================================================

    /** Public feed, newest first, up to three pages. Works for signed-in users and guests. */
    suspend fun syncFeedFromCentralVaults(): Result<Int> {
        return try {
            val selfId = SessionStore.current()?.nexoraId
            var total = 0
            var before: Long? = null
            var page = 0
            while (page < FEED_PAGES) {
                val cursor = before
                val r = backend.withBackoff(maxAttempts = 4) { backend.fetchFeed(FEED_PAGE_SIZE, cursor) }
                if (r !is BackendResult.Ok) {
                    if (page == 0) return Result.failure(IllegalStateException("feed sync failed: $r"))
                    break
                }
                val items = r.value
                total += db.withTransaction { writeVideos(items, selfId, protectedVideoIds()) }
                if (items.size < FEED_PAGE_SIZE) break
                before = items.last().timestamp
                page += 1
            }
            Result.success(total)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Feed sync error: ${e.message}")
            Result.failure(e)
        }
    }

    /** Pulls the profile, every video the account owns and the liked videos from the server into Room. */
    suspend fun restoreAccountFromCentralVaults(): Result<Boolean> {
        if (SessionStore.current() == null) return Result.failure(IllegalStateException("No signed-in session"))
        return try {
            when (val r = authed(maxAttempts = 6) { token -> backend.restoreProfile(token) }) {
                null -> Result.failure(IllegalStateException("No signed-in session"))
                is BackendResult.Ok -> {
                    applyProfile(r.value)
                    Result.success(true)
                }
                BackendResult.NotFound -> Result.success(false)
                else -> Result.failure(IllegalStateException("restore failed: $r"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Account restore error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun applyProfile(profile: ServerProfile) {
        val protectedIds = protectedVideoIds()
        db.withTransaction {
            val existing = dao.getChannelByIdSync(OWN_CHANNEL_ID)
            dao.insertChannel(
                ChannelEntity(
                    channelId = OWN_CHANNEL_ID,
                    channelName = profile.username.ifBlank { existing?.channelName ?: "Nexora Creator" },
                    avatarUri = profile.avatarUri.ifBlank { existing?.avatarUri.orEmpty() },
                    bannerUri = existing?.bannerUri.orEmpty(),
                    subscriberCount = existing?.subscriberCount ?: 0L,
                    isSubscribed = existing?.isSubscribed ?: false,
                    creatorLevel = profile.creatorLevel,
                    hasZentoraBadge = true,
                    description = existing?.description.orEmpty(),
                    joinedTimestamp = existing?.joinedTimestamp ?: System.currentTimeMillis()
                )
            )
            writeVideos(profile.videos, profile.nexoraId, protectedIds)
            for (videoId in profile.likedVideoIds) markLikedFromServer(videoId)
        }
    }

    suspend fun searchGlobalVaults(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        try {
            val r = backend.withBackoff(maxAttempts = 2) { backend.searchVideos(q) }
            if (r is BackendResult.Ok) {
                val items = r.value
                val selfId = SessionStore.current()?.nexoraId
                db.withTransaction { writeVideos(items, selfId, protectedVideoIds()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
        }
    }

    /**
     * Idempotent: REPLACE keyed by video id, so repeating a sync never duplicates a row.
     * Videos that are queued locally for publish or delete are left alone until the queue has been processed.
     */
    private suspend fun writeVideos(items: List<ServerVideo>, selfId: String?, protectedIds: Set<String>): Int {
        var written = 0
        for (item in items) {
            if (item.id in protectedIds) continue
            try {
                val own = selfId != null && item.ownerNexoraId == selfId
                val local = dao.getVideoByIdSync(item.id)
                val channelId = if (own) OWN_CHANNEL_ID else item.authorChannelId.ifBlank { "ch_" + item.ownerNexoraId }
                if (!own) upsertCreatorChannel(channelId, item)
                dao.insertVideo(
                    VideoEntity(
                        id = item.id,
                        localUri = chooseVideoUri(local, item.localUri),
                        title = item.title,
                        description = item.description,
                        category = item.category,
                        tags = withVisibilityTag(item.tags, item.visibility),
                        timestamp = item.timestamp,
                        duration = item.duration,
                        viewCount = maxOf(item.viewCount, local?.viewCount ?: 0L),
                        chapterMarkersJson = item.chapterMarkersJson,
                        thumbnailUri = item.thumbnailUri.ifBlank { local?.thumbnailUri.orEmpty() },
                        authorChannelId = channelId,
                        collabChannelId = item.collabChannelId,
                        isPremiere = item.isPremiere,
                        premiereScheduledTimeMs = item.premiereScheduledTimeMs
                    )
                )
                mergeInteractionTotals(item.id, item.likeCount, item.sparkCount)
                written += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Skipping bad video row ${item.id}: ${e.message}")
            }
        }
        return written
    }

    /** An own video that still exists as a file on this phone keeps playing from the file. */
    private fun chooseVideoUri(local: VideoEntity?, serverUri: String): String {
        if (local != null) {
            val file = localFile(local.localUri)
            if (file != null && file.isFile) return local.localUri
        }
        return serverUri
    }

    private suspend fun upsertCreatorChannel(channelId: String, item: ServerVideo) {
        val existing = dao.getChannelByIdSync(channelId)
        if (existing != null) {
            dao.insertChannel(
                existing.copy(
                    channelName = item.channelName.ifBlank { existing.channelName },
                    avatarUri = item.channelAvatarUri.ifBlank { existing.avatarUri }
                )
            )
        } else {
            dao.insertChannel(
                ChannelEntity(
                    channelId = channelId,
                    channelName = item.channelName.ifBlank { "Nexora Creator" },
                    avatarUri = item.channelAvatarUri,
                    bannerUri = "",
                    subscriberCount = 0L,
                    isSubscribed = false,
                    creatorLevel = NexoraIdManager.calculateCreatorLevel(0L, item.viewCount),
                    hasZentoraBadge = true,
                    description = ""
                )
            )
        }
    }

    private suspend fun mergeInteractionTotals(videoId: String, likes: Long, sparks: Long) {
        val current = dao.getInteractionSync(videoId)
        if (current == null) {
            dao.insertInteraction(InteractionEntity(videoId = videoId, likesCount = likes, sparksReceived = sparks))
        } else {
            dao.updateInteraction(
                current.copy(
                    likesCount = maxOf(likes, if (current.isLikedByMe) 1L else 0L),
                    sparksReceived = maxOf(current.sparksReceived, sparks)
                )
            )
        }
    }

    private suspend fun markLikedFromServer(videoId: String) {
        val current = dao.getInteractionSync(videoId)
        if (current == null) {
            dao.insertInteraction(InteractionEntity(videoId = videoId, likesCount = 1L, isLikedByMe = true))
        } else if (!current.isLikedByMe) {
            dao.updateInteraction(current.copy(isLikedByMe = true, likesCount = maxOf(current.likesCount, 1L)))
        }
    }

    private suspend fun ensureInteractionExists(videoId: String) {
        if (dao.getInteractionSync(videoId) == null) {
            dao.insertInteraction(InteractionEntity(videoId = videoId))
        }
    }

    // =====================================================================
    // PRIVATE PROFILE & INTERACTIONS
    // =====================================================================

    suspend fun saveUserDataToCloud(phoneNumber: String, billingInfo: String): Boolean =
        authed(maxAttempts = 3) { token -> backend.savePrivate(token, phoneNumber, billingInfo) } is BackendResult.Ok

    suspend fun restoreUserDataFromCloud(onDataRestored: (phone: String, billing: String) -> Unit) {
        val r = authed(maxAttempts = 3) { token -> backend.fetchPrivate(token) }
        if (r is BackendResult.Ok) {
            val details = r.value
            withContext(Dispatchers.Main) { onDataRestored(details.phoneNumber, details.billingInfo) }
        }
    }

    /**
     * Saves an edited display name / handle / avatar on the server so they come back after a reinstall.
     * Call it after NexoraIdManager.updateSession(...) in the profile editor. Returns true when the server confirmed.
     */
    suspend fun updateProfileOnCloud(username: String, handle: String, avatarUri: String): Boolean {
        val r = authed(maxAttempts = 3) { token -> backend.saveProfile(token, username, handle, avatarUri) }
        if (r is BackendResult.Ok) {
            val p = r.value
            SessionStore.current()?.let { SessionStore.save(it.copy(displayName = p.username, handle = p.handle, avatarUri = p.avatarUri)) }
            return true
        }
        return false
    }

    private fun syncInBackground(tag: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$tag background sync failed: ${e.message}")
            }
        }
    }

    fun getVideosByAuthor(authorChannelId: String): Flow<List<VideoEntity>> = dao.getVideosByAuthor(authorChannelId)

    suspend fun recordView(videoId: String, durationMs: Long, positionMs: Long) {
        dao.incrementViewCount(videoId)
        dao.insertHistory(HistoryEntity(videoId = videoId, playbackPositionMs = positionMs, timestamp = System.currentTimeMillis()))
        syncInBackground("view") {
            authed(maxAttempts = 3) { token -> backend.interact(token, videoId, "view") }
        }
    }

    // =====================================================================
    // UPLOAD / PUBLISH / EDIT / DELETE
    // =====================================================================

    /**
     * Saves the video locally at once (the UI never waits for the network) and queues it for publishing.
     * The queue uploads the video and thumbnail to the Nexora server in resumable chunks, then publishes it.
     * It survives process death and resumes on the next app start. Guests never upload.
     */
    suspend fun uploadVideo(
        title: String,
        description: String,
        category: String,
        tags: String,
        localUri: String,
        thumbnailUri: String,
        collabChannelId: String? = null,
        isPremiere: Boolean = false,
        scheduledTimeMs: Long? = null,
        duration: Long = 60000L,
        chapterMarkersJson: String = "[]",
        visibility: String = "Public",
        isMadeForKids: Boolean = false,
        isAgeRestricted: Boolean = false,
        location: String = "",
        shortsRemixing: String = "Allow video and audio remixing",
        commentsModeration: String = "On (Strict)",
        showLikesCount: Boolean = true,
        containsPaidPromotion: Boolean = false,
        videoLanguage: String = "English",
        licenseType: String = "Standard Nexora License",
        allowEmbedding: Boolean = true,
        notifySubscribers: Boolean = true
    ): VideoEntity {
        val normalizedVisibility = normalizeVisibility(visibility)
        val storedTags = withVisibilityTag(tags, normalizedVisibility)
        val newVideo = VideoEntity(
            id = "vid_${UUID.randomUUID()}",
            localUri = localUri,
            title = title,
            description = description,
            category = category,
            tags = storedTags,
            timestamp = System.currentTimeMillis(),
            duration = duration,
            viewCount = 0L,
            chapterMarkersJson = chapterMarkersJson,
            thumbnailUri = thumbnailUri,
            authorChannelId = OWN_CHANNEL_ID,
            collabChannelId = collabChannelId,
            isPremiere = isPremiere,
            premiereScheduledTimeMs = scheduledTimeMs
        )
        dao.insertVideo(newVideo)
        ensureInteractionExists(newVideo.id)

        if (SessionStore.current() != null) {
            val entry = PendingPublish(
                id = newVideo.id,
                title = title.trim(),
                description = description,
                category = category,
                tags = storedTags,
                visibility = normalizedVisibility,
                duration = duration,
                chapterMarkersJson = chapterMarkersJson,
                collabChannelId = collabChannelId,
                isPremiere = isPremiere,
                premiereScheduledTimeMs = scheduledTimeMs,
                createdAt = newVideo.timestamp,
                videoSrc = localUri,
                thumbSrc = thumbnailUri
            )
            withContext(Dispatchers.IO) {
                synchronized(pendingLock) {
                    val list = readPublishes()
                    list.removeAll { it.id == entry.id }
                    list.add(entry)
                    writePublishes(list)
                }
            }
            kickPending()
        }
        return newVideo
    }

    suspend fun updateVideoMetadata(id: String, title: String, description: String, category: String, tags: String) {
        val existing = dao.getVideoByIdSync(id) ?: return
        dao.updateVideo(existing.copy(title = title, description = description, category = category, tags = tags))
        if (existing.authorChannelId != OWN_CHANNEL_ID || SessionStore.current() == null) return

        val visibility = visibilityOf(tags)
        val stillQueued = withContext(Dispatchers.IO) {
            editPending(id) {
                it.title = title.trim()
                it.description = description
                it.category = category
                it.tags = tags
                it.visibility = visibility
            }
        }
        if (stillQueued != null) {
            kickPending()
            return
        }

        syncInBackground("metadata") {
            val payload = JSONObject()
                .put("id", id)
                .put("title", title.trim())
                .put("description", description)
                .put("category", category)
                .put("tags", tags)
                .put("visibility", visibility)
            val r = authed(maxAttempts = 4) { token -> backend.publishVideo(token, payload) }
            if (r != null && r !is BackendResult.Ok) Log.w(TAG, "Metadata update not confirmed: $r")
        }
    }

    suspend fun deleteVideo(id: String) {
        val video = dao.getVideoByIdSync(id)
        dao.deleteVideoById(id)
        dao.deleteHistoryById(id)
        if (video == null || video.authorChannelId != OWN_CHANNEL_ID || SessionStore.current() == null) return

        withContext(Dispatchers.IO) {
            synchronized(pendingLock) {
                val list = readPublishes()
                val removed = list.filter { it.id == id }
                if (removed.isNotEmpty()) {
                    list.removeAll { it.id == id }
                    writePublishes(list)
                    removed.forEach {
                        deleteTemp(it.videoSrc)
                        deleteTemp(it.thumbSrc)
                    }
                }
                val deletes = readDeletes()
                deletes.add(id)
                writeDeletes(deletes)
            }
        }
        kickPending()
    }

    fun getInteraction(videoId: String): Flow<InteractionEntity?> = dao.getInteraction(videoId)

    suspend fun toggleLike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newLiked = !current.isLikedByMe
        val newLikes = if (newLiked) current.likesCount + 1 else maxOf(0L, current.likesCount - 1)
        dao.insertInteraction(current.copy(isLikedByMe = newLiked, likesCount = newLikes))
        syncInBackground("like") {
            authed(maxAttempts = 4) { token -> backend.interact(token, videoId, if (newLiked) "like" else "unlike") }
        }
    }

    suspend fun toggleDislike(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        val newDisliked = !current.isDislikedByMe
        val newDislikes = if (newDisliked) current.dislikesCount + 1 else maxOf(0L, current.dislikesCount - 1)
        dao.insertInteraction(current.copy(isDislikedByMe = newDisliked, dislikesCount = newDislikes))
    }

    suspend fun toggleWatchLater(videoId: String) {
        val current = dao.getInteractionSync(videoId) ?: InteractionEntity(videoId)
        dao.insertInteraction(current.copy(savedToWatchLater = !current.savedToWatchLater))
    }

    suspend fun sendSparks(videoId: String, sparks: Long) {
        ensureInteractionExists(videoId)
        dao.addSparks(videoId, sparks)
        if (sparks <= 0L) return
        syncInBackground("sparks") {
            authed(maxAttempts = 4) { token -> backend.interact(token, videoId, "spark", sparks.coerceAtMost(MAX_SPARKS_PER_CALL)) }
        }
    }

    // =====================================================================
    // COMMENTS & OTHER ENTITIES
    // =====================================================================

    /** Emits the cached comments at once and refreshes them from the server in the background. */
    fun getComments(videoId: String): Flow<List<CommentEntity>> {
        refreshCommentsInBackground(videoId)
        return dao.getCommentsForVideo(videoId)
    }

    private fun refreshCommentsInBackground(videoId: String) {
        val now = System.currentTimeMillis()
        val last = commentRefreshedAt[videoId]
        if (last != null && now - last < COMMENT_REFRESH_INTERVAL_MS) return
        commentRefreshedAt[videoId] = now
        syncInBackground("comments") {
            val r = backend.withBackoff(maxAttempts = 2) { backend.fetchComments(videoId) }
            if (r is BackendResult.Ok) {
                val known = dao.getCommentsForVideo(videoId).first().map { it.commentId }.toHashSet()
                for (c in r.value) {
                    if (c.commentId in known) continue
                    val safe = CommunityShield.isContentSafe(c.text)
                    dao.insertComment(
                        CommentEntity(
                            commentId = c.commentId,
                            videoId = c.videoId.ifBlank { videoId },
                            authorName = c.authorName,
                            commentText = if (safe) c.text else CommunityShield.filterText(c.text),
                            timestamp = c.timestamp,
                            likeCount = c.likeCount,
                            isFiltered = !safe,
                            isLikedByMe = false
                        )
                    )
                }
            }
        }
    }

    /** Returns false when the community filter blocked the text. Blocked text is never sent to the server. */
    suspend fun addComment(videoId: String, authorName: String, text: String): Boolean {
        val isSafe = CommunityShield.isContentSafe(text)
        val sanitized = if (isSafe) text else CommunityShield.filterText(text)
        val comment = CommentEntity(
            commentId = "c_${UUID.randomUUID()}",
            videoId = videoId,
            authorName = authorName,
            commentText = sanitized,
            timestamp = System.currentTimeMillis(),
            likeCount = 0L,
            isFiltered = !isSafe,
            isLikedByMe = false
        )
        dao.insertComment(comment)
        if (isSafe) {
            syncInBackground("comment") {
                authed(maxAttempts = 5) { token -> backend.postComment(token, comment.commentId, videoId, sanitized) }
            }
        }
        return isSafe
    }

    suspend fun toggleCommentLike(comment: CommentEntity) {
        val newLiked = !comment.isLikedByMe
        val newCount = if (newLiked) comment.likeCount + 1 else maxOf(0L, comment.likeCount - 1)
        dao.updateComment(comment.copy(isLikedByMe = newLiked, likeCount = newCount))
    }

    fun getChannel(channelId: String): Flow<ChannelEntity?> = dao.getChannelById(channelId)

    suspend fun toggleSubscribe(channelId: String) {
        val channel = dao.getChannelByIdSync(channelId) ?: return
        val newSubscribed = !channel.isSubscribed
        val newSubs = if (newSubscribed) channel.subscriberCount + 1 else maxOf(0L, channel.subscriberCount - 1)
        val newLevel = NexoraIdManager.calculateCreatorLevel(newSubs, 0L)
        dao.updateChannel(channel.copy(isSubscribed = newSubscribed, subscriberCount = newSubs, creatorLevel = newLevel))
    }

    fun getNotes(videoId: String): Flow<List<NotesEntity>> = dao.getNotesForVideo(videoId)

    suspend fun addNote(videoId: String, timestampMs: Long, text: String) {
        dao.insertNote(NotesEntity(noteId = "note_${UUID.randomUUID()}", videoId = videoId, timestampMs = timestampMs, noteText = text))
    }

    suspend fun deleteNote(noteId: String) = dao.deleteNote(noteId)

    suspend fun createCommunityPost(channelId: String, contentText: String, imageUri: String?, pollOptionsJson: String?) {
        dao.insertCommunityPost(
            CommunityPostEntity(
                postId = "post_${UUID.randomUUID()}",
                channelId = channelId,
                contentText = contentText,
                imageUri = imageUri,
                pollOptionsJson = pollOptionsJson,
                upvotes = 0L,
                isUpvotedByMe = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePostUpvote(post: CommunityPostEntity) {
        val newUpvoted = !post.isUpvotedByMe
        val newCount = if (newUpvoted) post.upvotes + 1 else maxOf(0L, post.upvotes - 1)
        dao.updateCommunityPost(post.copy(isUpvotedByMe = newUpvoted, upvotes = newCount))
    }

    fun setKidsMode(enabled: Boolean, enteredPin: String): Boolean {
        if (enteredPin == _masterPin.value) {
            _isKidsMode.value = enabled
            return true
        }
        return false
    }

    fun updateMasterPin(oldPin: String, newPin: String): Boolean {
        if (oldPin == _masterPin.value && newPin.length == 4) {
            _masterPin.value = newPin
            return true
        }
        return false
    }

    fun setDataSaver(enabled: Boolean) {
        _isDataSaver.value = enabled
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    suspend fun clearHistory() = dao.clearHistory()
    suspend fun removeFromHistory(videoId: String) = dao.deleteHistoryById(videoId)
    suspend fun upsertChannel(channel: ChannelEntity) = dao.insertChannel(channel)

    suspend fun getPrivateProfile(): PrivateFriendProfileEntity? = dao.getPrivateProfileSync()
    suspend fun isHandleTaken(handle: String): Boolean = dao.countHandleOccurrences(handle.trim()) > 0
    suspend fun savePrivateProfile(profile: PrivateFriendProfileEntity) = dao.insertPrivateProfile(profile)
    suspend fun updatePrivateProfile(profile: PrivateFriendProfileEntity) = dao.updatePrivateProfile(profile)

    fun getAllPrivateFriends(): Flow<List<PrivateFriendEntity>> = dao.getAllPrivateFriends()
    fun searchPrivateFriends(query: String): Flow<List<PrivateFriendEntity>> = dao.searchPrivateFriends(query)
    suspend fun getPrivateFriend(handle: String): PrivateFriendEntity? = dao.getPrivateFriendByHandle(handle)
    suspend fun addPrivateFriend(friend: PrivateFriendEntity) = dao.insertPrivateFriend(friend)

    fun getMessagesForConversation(handle: String): Flow<List<PrivateChatMessageEntity>> =
        dao.getMessagesForConversation(handle)

    suspend fun sendChatMessage(conversationHandle: String, senderHandle: String, text: String, mediaUri: String? = null) {
        dao.insertChatMessage(
            PrivateChatMessageEntity(
                messageId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                conversationHandle = conversationHandle,
                senderHandle = senderHandle,
                text = text,
                mediaUri = mediaUri,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                isRead = true
            )
        )
    }

    suspend fun markConversationAsRead(handle: String) {
        dao.markMessagesAsRead(handle)
        dao.clearUnreadCount(handle)
    }

    // =====================================================================
    // VISIBILITY (the app stores it as a word inside the tags)
    // =====================================================================

    private fun normalizeVisibility(value: String): String = when {
        value.equals("Private", ignoreCase = true) -> "Private"
        value.equals("Unlisted", ignoreCase = true) -> "Unlisted"
        else -> "Public"
    }

    private fun visibilityOf(tags: String): String = when {
        tags.contains("Private", ignoreCase = true) -> "Private"
        tags.contains("Unlisted", ignoreCase = true) -> "Unlisted"
        else -> "Public"
    }

    private fun withVisibilityTag(tags: String, visibility: String): String {
        if (visibility == "Public" || tags.contains(visibility, ignoreCase = true)) return tags
        return if (tags.isBlank()) visibility else "$tags, $visibility"
    }

    // =====================================================================
    // OFFLINE QUEUE: durable storage
    // =====================================================================

    private class PendingPublish(
        val id: String,
        var title: String,
        var description: String,
        var category: String,
        var tags: String,
        var visibility: String,
        val duration: Long,
        val chapterMarkersJson: String,
        val collabChannelId: String?,
        val isPremiere: Boolean,
        val premiereScheduledTimeMs: Long?,
        val createdAt: Long,
        var videoSrc: String,
        var thumbSrc: String,
        var videoUploadId: String = "",
        var thumbUploadId: String = "",
        var videoUrl: String = "",
        var thumbUrl: String = "",
        var failures: Int = 0
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("title", title)
            .put("description", description)
            .put("category", category)
            .put("tags", tags)
            .put("visibility", visibility)
            .put("duration", duration)
            .put("chapterMarkersJson", chapterMarkersJson)
            .put("collabChannelId", collabChannelId ?: JSONObject.NULL)
            .put("isPremiere", isPremiere)
            .put("premiereScheduledTimeMs", premiereScheduledTimeMs ?: JSONObject.NULL)
            .put("createdAt", createdAt)
            .put("videoSrc", videoSrc)
            .put("thumbSrc", thumbSrc)
            .put("videoUploadId", videoUploadId)
            .put("thumbUploadId", thumbUploadId)
            .put("videoUrl", videoUrl)
            .put("thumbUrl", thumbUrl)
            .put("failures", failures)

        companion object {
            fun fromJson(o: JSONObject): PendingPublish? {
                val id = o.optString("id", "")
                if (id.isBlank()) return null
                return PendingPublish(
                    id = id,
                    title = o.optString("title", ""),
                    description = o.optString("description", ""),
                    category = o.optString("category", "General"),
                    tags = o.optString("tags", ""),
                    visibility = o.optString("visibility", "Public"),
                    duration = o.optLong("duration", 0L),
                    chapterMarkersJson = o.optString("chapterMarkersJson", "[]"),
                    collabChannelId = if (o.isNull("collabChannelId")) null else o.optString("collabChannelId", ""),
                    isPremiere = o.optBoolean("isPremiere", false),
                    premiereScheduledTimeMs = if (o.isNull("premiereScheduledTimeMs")) null else o.optLong("premiereScheduledTimeMs"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    videoSrc = o.optString("videoSrc", ""),
                    thumbSrc = o.optString("thumbSrc", ""),
                    videoUploadId = o.optString("videoUploadId", ""),
                    thumbUploadId = o.optString("thumbUploadId", ""),
                    videoUrl = o.optString("videoUrl", ""),
                    thumbUrl = o.optString("thumbUrl", ""),
                    failures = o.optInt("failures", 0)
                )
            }
        }
    }

    /** Callers that modify the queue hold [pendingLock]. */
    private fun readPublishes(): MutableList<PendingPublish> {
        val raw = pendingPrefs.getString(K_PUBLISH_QUEUE, null) ?: return mutableListOf()
        val out = mutableListOf<PendingPublish>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                PendingPublish.fromJson(o)?.let { out.add(it) }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Publish queue was unreadable and has been reset: ${e.message}")
        }
        return out
    }

    private fun writePublishes(list: List<PendingPublish>) {
        val array = JSONArray()
        for (entry in list) array.put(entry.toJson())
        pendingPrefs.edit().putString(K_PUBLISH_QUEUE, array.toString()).commit()
    }

    private fun readDeletes(): MutableSet<String> =
        pendingPrefs.getStringSet(K_DELETE_QUEUE, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun writeDeletes(ids: Set<String>) {
        pendingPrefs.edit().putStringSet(K_DELETE_QUEUE, HashSet(ids)).commit()
    }

    /** Applies [change] to the stored entry. Returns the updated entry, or null when it is no longer queued. */
    private fun editPending(id: String, change: (PendingPublish) -> Unit): PendingPublish? =
        synchronized(pendingLock) {
            val list = readPublishes()
            val entry = list.firstOrNull { it.id == id } ?: return@synchronized null
            change(entry)
            writePublishes(list)
            entry
        }

    private fun loadPending(id: String): PendingPublish? =
        synchronized(pendingLock) { readPublishes().firstOrNull { it.id == id } }

    private fun removePending(id: String) {
        synchronized(pendingLock) {
            val list = readPublishes()
            if (list.removeAll { it.id == id }) writePublishes(list)
        }
    }

    private fun removePendingDelete(id: String) {
        synchronized(pendingLock) {
            val ids = readDeletes()
            if (ids.remove(id)) writeDeletes(ids)
        }
    }

    private fun protectedVideoIds(): Set<String> = synchronized(pendingLock) {
        val ids = HashSet<String>()
        readPublishes().forEach { ids.add(it.id) }
        ids.addAll(readDeletes())
        ids
    }

    // =====================================================================
    // OFFLINE QUEUE: processing (upload in chunks, publish, delete)
    // =====================================================================

    private enum class DrainResult { IDLE, RETRY }
    private enum class PublishStep { DONE, DROPPED, RETRY, AUTH_FAILED }

    private sealed interface UploadStep {
        data class Ready(val url: String) : UploadStep
        data object Empty : UploadStep
        data object Retry : UploadStep
        data object Auth : UploadStep
        data class Failed(val reason: String) : UploadStep
    }

    /** Starts (or restarts) the queue worker. A worker that is only waiting for its next retry is replaced. */
    private fun kickPending() {
        if (SessionStore.current() == null) return
        synchronized(drainLock) {
            val running = drainJob
            if (running != null && running.isActive && drainWorking) return
            running?.cancel()
            drainJob = scope.launch { drainPending() }
        }
    }

    private suspend fun drainPending() {
        var round = 0
        while (true) {
            drainWorking = true
            val result = try {
                processPendingOnce()
            } finally {
                drainWorking = false
            }
            if (result == DrainResult.IDLE || round >= MAX_DRAIN_ROUNDS) return
            delay(retryDelayMs(round))
            round += 1
        }
    }

    private fun retryDelayMs(round: Int): Long {
        val ceiling = min(MAX_DRAIN_DELAY_MS, BASE_DRAIN_DELAY_MS shl min(round, 5))
        return Random.nextLong(ceiling / 2L + 1L, ceiling + 1L)
    }

    private suspend fun processPendingOnce(): DrainResult {
        return processMutex.withLock {
            if (SessionStore.current() == null) return@withLock DrainResult.IDLE
            var retry = false

            for (id in synchronized(pendingLock) { readDeletes() }) {
                val r = authed(maxAttempts = 2) { token -> backend.deleteVideo(token, id) }
                    ?: return@withLock DrainResult.IDLE
                when (r) {
                    is BackendResult.Ok, BackendResult.NotFound -> removePendingDelete(id)
                    BackendResult.Unauthorized -> return@withLock DrainResult.IDLE
                    is BackendResult.Retryable -> retry = true
                    is BackendResult.Fatal -> {
                        Log.w(TAG, "Delete of $id was rejected: ${r.message}")
                        removePendingDelete(id)
                    }
                }
            }

            val entries = synchronized(pendingLock) { readPublishes() }
            for (entry in entries) {
                when (processPublish(entry)) {
                    PublishStep.DONE, PublishStep.DROPPED -> Unit
                    PublishStep.RETRY -> retry = true
                    PublishStep.AUTH_FAILED -> return@withLock DrainResult.IDLE
                }
            }
            if (retry) DrainResult.RETRY else DrainResult.IDLE
        }
    }

    private suspend fun processPublish(entry: PendingPublish): PublishStep {
        if (entry.failures >= MAX_ENTRY_FAILURES) return dropEntry(entry, "the server kept rejecting the upload")

        if (entry.videoUrl.isBlank()) {
            when (val step = ensureUploaded(entry, isVideo = true)) {
                is UploadStep.Ready -> {
                    entry.videoUrl = step.url
                    editPending(entry.id) { it.videoUrl = step.url }
                }
                UploadStep.Empty -> return dropEntry(entry, "no video file was selected")
                UploadStep.Retry -> return PublishStep.RETRY
                UploadStep.Auth -> return PublishStep.AUTH_FAILED
                is UploadStep.Failed -> return dropEntry(entry, step.reason)
            }
        }

        if (entry.thumbUrl.isBlank() && entry.thumbSrc.isNotBlank()) {
            when (val step = ensureUploaded(entry, isVideo = false)) {
                is UploadStep.Ready -> {
                    entry.thumbUrl = step.url
                    editPending(entry.id) { it.thumbUrl = step.url }
                }
                UploadStep.Empty -> Unit
                UploadStep.Retry -> return PublishStep.RETRY
                UploadStep.Auth -> return PublishStep.AUTH_FAILED
                is UploadStep.Failed -> {
                    Log.w(TAG, "Thumbnail of ${entry.id} was skipped: ${step.reason}")
                    entry.thumbSrc = ""
                    editPending(entry.id) { it.thumbSrc = "" }
                }
            }
        }

        val current = loadPending(entry.id) ?: return PublishStep.DROPPED
        current.videoUrl = entry.videoUrl
        current.thumbUrl = entry.thumbUrl

        val payload = JSONObject()
            .put("id", current.id)
            .put("title", current.title)
            .put("description", current.description)
            .put("category", current.category)
            .put("tags", current.tags)
            .put("videoUrl", current.videoUrl)
            .put("visibility", current.visibility)
            .put("duration", current.duration)
            .put("chapterMarkersJson", current.chapterMarkersJson)
            .put("isPremiere", current.isPremiere)
            .put("timestamp", current.createdAt)
        if (current.thumbUrl.isNotBlank()) payload.put("thumbnailUrl", current.thumbUrl)
        if (current.collabChannelId != null) payload.put("collabChannelId", current.collabChannelId)
        if (current.premiereScheduledTimeMs != null) payload.put("premiereScheduledTimeMs", current.premiereScheduledTimeMs)

        val r = authed(maxAttempts = 3) { token -> backend.publishVideo(token, payload) }
            ?: return PublishStep.AUTH_FAILED
        return when (r) {
            is BackendResult.Ok -> {
                finishPublished(current, r.value)
                PublishStep.DONE
            }
            BackendResult.Unauthorized -> PublishStep.AUTH_FAILED
            is BackendResult.Retryable -> PublishStep.RETRY
            BackendResult.NotFound -> dropEntry(current, "the server could not find the account")
            is BackendResult.Fatal -> {
                if (r.code == 400 && r.message.contains(ATTACH_FAILED_MARKER)) {
                    // The server no longer has the uploaded file (for example after a host reset): upload it again.
                    val reset = editPending(current.id) {
                        it.videoUrl = ""
                        it.thumbUrl = ""
                        it.videoUploadId = ""
                        it.thumbUploadId = ""
                        it.failures += 1
                    }
                    if (reset == null) PublishStep.DROPPED else PublishStep.RETRY
                } else {
                    dropEntry(current, r.message)
                }
            }
        }
    }

    private suspend fun ensureUploaded(entry: PendingPublish, isVideo: Boolean): UploadStep {
        var source = if (isVideo) entry.videoSrc else entry.thumbSrc
        if (source.isBlank()) return UploadStep.Empty
        if (isRemoteUrl(source)) return UploadStep.Ready(source)

        if (source.startsWith("content://")) {
            val copy = withContext(Dispatchers.IO) { copyToPending(entry.id, if (isVideo) "video" else "thumb", source) }
                ?: return UploadStep.Failed("the selected file could not be read any more")
            source = copy.absolutePath
            val stored = editPending(entry.id) { if (isVideo) it.videoSrc = copy.absolutePath else it.thumbSrc = copy.absolutePath }
                ?: return UploadStep.Failed("the video was removed")
            if (isVideo) entry.videoSrc = stored.videoSrc else entry.thumbSrc = stored.thumbSrc
        }

        val payload = payloadForFile(source) ?: return UploadStep.Failed("the file is no longer available on this phone")
        val resumeId = (if (isVideo) entry.videoUploadId else entry.thumbUploadId).ifBlank { null }
        val token = SessionStore.current()?.sessionToken ?: return UploadStep.Auth

        val result = backend.uploadMedia(
            sessionToken = token,
            kind = if (isVideo) "video" else "image",
            payload = payload,
            resumeUploadId = resumeId,
            onUploadSession = { uploadId ->
                editPending(entry.id) { if (isVideo) it.videoUploadId = uploadId else it.thumbUploadId = uploadId }
                if (isVideo) entry.videoUploadId = uploadId else entry.thumbUploadId = uploadId
            },
            onProgress = { sent, total -> if (isVideo) reportProgress(entry.id, sent, total) }
        )
        if (isVideo) clearProgress(entry.id)

        return when (result) {
            is BackendResult.Ok -> UploadStep.Ready(result.value.url)
            BackendResult.NotFound -> {
                // The server forgot this upload session: start a fresh one next time.
                editPending(entry.id) {
                    if (isVideo) it.videoUploadId = "" else it.thumbUploadId = ""
                    it.failures += 1
                }
                if (isVideo) entry.videoUploadId = "" else entry.thumbUploadId = ""
                entry.failures += 1
                UploadStep.Retry
            }
            BackendResult.Unauthorized -> {
                handleUnauthorized()
                UploadStep.Auth
            }
            is BackendResult.Retryable -> UploadStep.Retry
            is BackendResult.Fatal -> UploadStep.Failed(result.message)
        }
    }

    private suspend fun finishPublished(entry: PendingPublish, published: ServerVideo) {
        removePending(entry.id)
        deleteTemp(entry.videoSrc)
        deleteTemp(entry.thumbSrc)
        clearProgress(entry.id)
        val local = dao.getVideoByIdSync(entry.id) ?: return
        val videoFile = localFile(local.localUri)
        val thumbFile = localFile(local.thumbnailUri)
        dao.updateVideo(
            local.copy(
                localUri = if (videoFile != null && !videoFile.isFile) published.localUri else local.localUri,
                thumbnailUri = when {
                    published.thumbnailUri.isBlank() -> local.thumbnailUri
                    local.thumbnailUri.isBlank() || (thumbFile != null && !thumbFile.isFile) -> published.thumbnailUri
                    else -> local.thumbnailUri
                }
            )
        )
    }

    private fun dropEntry(entry: PendingPublish, reason: String): PublishStep {
        removePending(entry.id)
        deleteTemp(entry.videoSrc)
        deleteTemp(entry.thumbSrc)
        clearProgress(entry.id)
        val name = entry.title.ifBlank { "your video" }
        _syncError.value = "\"$name\" could not be uploaded: $reason."
        Log.w(TAG, "Dropped queued publish ${entry.id}: $reason")
        return PublishStep.DROPPED
    }

    private fun reportProgress(videoId: String, sent: Long, total: Long) {
        if (total <= 0L) return
        val fraction = (sent.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        val previous = _uploadProgress.value[videoId]
        if (previous != null && fraction < 1f && fraction - previous < 0.01f) return
        _uploadProgress.value = _uploadProgress.value + (videoId to fraction)
    }

    private fun clearProgress(videoId: String) {
        if (_uploadProgress.value.containsKey(videoId)) _uploadProgress.value = _uploadProgress.value - videoId
    }

    // =====================================================================
    // FILE HELPERS
    // =====================================================================

    private fun pendingDir(): File = File(context.filesDir, "pending_uploads")

    private fun isRemoteUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")

    private fun localFile(uri: String): File? = when {
        uri.startsWith("file://") -> Uri.parse(uri).path?.let { File(it) }
        uri.startsWith("/") -> File(uri)
        else -> null
    }

    private fun payloadForFile(path: String): MediaPayload? {
        val file = localFile(path) ?: return null
        if (!file.isFile || !file.canRead()) return null
        val length = file.length()
        if (length <= 0L) return null
        return MediaPayload(length) { FileInputStream(file) }
    }

    /** A content:// uri can lose its read permission when the app restarts, so the bytes are kept in app storage. */
    private fun copyToPending(videoId: String, label: String, source: String): File? {
        return try {
            val dir = pendingDir()
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val destination = File(dir, "${videoId}_$label")
            val input = context.contentResolver.openInputStream(Uri.parse(source)) ?: return null
            input.use { stream -> destination.outputStream().use { out -> stream.copyTo(out) } }
            destination
        } catch (e: IOException) {
            Log.w(TAG, "Could not copy $label of $videoId: ${e.message}")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read $label of $videoId: ${e.message}")
            null
        }
    }

    private fun deleteTemp(path: String) {
        if (path.isBlank()) return
        val file = File(path)
        try {
            if (file.parentFile?.canonicalPath == pendingDir().canonicalPath && file.exists()) file.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Could not delete temporary file: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "NexoraRepository"
        private const val BACKEND_BASE_URL = "https://nexora-backend-fq54.onrender.com"
        private const val OWN_CHANNEL_ID = "ch_zentora_core"

        private const val VAULT1_PREFS = "nexora_vault1_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_IS_GUEST = "is_guest"

        private const val PENDING_PREFS = "nexora_pending_sync"
        private const val K_PUBLISH_QUEUE = "publish_queue"
        private const val K_DELETE_QUEUE = "delete_queue"

        private const val FEED_PAGE_SIZE = 50
        private const val FEED_PAGES = 3
        private const val MAX_SPARKS_PER_CALL = 1000L
        private const val COMMENT_REFRESH_INTERVAL_MS = 15_000L

        private const val MAX_ENTRY_FAILURES = 10
        private const val MAX_DRAIN_ROUNDS = 8
        private const val BASE_DRAIN_DELAY_MS = 15_000L
        private const val MAX_DRAIN_DELAY_MS = 5 * 60_000L

        /** Part of the message AccountBackend produces for the server error "invalid_video_url". */
        private const val ATTACH_FAILED_MARKER = "could not be attached"

        @Volatile
        private var INSTANCE: NexoraStreamRepository? = null

        fun getInstance(context: Context): NexoraStreamRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NexoraStreamRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
