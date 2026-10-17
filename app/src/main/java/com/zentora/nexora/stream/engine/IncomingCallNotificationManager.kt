/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.zentora.nexora.stream.MainActivity
import com.zentora.nexora.stream.R

/**
 * Incoming Call Notification Manager (Pillar 3: Real Hardware Phone Calling Pipeline).
 * Dispatches high-priority full-screen intent notifications to wake up the screen,
 * bypass screen lock, and loop real device ringtone and vibration until answered or declined.
 */
class IncomingCallNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nexora Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority full-screen incoming voice and video calls"
                setSound(null, null) // Sound is handled explicitly via MediaPlayer loop
                enableVibration(false) // Vibration is handled explicitly via Vibrator loop
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Wakes the device, turns the screen on, plays continuous ringtone, and shows call notification.
     */
    fun startRinging(
        callerName: String,
        callerHandle: String,
        isVideo: Boolean = false
    ) {
        stopRinging()

        // 1. Full-screen intent to bypass lock screen and wake device immediately
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            putExtra("CALL_HANDLE", callerHandle)
            putExtra("CALLER_NAME", callerName)
            putExtra("CALL_IS_VIDEO", isVideo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Build High-Priority Call Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.nexora_infinity_logo)
            .setContentTitle(if (isVideo) "Incoming Video Call" else "Incoming Voice Call")
            .setContentText("$callerName ($callerHandle)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}

        // 3. Play continuous device ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}

        // 4. Trigger continuous hardware vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    /**
     * Stops continuous ringtone, cancels vibration pattern, and clears call notification.
     */
    fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null

        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "nexora_incoming_calls_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_INCOMING_CALL = "com.zentora.nexora.stream.ACTION_INCOMING_CALL"

        @Volatile
        private var INSTANCE: IncomingCallNotificationManager? = null

        fun getInstance(context: Context): IncomingCallNotificationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IncomingCallNotificationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
