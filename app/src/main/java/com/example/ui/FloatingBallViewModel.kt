package com.example.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppListRepository
import com.example.data.BallSettings
import com.example.data.FloatingBallPreferences
import com.example.model.AppInfo
import com.example.service.FloatingBallAccessibilityService
import com.example.service.FloatingBallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilter {
    ALL, USER, SYSTEM
}

class FloatingBallViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppListRepository(application)
    private val preferences = FloatingBallPreferences.getInstance(application)

    val settings: StateFlow<BallSettings> = preferences.settingsFlow

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.USER)
    val selectedFilter: StateFlow<AppFilter> = _selectedFilter.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _googleMeetApp = MutableStateFlow<AppInfo?>(null)
    val googleMeetApp: StateFlow<AppInfo?> = _googleMeetApp.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        _searchQuery,
        _selectedFilter
    ) { apps, query, filter ->
        apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
            }
            val matchesQuery = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshPermissions()
        loadInstalledApps()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _hasOverlayPermission.value = Settings.canDrawOverlays(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _hasNotificationPermission.value = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            _hasNotificationPermission.value = true
        }

        // Check Accessibility Service status
        _isAccessibilityEnabled.value = checkAccessibilityEnabled(context)
    }

    private fun checkAccessibilityEnabled(context: Context): Boolean {
        if (FloatingBallAccessibilityService.isServiceRunning) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedServiceName = "${context.packageName}/${FloatingBallAccessibilityService::class.java.name}"
        return enabledServices.any {
            it.id.equals(expectedServiceName, ignoreCase = true) ||
                    it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = repository.getInstalledApps()
            _installedApps.value = apps

            // Find Google Meet if installed
            val meetPackages = listOf("com.google.android.apps.tachyon", "com.google.android.apps.meetings")
            val foundMeet = apps.find { app -> meetPackages.contains(app.packageName) }
            _googleMeetApp.value = foundMeet

            // If no target app is selected yet, pick Meet if installed, else first user app
            val currentSettings = preferences.getSettings()
            if (currentSettings.targetPackage.isEmpty()) {
                if (foundMeet != null) {
                    selectTargetApp(foundMeet, autoStart = false, isMeet = true)
                } else if (apps.isNotEmpty()) {
                    val candidate = apps.firstOrNull { !it.isSystemApp } ?: apps.first()
                    selectTargetApp(candidate, autoStart = false, isMeet = false)
                }
            }
            _isLoadingApps.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChanged(filter: AppFilter) {
        _selectedFilter.value = filter
    }

    fun selectTargetApp(app: AppInfo, autoStart: Boolean = true, isMeet: Boolean = false) {
        val context = getApplication<Application>()
        val displayName = if (isMeet) "Google Meet (Ongoing Call Switcher)" else app.appName
        preferences.setTargetApp(app.packageName, displayName, isMeet = isMeet)

        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(context)
        } else if (autoStart && Settings.canDrawOverlays(context)) {
            FloatingBallService.start(context)
            preferences.setServiceEnabled(true)
        }

        Toast.makeText(
            context,
            "Floating Ball linked to $displayName",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun selectGoogleMeetPreset() {
        val meet = _googleMeetApp.value
        val context = getApplication<Application>()
        if (meet != null) {
            selectTargetApp(meet, autoStart = true, isMeet = true)
        } else {
            // Direct link even if not yet queried
            preferences.setTargetApp(
                "com.google.android.apps.tachyon",
                "Google Meet (Call Switcher)",
                isMeet = true
            )
            if (preferences.getSettings().isServiceEnabled) {
                FloatingBallService.updateConfig(context)
            } else if (Settings.canDrawOverlays(context)) {
                FloatingBallService.start(context)
                preferences.setServiceEnabled(true)
            }
            Toast.makeText(
                context,
                "Floating Ball configured for Google Meet Calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleService() {
        val context = getApplication<Application>()
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(
                context,
                "Overlay permission required to display Floating Ball",
                Toast.LENGTH_LONG
            ).show()
            openOverlaySettings(context)
            return
        }

        val isCurrentlyEnabled = preferences.getSettings().isServiceEnabled
        if (isCurrentlyEnabled) {
            FloatingBallService.stop(context)
            preferences.setServiceEnabled(false)
        } else {
            FloatingBallService.start(context)
            preferences.setServiceEnabled(true)
        }
    }

    fun updateBallSize(sizeDp: Int) {
        preferences.setBallSize(sizeDp)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateBallOpacity(opacity: Float) {
        preferences.setBallOpacity(opacity)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateSnapToEdge(snap: Boolean) {
        preferences.setSnapToEdge(snap)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateAutoHide(autoHide: Boolean) {
        preferences.setAutoHideOnLaunch(autoHide)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateVibration(vibrate: Boolean) {
        preferences.setVibrationFeedback(vibrate)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateShowAppIcon(show: Boolean) {
        preferences.setShowAppIcon(show)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateAutoCleanActiveApp(clean: Boolean) {
        preferences.setAutoCleanActiveApp(clean)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateRecentsDismissTarget(target: String) {
        preferences.setRecentsDismissTarget(target)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateTurboSpeed(enabled: Boolean) {
        preferences.setTurboSpeed(enabled)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun updateMeetingLinkOrCode(link: String) {
        preferences.setMeetingLinkOrCode(link)
        if (preferences.getSettings().isServiceEnabled) {
            FloatingBallService.updateConfig(getApplication())
        }
    }

    fun testLaunchTargetApp() {
        val context = getApplication<Application>()
        val pkg = preferences.getSettings().targetPackage
        if (pkg.isEmpty()) {
            Toast.makeText(context, "No target app selected", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Unable to launch target app", Toast.LENGTH_SHORT).show()
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Please enable Floating Ball in Accessibility Settings", Toast.LENGTH_SHORT).show()
        }
    }
}
