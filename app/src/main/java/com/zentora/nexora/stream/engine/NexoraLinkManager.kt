/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.content.Intent
import java.security.SecureRandom

/**
 * Universal Dynamic Share & Deep-Link Router Engine:
 * - Generates shareable, dynamic links for any active Video or Channel.
 * - Extracts active runtime entity metadata without hardcoding or raw bucket storage URLs.
 * - Launches native Android system chooser share intents.
 */
object NexoraLinkManager {

    private const val CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val secureRandom = SecureRandom()

    /**
     * Generates an alphanumeric pseudo-random short token of specified length for link attribution.
     */
    fun generateShortToken(length: Int = 7): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(CHARACTERS[secureRandom.nextInt(CHARACTERS.length)])
        }
        return sb.toString()
    }

    /**
     * Builds standard share payload for an active video.
     * e.g., Watch "Title" on Nexora Stream: https://nexora.to/videoId?si=xyz1234
     */
    fun buildVideoShareText(videoTitle: String, videoId: String): String {
        val token = generateShortToken(7)
        val cleanTitle = videoTitle.trim().ifBlank { "Video" }
        return "Watch \"$cleanTitle\" on Nexora Stream:\nhttps://nexora.to/$videoId?si=$token"
    }

    /**
     * Builds standard share payload for an active channel.
     * e.g., Check out Channel Name on Nexora Stream: https://nexora.stream/@handle?si=xyz1234
     */
    fun buildChannelShareText(channelName: String, handle: String): String {
        val cleanName = channelName.trim().ifBlank { "Creator" }
        val rawHandle = handle.trim().ifBlank { cleanName.lowercase().replace(" ", "_") }
        val formattedHandle = if (rawHandle.startsWith("@")) rawHandle else "@$rawHandle"
        val token = generateShortToken(7)
        return "Check out $cleanName on Nexora Stream:\nhttps://nexora.stream/$formattedHandle?si=$token"
    }

    /**
     * Launches the native Android Share sheet chooser with plain text payload.
     */
    fun launchSystemShare(context: Context, payload: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        val chooser = Intent.createChooser(sendIntent, "Share via Nexora Stream")
        // If not invoked from an activity context, ensure NEW_TASK flag is handled
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
