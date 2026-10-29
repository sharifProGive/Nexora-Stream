/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Vault 6 – the signed-in account on this device.
 *
 * The nexoraId is NEVER generated on the phone. It is written only after our own server has verified the
 * user's Google account and returned the permanent id derived from it. [sessionToken] is the token issued by
 * the Nexora backend itself (self-hosted, no third-party service) that authorises every later request.
 */
data class AccountSession(
    val nexoraId: String,
    val email: String,
    val displayName: String,
    val handle: String,
    val avatarUri: String,
    val sessionToken: String
)

object SessionStore {
    private const val PREFS = "nexora_session_prefs"
    private const val K_ID = "nexora_id"
    private const val K_EMAIL = "nexora_email"
    private const val K_NAME = "nexora_display_name"
    private const val K_HANDLE = "nexora_handle"
    private const val K_AVATAR = "nexora_avatar_uri"
    private const val K_TOKEN = "nexora_session_token"

    // Only set by save(). Installs that hold an id from an older, locally generated scheme do not have this
    // flag, so current() returns null for them and the app shows the sign-in gate (automatic migration).
    private const val K_VERIFIED = "nexora_id_server_verified_v3"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs == null) {
                prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
        }
    }

    /** Null = signed out, never verified, or not initialised yet -> the sign-in gate must be shown. */
    fun current(): AccountSession? {
        val sp = prefs ?: return null
        if (!sp.getBoolean(K_VERIFIED, false)) return null
        val id = sp.getString(K_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = sp.getString(K_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return AccountSession(
            nexoraId = id,
            email = sp.getString(K_EMAIL, "").orEmpty(),
            displayName = sp.getString(K_NAME, "").orEmpty(),
            handle = sp.getString(K_HANDLE, "").orEmpty(),
            avatarUri = sp.getString(K_AVATAR, "").orEmpty(),
            sessionToken = token
        )
    }

    /** commit() (not apply()) so the id is durable before the data-restore steps start. */
    fun save(session: AccountSession) {
        val sp = checkNotNull(prefs) { "SessionStore.init(context) must be called before save()" }
        sp.edit()
            .putString(K_ID, session.nexoraId)
            .putString(K_EMAIL, session.email)
            .putString(K_NAME, session.displayName)
            .putString(K_HANDLE, session.handle)
            .putString(K_AVATAR, session.avatarUri)
            .putString(K_TOKEN, session.sessionToken)
            .putBoolean(K_VERIFIED, true)
            .commit()
    }

    /** Called when the server hands out a renewed session token. Ignored while signed out. */
    fun updateSessionToken(token: String) {
        val sp = prefs ?: return
        if (token.isBlank() || !sp.getBoolean(K_VERIFIED, false)) return
        sp.edit().putString(K_TOKEN, token).commit()
    }

    fun updateProfile(displayName: String, handle: String, avatarUri: String) {
        val sp = prefs ?: return
        if (!sp.getBoolean(K_VERIFIED, false)) return
        sp.edit()
            .putString(K_NAME, displayName)
            .putString(K_HANDLE, handle)
            .putString(K_AVATAR, avatarUri)
            .commit()
    }

    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }
}
