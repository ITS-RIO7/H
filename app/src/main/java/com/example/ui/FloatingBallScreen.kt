package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.BallSettings
import com.example.model.AppInfo
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseError
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingBallScreen(
    viewModel: FloatingBallViewModel,
    onExitAndClearRecents: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val googleMeetApp by viewModel.googleMeetApp.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settings.isServiceEnabled && hasOverlayPermission)
                                        EmeraldActive
                                    else
                                        Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Floating Ball",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (settings.isServiceEnabled) "Active" else "Disabled",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (settings.isServiceEnabled) EmeraldActive else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = settings.isServiceEnabled,
                            onCheckedChange = { viewModel.toggleService() },
                            modifier = Modifier.testTag("service_toggle_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmeraldActive
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Notice
            if (!hasOverlayPermission) {
                item {
                    OverlayPermissionCard(
                        onGrantClicked = { viewModel.openOverlaySettings(context) }
                    )
                }
            }

            // Accessibility Notice if not enabled
            if (!isAccessibilityEnabled) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accessibility_alert_banner"),
                        colors = CardDefaults.cardColors(
                            containerColor = AmberWarning.copy(alpha = 0.18f)
                        ),
                        border = BorderStroke(1.5.dp, AmberWarning),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Accessibility Service is OFF!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Bina Accessibility ON kiye Android kisi doosri app ko Recents se remove nahi kar sakta aur na hi Meet me 'Rejoin' par auto-click kar sakta hai. Abhi enable karein:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                            ) {
                                Text("Turn ON Floating Ball in Accessibility Settings", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Google Meet Call Switcher Preset Card (FEATURE REQUESTED)
            item {
                GoogleMeetSwitcherCard(
                    isLinked = settings.isMeetMode ||
                            settings.targetPackage == "com.google.android.apps.tachyon" ||
                            settings.targetPackage == "com.google.android.apps.meetings",
                    meetApp = googleMeetApp,
                    meetingLinkOrCode = settings.meetingLinkOrCode,
                    onMeetingLinkChanged = { viewModel.updateMeetingLinkOrCode(it) },
                    onSelectMeet = { viewModel.selectGoogleMeetPreset() },
                    onTestLaunch = { viewModel.testLaunchTargetApp() }
                )
            }

            // Target App Linked Card
            item {
                TargetAppHeroCard(
                    settings = settings,
                    targetAppInfo = filteredApps.find { it.packageName == settings.targetPackage },
                    onTestLaunch = { viewModel.testLaunchTargetApp() }
                )
            }

            // Auto-Clean Active App & Dual Recents Removal Card (FEATURE REQUESTED)
            item {
                ActiveAppCleanerCard(
                    autoClean = settings.autoCleanActiveApp,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onToggleAutoClean = { viewModel.updateAutoCleanActiveApp(it) },
                    onOpenAccessibility = { viewModel.openAccessibilitySettings(context) }
                )
            }

            // Floating Ball Preview & Settings Customizer Card
            item {
                FloatingBallControlsCard(
                    settings = settings,
                    onSizeChange = { viewModel.updateBallSize(it) },
                    onOpacityChange = { viewModel.updateBallOpacity(it) },
                    onSnapChange = { viewModel.updateSnapToEdge(it) },
                    onAutoHideChange = { viewModel.updateAutoHide(it) },
                    onVibrateChange = { viewModel.updateVibration(it) },
                    onShowAppIconChange = { viewModel.updateShowAppIcon(it) }
                )
            }

            // 1-Tap Hide & Purge Floating Ball from Recents Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("purge_recents_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Floating Ball Recents Purge",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Close setup screen completely while keeping the floating ball active on top without leaving any entry in Recents",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onExitAndClearRecents,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Clear & Hide", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Installed Applications Selector Section Header
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Installed Applications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${filteredApps.size} apps",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "Tap any app to set it as the quick-switch target for the Floating Ball",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_search_field"),
                        placeholder = { Text("Search installed apps...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedFilter == AppFilter.USER,
                            onClick = { viewModel.onFilterChanged(AppFilter.USER) },
                            label = { Text("User Apps") },
                            leadingIcon = if (selectedFilter == AppFilter.USER) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedFilter == AppFilter.ALL,
                            onClick = { viewModel.onFilterChanged(AppFilter.ALL) },
                            label = { Text("All Apps") },
                            leadingIcon = if (selectedFilter == AppFilter.ALL) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedFilter == AppFilter.SYSTEM,
                            onClick = { viewModel.onFilterChanged(AppFilter.SYSTEM) },
                            label = { Text("System") },
                            leadingIcon = if (selectedFilter == AppFilter.SYSTEM) {
                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Apps List
            if (isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading installed applications...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No matching applications found", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Try adjusting your search query or switching filters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isTarget = app.packageName == settings.targetPackage
                    AppListItem(
                        app = app,
                        isSelected = isTarget,
                        onSelect = { viewModel.selectTargetApp(app) }
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Google Meet ongoing call switcher preset card
 */
@Composable
fun GoogleMeetSwitcherCard(
    isLinked: Boolean,
    meetApp: AppInfo?,
    meetingLinkOrCode: String,
    onMeetingLinkChanged: (String) -> Unit,
    onSelectMeet: () -> Unit,
    onTestLaunch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_meet_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLinked)
                CyberPurple.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            if (isLinked) CyberPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoCall,
                            contentDescription = "Meet Call",
                            tint = CyberPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Google Meet Call Switcher",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct switch to ongoing meeting call",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLinked) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldActive.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, EmeraldActive)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldActive,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = EmeraldActive.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, EmeraldActive.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Direct Call Switch: Floating Ball tap karte hi Google Meet khulne par 'Rejoin' / 'Return to call' button par automatically click ho jayega (zero manual clicks!).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Optional direct meeting code or link
            OutlinedTextField(
                value = meetingLinkOrCode,
                onValueChange = onMeetingLinkChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meeting Link / Room Code (Optional)", fontSize = 12.sp) },
                placeholder = { Text("e.g. abc-defg-hij or meet.google.com/...", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberPurple
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSelectMeet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("link_google_meet_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLinked) CyberPurple else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isLinked) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isLinked) Icons.Default.CheckCircle else Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isLinked) "Google Meet Linked" else "Link Google Meet Call",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLinked) {
                    OutlinedButton(
                        onClick = onTestLaunch,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Direct Switch")
                    }
                }
            }
        }
    }
}

/**
 * Dual Recents & Active App Auto-Clean Card (FEATURE REQUESTED)
 */
@Composable
fun ActiveAppCleanerCard(
    autoClean: Boolean,
    isAccessibilityEnabled: Boolean,
    onToggleAutoClean: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_app_cleaner_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Auto-Clean Both Apps from Recents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Closes active app & excludes Floating Ball",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = autoClean,
                    onCheckedChange = onToggleAutoClean,
                    modifier = Modifier.testTag("auto_clean_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation of the dual clean behavior
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("✓", color = EmeraldActive, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Floating Ball App: Automatically excluded from Android 3-line Recents via android:excludeFromRecents=\"true\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.Top) {
                    Text("✓", color = EmeraldActive, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Active App You Clicked In: Automatically closed and backed out before switching so recents stays clean.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Accessibility Status & Action
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAccessibilityEnabled)
                    EmeraldActive.copy(alpha = 0.12f)
                else
                    AmberWarning.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isAccessibilityEnabled) EmeraldActive else AmberWarning
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAccessibilityEnabled)
                                "Accessibility Active (Clean Switch Enabled)"
                            else
                                "Accessibility Required for Auto-Close",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAccessibilityEnabled) EmeraldActive else AmberWarning
                        )
                        Text(
                            text = if (isAccessibilityEnabled)
                                "Simulated back-press cleans active app automatically"
                            else
                                "Tap to enable Floating Ball in Accessibility Settings",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isAccessibilityEnabled) {
                        Button(
                            onClick = onOpenAccessibility,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionCard(onGrantClicked: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overlay_permission_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, RoseError.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Permission Required",
                    tint = RoseError,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Overlay Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Android requires the 'Display over other apps' (SYSTEM_ALERT_WINDOW) permission so the Floating Ball can remain accessible over any screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onGrantClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Display Over Other Apps", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TargetAppHeroCard(
    settings: BallSettings,
    targetAppInfo: AppInfo?,
    onTestLaunch: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("target_app_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            if (settings.targetPackage.isNotEmpty())
                NeonCyan.copy(alpha = pulseBorder)
            else
                MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LINKED TARGET APP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = NeonCyan
                )

                if (settings.targetPackage.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldActive.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, EmeraldActive)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldActive)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldActive
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Target App Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            NeonCyan.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconImage(
                        drawable = targetAppInfo?.icon,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (settings.targetPackage.isNotEmpty()) settings.targetAppName else "No App Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (settings.targetPackage.isNotEmpty()) settings.targetPackage else "Choose an application below to link",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestLaunch,
                    enabled = settings.targetPackage.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_launch_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Launch App", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun FloatingBallControlsCard(
    settings: BallSettings,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSnapChange: (Boolean) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onShowAppIconChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ball_controls_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Floating Ball Appearance & Physics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ball Size Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ball Size",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${settings.ballSizeDp} dp",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
            Slider(
                value = settings.ballSizeDp.toFloat(),
                onValueChange = { onSizeChange(it.roundToInt()) },
                valueRange = 44f..80f,
                steps = 6,
                modifier = Modifier.testTag("ball_size_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan
                )
            )

            // Ball Opacity Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ball Opacity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(settings.ballOpacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
            Slider(
                value = settings.ballOpacity,
                onValueChange = { onOpacityChange(it) },
                valueRange = 0.35f..1.0f,
                steps = 12,
                modifier = Modifier.testTag("ball_opacity_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan
                )
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Snap to Edge Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSnapChange(!settings.snapToEdge) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Snap to Screen Edges", fontWeight = FontWeight.Medium)
                    Text(
                        "Snaps smoothly to left or right margin when dragging stops",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.snapToEdge,
                    onCheckedChange = onSnapChange,
                    modifier = Modifier.testTag("snap_to_edge_switch")
                )
            }

            // Auto-Hide / Close Ball on Launch Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAutoHideChange(!settings.autoHideOnLaunch) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Hide Ball on Launch", fontWeight = FontWeight.Medium)
                    Text(
                        "Dismisses overlay after jumping to the target application",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoHideOnLaunch,
                    onCheckedChange = onAutoHideChange,
                    modifier = Modifier.testTag("auto_hide_switch")
                )
            }

            // Vibration Feedback
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVibrateChange(!settings.vibrationFeedback) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", fontWeight = FontWeight.Medium)
                    Text(
                        "Tactile vibration click when tapping or dismissing ball",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.vibrationFeedback,
                    onCheckedChange = onVibrateChange,
                    modifier = Modifier.testTag("vibration_switch")
                )
            }

            // Show Real App Icon on Ball
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowAppIconChange(!settings.showAppIcon) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Render Target App Icon on Ball", fontWeight = FontWeight.Medium)
                    Text(
                        "Displays the linked app's real logo inside the floating orb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.showAppIcon,
                    onCheckedChange = onShowAppIconChange
                )
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) NeonCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AppIconImage(
                    drawable = app.icon,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.isSystemApp) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "System",
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Select", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AppIconImage(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable == null) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        return
    }

    val bitmap = remember(drawable) {
        try {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        AndroidView(
            factory = { context ->
                android.widget.ImageView(context).apply {
                    setImageDrawable(drawable)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable)
            },
            modifier = modifier
        )
    }
}
