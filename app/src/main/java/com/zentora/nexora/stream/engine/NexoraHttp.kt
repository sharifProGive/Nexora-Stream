/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one HTTP transport for every Nexora server call that needs a session token (Friends, Chat, and the later
 * Vault 35 sub-modules). It only moves bytes; each backend decides what a status code means.
 *
 * A call stops at once when its coroutine is cancelled: the socket is closed, so a parked long poll does not keep a
 * thread blocked after the app has gone to the background.
 *
 * @param onSessionTokenRenewed called when the server sends a fresh session token (X-Nexora-Session-Token)
 */
class NexoraHttp(
    baseUrl: String,
    private val onSessionTokenRenewed: (String) -> Unit
) {

    class Raw(val code: Int, val text: String)

    private val baseUrl: String = baseUrl.trimEnd('/')

    companion object {
        // Render's free tier needs up to a minute to wake up, so the first request is allowed to be slow.
        const val CONNECT_TIMEOUT_MS = 30_000
        const val DEFAULT_READ_TIMEOUT_MS = 60_000
        private const val USER_AGENT = "NexoraStream/3.0 (Zentora CLC)"
        private const val RENEWED_TOKEN_HEADER = "X-Nexora-Session-Token"
    }

    suspend fun exchange(
        method: String,
        path: String,
        bearer: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
    ): Raw = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        coroutineScope {
            // Closes the socket the moment this call is cancelled; a blocked read cannot be interrupted any other way.
            val canceller = launch {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                perform(connection, method, bearer, body, readTimeoutMs)
            } finally {
                canceller.cancel()
            }
        }
    }

    private fun perform(
        connection: HttpURLConnection,
        method: String,
        bearer: String,
        body: JSONObject?,
        readTimeoutMs: Int
    ): Raw {
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val renewed = connection.getHeaderField(RENEWED_TOKEN_HEADER)
            if (!renewed.isNullOrBlank()) onSessionTokenRenewed(renewed)
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Raw(code, text)
        } finally {
            connection.disconnect()
        }
    }

    /** The body as a JSON object, or null when it is empty or not JSON (a proxy or "waking up" page). */
    fun parseObject(text: String): JSONObject? = try {
        if (text.isBlank()) null else JSONObject(text)
    } catch (e: JSONException) {
        null
    }
}
