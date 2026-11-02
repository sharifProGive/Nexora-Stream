/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.data.repository

import android.content.Context
import com.zentora.nexora.stream.engine.AccountBackend
import com.zentora.nexora.stream.engine.BackendResult
import com.zentora.nexora.stream.engine.NexoraHttp
import com.zentora.nexora.stream.engine.NexoraIdManager
import com.zentora.nexora.stream.engine.SessionStore

/**
 * What every call to the Nexora server that needs the signed-in session has in common: the shared transport, the
 * session token (read fresh for every attempt, so a renewed token is used at once), retries with exponential backoff
 * and jitter, and the reaction to an expired session. Friends, Chat and the later Vault 35 sub-modules use this one
 * object instead of each keeping its own copy.
 */
class SessionCalls private constructor(private val context: Context) {

    /** One transport for the whole app; it hands renewed session tokens to [renewToken]. */
    val http = NexoraHttp(BASE_URL) { token -> renewToken(token) }

    /** Only used for its retry policy ([AccountBackend.withBackoff]). */
    private val retryPolicy = AccountBackend(BASE_URL) { }

    /** The server sent a fresh session token: keep it in both places the app reads it from. */
    fun renewToken(token: String) {
        if (token.isBlank() || SessionStore.current() == null) return
        SessionStore.updateSessionToken(token)
        context.getSharedPreferences(VAULT1_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    /** A 401 on a session call: the session is expired or the account is unknown to the server. Sign in again. */
    fun handleUnauthorized() {
        SessionStore.clear()
        NexoraIdManager.clearSession(context)
        NexoraStreamRepository.getInstance(context).beginSession()
    }

    /**
     * Runs a call that needs the server session, repeating on cold starts and network errors with backoff and jitter.
     * Returns null when nobody is signed in: nothing is sent in that case.
     */
    suspend fun <T> authed(
        maxAttempts: Int = DEFAULT_ATTEMPTS,
        block: suspend (sessionToken: String) -> BackendResult<T>
    ): BackendResult<T>? {
        val first = SessionStore.current()?.sessionToken ?: return null
        val result = retryPolicy.withBackoff(maxAttempts = maxAttempts) {
            block(SessionStore.current()?.sessionToken ?: first)
        }
        if (result is BackendResult.Unauthorized) handleUnauthorized()
        return result
    }

    companion object {
        // Same server as NexoraStreamRepository (that constant is private to its companion).
        const val BASE_URL = "https://nexora-backend-fq54.onrender.com"
        private const val VAULT1_PREFS = "nexora_vault1_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val DEFAULT_ATTEMPTS = 4

        @Volatile
        private var INSTANCE: SessionCalls? = null

        fun getInstance(context: Context): SessionCalls =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionCalls(context.applicationContext).also { INSTANCE = it }
            }
    }
}
