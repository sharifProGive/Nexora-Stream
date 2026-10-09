/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SettingsPreferencesManager
 * Local persistent store for 3-tier deep YouTube settings.
 * Engineered for fast reactive observation in Compose UI.
 */
class SettingsPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Account Security Settings
    private val _twoStepVerification = MutableStateFlow(prefs.getBoolean(KEY_TWO_STEP, false))
    val twoStepVerification: StateFlow<Boolean> = _twoStepVerification.asStateFlow()

    private val _passwordRecoveryAlerts = MutableStateFlow(prefs.getBoolean(KEY_RECOVERY_ALERTS, true))
    val passwordRecoveryAlerts: StateFlow<Boolean> = _passwordRecoveryAlerts.asStateFlow()

    // Playback Settings
    private val _doubleTapSeekEnabled = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_TAP_SEEK_ENABLED, true))
    val doubleTapSeekEnabled: StateFlow<Boolean> = _doubleTapSeekEnabled.asStateFlow()

    private val _seekDurationSeconds = MutableStateFlow(prefs.getInt(KEY_SEEK_SECONDS, 10))
    val seekDurationSeconds: StateFlow<Int> = _seekDurationSeconds.asStateFlow()

    private val _inlinePlaybackFeeds = MutableStateFlow(prefs.getBoolean(KEY_INLINE_PLAYBACK, true))
    val inlinePlaybackFeeds: StateFlow<Boolean> = _inlinePlaybackFeeds.asStateFlow()

    // Reminders & Well-being
    private val _takeBreakReminder = MutableStateFlow(prefs.getBoolean(KEY_TAKE_BREAK, false))
    val takeBreakReminder: StateFlow<Boolean> = _takeBreakReminder.asStateFlow()

    private val _breakReminderMinutes = MutableStateFlow(prefs.getInt(KEY_BREAK_MINUTES, 75))
    val breakReminderMinutes: StateFlow<Int> = _breakReminderMinutes.asStateFlow()

    private val _bedtimeReminder = MutableStateFlow(prefs.getBoolean(KEY_BEDTIME, false))
    val bedtimeReminder: StateFlow<Boolean> = _bedtimeReminder.asStateFlow()

    private val _bedtimeHour = MutableStateFlow(prefs.getInt(KEY_BEDTIME_HOUR, 23))
    val bedtimeHour: StateFlow<Int> = _bedtimeHour.asStateFlow()

    private val _bedtimeMinute = MutableStateFlow(prefs.getInt(KEY_BEDTIME_MINUTE, 0))
    val bedtimeMinute: StateFlow<Int> = _bedtimeMinute.asStateFlow()

    // Video Quality
    private val _mobileVideoQuality = MutableStateFlow(prefs.getString(KEY_MOBILE_QUALITY, "Auto (recommended)") ?: "Auto (recommended)")
    val mobileVideoQuality: StateFlow<String> = _mobileVideoQuality.asStateFlow()

    private val _wifiVideoQuality = MutableStateFlow(prefs.getString(KEY_WIFI_QUALITY, "Higher picture quality") ?: "Higher picture quality")
    val wifiVideoQuality: StateFlow<String> = _wifiVideoQuality.asStateFlow()

    // Downloads
    private val _downloadWifiOnly = MutableStateFlow(prefs.getBoolean(KEY_DOWNLOAD_WIFI_ONLY, true))
    val downloadWifiOnly: StateFlow<Boolean> = _downloadWifiOnly.asStateFlow()

    private val _downloadQuality = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_QUALITY, "High (720p)") ?: "High (720p)")
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    // Setters
    fun setTwoStepVerification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TWO_STEP, enabled).apply()
        _twoStepVerification.value = enabled
    }

    fun setPasswordRecoveryAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECOVERY_ALERTS, enabled).apply()
        _passwordRecoveryAlerts.value = enabled
    }

    fun setDoubleTapSeekEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_SEEK_ENABLED, enabled).apply()
        _doubleTapSeekEnabled.value = enabled
    }

    fun setSeekDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SEEK_SECONDS, seconds).apply()
        _seekDurationSeconds.value = seconds
    }

    fun setInlinePlaybackFeeds(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INLINE_PLAYBACK, enabled).apply()
        _inlinePlaybackFeeds.value = enabled
    }

    fun setTakeBreakReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TAKE_BREAK, enabled).apply()
        _takeBreakReminder.value = enabled
    }

    fun setBreakReminderMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_BREAK_MINUTES, minutes).apply()
        _breakReminderMinutes.value = minutes
    }

    fun setBedtimeReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BEDTIME, enabled).apply()
        _bedtimeReminder.value = enabled
    }

    fun setBedtimeTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_BEDTIME_HOUR, hour).putInt(KEY_BEDTIME_MINUTE, minute).apply()
        _bedtimeHour.value = hour
        _bedtimeMinute.value = minute
    }

    fun setMobileVideoQuality(quality: String) {
        prefs.edit().putString(KEY_MOBILE_QUALITY, quality).apply()
        _mobileVideoQuality.value = quality
    }

    fun setWifiVideoQuality(quality: String) {
        prefs.edit().putString(KEY_WIFI_QUALITY, quality).apply()
        _wifiVideoQuality.value = quality
    }

    fun setDownloadWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOAD_WIFI_ONLY, enabled).apply()
        _downloadWifiOnly.value = enabled
    }

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
        _downloadQuality.value = quality
    }

    companion object {
        private const val PREFS_NAME = "nexora_settings_prefs"
        private const val KEY_TWO_STEP = "two_step_verification"
        private const val KEY_RECOVERY_ALERTS = "password_recovery_alerts"
        private const val KEY_DOUBLE_TAP_SEEK_ENABLED = "double_tap_seek_enabled"
        private const val KEY_SEEK_SECONDS = "seek_duration_seconds"
        private const val KEY_INLINE_PLAYBACK = "inline_playback_feeds"
        private const val KEY_TAKE_BREAK = "take_break_reminder"
        private const val KEY_BREAK_MINUTES = "break_reminder_minutes"
        private const val KEY_BEDTIME = "bedtime_reminder"
        private const val KEY_BEDTIME_HOUR = "bedtime_hour"
        private const val KEY_BEDTIME_MINUTE = "bedtime_minute"
        private const val KEY_MOBILE_QUALITY = "mobile_video_quality"
        private const val KEY_WIFI_QUALITY = "wifi_video_quality"
        private const val KEY_DOWNLOAD_WIFI_ONLY = "download_wifi_only"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"

        @Volatile
        private var instance: SettingsPreferencesManager? = null

        fun getInstance(context: Context): SettingsPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
