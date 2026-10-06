/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.File
import java.util.concurrent.TimeUnit

// ==================== DTO DATA MODELS ====================

data class NetworkVideoItem(
    val id: String,
    val localUri: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val timestamp: Long,
    val duration: Long,
    val viewCount: Long = 0L,
    val chapterMarkersJson: String = "[]",
    val thumbnailUri: String = "",
    val authorChannelId: String = "ch_zentora_core",
    val collabChannelId: String? = null,
    val isPremiere: Boolean = false,
    val premiereScheduledTimeMs: Long? = null
)

data class FeedResponse(
    val status: String = "SUCCESS",
    val vaultStatus: String = "VAULT_SYNC_OK",
    val totalCount: Int = 0,
    val videos: List<NetworkVideoItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class PublishVideoRequest(
    val videoId: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val mediaUri: String,
    val thumbnailUri: String,
    val duration: Long,
    val authorChannelId: String,
    val nexoraAuthToken: String
)

data class PublishVideoResponse(
    val status: String = "SUCCESS",
    val vault3ReceiptId: String = "",
    val vault5LockHash: String = "",
    val message: String = "Video registered to Vault 3 and sealed into Vault 5 Grand Master",
    val timestamp: Long = System.currentTimeMillis()
)

data class InteractRequest(
    val videoId: String,
    val viewDelta: Long = 0L,
    val likeDelta: Long = 0L,
    val sparksDelta: Long = 0L,
    val nexoraId: String = ""
)

data class InteractResponse(
    val status: String = "SUCCESS",
    val videoId: String = "",
    val vault2Acknowledged: Boolean = true,
    val vault5Synced: Boolean = true,
    val currentLikes: Long = 0L,
    val currentViews: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class VaultAuditResponse(
    val vaultsSynced: Boolean = true,
    val vault1LocalCount: Int = 0,
    val vault2RealtimeInteractionsStatus: String = "HEALTHY",
    val vault3RegistryStatus: String = "ACTIVE",
    val vault4DistributionStatus: String = "ONLINE",
    val vault5GrandMasterIntegrity: String = "SEALED",
    val auditHash: String = "NCP-V5-SEALED-INTEGRITY",
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== RETROFIT API SERVICE ====================

interface NexoraApiService {

    /**
     * 1. GET /api/v1/feed
     * Fetches global aggregated feed (Vault 2 + Vault 3 + Vault 4).
     */
    @GET("api/v1/feed")
    suspend fun getFeed(): retrofit2.Response<FeedResponse>

    /**
     * 2. POST /api/v1/videos/publish
     * Registers uploaded video to Vault 3 and locks into Vault 5 (Grand Master).
     */
    @POST("api/v1/videos/publish")
    suspend fun publishVideo(@Body request: PublishVideoRequest): retrofit2.Response<PublishVideoResponse>

    /**
     * 3. POST /api/v1/videos/interact
     * Sends real-time views, likes, and sparks to Vault 2 and Vault 5.
     */
    @POST("api/v1/videos/interact")
    suspend fun sendInteraction(@Body request: InteractRequest): retrofit2.Response<InteractResponse>

    /**
     * 4. GET /api/v1/vaults/audit
     * Verifies 5-Vault synchronization integrity.
     */
    @GET("api/v1/vaults/audit")
    suspend fun auditVaults(): retrofit2.Response<VaultAuditResponse>
}

// ==================== NEXORA NETWORK CLIENT ====================

object NexoraNetworkClient {

    private const val DEFAULT_BASE_URL = "https://stream.zentora.internal/"
    private const val USER_AGENT_HEADER = "NexoraStream/1.0 (Zentora CLC Core Engine)"
    const val VAULT_4_BLOBSTORE_URL = "https://nexora-backend-fq54.onrender.com/api/v1/videos/upload-stream"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authAndUserAgentInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val session = NexoraIdManager.getSession()
        val authenticatedRequest = originalRequest.newBuilder()
            .header("User-Agent", USER_AGENT_HEADER)
            .header("X-Nexora-Protocol", "NCP-v1.0")
            .header("X-Zentora-Auth-Token", session.zentoraAuthToken)
            .header("X-Nexora-Client-Id", session.nexoraId)
            .build()
        chain.proceed(authenticatedRequest)
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authAndUserAgentInterceptor)
        .retryOnConnectionFailure(true)
        .build()

    private var retrofit: Retrofit = buildRetrofit(DEFAULT_BASE_URL)

    var apiService: NexoraApiService = retrofit.create(NexoraApiService::class.java)
        private set

    fun getBaseUrl(): String = retrofit.baseUrl().toString()

    fun updateBaseUrl(newBaseUrl: String) {
        val sanitizedUrl = if (newBaseUrl.endsWith("/")) newBaseUrl else "$newBaseUrl/"
        retrofit = buildRetrofit(sanitizedUrl)
        apiService = retrofit.create(NexoraApiService::class.java)
    }

    private fun buildRetrofit(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Vault 4 (Zentora Blobstore Vault): Persistent binary streaming node.
     * Executes real OkHttp MultipartBody upload directly to the Master Server Vault 4 endpoint.
     */
    suspend fun uploadToVault4Blobstore(
        videoFile: File,
        thumbnailFile: File?,
        title: String,
        description: String,
        category: String,
        tags: String,
        authorChannelId: String,
        isShort: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = NexoraIdManager.getSession()
            val mediaTypeVideo = "video/mp4".toMediaTypeOrNull()
            val mediaTypeImage = "image/jpeg".toMediaTypeOrNull()

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("description", description)
                .addFormDataPart("category", category)
                .addFormDataPart("tags", tags)
                .addFormDataPart("authorChannelId", authorChannelId)
                .addFormDataPart("isShort", isShort.toString())
                .addFormDataPart("nexoraId", session.nexoraId)
                .addFormDataPart(
                    "mediaFile",
                    videoFile.name,
                    videoFile.asRequestBody(mediaTypeVideo)
                )

            if (thumbnailFile != null && thumbnailFile.exists()) {
                builder.addFormDataPart(
                    "thumbnailFile",
                    thumbnailFile.name,
                    thumbnailFile.asRequestBody(mediaTypeImage)
                )
            }

            val request = Request.Builder()
                .url(VAULT_4_BLOBSTORE_URL)
                .post(builder.build())
                .header("User-Agent", USER_AGENT_HEADER)
                .header("X-Nexora-Protocol", "NCP-v1.0")
                .header("X-Zentora-Auth-Token", session.zentoraAuthToken)
                .header("X-Nexora-Client-Id", session.nexoraId)
                .header("X-Nexora-Vault-Target", "VAULT_4_BLOBSTORE")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string() ?: "VAULT_4_STREAM_INGESTED"
            } else {
                "VAULT_4_INGEST_ACK_${response.code}"
            }
        }
    }
}
