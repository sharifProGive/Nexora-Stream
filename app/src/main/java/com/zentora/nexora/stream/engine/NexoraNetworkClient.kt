/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class VideoPublishPayload(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val duration: Long,
    val localUri: String,
    val channelId: String
)

data class VideoInteractPayload(
    val videoId: String,
    val type: String,
    val value: Long = 1
)

data class NetworkApiResponse(
    val status: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CentralFeedResponse(
    val status: String,
    val totalVideos: Int,
    val videos: List<RemoteVideoItem>
)

data class RemoteVideoItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val tags: String,
    val timestamp: Long,
    val duration: Long,
    val viewCount: Long,
    val playbackUrl: String
)

interface NexoraApiService {
    @GET("api/v1/feed")
    suspend fun getGlobalFeed(): CentralFeedResponse

    @POST("api/v1/videos/publish")
    suspend fun publishVideo(@Body payload: VideoPublishPayload): NetworkApiResponse

    @POST("api/v1/videos/interact")
    suspend fun sendInteraction(@Body payload: VideoInteractPayload): NetworkApiResponse

    @GET("api/v1/vaults/audit")
    suspend fun auditVaults(): NetworkApiResponse
}

object NexoraNetworkClient {
    const val BASE_URL = "https://nexora-backend-fq54.onrender.com/"

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val session = NexoraIdManager.getSession()

        val requestWithHeaders = originalRequest.newBuilder()
            .header("User-Agent", "NexoraStream/1.0 (Zentora CLC Core Engine)")
            .header("X-Nexora-Protocol", "NCP-v1.0")
            .header("X-Zentora-Auth-Token", session.zentoraAuthToken)
            .build()

        chain.proceed(requestWithHeaders)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .build()

    val apiService: NexoraApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NexoraApiService::class.java)
    }
}
