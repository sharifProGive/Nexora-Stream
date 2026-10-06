/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Vault 1 (On-Device Storage Engine):
 * Native internal file persistence under context.filesDir/nexora_vault_media/
 * permanently preventing Android Scoped Storage content:// URI permission revocation
 * (Black Screen Bug) and extracting automatic 1-second video frames via MediaMetadataRetriever.
 */
object Vault1StorageEngine {

    private const val VAULT_DIR_NAME = "nexora_vault_media"

    fun getVaultMediaDirectory(context: Context): File {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copies video content:// or file:// stream into Vault 1 internal directory.
     * Guarantees perpetual access without Scoped Storage security revocation.
     */
    fun persistVideoToVault1(context: Context, sourceUri: Uri, isShort: Boolean): File {
        val vaultDir = getVaultMediaDirectory(context)
        val prefix = if (isShort) "vault1_short_" else "vault1_vid_"
        val uniqueTag = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val destinationFile = File(vaultDir, "$prefix$uniqueTag.mp4")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Vault 1 Error: Failed to open input stream for source URI: $sourceUri")

        return destinationFile
    }

    /**
     * Extracts an automatic 1.0-second video frame (1,000,000 microseconds) from the persisted video file
     * via MediaMetadataRetriever and saves it into Vault 1 as a JPEG.
     */
    fun extractAutomatic1SecThumbnail(context: Context, videoFile: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            // 1.0 second = 1,000,000 microseconds
            val frameBitmap = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

            if (frameBitmap != null) {
                val thumbFile = File(getVaultMediaDirectory(context), "thumb_${videoFile.nameWithoutExtension}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                thumbFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Copies a user-selected custom thumbnail image into Vault 1 internal directory.
     */
    fun persistCustomThumbnailToVault1(context: Context, imageUri: Uri): File {
        val vaultDir = getVaultMediaDirectory(context)
        val uniqueTag = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val destinationFile = File(vaultDir, "custom_thumb_$uniqueTag.jpg")

        context.contentResolver.openInputStream(imageUri)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Vault 1 Error: Failed to open input stream for thumbnail URI: $imageUri")

        return destinationFile
    }

    /**
     * Extracts media duration in milliseconds via MediaMetadataRetriever.
     */
    fun extractDurationMs(videoFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 60000L
        } catch (_: Exception) {
            60000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
