package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BallSettings(
    val targetPackage: String = "",
    val targetAppName: String = "None Selected",
    val ballSizeDp: Int = 58,
    val ballOpacity: Float = 0.92f,
    val snapToEdge: Boolean = true,
    val autoHideOnLaunch: Boolean = false,
    val vibrationFeedback: Boolean = true,
    val showAppIcon: Boolean = true,
    val isServiceEnabled: Boolean = false,
    val autoCleanActiveApp: Boolean = true,
    val recentsDismissTarget: String = "right", // "right", "center", "both"
    val turboSpeed: Boolean = true, // Ultra-fast switching with 60ms recents delay
    val isMeetMode: Boolean = false,
    val meetingLinkOrCode: String = ""
)

class FloatingBallPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_ball_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TARGET_PACKAGE = "target_package"
        private const val KEY_TARGET_APP_NAME = "target_app_name"
        private const val KEY_BALL_SIZE = "ball_size"
        private const val KEY_BALL_OPACITY = "ball_opacity"
        private const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        private const val KEY_AUTO_HIDE = "auto_hide_on_launch"
        private const val KEY_VIBRATION = "vibration_feedback"
        private const val KEY_SHOW_APP_ICON = "show_app_icon"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_AUTO_CLEAN_ACTIVE_APP = "auto_clean_active_app"
        private const val KEY_RECENTS_DISMISS_TARGET = "recents_dismiss_target"
        private const val KEY_TURBO_SPEED = "turbo_speed"
        private const val KEY_IS_MEET_MODE = "is_meet_mode"
        private const val KEY_MEETING_LINK = "meeting_link_or_code"

        @Volatile
        private var instance: FloatingBallPreferences? = null

        fun getInstance(context: Context): FloatingBallPreferences {
            return instance ?: synchronized(this) {
                instance ?: FloatingBallPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<BallSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): BallSettings = loadSettings()

    private fun loadSettings(): BallSettings {
        return BallSettings(
            targetPackage = prefs.getString(KEY_TARGET_PACKAGE, "") ?: "",
            targetAppName = prefs.getString(KEY_TARGET_APP_NAME, "None Selected") ?: "None Selected",
            ballSizeDp = prefs.getInt(KEY_BALL_SIZE, 58),
            ballOpacity = prefs.getFloat(KEY_BALL_OPACITY, 0.92f),
            snapToEdge = prefs.getBoolean(KEY_SNAP_TO_EDGE, true),
            autoHideOnLaunch = prefs.getBoolean(KEY_AUTO_HIDE, false),
            vibrationFeedback = prefs.getBoolean(KEY_VIBRATION, true),
            showAppIcon = prefs.getBoolean(KEY_SHOW_APP_ICON, true),
            isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false),
            autoCleanActiveApp = prefs.getBoolean(KEY_AUTO_CLEAN_ACTIVE_APP, true),
            recentsDismissTarget = prefs.getString(KEY_RECENTS_DISMISS_TARGET, "right") ?: "right",
            turboSpeed = prefs.getBoolean(KEY_TURBO_SPEED, true),
            isMeetMode = prefs.getBoolean(KEY_IS_MEET_MODE, false),
            meetingLinkOrCode = prefs.getString(KEY_MEETING_LINK, "") ?: ""
        )
    }

    fun setTargetApp(packageName: String, appName: String, isMeet: Boolean = false) {
        prefs.edit()
            .putString(KEY_TARGET_PACKAGE, packageName)
            .putString(KEY_TARGET_APP_NAME, appName)
            .putBoolean(KEY_IS_MEET_MODE, isMeet)
            .apply()
        _settingsFlow.value = loadSettings()
    }

    fun setMeetingLinkOrCode(link: String) {
        prefs.edit().putString(KEY_MEETING_LINK, link.trim()).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setBallSize(sizeDp: Int) {
        prefs.edit().putInt(KEY_BALL_SIZE, sizeDp).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setBallOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_BALL_OPACITY, opacity).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setSnapToEdge(snap: Boolean) {
        prefs.edit().putBoolean(KEY_SNAP_TO_EDGE, snap).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setAutoHideOnLaunch(autoHide: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE, autoHide).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setVibrationFeedback(vibrate: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, vibrate).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setShowAppIcon(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_APP_ICON, show).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setAutoCleanActiveApp(clean: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CLEAN_ACTIVE_APP, clean).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setRecentsDismissTarget(target: String) {
        prefs.edit().putString(KEY_RECENTS_DISMISS_TARGET, target).apply()
        _settingsFlow.value = loadSettings()
    }

    fun setTurboSpeed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TURBO_SPEED, enabled).apply()
        _settingsFlow.value = loadSettings()
    }
}
