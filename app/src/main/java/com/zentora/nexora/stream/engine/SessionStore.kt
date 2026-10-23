/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * Vault 6 – local session. The nexoraId here is NEVER generated on the device.
 * It is only ever written after the server has confirmed it for a verified Google account.
 */
data class AccountSession(
    val nexoraId: String,
    val email: String,
    val displayName: String,
    val avatarUri: String
)

object SessionStore {
    private const val PREFS = "nexora_session_prefs"
    private const val K_ID = "nexora_id"
    private const val K_EMAIL = "nexora_email"
    private const val K_NAME = "nexora_display_name"
    private const val K_AVATAR = "nexora_avatar_uri"

    // v2 flag: legacy installs hold a locally generated id that was never server-verified.
    // Without this flag current() returns null, which forces the sign-in gate (= automatic migration).
    private const val K_VERIFIED = "nexora_id_server_verified_v2"

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

    private fun p(): SharedPreferences =
        checkNotNull(prefs) { "SessionStore.init(context) must be called in Application.onCreate()" }

    /** Null = signed out / never verified -> show the sign-in gate. */
    fun current(): AccountSession? {
        val sp = p()
        if (!sp.getBoolean(K_VERIFIED, false)) return null
        val id = sp.getString(K_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return AccountSession(
            nexoraId = id,
            email = sp.getString(K_EMAIL, "").orEmpty(),
            displayName = sp.getString(K_NAME, "").orEmpty(),
            avatarUri = sp.getString(K_AVATAR, "").orEmpty()
        )
    }

    /** commit() (not apply()) so the id is durable before the data-restore steps start. */
    fun save(session: AccountSession) {
        p().edit()
            .putString(K_ID, session.nexoraId)
            .putString(K_EMAIL, session.email)
            .putString(K_NAME, session.displayName)
            .putString(K_AVATAR, session.avatarUri)
            .putBoolean(K_VERIFIED, true)
            .commit()
    }

    fun clear() {
        p().edit().clear().commit()
    }
}
