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
