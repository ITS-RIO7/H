package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.PendingIntent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity

class FloatingBallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FloatingBallA11y"

        @Volatile
        var instance: FloatingBallAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        @Volatile
        var shouldAutoClickCall: Boolean = false

        @Volatile
        var cachedMeetCallPendingIntent: PendingIntent? = null

        /**
         * Clears BOTH apps from Recents at the exact moment the floating ball is clicked:
         * 1. Floating Ball Launcher App: Purged completely via finishAndRemoveTask().
         * 2. Active App (the app you were in): Dismissed directly from Recents using ACTION_DISMISS / Upward Swipe.
         */
        fun purgeBothAppsFromRecentsAndSwitch(
            targetPackage: String,
            screenWidth: Int,
            screenHeight: Int,
            onFinished: () -> Unit
        ) {
            // 1. Immediately purge Floating Ball launcher itself from Recents
            MainActivity.closeAndRemoveFromRecents()

            val service = instance
            if (service == null) {
                // If accessibility service is not granted, proceed directly to launch
                onFinished()
                return
            }

            // If user is already in the target app, do not dismiss it
            if (service.currentForegroundPackage.isNotEmpty() &&
                service.currentForegroundPackage == targetPackage
            ) {
                onFinished()
                return
            }

            // 2. Open Android Recents Overview
            service.performGlobalAction(GLOBAL_ACTION_RECENTS)

            // Wait 350ms for Recents card view to fully animate and become interactive
            Handler(Looper.getMainLooper()).postDelayed({
                service.dismissActiveAppFromRecents(screenWidth, screenHeight) {
                    // Wait 180ms after card dismissal completes, then launch Google Meet / Target app
                    Handler(Looper.getMainLooper()).postDelayed({
                        onFinished()
                    }, 180)
                }
            }, 350)
        }

        /**
         * Arm auto-click for Google Meet call or rejoin button.
         * Runs continuous periodic checks for 4 seconds to catch the button as soon as it appears.
         */
        fun armAutoClickForMeetCall() {
            shouldAutoClickCall = true
            val service = instance ?: return

            // Continuous polling every 150ms for up to 4 seconds
            val handler = Handler(Looper.getMainLooper())
            for (delay in 100..4000 step 150) {
                handler.postDelayed({
                    if (shouldAutoClickCall) {
                        service.tryAutoClickCallButton()
                    }
                }, delay.toLong())
            }

            // Disarm after 4.5 seconds timeout
            handler.postDelayed({
                shouldAutoClickCall = false
            }, 4500)
        }
    }

    var currentForegroundPackage: String = ""
        private set

    private val meetPackages = setOf(
        "com.google.android.apps.tachyon",
        "com.google.android.apps.meetings",
        "com.google.android.gm"
    )

    // Complete set of keywords prioritizing Rejoin and Return to Call
    private val callAndRejoinKeywords = listOf(
        "rejoin",
        "rejoin meeting",
        "rejoin call",
        "re-join",
        "join now",
        "join meeting",
        "return to call",
        "back to call",
        "return to meeting",
        "back to meeting",
        "ongoing call",
        "call in progress",
        "ongoing meeting",
        "meeting in progress",
        "in call",
        "resume",
        "return",
        "join",
        "पुनः जुड़ें",
        "फिर से जुड़ें",
        "शामिल हों",
        "वापस जाएं"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "FloatingBallAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isNotEmpty() && pkg != packageName) {
            currentForegroundPackage = pkg
        }

        // Listen for Google Meet active call notifications
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val parcel = event.parcelableData
            if (parcel is Notification && (meetPackages.contains(pkg) || pkg.contains("tachyon") || pkg.contains("meetings"))) {
                // If the notification has an ongoing call action or contentIntent, cache it
                parcel.contentIntent?.let {
                    cachedMeetCallPendingIntent = it
                    Log.d(TAG, "Cached active Google Meet call notification PendingIntent")
                }
                parcel.actions?.forEach { action ->
                    val title = action.title?.toString()?.lowercase() ?: ""
                    if (title.contains("return") || title.contains("rejoin") || title.contains("join") || title.contains("call")) {
                        cachedMeetCallPendingIntent = action.actionIntent
                        Log.d(TAG, "Cached active Google Meet call action PendingIntent: $title")
                    }
                }
            }
        }

        // If Google Meet is active and auto-click is armed, search and click "Rejoin" / "Return to call"
        if (shouldAutoClickCall && (meetPackages.contains(pkg) || pkg.contains("tachyon") || pkg.contains("meetings"))) {
            tryAutoClickCallButton()
        }
    }

    /**
     * Dismisses the active app card from Android Recents:
     * 1. Inspects Recents view for ACTION_DISMISS or "Clear all" buttons.
     * 2. Executes an upward swipe gesture on the center card.
     */
    fun dismissActiveAppFromRecents(
        screenWidth: Int,
        screenHeight: Int,
        onDone: () -> Unit
    ) {
        var dismissedViaNode = false

        // 1. Try finding and dismissing via AccessibilityAction.ACTION_DISMISS or Clear button
        try {
            val root = rootInActiveWindow
            if (root != null) {
                dismissedViaNode = searchAndDismissRecentsNode(root)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recents nodes: ${e.message}")
        }

        // 2. Perform upward swipe gesture on the center card
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val width = if (screenWidth > 0) screenWidth else 1080
            val height = if (screenHeight > 0) screenHeight else 2400

            val startX = width / 2f
            val startY = height * 0.58f
            val endY = height * 0.05f

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }

            val stroke = GestureDescription.StrokeDescription(swipePath, 0, 160)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Recents swipe dismiss gesture completed")
                    onDone()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Recents swipe dismiss cancelled, falling back")
                    fallbackClose(onDone)
                }
            }, null)

            if (!dispatched) {
                if (dismissedViaNode) {
                    onDone()
                } else {
                    fallbackClose(onDone)
                }
            }
        } else {
            fallbackClose(onDone)
        }
    }

    private fun searchAndDismissRecentsNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Check for ACTION_DISMISS action on task card
        for (action in node.actionList) {
            if (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id) {
                val ok = node.performAction(action.id)
                if (ok) {
                    Log.d(TAG, "Dismissed recent task via ACTION_DISMISS")
                    return true
                }
            }
        }

        // Check for "Clear all" or "Dismiss" text
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains("clear all") || text.contains("close all") ||
            desc.contains("clear all") || desc.contains("close all") ||
            viewId.contains("clear_all") || viewId.contains("dismiss_task")
        ) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked Clear All button in Recents")
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (searchAndDismissRecentsNode(node.getChild(i))) {
                return true
            }
        }

        return false
    }

    private fun fallbackClose(onDone: () -> Unit) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            Handler(Looper.getMainLooper()).postDelayed({
                onDone()
            }, 60)
        }, 60)
    }

    /**
     * Searches all active windows on screen for "Rejoin" / "Return to call"
     * and performs an immediate click.
     */
    fun tryAutoClickCallButton() {
        // First search across all windows on screen (including PiP, dialogs, overlays)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val allWindows = windows
                for (window in allWindows) {
                    val root = window.root ?: continue
                    if (searchAndClickCallOrRejoinNode(root)) {
                        shouldAutoClickCall = false
                        Log.d(TAG, "Successfully auto-clicked Rejoin / Return to Call in window: ${window.title}")
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to rootInActiveWindow below
        }

        // Fallback to active window
        val root = rootInActiveWindow ?: return
        val clicked = searchAndClickCallOrRejoinNode(root)
        if (clicked) {
            shouldAutoClickCall = false
            Log.d(TAG, "Successfully auto-clicked Rejoin / Return to Call in active window!")
        }
    }

    private fun searchAndClickCallOrRejoinNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        // Check keywords
        val textMatches = callAndRejoinKeywords.any { kw ->
            text.contains(kw) || desc.contains(kw)
        }

        val idMatches = viewId.contains("rejoin") ||
                viewId.contains("join_button") ||
                viewId.contains("action_rejoin") ||
                viewId.contains("return_to_call") ||
                viewId.contains("call_bar") ||
                viewId.contains("pip_view") ||
                viewId.contains("ongoing_call") ||
                viewId.contains("active_call")

        if (textMatches || idMatches) {
            Log.d(TAG, "Found target node matching Rejoin/Call trigger: text='$text', desc='$desc', id='$viewId'")

            // 1. Try clicking the node directly
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "Clicked target node directly!")
                return true
            }

            // 2. Try clicking clickable parent
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable || target.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
                    if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        Log.d(TAG, "Clicked parent target!")
                        return true
                    }
                }
                target = target.parent
            }

            // 3. Fallback: Physical Touch Gesture at node screen coordinates!
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                dispatchTapAtCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Dispatched physical tap at (${rect.centerX()}, ${rect.centerY()})")
                return true
            }
        }

        // Search children recursively
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (searchAndClickCallOrRejoinNode(child)) {
                return true
            }
        }

        return false
    }

    private fun dispatchTapAtCoordinates(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tapPath = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(tapPath, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    }

    override fun onInterrupt() {
        // Required
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
