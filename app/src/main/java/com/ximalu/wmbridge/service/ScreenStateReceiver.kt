package com.ximalu.wmbridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 监听 SCREEN_OFF / SCREEN_ON 等广播，维持进程活跃。
 * 注册在 ForegroundService 中，极低成本。
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen off — keeping alive")
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen on")
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User present")
            }
        }
    }

    companion object {
        private const val TAG = "WMBridge.ScreenState"
    }
}
