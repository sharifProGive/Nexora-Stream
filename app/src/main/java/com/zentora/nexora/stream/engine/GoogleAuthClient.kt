/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/**
 * Google OAuth *Web application* client id. It is used for exactly one thing: the phone asks Google for an
 * ID token addressed to this id, and our own backend (env GOOGLE_WEB_CLIENT_ID, same value) verifies it.
 * Create it once in Google Cloud Console > APIs & Services > Credentials (type: Web application) and also add
 * an Android OAuth client for package com.zentora.nexora.stream with your signing key's SHA-1.
 */
object NexoraAuthConfig {
    const val GOOGLE_WEB_CLIENT_ID: String = "PASTE_YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"

    val isConfigured: Boolean
        get() = GOOGLE_WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com") &&
            !GOOGLE_WEB_CLIENT_ID.startsWith("PASTE_")
}

sealed interface GoogleSignInResult {
    data class Success(val idToken: String, val hint: GoogleProfileHint) : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

/** Thin wrapper around AndroidX Credential Manager. It returns a Google ID token and nothing else. */
class GoogleAuthClient(context: Context) {

    private val credentialManager = CredentialManager.create(context.applicationContext)

    /** [activityContext] must be an Activity context because Google shows the account picker on top of it. */
    suspend fun signIn(activityContext: Context): GoogleSignInResult {
        if (!NexoraAuthConfig.isConfigured) {
            return GoogleSignInResult.Error(
                "Google sign-in is not configured. Set NexoraAuthConfig.GOOGLE_WEB_CLIENT_ID to your Web client id."
            )
        }
        val option = GetSignInWithGoogleOption.Builder(NexoraAuthConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = credentialManager.getCredential(context = activityContext, request = request)
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = google.idToken
                if (idToken.isBlank()) {
                    GoogleSignInResult.Error("Google did not return a sign-in token. Please try again.")
                } else {
                    GoogleSignInResult.Success(
                        idToken = idToken,
                        hint = GoogleProfileHint(
                            email = google.id,
                            displayName = google.displayName ?: google.givenName ?: "",
                            avatarUri = google.profilePictureUri?.toString().orEmpty()
                        )
                    )
                }
            } else {
                GoogleSignInResult.Error("Unexpected credential type returned by Google sign-in.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Error("No Google account is available on this device. Add one in Settings and try again.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Error("Google returned an invalid sign-in token. Please try again.")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Error(e.message ?: "Google sign-in failed. Please try again.")
        }
    }

    /** Forgets the Google account choice on this phone (used by sign-out). Failures are harmless. */
    suspend fun clearState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClearCredentialException) {
            // Nothing to clear or the provider is unavailable: the local session is removed regardless.
        }
    }
}
