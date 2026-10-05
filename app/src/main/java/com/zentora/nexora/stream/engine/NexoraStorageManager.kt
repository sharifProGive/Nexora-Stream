/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Zentora Vault 1 (On-Device Storage Engine)
 * ২ ঘণ্টা পর content:// পারমিশন বাতিলের কারণে কালো স্ক্রিন হওয়া বন্ধ করতে
 * ভিডিও ফাইল অ্যাপের স্থায়ী ইন্টারনাল ডিরেক্টরিতে সংরক্ষণ ও অটো-থাম্বনেইল এক্সট্রাক্ট করে।
 */
object NexoraStorageManager {

    private const val VIDEO_DIR_NAME = "zentora_vault_videos"
    private const val THUMB_DIR_NAME = "zentora_vault_thumbs"

    /**
     * অস্থায়ী content:// URI থেকে ভিডিও ডাটা অ্যাপের স্থায়ী ইন্টারনাল ফাইলে কপি করে।
     * এর ফলে অ্যান্ড্রয়েড পারমিশন বাতিল করলেও ভিডিও আর কখনো কালো স্ক্রিন হবে না।
     */
    suspend fun persistVideoLocally(context: Context, sourceUri: Uri, videoId: String): String = withContext(Dispatchers.IO) {
        val storageDir = File(context.filesDir, VIDEO_DIR_NAME)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val destinationFile = File(storageDir, "VID_${videoId}.mp4")

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream: InputStream ->
            FileOutputStream(destinationFile).use { outputStream: FileOutputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        destinationFile.absolutePath
    }

    /**
     * ভিডিও ফাইল থেকে ১ সেকেন্ডের মাথায় থাকা আসল ফ্রেম ক্যাপচার করে জেপিজি থাম্বনেইল তৈরি করে।
     * ব্যবহারকারী থাম্বনেইল না দিলেও এটি স্বয়ংক্রিয়ভাবে থাম্বনেইল ফাইল তৈরি করে তার পাথ প্রদান করে।
     */
    suspend fun generateAutoThumbnail(context: Context, videoPath: String, videoId: String): String = withContext(Dispatchers.IO) {
        val thumbDir = File(context.filesDir, THUMB_DIR_NAME)
        if (!thumbDir.exists()) {
            thumbDir.mkdirs()
        }

        val thumbFile = File(thumbDir, "THUMB_${videoId}.jpg")
        val retriever = MediaMetadataRetriever()

        try {
            if (videoPath.startsWith("/")) {
                retriever.setDataSource(videoPath)
            } else {
                retriever.setDataSource(context, Uri.parse(videoPath))
            }

            // ১ সেকেন্ড (১,০০০,০০০ মাইক্রোসেকেন্ড) অবস্থানের ফ্রেম এক্সট্র্যাক্ট করা
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            bitmap?.let {
                FileOutputStream(thumbFile).use { outputStream ->
                    it.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        if (thumbFile.exists()) thumbFile.absolutePath else ""
    }

    /**
     * কোনো ভিডিও বা তার থাম্বনেইল ফাইল লোকাল ডিভাইস থেকে মুছে ফেলার ইউটিলিটি
     */
    fun removeLocalVaultMedia(videoPath: String?, thumbPath: String?) {
        try {
            videoPath?.let { path ->
                val vFile = File(path)
                if (vFile.exists()) vFile.delete()
            }
            thumbPath?.let { path ->
                val tFile = File(path)
                if (tFile.exists()) tFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
