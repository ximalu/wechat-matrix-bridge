package com.ximalu.wmbridge.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ximalu.wmbridge.MainActivity
import com.ximalu.wmbridge.R
import com.ximalu.wmbridge.data.Config

class ForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startServiceWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART_NOTIFICATION) {
            startServiceWithNotification()
        }
        return START_STICKY
    }

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

    companion object {
        private const val CHANNEL_ID = "wmbridge_foreground"
        private const val CHANNEL_NAME = "通知桥接服务"
        private const val CHANNEL_DESC = "微信通知转发到 Matrix 的后台服务"
        private const val NOTIFICATION_ID = 1
        const val ACTION_RESTART_NOTIFICATION = "com.ximalu.wmbridge.RESTART_NOTIFICATION"

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
            return manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == T::class.java.name
            }
        }
    }
}
