package com.ximalu.wmbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.service.ForegroundService

/**
 * 开机自启接收器。
 * 如果用户之前开启了服务，开机后自动重启服务。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val config = Config(context)
            if (config.serviceEnabled) {
                Log.i(TAG, "Boot completed, restarting service")
                ForegroundService.start(context)
            }
        }
    }

    companion object {
        private const val TAG = "WMBridge.BootReceiver"
    }
}
