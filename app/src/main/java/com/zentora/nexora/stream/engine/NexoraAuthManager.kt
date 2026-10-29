/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.content.Intent
import com.zentora.nexora.stream.data.repository.NexoraStreamRepository
import com.zentora.nexora.stream.data.repository.RecoveryState

/**
 * Outcome of a sign-in attempt.
 * NeedsIntentFallback is kept so existing screens that handle it still compile; it is never produced any more,
 * because an account is only ever created from a Google ID token that the Nexora server has verified.
 */
sealed class NexoraAuthResult {
    data class Success(val displayName: String, val email: String, val avatarUri: String) : NexoraAuthResult()
    data class NeedsIntentFallback(val intent: Intent) : NexoraAuthResult()
    data class Error(val message: String) : NexoraAuthResult()
}

/**
 * Self-hosted sign-in:
 *
 *   1. Google account chooser (Credential Manager) -> signed Google ID token
 *   2. NexoraStreamRepository.recoverOrRegister:
 *        GET  /api/v1/account/get-profile   200 -> existing account, original nexoraId (reinstall recovery)
 *                                           404 -> POST /api/v1/account/register (new account)
 *        restore profile + videos, then sync the feed
 *   3. Only then is the Vault 1 session written, so no screen can run before the server confirmed the id.
 */
class NexoraAuthManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val googleAuth = GoogleAuthClient(appContext)
    private val repository = NexoraStreamRepository.getInstance(appContext)

    /** [activityContext] must be the Activity so Google can show its account sheet. */
    suspend fun signInWithGoogle(activityContext: Context): NexoraAuthResult =
        when (val result = googleAuth.signIn(activityContext)) {
            is GoogleSignInResult.Success -> finishSignIn(result.idToken, result.hint)
            GoogleSignInResult.Cancelled -> {
                repository.reportSignInCancelled()
                NexoraAuthResult.Error("Sign-in was cancelled.")
            }
            is GoogleSignInResult.Error -> {
                repository.reportSignInFailure(result.message)
                NexoraAuthResult.Error(result.message)
            }
        }

    /**
     * Completes a sign-in for which the caller already holds a Google ID token.
     * Without a token nothing is created: there are no offline or "demo" accounts.
     */
    suspend fun completeAuthentication(
        displayName: String,
        email: String,
        photoUrl: String?,
        idToken: String?
    ): NexoraAuthResult {
        if (idToken.isNullOrBlank()) {
            return NexoraAuthResult.Error("A verified Google sign-in is required to create or restore an account.")
        }
        return finishSignIn(idToken, GoogleProfileHint(email = email, displayName = displayName, avatarUri = photoUrl ?: ""))
    }

    /** Browsing without an account. Nothing is uploaded or synced in this mode. */
    fun activateGuestMode() {
        NexoraIdManager.activateGuestMode(appContext)
    }

    /** Removes the account from this phone (local data included). The data on the server is untouched. */
    suspend fun signOut() {
        repository.signOut()
        googleAuth.clearState()
        NexoraIdManager.clearSession(appContext)
    }

    private suspend fun finishSignIn(idToken: String, hint: GoogleProfileHint): NexoraAuthResult {
        return when (val state = repository.recoverOrRegister(idToken, hint)) {
            is RecoveryState.Ready -> {
                val session = SessionStore.current()
                    ?: return NexoraAuthResult.Error("Your session could not be saved. Please try again.")
                persistVault1Session(session)
                NexoraAuthResult.Success(
                    displayName = session.displayName.ifBlank { hint.displayName }.ifBlank { "Nexora Creator" },
                    email = session.email.ifBlank { hint.email },
                    avatarUri = session.avatarUri
                )
            }
            is RecoveryState.Failed -> NexoraAuthResult.Error(state.message)
            else -> NexoraAuthResult.Error("Sign-in did not finish. Please try again.")
        }
    }

    /**
     * Mirrors the confirmed account into the Vault 1 preferences that NexoraIdManager and MainActivity read.
     * The keys below are the ones NexoraIdManager uses; the id and token written here come from the server.
     */
    private fun persistVault1Session(session: AccountSession) {
        val handle = session.handle.ifBlank {
            "@" + session.email.substringBefore("@").replace(".", "").lowercase().ifBlank { "nexoracreator" }
        }
        appContext.getSharedPreferences(VAULT1_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUTH_TOKEN, session.sessionToken)
            .putString(KEY_NEXORA_ID, session.nexoraId)
            .putString(KEY_USERNAME, session.displayName.ifBlank { "Nexora Creator" })
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_HANDLE, handle)
            .putString(KEY_AVATAR, session.avatarUri)
            .putBoolean(KEY_IS_GOOGLE, true)
            .putBoolean(KEY_IS_GUEST, false)
            .commit()
        NexoraIdManager.initSession(appContext)
    }

    private companion object {
        const val VAULT1_PREFS = "nexora_vault1_session"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_NEXORA_ID = "nexora_id"
        const val KEY_USERNAME = "username"
        const val KEY_EMAIL = "email"
        const val KEY_HANDLE = "handle"
        const val KEY_AVATAR = "avatar"
        const val KEY_IS_GOOGLE = "is_google"
        const val KEY_IS_GUEST = "is_guest"
    }
}
