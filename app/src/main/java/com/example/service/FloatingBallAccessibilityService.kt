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
         * 2. Active App (the app you were in): Dismissed directly from Recents (Right card by default).
         */
        fun purgeBothAppsFromRecentsAndSwitch(
            targetPackage: String,
            screenWidth: Int,
            screenHeight: Int,
            targetCard: String = "right",
            isTurbo: Boolean = true,
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

            // Ultra-fast timings: In Turbo mode, only wait 60ms for Recents view to mount
            val recentsOpenDelay = if (isTurbo) 60L else 180L
            val postDismissDelay = if (isTurbo) 0L else 50L

            Handler(Looper.getMainLooper()).postDelayed({
                service.dismissActiveAppFromRecents(screenWidth, screenHeight, targetCard, isTurbo) {
                    if (postDismissDelay > 0L) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            onFinished()
                        }, postDismissDelay)
                    } else {
                        onFinished()
                    }
                }
            }, recentsOpenDelay)
        }

        /**
         * Arm auto-click for Google Meet call or rejoin button.
         * Runs instant and continuous periodic checks starting at 0ms.
         */
        fun armAutoClickForMeetCall() {
            shouldAutoClickCall = true
            val service = instance ?: return

            // Instant check right now
            service.tryAutoClickCallButton()

            val handler = Handler(Looper.getMainLooper())
            val delays = listOf(
                20L, 45L, 75L, 110L, 150L, 200L, 260L, 330L, 420L, 520L, 650L,
                800L, 1000L, 1300L, 1700L, 2200L, 2800L, 3500L, 4200L
            )
            for (delay in delays) {
                handler.postDelayed({
                    if (shouldAutoClickCall) {
                        service.tryAutoClickCallButton()
                    }
                }, delay)
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

    private data class TaskCardCandidate(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val actionId: Int
    )

    /**
     * Dismisses the active app card from Android Recents:
     * Targets the RIGHT card by default (as requested: "right vala remove kare"),
     * or CENTER, or BOTH cards.
     */
    fun dismissActiveAppFromRecents(
        screenWidth: Int,
        screenHeight: Int,
        targetCard: String = "right",
        isTurbo: Boolean = true,
        onDone: () -> Unit
    ) {
        val width = if (screenWidth > 0) screenWidth else 1080
        val height = if (screenHeight > 0) screenHeight else 2400

        // 1. Collect all task cards that support ACTION_DISMISS
        val candidates = mutableListOf<TaskCardCandidate>()
        try {
            val root = rootInActiveWindow
            if (root != null) {
                collectDismissableTaskCards(root, candidates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recents nodes: ${e.message}")
        }

        var rightTargetX = width * 0.80f
        var rightTargetY = height * 0.58f
        var centerTargetX = width * 0.50f
        var centerTargetY = height * 0.58f

        if (candidates.isNotEmpty()) {
            val rightCandidate = candidates.maxByOrNull { it.bounds.centerX() }
            val centerCandidate = candidates.minByOrNull { kotlin.math.abs(it.bounds.centerX() - width / 2) }

            if (rightCandidate != null && rightCandidate.bounds.centerX() > 0) {
                rightTargetX = rightCandidate.bounds.centerX().toFloat()
                rightTargetY = rightCandidate.bounds.centerY().toFloat().coerceIn(height * 0.3f, height * 0.7f)
            }
            if (centerCandidate != null && centerCandidate.bounds.centerX() > 0) {
                centerTargetX = centerCandidate.bounds.centerX().toFloat()
                centerTargetY = centerCandidate.bounds.centerY().toFloat().coerceIn(height * 0.3f, height * 0.7f)
            }

            when (targetCard) {
                "right" -> {
                    rightCandidate?.node?.performAction(rightCandidate.actionId)
                    Log.d(TAG, "Triggered ACTION_DISMISS on right card (centerX=${rightCandidate?.bounds?.centerX()})")
                }
                "center" -> {
                    centerCandidate?.node?.performAction(centerCandidate.actionId)
                    Log.d(TAG, "Triggered ACTION_DISMISS on center card (centerX=${centerCandidate?.bounds?.centerX()})")
                }
                "both" -> {
                    rightCandidate?.node?.performAction(rightCandidate.actionId)
                    centerCandidate?.node?.performAction(centerCandidate.actionId)
                    Log.d(TAG, "Triggered ACTION_DISMISS on both right and center cards")
                }
            }
        }

        // 2. Perform upward swipe gesture on the chosen card (Right card by default)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val startX = when (targetCard) {
                "center" -> centerTargetX
                else -> rightTargetX // "right" or "both" flings the right card first
            }
            val startY = when (targetCard) {
                "center" -> centerTargetY
                else -> rightTargetY
            }
            val endY = height * 0.05f

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }

            // Rapid 45ms flick in Turbo mode (instead of 160ms)
            val strokeDuration = if (isTurbo) 45L else 120L
            val stroke = GestureDescription.StrokeDescription(swipePath, 0, strokeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Recents swipe dismiss on $targetCard card completed")
                    if (targetCard == "both") {
                        Handler(Looper.getMainLooper()).postDelayed({
                            swipeCenterCard(centerTargetX, centerTargetY, height, isTurbo, onDone)
                        }, if (isTurbo) 50L else 100L)
                    } else {
                        onDone()
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Recents swipe dismiss cancelled, falling back")
                    fallbackClose(onDone)
                }
            }, null)

            if (!dispatched) {
                fallbackClose(onDone)
            }
        } else {
            fallbackClose(onDone)
        }
    }

    private fun swipeCenterCard(
        startX: Float,
        startY: Float,
        screenHeight: Int,
        isTurbo: Boolean = true,
        onDone: () -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, screenHeight * 0.05f)
            }
            val strokeDuration = if (isTurbo) 45L else 120L
            val stroke = GestureDescription.StrokeDescription(swipePath, 0, strokeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { onDone() }
                override fun onCancelled(gestureDescription: GestureDescription?) { onDone() }
            }, null)
        } else {
            onDone()
        }
    }

    private fun collectDismissableTaskCards(
        node: AccessibilityNodeInfo?,
        outList: MutableList<TaskCardCandidate>
    ) {
        if (node == null) return

        for (action in node.actionList) {
            if (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                outList.add(TaskCardCandidate(node, bounds, action.id))
                break
            }
        }

        for (i in 0 until node.childCount) {
            collectDismissableTaskCards(node.getChild(i), outList)
        }
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
