package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.FloatingBallApp
import com.example.MainActivity
import com.example.R
import com.example.data.FloatingBallPreferences

class FloatingBallService : Service() {

    companion object {
        const val ACTION_START = "com.example.floatingball.ACTION_START"
        const val ACTION_STOP = "com.example.floatingball.ACTION_STOP"
        const val ACTION_UPDATE_CONFIG = "com.example.floatingball.ACTION_UPDATE_CONFIG"
        const val ACTION_LAUNCH_TARGET = "com.example.floatingball.ACTION_LAUNCH_TARGET"

        private const val NOTIFICATION_ID = 9001

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateConfig(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            context.startService(intent)
        }
    }

    private var overlay: FloatingBallOverlay? = null
    private lateinit var prefs: FloatingBallPreferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = FloatingBallPreferences.getInstance(this)
        startForegroundWithNotification()
        showOverlay()
        prefs.setServiceEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                updateNotification()
                overlay?.applySettings(prefs.getSettings())
            }
            ACTION_LAUNCH_TARGET -> {
                val target = prefs.getSettings().targetPackage
                if (target.isNotEmpty()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(target)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                }
            }
            ACTION_START, null -> {
                updateNotification()
                overlay?.applySettings(prefs.getSettings())
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlay == null) {
            overlay = FloatingBallOverlay(this) {
                stopSelf()
            }
        } else {
            overlay?.applySettings(prefs.getSettings())
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val settings = prefs.getSettings()
        val appName = if (settings.targetPackage.isNotEmpty()) settings.targetAppName else "No app selected"

        // Open Main App Settings
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Switch Action
        val launchIntent = Intent(this, FloatingBallService::class.java).apply {
            action = ACTION_LAUNCH_TARGET
        }
        val launchPendingIntent = PendingIntent.getService(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Service Action
        val stopIntent = Intent(this, FloatingBallService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FloatingBallApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_floating_ball)
            .setContentTitle("Floating Ball Active")
            .setContentText("Target: $appName • Tap floating ball to switch")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_rotate, "Switch Target", launchPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay?.destroy()
        overlay = null
        prefs.setServiceEnabled(false)
    }
}
