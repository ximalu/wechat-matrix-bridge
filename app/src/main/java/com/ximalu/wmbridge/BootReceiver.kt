package com.ximalu.wmbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ximalu.wmbridge.data.Config
import com.ximalu.wmbridge.service.ForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val config = Config(context)
            if (config.serviceEnabled && config.isConfigured) {
                Log.i("WMBridge", "Boot completed, starting service")
                ForegroundService.start(context)
            }
        }
    }
}
