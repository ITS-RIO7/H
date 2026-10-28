package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.data.BallSettings
import com.example.data.FloatingBallPreferences
import kotlin.math.abs
import kotlin.math.hypot

class FloatingBallOverlay(
    private val context: Context,
    private val onCloseRequested: () -> Unit
) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = FloatingBallPreferences.getInstance(context)

    private var ballView: FrameLayout? = null
    private var ballIconImageView: ImageView? = null
    private var closeTargetView: FrameLayout? = null

    private lateinit var ballLayoutParams: WindowManager.LayoutParams
    private lateinit var closeLayoutParams: WindowManager.LayoutParams

    private var screenWidth = 0
    private var screenHeight = 0
    private var isDragging = false
    private var isOverCloseTarget = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchStartTime = 0L

    init {
        updateScreenDimensions()
        createCloseTargetView()
        createBallView()
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun createCloseTargetView() {
        val sizePx = dpToPx(72)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        closeLayoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(48)
        }

        val frame = FrameLayout(context).apply {
            visibility = View.GONE
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCCEF4444.toInt()) // Red accent
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bg
            elevation = dpToPx(8).toFloat()

            val textView = TextView(context).apply {
                text = "✕"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            addView(
                textView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        closeTargetView = frame

        try {
            windowManager.addView(closeTargetView, closeLayoutParams)
        } catch (e: Exception) {
            // Error handling if overlay fails
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBallView() {
        val settings = prefs.getSettings()
        val sizePx = dpToPx(settings.ballSizeDp)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        ballLayoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - sizePx - dpToPx(12)
            y = screenHeight / 3
        }

        val ballFrame = FrameLayout(context).apply {
            alpha = settings.ballOpacity
            elevation = dpToPx(10).toFloat()
            clipToOutline = true

            // Glowing Outer Ring & Circular Background
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE0F172A.toInt()) // Deep Dark Indigo/Slate
                setStroke(dpToPx(3), 0xFF00E5FF.toInt()) // Glowing Neon Cyan
            }
            background = bg

            // Center Target App Icon or Quick Switch Orb
            val iconView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val padding = dpToPx(8)
                setPadding(padding, padding, padding, padding)
            }
            ballIconImageView = iconView
            addView(
                iconView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )

            // Touch Listener for Drag, Tap, and Edge Snap
            setOnTouchListener { view, event ->
                handleTouchEvent(view, event)
            }
        }

        ballView = ballFrame
        updateBallIcon()

        try {
            windowManager.addView(ballView, ballLayoutParams)
        } catch (e: Exception) {
            // Handled
        }
    }

    fun updateBallIcon() {
        val settings = prefs.getSettings()
        val pkg = settings.targetPackage

        if (pkg.isNotEmpty() && settings.showAppIcon) {
            try {
                val icon = context.packageManager.getApplicationIcon(pkg)
                ballIconImageView?.setImageDrawable(icon)
                return
            } catch (e: Exception) {
                // Fallback below
            }
        }

        // Default glowing launcher icon if no app selected
        ballIconImageView?.setImageResource(android.R.drawable.ic_menu_rotate)
        ballIconImageView?.setColorFilter(0xFF00E5FF.toInt())
    }

    fun applySettings(settings: BallSettings) {
        val sizePx = dpToPx(settings.ballSizeDp)
        ballLayoutParams.width = sizePx
        ballLayoutParams.height = sizePx
        ballView?.alpha = settings.ballOpacity
        updateBallIcon()

        try {
            ballView?.let { windowManager.updateViewLayout(it, ballLayoutParams) }
        } catch (e: Exception) {
            // Handled
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ballLayoutParams.x
                initialY = ballLayoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                touchStartTime = System.currentTimeMillis()
                isDragging = false

                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(120).start()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY

                if (!isDragging && (abs(deltaX) > dpToPx(8) || abs(deltaY) > dpToPx(8))) {
                    isDragging = true
                    showCloseTarget(true)
                }

                if (isDragging) {
                    ballLayoutParams.x = initialX + deltaX.toInt()
                    ballLayoutParams.y = initialY + deltaY.toInt()

                    try {
                        windowManager.updateViewLayout(view, ballLayoutParams)
                    } catch (e: Exception) {
                        // Handled
                    }

                    checkCloseTargetHover(event.rawX, event.rawY)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                showCloseTarget(false)

                val duration = System.currentTimeMillis() - touchStartTime
                val distance = hypot(
                    (event.rawX - initialTouchX).toDouble(),
                    (event.rawY - initialTouchY).toDouble()
                )

                if (isOverCloseTarget) {
                    // Dropped in close zone
                    vibrateFeedback()
                    onCloseRequested()
                    return true
                }

                if (!isDragging && distance < dpToPx(12) && duration < 350) {
                    // Click / Tap detected!
                    onBallClicked()
                } else if (prefs.getSettings().snapToEdge) {
                    snapToScreenEdge()
                }
                return true
            }
        }
        return false
    }

    private fun onBallClicked() {
        val settings = prefs.getSettings()
        vibrateFeedback()

        // Snappy visual click ripple / bounce
        ballView?.animate()
            ?.scaleX(1.18f)
            ?.scaleY(1.18f)
            ?.setDuration(50)
            ?.withEndAction {
                ballView?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(50)?.start()
            }
            ?.start()

        val targetPkg = settings.targetPackage
        if (targetPkg.isEmpty()) {
            Toast.makeText(
                context,
                "No target app selected! Open Floating Ball to select an app.",
                Toast.LENGTH_SHORT
            ).show()
            val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(mainIntent)
            return
        }

        val isMeet = settings.isMeetMode ||
                targetPkg == "com.google.android.apps.tachyon" ||
                targetPkg == "com.google.android.apps.meetings" ||
                targetPkg.contains("tachyon") ||
                targetPkg.contains("meetings")

        // Purge BOTH apps from Recents at the exact moment of ball click
        if (settings.autoCleanActiveApp) {
            if (!FloatingBallAccessibilityService.isServiceRunning) {
                Toast.makeText(
                    context,
                    "⚠️ Accessibility permission required for Recents auto-clean!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            FloatingBallAccessibilityService.purgeBothAppsFromRecentsAndSwitch(
                targetPackage = targetPkg,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                targetCard = settings.recentsDismissTarget,
                isTurbo = settings.turboSpeed,
                isMeetMode = isMeet
            ) {
                executeLaunch(targetPkg, settings)
            }
        } else {
            MainActivity.closeAndRemoveFromRecents()
            executeLaunch(targetPkg, settings)
        }
    }

    private fun executeLaunch(targetPkg: String, settings: BallSettings) {
        val isMeet = settings.isMeetMode || targetPkg.contains("tachyon") || targetPkg.contains("meetings")
        if (isMeet) {
            FloatingBallAccessibilityService.armAutoClickForMeetCall()

            // 1. If we have a cached ongoing call PendingIntent from Google Meet, launch directly into call!
            val meetPendingIntent = FloatingBallAccessibilityService.cachedMeetCallPendingIntent
            if (meetPendingIntent != null) {
                try {
                    meetPendingIntent.send()
                    if (settings.autoHideOnLaunch) onCloseRequested()
                    return
                } catch (e: Exception) {
                    // Fallback to direct URI / standard launch
                }
            }
        }

        // Check if user set a direct meeting link or room code
        val directLink = settings.meetingLinkOrCode.trim()
        if (isMeet && directLink.isNotEmpty()) {
            val formattedUri = when {
                directLink.startsWith("http://") || directLink.startsWith("https://") -> android.net.Uri.parse(directLink)
                directLink.contains("meet.google.com") -> android.net.Uri.parse("https://$directLink")
                else -> android.net.Uri.parse("https://meet.google.com/$directLink")
            }
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, formattedUri).apply {
                    setPackage(targetPkg)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                context.startActivity(directIntent)
                if (settings.autoHideOnLaunch) onCloseRequested()
                return
            } catch (e: Exception) {
                // Fallback to standard launch below
            }
        }

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                // If it's Google Meet or ongoing call mode, bring directly to front
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                context.startActivity(launchIntent)

                // Schedule instant auto-clicks for Google Meet ongoing call buttons
                if (isMeet) {
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    handler.post { FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 30)
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 80)
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 160)
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 300)
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 600)
                    handler.postDelayed({ FloatingBallAccessibilityService.instance?.tryAutoClickCallButton() }, 1100)
                }

                // If user enabled auto-hide / close ball upon launch
                if (settings.autoHideOnLaunch) {
                    onCloseRequested()
                }
            } else {
                Toast.makeText(
                    context,
                    "Unable to launch ${settings.targetAppName}. App might have been uninstalled.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Launch error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCloseTarget(show: Boolean) {
        closeTargetView?.let { closeView ->
            if (show) {
                closeView.visibility = View.VISIBLE
                closeView.alpha = 0f
                closeView.animate().alpha(1f).setDuration(200).start()
            } else {
                closeView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction { closeView.visibility = View.GONE }
                    .start()
            }
        }
    }

    private fun checkCloseTargetHover(touchX: Float, touchY: Float) {
        val targetY = screenHeight - dpToPx(100)
        val targetX = screenWidth / 2f
        val distance = hypot((touchX - targetX).toDouble(), (touchY - targetY).toDouble())

        if (distance < dpToPx(70)) {
            if (!isOverCloseTarget) {
                isOverCloseTarget = true
                vibrateFeedback()
                closeTargetView?.animate()?.scaleX(1.3f)?.scaleY(1.3f)?.setDuration(150)?.start()
            }
        } else {
            if (isOverCloseTarget) {
                isOverCloseTarget = false
                closeTargetView?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)?.start()
            }
        }
    }

    private fun snapToScreenEdge() {
        val ballSize = dpToPx(prefs.getSettings().ballSizeDp)
        val currentX = ballLayoutParams.x
        val middleX = (screenWidth - ballSize) / 2
        val targetX = if (currentX < middleX) dpToPx(8) else screenWidth - ballSize - dpToPx(8)

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { anim ->
                ballLayoutParams.x = anim.animatedValue as Int
                try {
                    ballView?.let { windowManager.updateViewLayout(it, ballLayoutParams) }
                } catch (e: Exception) {
                    // Handled
                }
            }
        }
        animator.start()
    }

    private fun vibrateFeedback() {
        if (!prefs.getSettings().vibrationFeedback) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(25)
                }
            }
        } catch (e: Exception) {
            // Graceful fallback
        }
    }

    fun destroy() {
        try {
            ballView?.let { windowManager.removeView(it) }
            ballView = null
        } catch (e: Exception) {
            // Handled
        }

        try {
            closeTargetView?.let { windowManager.removeView(it) }
            closeTargetView = null
        } catch (e: Exception) {
            // Handled
        }
    }
}
