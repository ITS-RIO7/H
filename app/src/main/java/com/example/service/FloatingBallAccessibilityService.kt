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
            isMeetMode: Boolean = false,
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

            // 2. Open Android Recents Overview
            service.performGlobalAction(GLOBAL_ACTION_RECENTS)

            // Reliable, fast timings: 160ms in Turbo mode ensures Overview is fully mounted
            val recentsOpenDelay = if (isTurbo) 160L else 220L
            val postDismissDelay = if (isTurbo) 100L else 160L

            Handler(Looper.getMainLooper()).postDelayed({
                if (isMeetMode) {
                    service.clearAllExceptMeetAndCallFromRecents(screenWidth, screenHeight, isTurbo) {
                        if (postDismissDelay > 0L) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                onFinished()
                            }, postDismissDelay)
                        } else {
                            onFinished()
                        }
                    }
                } else {
                    service.dismissActiveAppFromRecents(screenWidth, screenHeight, targetCard, isTurbo) {
                        if (postDismissDelay > 0L) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                onFinished()
                            }, postDismissDelay)
                        } else {
                            onFinished()
                        }
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
        val actionId: Int,
        val isMeetOrCall: Boolean = false
    )

    private data class VisualCardInfo(
        val bounds: Rect,
        val isMeetOrCall: Boolean,
        val title: String
    )

    /**
     * Clears ALL app cards from Android Recents EXCEPT Google Meet and active Call.
     * Preserves Google Meet and ongoing Call cards intact.
     */
    fun clearAllExceptMeetAndCallFromRecents(
        screenWidth: Int,
        screenHeight: Int,
        isTurbo: Boolean = true,
        onDone: () -> Unit
    ) {
        val width = if (screenWidth > 0) screenWidth else 1080
        val height = if (screenHeight > 0) screenHeight else 2400

        val candidates = mutableListOf<TaskCardCandidate>()
        val visualCards = mutableListOf<VisualCardInfo>()
        val meetCallBounds = mutableListOf<Rect>()

        try {
            val root = rootInActiveWindow
            if (root != null) {
                collectAllTaskCardsWithDetails(root, candidates, visualCards, meetCallBounds, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recents nodes: ${e.message}")
        }

        // 1. Accessibility Dismiss: Dismiss all cards with ACTION_DISMISS that are NOT Meet/Call
        var dismissedViaActionCount = 0
        for (candidate in candidates) {
            if (!candidate.isMeetOrCall) {
                candidate.node.performAction(candidate.actionId)
                dismissedViaActionCount++
                Log.d(TAG, "Dismissed non-Meet task via ACTION_DISMISS at centerX=${candidate.bounds.centerX()}")
            } else {
                Log.d(TAG, "Preserving Meet/Call card at centerX=${candidate.bounds.centerX()}")
            }
        }

        // 2. Gesture Dismiss Swipes: For OEM launchers without ACTION_DISMISS, swipe non-Meet cards
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val isMeetOnRight = meetCallBounds.any { it.centerX() > width * 0.58f }
            val isMeetOnCenter = meetCallBounds.any { kotlin.math.abs(it.centerX() - width / 2) <= width * 0.15f }

            val targetsToSwipe = mutableListOf<Float>()

            // If Meet is not on the right, swipe right card
            if (!isMeetOnRight) {
                targetsToSwipe.add(width * 0.75f)
            }
            // If Meet is not in the center, swipe center card
            if (!isMeetOnCenter) {
                targetsToSwipe.add(width * 0.50f)
            }

            if (targetsToSwipe.isEmpty()) {
                // Only Meet/Call is visible in Recents
                Log.d(TAG, "Only Meet/Call present in recents, preserving it")
                onDone()
                return
            }

            executeSequentialSwipes(targetsToSwipe, height, isTurbo, onDone)
        } else {
            onDone()
        }
    }

    private fun executeSequentialSwipes(
        targetXs: List<Float>,
        screenHeight: Int,
        isTurbo: Boolean,
        onDone: () -> Unit
    ) {
        if (targetXs.isEmpty()) {
            onDone()
            return
        }

        val strokeDuration = if (isTurbo) 85L else 115L
        val intervalDelay = if (isTurbo) 60L else 100L
        val startY = screenHeight * 0.56f
        val endY = screenHeight * 0.04f

        fun swipeAt(index: Int) {
            if (index >= targetXs.size) {
                onDone()
                return
            }

            val targetX = targetXs[index]
            val path = Path().apply {
                moveTo(targetX, startY)
                lineTo(targetX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, strokeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe dismissed non-Meet card at X=$targetX (${index + 1}/${targetXs.size})")
                    if (index + 1 < targetXs.size) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            swipeAt(index + 1)
                        }, intervalDelay)
                    } else {
                        onDone()
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe dismiss cancelled at X=$targetX")
                    onDone()
                }
            }, null)
        }

        swipeAt(0)
    }

    /**
     * Dismisses the active app card from Android Recents:
     * Strictly targets the RIGHT card by default (as requested: "right vala remove kare"),
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

        // 1. Collect all task cards that support ACTION_DISMISS and visual cards
        val candidates = mutableListOf<TaskCardCandidate>()
        val visualCards = mutableListOf<Rect>()
        try {
            val root = rootInActiveWindow
            if (root != null) {
                collectDismissableTaskCards(root, candidates)
                collectVisualTaskCards(root, visualCards, width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching recents nodes: ${e.message}")
        }

        // Strict right-side candidate filtering (centerX MUST be > 55% of screen width)
        val rightDismissCandidate = candidates.filter { it.bounds.centerX() > width * 0.55f }
            .maxByOrNull { it.bounds.centerX() }
        val rightVisualCard = visualCards.filter { it.centerX() > width * 0.55f }
            .maxByOrNull { it.centerX() }

        // Center candidate (closest to width / 2)
        val centerCandidate = candidates.minByOrNull { kotlin.math.abs(it.bounds.centerX() - width / 2) }

        var rightTargetX = width * 0.75f
        var rightTargetY = height * 0.56f
        var centerTargetX = width * 0.50f
        var centerTargetY = height * 0.56f

        if (rightDismissCandidate != null) {
            rightTargetX = rightDismissCandidate.bounds.centerX().toFloat()
            rightTargetY = rightDismissCandidate.bounds.centerY().toFloat().coerceIn(height * 0.35f, height * 0.68f)
            Log.d(TAG, "Found right dismissable node at X=$rightTargetX, Y=$rightTargetY")
        } else if (rightVisualCard != null) {
            rightTargetX = rightVisualCard.centerX().toFloat()
            rightTargetY = rightVisualCard.centerY().toFloat().coerceIn(height * 0.35f, height * 0.68f)
            Log.d(TAG, "Found right visual card at X=$rightTargetX, Y=$rightTargetY")
        } else {
            // Default: 75% width on right side of screen
            rightTargetX = width * 0.75f
            rightTargetY = height * 0.56f
            Log.d(TAG, "Using default right target coordinates: X=$rightTargetX, Y=$rightTargetY")
        }

        if (centerCandidate != null && centerCandidate.bounds.centerX() > 0) {
            centerTargetX = centerCandidate.bounds.centerX().toFloat()
            centerTargetY = centerCandidate.bounds.centerY().toFloat().coerceIn(height * 0.35f, height * 0.68f)
        }

        // Trigger ACTION_DISMISS ONLY on appropriate candidates
        when (targetCard) {
            "right" -> {
                if (rightDismissCandidate != null) {
                    rightDismissCandidate.node.performAction(rightDismissCandidate.actionId)
                    Log.d(TAG, "Triggered ACTION_DISMISS on right card")
                }
            }
            "center" -> {
                if (centerCandidate != null) {
                    centerCandidate.node.performAction(centerCandidate.actionId)
                    Log.d(TAG, "Triggered ACTION_DISMISS on center card")
                }
            }
            "both" -> {
                rightDismissCandidate?.node?.performAction(rightDismissCandidate.actionId)
                centerCandidate?.node?.performAction(centerCandidate.actionId)
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
            val endY = height * 0.04f

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(startX, endY)
            }

            // High-velocity 90ms stroke in Turbo mode (120ms normal)
            val strokeDuration = if (isTurbo) 90L else 120L
            val stroke = GestureDescription.StrokeDescription(swipePath, 0, strokeDuration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Recents swipe dismiss on $targetCard card completed at ($startX, $startY)")
                    if (targetCard == "both") {
                        Handler(Looper.getMainLooper()).postDelayed({
                            swipeCenterCard(centerTargetX, centerTargetY, height, isTurbo, onDone)
                        }, if (isTurbo) 70L else 120L)
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

    private fun collectVisualTaskCards(
        node: AccessibilityNodeInfo?,
        outBounds: MutableList<Rect>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (node == null) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() >= screenWidth * 0.20f && bounds.height() >= screenHeight * 0.20f &&
            bounds.height() < screenHeight * 0.95f) {
            val cls = node.className?.toString() ?: ""
            if (cls.contains("Task") || cls.contains("FrameLayout") || cls.contains("Card") ||
                cls.contains("View") || node.isClickable) {
                outBounds.add(bounds)
            }
        }
        for (i in 0 until node.childCount) {
            collectVisualTaskCards(node.getChild(i), outBounds, screenWidth, screenHeight)
        }
    }

    private fun isMeetOrCallNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.toString()?.lowercase() ?: ""
        val pkg = node.packageName?.toString()?.lowercase() ?: ""

        val keywords = listOf(
            "meet", "tachyon", "meetings", "call", "calling", "ongoing",
            "in call", "rejoin", "return to call", "phone", "dialer", "incall",
            "कॉल", "मीटिंग", "पुनः जुड़ें", "शामिल हों"
        )

        if (keywords.any { text.contains(it) || desc.contains(it) || viewId.contains(it) || pkg.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = child.text?.toString()?.lowercase() ?: ""
            val childDesc = child.contentDescription?.toString()?.lowercase() ?: ""
            val childViewId = child.viewIdResourceName?.toString()?.lowercase() ?: ""
            if (keywords.any { childText.contains(it) || childDesc.contains(it) || childViewId.contains(it) }) {
                return true
            }
        }
        return false
    }

    private fun collectAllTaskCardsWithDetails(
        node: AccessibilityNodeInfo?,
        outCandidates: MutableList<TaskCardCandidate>,
        outVisualCards: MutableList<VisualCardInfo>,
        outMeetCallBounds: MutableList<Rect>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (node == null) return

        val isMeet = isMeetOrCallNode(node)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (isMeet && bounds.width() > screenWidth * 0.15f && bounds.height() > screenHeight * 0.15f) {
            outMeetCallBounds.add(bounds)
        }

        for (action in node.actionList) {
            if (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id) {
                outCandidates.add(TaskCardCandidate(node, bounds, action.id, isMeet))
                break
            }
        }

        if (bounds.width() >= screenWidth * 0.20f && bounds.height() >= screenHeight * 0.20f &&
            bounds.height() < screenHeight * 0.95f) {
            val cls = node.className?.toString() ?: ""
            if (cls.contains("Task") || cls.contains("FrameLayout") || cls.contains("Card") ||
                cls.contains("View") || node.isClickable) {
                val title = node.contentDescription?.toString() ?: node.text?.toString() ?: ""
                outVisualCards.add(VisualCardInfo(bounds, isMeet, title))
            }
        }

        for (i in 0 until node.childCount) {
            collectAllTaskCardsWithDetails(
                node.getChild(i),
                outCandidates,
                outVisualCards,
                outMeetCallBounds,
                screenWidth,
                screenHeight
            )
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
