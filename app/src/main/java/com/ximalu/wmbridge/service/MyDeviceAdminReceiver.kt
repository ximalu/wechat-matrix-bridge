package com.ximalu.wmbridge.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 设备管理员接收器。
 * 激活后用户卸载 APK 必须先取消设备管理员，增加卸载门槛。
 * 代码量极小——继承即可，逻辑通过声明生效。
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled")
    }

    companion object {
        private const val TAG = "WMBridge.DeviceAdmin"
    }
}
