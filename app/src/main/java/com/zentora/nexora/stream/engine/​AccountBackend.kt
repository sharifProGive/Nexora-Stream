/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import org.json.JSONObject
import retrofit2.Response as RetrofitResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

fun interface IdTokenProvider {
    /** Returns a fresh Google ID token, or null if the user must sign in again. */
    suspend fun idToken(forceRefresh: Boolean): String?
}

data class GoogleProfileHint(val email: String, val displayName: String, val avatarUri: String)
data class CloudIdentity(val nexoraId: String)
data class PrivateDetails(val phoneNumber: String, val billingInfo: String)

sealed interface BackendResult<out T> {
    data class Ok<T>(val value: T) : BackendResult<T>
    data object NotFound : BackendResult<Nothing>                 // 404 -> e.g. new user
    data object Unauthorized : BackendResult<Nothing>             // 401/403 -> token rejected
    data class Retryable(val code: Int?, val cause: Throwable? = null) : BackendResult<Nothing> // 5xx / 408 / 429 / IO
    data class Fatal(val code: Int, val message: String) : BackendResult<Nothing>
}

// ---------------------------------------------------------------------------
// Backend client (Vault 2 account endpoints)
// ---------------------------------------------------------------------------

class AccountBackend(private val baseUrl: String) {

    // Render free tier cold starts take ~30-60 s, so the FIRST request is allowed to be slow;
    // withBackoff() then covers the cases where Render answers 502/503 while spinning up.
    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }

    /** Fire-and-forget ping so the instance starts waking up while the user taps "Sign in". */
    suspend fun warmUp() {
        call("GET", "/health", idToken = null)
    }

    /** 200 {nexoraId} | 404 | 401 | 5xx */
    suspend fun fetchProfile(idToken: String): BackendResult<CloudIdentity> =
        call("GET", "/api/v1/user/get-profile", idToken).mapOk { json ->
            json.optString("nexoraId").takeIf { it.isNotBlank() }
                ?.let { BackendResult.Ok(CloudIdentity(it)) }
                ?: BackendResult.Fatal(200, "missing nexoraId")
        }

    /** Server derives/stores the id from the verified `sub`. 201 (created) or 200 (already existed). */
    suspend fun register(idToken: String, hint: GoogleProfileHint): BackendResult<CloudIdentity> =
        call(
            "POST", "/api/v1/user/register", idToken,
            JSONObject().apply {
                put("username", hint.displayName)
                put("avatarUri", hint.avatarUri)
            }
        ).mapOk { json ->
            json.optString("nexoraId").takeIf { it.isNotBlank() }
                ?.let { BackendResult.Ok(CloudIdentity(it)) }
                ?: BackendResult.Fatal(200, "missing nexoraId")
        }

    /** Phone/billing are NOT part of recovery; they live behind their own authenticated endpoint. */
    suspend fun fetchPrivate(idToken: String): BackendResult<PrivateDetails> =
        call("GET", "/api/v1/user/get-private", idToken).mapOk { json ->
            BackendResult.Ok(
                PrivateDetails(json.optString("phoneNumber", ""), json.optString("billingInfo", ""))
            )
        }

    suspend fun savePrivate(idToken: String, phone: String, billing: String): BackendResult<Unit> =
        call(
            "POST", "/api/v1/user/save-profile", idToken,
            JSONObject().apply { put("phoneNumber", phone); put("billingInfo", billing) }
        ).mapOk { BackendResult.Ok(Unit) }

    suspend fun publishVideo(idToken: String, payload: JSONObject): BackendResult<Unit> =
        call("POST", "/api/v1/videos/publish", idToken, payload).mapOk { BackendResult.Ok(Unit) }

    // -- generic retry ------------------------------------------------------

    /**
     * Exponential backoff with equal jitter: ~1.5s, 3s, 6s, 12s, 20s, 20s (+ the long per-request timeout).
     * Only [BackendResult.Retryable] is retried; 404/401/4xx return immediately.
     */
    suspend fun <T> withBackoff(
        maxAttempts: Int = 7,
        initialDelayMs: Long = 1_500,
        maxDelayMs: Long = 20_000,
        onRetry: (nextAttempt: Int) -> Unit = {},
        block: suspend () -> BackendResult<T>
    ): BackendResult<T> {
        var attempt = 1
        while (true) {
            val result = block()
            if (result !is BackendResult.Retryable || attempt >= maxAttempts) return result
            onRetry(attempt + 1)
            val ceiling = min(maxDelayMs, initialDelayMs shl (attempt - 1))
            delay(ceiling / 2 + Random.nextLong(ceiling / 2 + 1))
            attempt++
        }
    }

    /** Wraps a Retrofit call so it participates in the same result/retry model. */
    suspend fun <T> apiCall(block: suspend () -> RetrofitResponse<T>): BackendResult<T> =
        try {
            val r = block()
            val body = r.body()
            when {
                r.isSuccessful && body != null -> BackendResult.Ok(body)
                r.isSuccessful -> BackendResult.Fatal(r.code(), "empty body")
                else -> classify(r.code(), "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            BackendResult.Retryable(null, e)
        }

    // -- low level ----------------------------------------------------------

    private fun classify(code: Int, body: String): BackendResult<Nothing> = when {
        code == 404 -> BackendResult.NotFound
        code == 401 || code == 403 -> BackendResult.Unauthorized
        code == 408 || code == 425 || code == 429 || code >= 500 -> BackendResult.Retryable(code)
        else -> BackendResult.Fatal(code, body.take(200))
    }

    private suspend fun call(
        method: String,
        path: String,
        idToken: String?,
        body: JSONObject? = null
    ): BackendResult<JSONObject> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                if (idToken != null) setRequestProperty("Authorization", "Bearer $idToken")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (code in 200..299) {
                try {
                    BackendResult.Ok(JSONObject(text.ifBlank { "{}" }))
                } catch (e: org.json.JSONException) {
                    BackendResult.Fatal(code, "invalid json")
                }
            } else {
                classify(code, text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) { // timeouts, DNS, connection reset: all retryable
            BackendResult.Retryable(null, e)
        } finally {
            conn?.disconnect()
        }
    }

    private inline fun <T> BackendResult<JSONObject>.mapOk(
        transform: (JSONObject) -> BackendResult<T>
    ): BackendResult<T> = when (this) {
        is BackendResult.Ok -> transform(value)
        BackendResult.NotFound -> BackendResult.NotFound
        BackendResult.Unauthorized -> BackendResult.Unauthorized
        is BackendResult.Retryable -> this
        is BackendResult.Fatal -> this
    }
}

/** Attach to the OkHttpClient behind NexoraNetworkClient so Retrofit calls are authenticated too. */
class AuthInterceptor(private val token: suspend () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val t = runBlocking { token() }
        val req = if (t != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $t").build()
        } else chain.request()
        return chain.proceed(req)
    }
}
