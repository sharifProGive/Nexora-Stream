/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zentora.nexora.stream.MainActivity
import com.zentora.nexora.stream.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OngoingCallService (Pillar 3: Hardware Audio & Foreground Service).
 * Runs foreground phone call service with MODE_IN_COMMUNICATION audio routing,
 * real-time call timer, microphone mute controls, and speakerphone switching.
 */
class OngoingCallService : Service() {

    private val binder = CallBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager

    private val _callDurationSeconds = MutableStateFlow(0L)
    val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private var timerJob: Job? = null
    private var callerName: String = "Nexora Contact"
    private var callerHandle: String = "@contact"
    private var isVideoCall: Boolean = false

    inner class CallBinder : Binder() {
        fun getService(): OngoingCallService = this@OngoingCallService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createCallNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_ONGOING_CALL -> {
                callerName = intent.getStringExtra("EXTRA_CALLER_NAME") ?: "Nexora Contact"
                callerHandle = intent.getStringExtra("EXTRA_CALLER_HANDLE") ?: "@contact"
                isVideoCall = intent.getBooleanExtra("EXTRA_IS_VIDEO", false)

                startCallSession()
            }
            ACTION_STOP_ONGOING_CALL -> {
                terminateCallSession()
                stopSelf()
            }
            ACTION_TOGGLE_MIC -> {
                toggleMicrophone()
            }
            ACTION_TOGGLE_SPEAKER -> {
                toggleSpeakerphone()
            }
        }

        return START_NOT_STICKY
    }

    private fun startCallSession() {
        _isCallActive.value = true

        // 1. Configure Hardware Audio for VoIP Communication
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = isVideoCall
            _isMicrophoneMuted.value = false
            _isSpeakerphoneOn.value = isVideoCall
        } catch (_: Exception) {}

        // 2. Start Foreground Notification
        val notification = buildOngoingNotification(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }
            startForeground(SERVICE_NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }

        // 3. Start Call Duration Timer
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                _callDurationSeconds.value = seconds
                if (seconds % 5L == 0L) {
                    notificationManager.notify(SERVICE_NOTIFICATION_ID, buildOngoingNotification(seconds))
                }
            }
        }
    }

    fun toggleMicrophone(): Boolean {
        val newState = !_isMicrophoneMuted.value
        try {
            audioManager.isMicrophoneMute = newState
            _isMicrophoneMuted.value = newState
        } catch (_: Exception) {}
        return newState
    }

    fun toggleSpeakerphone(): Boolean {
        val newState = !_isSpeakerphoneOn.value
        try {
            audioManager.isSpeakerphoneOn = newState
            _isSpeakerphoneOn.value = newState
        } catch (_: Exception) {}
        return newState
    }

    private fun terminateCallSession() {
        timerJob?.cancel()
        timerJob = null
        _isCallActive.value = false
        _callDurationSeconds.value = 0L

        // Reset Audio Routing
        try {
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildOngoingNotification(seconds: Long): android.app.Notification {
        val formattedTime = String.format("%02d:%02d", seconds / 60, seconds % 60)

        val returnIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_HANDLE", callerHandle)
            putExtra("CALL_IS_VIDEO", isVideoCall)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            SERVICE_NOTIFICATION_ID,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.nexora_infinity_logo)
            .setContentTitle(if (isVideoCall) "Ongoing Video Call with $callerName" else "Ongoing Call with $callerName")
            .setContentText("Duration: $formattedTime • Encrypted WebRTC Stream")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notifications for ongoing Nexora phone calls"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        terminateCallSession()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "nexora_ongoing_calls_channel"
        const val SERVICE_NOTIFICATION_ID = 9002

        const val ACTION_START_ONGOING_CALL = "com.zentora.nexora.stream.ACTION_START_ONGOING_CALL"
        const val ACTION_STOP_ONGOING_CALL = "com.zentora.nexora.stream.ACTION_STOP_ONGOING_CALL"
        const val ACTION_TOGGLE_MIC = "com.zentora.nexora.stream.ACTION_TOGGLE_MIC"
        const val ACTION_TOGGLE_SPEAKER = "com.zentora.nexora.stream.ACTION_TOGGLE_SPEAKER"

        fun startCall(context: Context, callerName: String, callerHandle: String, isVideo: Boolean) {
            val intent = Intent(context, OngoingCallService::class.java).apply {
                action = ACTION_START_ONGOING_CALL
                putExtra("EXTRA_CALLER_NAME", callerName)
                putExtra("EXTRA_CALLER_HANDLE", callerHandle)
                putExtra("EXTRA_IS_VIDEO", isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopCall(context: Context) {
            val intent = Intent(context, OngoingCallService::class.java).apply {
                action = ACTION_STOP_ONGOING_CALL
            }
            context.startService(intent)
        }
    }
}
