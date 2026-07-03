package com.ximalu.wmbridge.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import com.ximalu.wmbridge.MainActivity
import com.ximalu.wmbridge.R
import com.ximalu.wmbridge.data.Config

class ForegroundService : Service() {

    private var overlayView: FrameLayout? = null
    private var screenStateReceiver: ScreenStateReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Force the system to bind our NotificationListener in the :bridge process.
        // After an app update Android may delay re-binding; this ensures it happens immediately.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cn = ComponentName(this, NotificationListener::class.java)
            NotificationListenerService.requestRebind(cn)
        }
        startServiceWithNotification()
        registerKeepAlive()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESTART_NOTIFICATION -> startServiceWithNotification()
            ACTION_TOGGLE_OVERLAY -> {
                if (overlayView != null) removeOverlay() else addOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        unregisterScreenReceiver()
        super.onDestroy()
    }

    // ── Notification ──

    private fun startServiceWithNotification() {
        val config = Config(this)
        createNotificationChannel(config.showPersistentNotification)
        startForeground(NOTIFICATION_ID, buildNotification(config.showPersistentNotification))
    }

    private fun createNotificationChannel(hidden: Boolean) {
        val importance = if (hidden) {
            NotificationManager.IMPORTANCE_NONE
        } else {
            NotificationManager.IMPORTANCE_LOW
        }
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESC
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(hidden: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (hidden) R.string.notification_title_hidden else R.string.notification_title
        val text = if (hidden) R.string.notification_text_hidden else R.string.notification_text
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(title))
            .setContentText(getString(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ── Overlay keep-alive (TYPE_ACCESSIBILITY_OVERLAY, no SYSTEM_ALERT_WINDOW needed) ──

    private fun addOverlay() {
        if (overlayView != null) return
        val config = Config(this)
        if (!config.keepAliveOverlay) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(0, 0)
            }
            val params = WindowManager.LayoutParams(
                0, 0,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                alpha = 0f
            }
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            // Overlay not supported on this device — safe to ignore
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
            } catch (_: Exception) { }
            overlayView = null
        }
    }

    // ── SCREEN_OFF broadcast keep-alive ──

    private fun registerKeepAlive() {
        val config = Config(this)
        if (config.keepAliveNotification) {
            startServiceWithNotification()
        }
        if (config.keepAliveOverlay) {
            addOverlay()
        }
        val receiver = ScreenStateReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, filter)
        screenStateReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        screenStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { }
            screenStateReceiver = null
        }
    }

    companion object {
        private const val CHANNEL_ID = "wmbridge_foreground"
        private const val CHANNEL_NAME = "通知桥接服务"
        private const val CHANNEL_DESC = "微信通知转发到 Matrix 的后台服务"
        private const val NOTIFICATION_ID = 1
        const val ACTION_RESTART_NOTIFICATION = "com.ximalu.wmbridge.RESTART_NOTIFICATION"
        const val ACTION_TOGGLE_OVERLAY = "com.ximalu.wmbridge.TOGGLE_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun restartNotification(context: Context) {
            val intent = Intent(context, ForegroundService::class.java).apply {
                action = ACTION_RESTART_NOTIFICATION
            }
            context.startService(intent)
        }

        inline fun <reified T : android.app.Service> isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            @Suppress("DEPRECATION")
            return manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == T::class.java.name
            }
        }
    }
}
